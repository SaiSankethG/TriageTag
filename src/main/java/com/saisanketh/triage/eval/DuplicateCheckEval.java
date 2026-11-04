package com.saisanketh.triage.eval;

import com.saisanketh.triage.ai.DuplicateCheck;
import com.saisanketh.triage.ai.DuplicateCheck.Verdict;
import com.saisanketh.triage.ai.DuplicateVerifier;
import com.saisanketh.triage.ai.IssueTextRepository;
import com.saisanketh.triage.ai.TriageLlm;
import com.saisanketh.triage.triage.SimilarIssue;
import com.saisanketh.triage.triage.VectorDuplicateSearch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Measures sai's comment-mode rule "top-1 score >= threshold AND the LLM says same root problem" on the replay set:
 * how many correct comments it keeps (duplicates) and how many false alarms it removes (non-duplicates).
 * Calls the LLM once per flagged issue (a few hundred calls on spring-boot). Writes docs/m4-duplicate-check.md.
 */
@Slf4j
@Service
public class DuplicateCheckEval {

    private final JdbcTemplate jdbc;
    private final VectorDuplicateSearch vectorSearch;
    private final DuplicateVerifier verifier;
    private final IssueTextRepository issueTexts;
    private final EvalService evalService;
    private final TriageLlm llm;
    private final double threshold;
    private final Path resultsFile;

    public DuplicateCheckEval(JdbcTemplate jdbc, VectorDuplicateSearch vectorSearch, DuplicateVerifier verifier,
                              IssueTextRepository issueTexts, EvalService evalService, TriageLlm llm,
                              @Value("${triage.duplicates.comment-threshold:0.825}") double threshold,
                              @Value("${triage.eval.duplicate-check-file:docs/m4-duplicate-check.md}") String resultsFile) {
        this.jdbc = jdbc;
        this.vectorSearch = vectorSearch;
        this.verifier = verifier;
        this.issueTexts = issueTexts;
        this.evalService = evalService;
        this.llm = llm;
        this.threshold = threshold;
        this.resultsFile = Path.of(resultsFile);
    }

    /** One issue checked: was it flagged, was the match right, what did the LLM say. */
    private record Outcome(boolean flagged, boolean correct, DuplicateCheck check) {
        boolean confirmed() {
            return flagged && check != null && check.confirmed();
        }
    }

    public DuplicateCheckReport evaluate(String repo) {
        long start = System.nanoTime();

        List<long[]> duplicates = jdbc.query("""
                SELECT d.id, o.number AS original
                FROM issue d JOIN issue o ON o.repo = d.repo AND o.number = d.duplicate_of
                WHERE lower(d.repo) = lower(?) AND o.created_at < d.created_at
                  AND d.author_association NOT IN ('OWNER', 'MEMBER', 'COLLABORATOR')
                ORDER BY d.number
                """, (rs, row) -> new long[]{rs.getLong("id"), rs.getInt("original")}, repo);
        List<Long> nonDuplicates = evalService.nonDuplicateSample(repo, true);
        log.info("Duplicate-check eval {}: {} duplicates, {} non-duplicates, threshold {}", repo, duplicates.size(),
                nonDuplicates.size(), threshold);

        int dupFlagged = 0, dupFlaggedCorrect = 0, dupConfirmed = 0, dupConfirmedCorrect = 0;
        int nonFlagged = 0, nonConfirmed = 0, unknown = 0, calls = 0;
        long tokens = 0;
        String promptVersion = null;

        for (long[] d : duplicates) {
            Outcome o = check(repo, d[0], (int) d[1]);
            if (o.flagged()) {
                dupFlagged++;
                calls++;
                tokens += o.check().tokens();
                promptVersion = o.check().promptVersion();
                unknown += o.check().verdict() == Verdict.UNKNOWN ? 1 : 0;
                dupFlaggedCorrect += o.correct() ? 1 : 0;
                dupConfirmed += o.confirmed() ? 1 : 0;
                dupConfirmedCorrect += o.confirmed() && o.correct() ? 1 : 0;
            }
        }
        for (Long id : nonDuplicates) {
            Outcome o = check(repo, id, -1);
            if (o.flagged()) {
                nonFlagged++;
                calls++;
                tokens += o.check().tokens();
                promptVersion = o.check().promptVersion();
                unknown += o.check().verdict() == Verdict.UNKNOWN ? 1 : 0;
                nonConfirmed += o.confirmed() ? 1 : 0;
            }
        }

        DuplicateCheckReport report = new DuplicateCheckReport(repo, threshold, llm.model(), promptVersion,
                duplicates.size(), dupFlagged, dupFlaggedCorrect, dupConfirmed, dupConfirmedCorrect,
                nonDuplicates.size(), nonFlagged, nonConfirmed, unknown, calls, tokens,
                (System.nanoTime() - start) / 1_000_000);
        write(report);
        log.info("Duplicate-check eval finished: {}", report);
        return report;
    }

