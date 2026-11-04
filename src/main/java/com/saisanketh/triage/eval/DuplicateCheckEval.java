package com.saisanketh.triage.eval;

import com.saisanketh.triage.ai.DuplicateCheck;
import com.saisanketh.triage.ai.DuplicateCheck.Verdict;
import com.saisanketh.triage.ai.DuplicateVerifier;
import com.saisanketh.triage.ai.IssueText;
import com.saisanketh.triage.ai.IssueTextRepository;
import com.saisanketh.triage.ai.TriageLlm;
import com.saisanketh.triage.eval.DuplicateCheckReport.Item;
import com.saisanketh.triage.eval.DuplicateCheckReport.Row;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * Measures the comment-mode rule "top-1 score >= threshold AND the LLM says same root problem" on the replay set:
 * how many correct comments it keeps (duplicates) and how many false alarms it leaves (non-duplicates), at the
 * configured comment threshold and at stricter ones ({@code triage.eval.extra-thresholds}).
 *
 * <p>The LLM is called once per issue that passes the LOWEST threshold (a few hundred calls on spring-boot); every
 * stricter threshold reuses those answers. Writes docs/m4-duplicate-check.md, including the confirmed false alarms for
 * reading by hand.
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
    private final List<Double> thresholds;
    private final Path resultsFile;

    public DuplicateCheckEval(JdbcTemplate jdbc, VectorDuplicateSearch vectorSearch, DuplicateVerifier verifier,
                              IssueTextRepository issueTexts, EvalService evalService, TriageLlm llm,
                              @Value("${triage.duplicates.comment-threshold:0.90}") double commentThreshold,
                              @Value("${triage.eval.extra-thresholds:0.825,0.85,0.90}") List<Double> extraThresholds,
                              @Value("${triage.eval.duplicate-check-file:docs/m4-duplicate-check.md}") String resultsFile) {
        this.jdbc = jdbc;
        this.vectorSearch = vectorSearch;
        this.verifier = verifier;
        this.issueTexts = issueTexts;
        this.evalService = evalService;
        this.llm = llm;
        TreeSet<Double> all = new TreeSet<>(extraThresholds);
        all.add(commentThreshold);
        this.thresholds = List.copyOf(all);   // sorted, lowest first, no repeats
        this.resultsFile = Path.of(resultsFile);
    }

    /** One issue checked at the lowest threshold. check is null if it wasn't flagged even there. */
    record Outcome(boolean duplicate, boolean correct, double score, Item item, DuplicateCheck check) {
        boolean flaggedAt(double threshold) {
            return check != null && score >= threshold;
        }

        boolean confirmedAt(double threshold) {
            return flaggedAt(threshold) && check.confirmed();
        }
    }

    public DuplicateCheckReport evaluate(String repo) {
        long start = System.nanoTime();
        double lowest = thresholds.get(0);

        List<long[]> duplicates = jdbc.query("""
                SELECT d.id, o.number AS original
                FROM issue d JOIN issue o ON o.repo = d.repo AND o.number = d.duplicate_of
                WHERE lower(d.repo) = lower(?) AND o.created_at < d.created_at
                  AND d.author_association NOT IN ('OWNER', 'MEMBER', 'COLLABORATOR')
                ORDER BY d.number
                """, (rs, row) -> new long[]{rs.getLong("id"), rs.getInt("original")}, repo);
        List<Long> nonDuplicates = evalService.nonDuplicateSample(repo, true);
        Integer external = jdbc.queryForObject("""
                SELECT count(*) FROM issue
                WHERE lower(repo) = lower(?) AND author_association NOT IN ('OWNER', 'MEMBER', 'COLLABORATOR')
                """, Integer.class, repo);
        log.info("Duplicate-check eval {}: {} duplicates, {} non-duplicates, thresholds {}", repo, duplicates.size(),
                nonDuplicates.size(), thresholds);

        List<Outcome> outcomes = new ArrayList<>();
        for (long[] d : duplicates) {
            outcomes.add(check(repo, d[0], (int) d[1], true, lowest));
        }
        for (Long id : nonDuplicates) {
            outcomes.add(check(repo, id, -1, false, lowest));
        }

        List<Row> rows = thresholds.stream().map(t -> row(t, outcomes)).toList();
        List<Outcome> checked = outcomes.stream().filter(o -> o.check() != null).toList();
        DuplicateCheckReport report = new DuplicateCheckReport(repo, llm.model(),
                checked.isEmpty() ? null : checked.get(0).check().promptVersion(),
                duplicates.size(), nonDuplicates.size(), external == null ? 0 : external, rows,
                checked.stream().filter(o -> !o.duplicate() && o.check().confirmed()).map(Outcome::item).toList(),
                checked.stream().filter(o -> o.duplicate() && !o.correct() && o.check().confirmed()).map(Outcome::item).toList(),
                (int) checked.stream().filter(o -> o.check().verdict() == Verdict.UNKNOWN).count(),
                checked.size(), checked.stream().mapToLong(o -> o.check().tokens()).sum(),
                (System.nanoTime() - start) / 1_000_000);
        write(report);
        log.info("Duplicate-check eval finished: {} LLM calls, {} tokens", report.llmCalls(), report.tokens());
        return report;
    }

    static Row row(double t, List<Outcome> outcomes) {
        int df = 0, dfc = 0, dc = 0, dcc = 0, nf = 0, nc = 0;
        for (Outcome o : outcomes) {
            if (o.duplicate()) {
                df += o.flaggedAt(t) ? 1 : 0;
                dfc += o.flaggedAt(t) && o.correct() ? 1 : 0;
                dc += o.confirmedAt(t) ? 1 : 0;
                dcc += o.confirmedAt(t) && o.correct() ? 1 : 0;
            } else {
                nf += o.flaggedAt(t) ? 1 : 0;
                nc += o.confirmedAt(t) ? 1 : 0;
            }
        }
        return new Row(t, df, dfc, dc, dcc, nf, nc);
    }

    /** @param originalNumber the marked original, or -1 for a non-duplicate */
    private Outcome check(String repo, long issueId, int originalNumber, boolean duplicate, double lowest) {
        List<SimilarIssue> top = vectorSearch.findSimilar(issueId, 1);
        if (top.isEmpty() || top.get(0).score() < lowest) {
            return new Outcome(duplicate, false, top.isEmpty() ? 0 : top.get(0).score(), null, null);
        }
        SimilarIssue match = top.get(0);
        IssueText issue = issueTexts.byId(issueId).orElseThrow();
        DuplicateCheck check = verifier.check(repo, issue, issueTexts.byNumber(repo, match.number()).orElseThrow());
        Item item = new Item(issue.number(), issue.title(), match.number(), match.title(), match.score(),
                check.verdict().name(), check.reason());
        return new Outcome(duplicate, match.number() == originalNumber, match.score(), item, check);
    }

    private void write(DuplicateCheckReport r) {
        try {
            Files.writeString(resultsFile, render(r));
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + resultsFile, e);
        }
    }

    static String render(DuplicateCheckReport r) {
        StringBuilder md = new StringBuilder();
        md.append("# M4: LLM check of the top duplicate match (comment-mode rule) on ").append(r.repo()).append("\n\n");
        if ("fake".equals(r.llmModel())) {
            md.append("> **FAKE LLM: these numbers test the code path only. They say nothing about a real model.**\n\n");
        }
        md.append(String.format(Locale.ROOT, """
                Generated by `POST /eval/%s/duplicate-check` (DuplicateCheckEval). Model: `%s`, prompt `%s`.
                Duration: %.1f s, %d LLM calls, %d tokens, %d without a valid answer (counted as "no").

                Rule: comment only if the vector top-1 score is >= the threshold AND the LLM says the two issues are the
                same root problem. External reporters only (what comment mode would see): %d scored duplicates and %d
                random non-duplicates. The LLM was asked once per issue above the lowest threshold; stricter thresholds
                reuse those answers.

                | Threshold | Duplicates flagged | ... correct (score only) | Confirmed by LLM | ... correct (score + LLM) | False alarms (score only) | False alarms (score + LLM) |
                |---|---|---|---|---|---|---|
                """, r.repo(), r.llmModel(), r.promptVersion(), r.durationMs() / 1000.0, r.llmCalls(), r.tokens(),
                r.unknownVerdicts(), r.duplicates(), r.nonDuplicates()));
        for (Row row : r.rows()) {
            md.append(String.format(Locale.ROOT, "| %.3f | %d | %d (%s) | %d | %d (%s) | %d (%s) | %d (%s) |%n",
                    row.threshold(), row.duplicatesFlagged(), row.duplicatesFlaggedCorrect(),
                    pct(row.duplicatesFlaggedCorrect(), r.duplicates()), row.duplicatesConfirmed(),
                    row.duplicatesConfirmedCorrect(), pct(row.duplicatesConfirmedCorrect(), r.duplicates()),
                    row.nonDuplicatesFlagged(), pct(row.nonDuplicatesFlagged(), r.nonDuplicates()),
                    row.nonDuplicatesConfirmed(), pct(row.nonDuplicatesConfirmed(), r.nonDuplicates())));
        }

        // Per 1,000 new external issues: duplicates are rare, so false alarms dominate the comments that would be posted.
        double dupShare = r.externalIssues() == 0 ? 0 : (double) r.duplicates() / r.externalIssues();
        md.append(String.format(Locale.ROOT, """

                ### Per 1,000 new external issues (estimate)
                %d of %d external issues are marked duplicates (%.1f%%). Expected public comments with score + LLM, and how
                many would point at the marked original. "Wrong" includes real but unmarked duplicates, so the true
                correct share is somewhat higher.

                | Threshold | Comments | Correct | Correct share |
                |---|---|---|---|
                """, r.duplicates(), r.externalIssues(), dupShare * 100));
        for (Row row : r.rows()) {
            double dups = 1000 * dupShare;
            double nons = 1000 - dups;
            double comments = dups * share(row.duplicatesConfirmed(), r.duplicates())
                    + nons * share(row.nonDuplicatesConfirmed(), r.nonDuplicates());
            double correct = dups * share(row.duplicatesConfirmedCorrect(), r.duplicates());
            md.append(String.format(Locale.ROOT, "| %.3f | %.1f | %.1f | %s |%n", row.threshold(), comments, correct,
                    comments == 0 ? "-" : String.format(Locale.ROOT, "%.0f%%", 100 * correct / comments)));
        }

        md.append("\n## Confirmed false alarms (non-duplicates the LLM called the same problem), for reading by hand\n\n");
        md.append("Lowest threshold. Score >= a stricter threshold is shown in the Score column, so each list covers every row.\n\n");
        items(md, r.repo(), r.confirmedFalseAlarms());
        md.append("\n## Confirmed duplicates whose top match is not the marked original\n\n");
        items(md, r.repo(), r.confirmedOtherMatches());
        return md.toString();
    }

    private static void items(StringBuilder md, String repo, List<Item> items) {
        if (items.isEmpty()) {
            md.append("None.\n");
            return;
        }
        md.append("| Issue | Matched issue | Score | LLM reason | Real duplicate? (sai) |\n|---|---|---|---|---|\n");
        for (Item i : items) {
            md.append(String.format(Locale.ROOT, "| [#%d](https://github.com/%s/issues/%d) %s | [#%d](https://github.com/%s/issues/%d) %s | %.3f | %s |  |%n",
                    i.number(), repo, i.number(), cell(i.title()), i.matchNumber(), repo, i.matchNumber(),
                    cell(i.matchTitle()), i.score(), cell(i.reason())));
        }
    }

    private static String cell(String text) {
        return text == null ? "" : text.replace("|", "\\|").replace("\n", " ");
    }

    private static double share(int part, int whole) {
        return whole == 0 ? 0 : (double) part / whole;
    }

    private static String pct(int part, int whole) {
        return whole == 0 ? "-" : String.format(Locale.ROOT, "%.1f%%", 100.0 * part / whole);
    }
}
