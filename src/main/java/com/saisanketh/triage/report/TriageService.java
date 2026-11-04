package com.saisanketh.triage.report;

import com.saisanketh.triage.ai.DraftReply;
import com.saisanketh.triage.ai.DraftReplyService;
import com.saisanketh.triage.ai.DuplicateCheck;
import com.saisanketh.triage.ai.DuplicateVerifier;
import com.saisanketh.triage.ai.IssueText;
import com.saisanketh.triage.ai.IssueTextRepository;
import com.saisanketh.triage.ai.MissingDetails;
import com.saisanketh.triage.ai.MissingDetailsChecker;
import com.saisanketh.triage.ai.TokenBudget;
import com.saisanketh.triage.ai.TriageLlm;
import com.saisanketh.triage.report.TriageReport.Duplicate;
import com.saisanketh.triage.triage.FixedHint;
import com.saisanketh.triage.triage.FixedHintService;
import com.saisanketh.triage.triage.LabelSuggester;
import com.saisanketh.triage.triage.LabelSuggester.LabelSuggestion;
import com.saisanketh.triage.triage.SimilarIssue;
import com.saisanketh.triage.triage.VectorDuplicateSearch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Builds and stores the triage report for one issue: duplicates (vector top 3), the LLM check of the top match (only if
 * it scores above the comment threshold), labels, missing details, the "already fixed?" hint, and a RAG draft reply.
 *
 * <p>Every LLM step handles its own failures (the part says UNKNOWN), so a report is always written; only a database
 * error stops it (and the Kafka worker then retries the message). Over the daily token budget, the LLM steps are refused
 * and the report's notes say so.
 */
@Slf4j
@Service
public class TriageService {

    static final int DUPLICATES = 3;

    private final JdbcTemplate jdbc;
    private final IssueTextRepository issueTexts;
    private final VectorDuplicateSearch vectorSearch;
    private final LabelSuggester labelSuggester;
    private final DuplicateVerifier duplicateVerifier;
    private final MissingDetailsChecker missingDetailsChecker;
    private final FixedHintService fixedHintService;
    private final DraftReplyService draftReplyService;
    private final TriageReportRepository reports;
    private final TriageLlm llm;
    private final TokenBudget budget;
    private final Clock clock;
    private final double commentThreshold;
    private final double fixedHintMinScore;

    public TriageService(JdbcTemplate jdbc, IssueTextRepository issueTexts, VectorDuplicateSearch vectorSearch,
                         LabelSuggester labelSuggester, DuplicateVerifier duplicateVerifier,
                         MissingDetailsChecker missingDetailsChecker, FixedHintService fixedHintService,
                         DraftReplyService draftReplyService, TriageReportRepository reports, TriageLlm llm,
                         TokenBudget budget, Clock clock,
                         @Value("${triage.duplicates.comment-threshold:0.825}") double commentThreshold,
                         @Value("${triage.fixed-hint.min-score:0.75}") double fixedHintMinScore) {
        this.jdbc = jdbc;
        this.issueTexts = issueTexts;
        this.vectorSearch = vectorSearch;
        this.labelSuggester = labelSuggester;
        this.duplicateVerifier = duplicateVerifier;
        this.missingDetailsChecker = missingDetailsChecker;
        this.fixedHintService = fixedHintService;
        this.draftReplyService = draftReplyService;
        this.reports = reports;
        this.llm = llm;
        this.budget = budget;
        this.clock = clock;
        this.commentThreshold = commentThreshold;
        this.fixedHintMinScore = fixedHintMinScore;
    }

    /**
     * For the Kafka worker: create the report only if the issue has none yet, so a redelivered "opened" event
     * doesn't pay for the LLM calls twice.
     *
     * @return true if a report was created now
     */
    public boolean createIfMissing(long issueId) {
        if (reports.exists(issueId)) {
            return false;
        }
        create(issueId);
        return true;
    }

    /** (Re)creates the report of an issue by repo + number; empty if no such issue is stored. */
    public Optional<TriageReport> create(String repo, int number) {
        return jdbc.queryForList("SELECT id FROM issue WHERE lower(repo) = lower(?) AND number = ?", Long.class,
                repo, number).stream().findFirst().map(this::create);
    }

    public TriageReport create(long issueId) {
        String repo = jdbc.queryForObject("SELECT repo FROM issue WHERE id = ?", String.class, issueId);
        IssueText issue = issueTexts.byId(issueId).orElseThrow();
        // Over budget: the LLM steps still run, but GuardedTriageLlm refuses each call, so each part says UNKNOWN with
        // the reason, and the report says why at the top. Duplicates, labels and the fixed hint need no LLM.
        String notes = budget.hasRoom() ? null
                : "LLM steps skipped: the daily token budget (" + budget.dailyLimit() + " tokens) is used up.";

        List<SimilarIssue> similar = vectorSearch.findSimilar(issueId, DUPLICATES);
        List<Duplicate> duplicates = similar.stream()
                .map(s -> new Duplicate(s.number(), s.title(), issueUrl(repo, s.number()), s.score())).toList();
        Optional<SimilarIssue> top = similar.stream().findFirst();

        DuplicateCheck duplicateCheck = null;
        if (top.isPresent() && top.get().score() >= commentThreshold) {
            duplicateCheck = duplicateVerifier.check(repo, issue,
                    issueTexts.byNumber(repo, top.get().number()).orElseThrow());
        }
        List<LabelSuggestion> labels = labelSuggester.suggest(issueId);
        MissingDetails missing = missingDetailsChecker.check(repo, issue.title(), issue.body());
        FixedHint fixedHint = top.filter(t -> t.score() >= fixedHintMinScore)
                .flatMap(t -> fixedHintService.hint(repo, t.number(), issue.body())).orElse(null);
        DraftReply draft = draftReplyService.draft(repo, issue, similar);

        Set<String> prompts = new LinkedHashSet<>();
        int tokens = 0;
        if (duplicateCheck != null) {
            prompts.add(duplicateCheck.promptVersion());
            tokens += duplicateCheck.tokens();
        }
        prompts.add(missing.promptVersion());
        tokens += missing.tokens();
        if (draft.promptVersion() != null) {
            prompts.add(draft.promptVersion());
            tokens += draft.tokens();
        }

        TriageReport report = new TriageReport(issueId, clock.instant(), duplicates, duplicateCheck, labels, missing,
                fixedHint, draft, llm.model(), String.join(",", prompts), tokens, notes);
        reports.save(report);
        log.info("Triage report for {}#{}: {} duplicates, {} labels, {} missing, draft {}, {} tokens", repo,
                issue.number(), duplicates.size(), labels.size(), missing.missing().size(), draft.status(), tokens);
        return report;
    }

    static String issueUrl(String repo, int number) {
        return "https://github.com/" + repo + "/issues/" + number;
    }
}