    /** @param originalNumber the true original, or -1 for a non-duplicate */
    private Outcome check(String repo, long issueId, int originalNumber) {
        List<SimilarIssue> top = vectorSearch.findSimilar(issueId, 1);
        if (top.isEmpty() || top.get(0).score() < threshold) {
            return new Outcome(false, false, null);
        }
        SimilarIssue match = top.get(0);
        DuplicateCheck check = verifier.check(repo, issueTexts.byId(issueId).orElseThrow(),
                issueTexts.byNumber(repo, match.number()).orElseThrow());
        return new Outcome(true, match.number() == originalNumber, check);
    }

    private void write(DuplicateCheckReport r) {
        try {
            Files.writeString(resultsFile, render(r));
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + resultsFile, e);
        }
    }

    static String render(DuplicateCheckReport r) {
        String warning = "fake".equals(r.llmModel())
                ? "\n> **FAKE LLM: these numbers test the code path only. They say nothing about a real model.**\n"
                : "";
        return String.format(Locale.ROOT, """
                # M4: LLM check of the top duplicate match (comment-mode rule) on %s
                %s
                Generated by `POST /eval/%s/duplicate-check` (DuplicateCheckEval). Model: `%s`, prompt `%s`.
                Duration: %.1f s, %d LLM calls, %d tokens, %d without a valid answer (counted as "no").

                Rule: comment only if the vector top-1 score is >= **%.3f** AND the LLM says the two issues are the same
                root problem. External reporters only (what comment mode would see); the repo's candidate filter, if any.

                | | Score only | Score + LLM "yes" |
                |---|---|---|
                | Duplicates (%d) flagged | %d | %d |
                | ... top match is the true original (correct comment) | %d (%s of duplicates) | %d (%s of duplicates) |
                | ... top match is another issue | %d | %d |
                | Non-duplicates (%d) flagged = false alarms | %d (%s) | %d (%s) |
                """,
                r.repo(), warning, r.repo(), r.llmModel(), r.promptVersion(), r.durationMs() / 1000.0, r.llmCalls(),
                r.tokens(), r.unknownVerdicts(), r.threshold(),
                r.duplicates(), r.duplicatesFlagged(), r.duplicatesConfirmed(),
                r.duplicatesFlaggedCorrect(), pct(r.duplicatesFlaggedCorrect(), r.duplicates()),
                r.duplicatesConfirmedCorrect(), pct(r.duplicatesConfirmedCorrect(), r.duplicates()),
                r.duplicatesFlagged() - r.duplicatesFlaggedCorrect(), r.duplicatesConfirmed() - r.duplicatesConfirmedCorrect(),
                r.nonDuplicates(), r.nonDuplicatesFlagged(), pct(r.nonDuplicatesFlagged(), r.nonDuplicates()),
                r.nonDuplicatesConfirmed(), pct(r.nonDuplicatesConfirmed(), r.nonDuplicates()));
    }

    private static String pct(int part, int whole) {
        return whole == 0 ? "-" : String.format(Locale.ROOT, "%.1f%%", 100.0 * part / whole);
    }
}
