package com.saisanketh.triage.eval;

import com.saisanketh.triage.eval.EvalReport.AnswerKey;
import com.saisanketh.triage.eval.EvalReport.FalseAlarms;
import com.saisanketh.triage.eval.EvalReport.LabelScore;
import com.saisanketh.triage.eval.EvalReport.SearchScore;
import com.saisanketh.triage.triage.CandidateFilter;
import com.saisanketh.triage.triage.DuplicateSearch;
import com.saisanketh.triage.triage.KeywordDuplicateSearch;
import com.saisanketh.triage.triage.LabelSuggester;
import com.saisanketh.triage.triage.LabelSuggester.LabelSuggestion;
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
import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The replay evaluation: for every answer-key duplicate, search as if the duplicate had just been opened
 * (only earlier issues of the same repo) and check where its true original lands.
 *
 * <ul>
 *   <li>Scored set: duplicate_of is filled, the original is a stored issue, and it is OLDER than the duplicate.</li>
 *   <li>recall@1/3/10 and MRR for keyword vs vector.</li>
 *   <li>False alarms: on random non-duplicates, how often the vector top-1 score passes a threshold; the threshold
 *       is chosen so that this stays at or below 10%, and recall is reported at that threshold.</li>
 *   <li>Labels: precision of the top suggested label on the most recent issues that have real labels.</li>
 * </ul>
 */
@Slf4j
@Service
public class EvalService {

    static final int TOP_K = 10;
    static final int FALSE_ALARM_SAMPLE = 1_000;
    static final double MAX_FALSE_ALARM_RATE = 0.10;
    static final int LABEL_SAMPLE = 1_000;

    private final JdbcTemplate jdbc;
    private final KeywordDuplicateSearch keywordSearch;
    private final VectorDuplicateSearch vectorSearch;
    private final LabelSuggester labelSuggester;
    private final CandidateFilter candidateFilter;
    private final Path resultsFile;

    public EvalService(JdbcTemplate jdbc, KeywordDuplicateSearch keywordSearch, VectorDuplicateSearch vectorSearch,
                       LabelSuggester labelSuggester, CandidateFilter candidateFilter,
                       @Value("${triage.eval.results-file:docs/m3-results.md}") String resultsFile) {
        this.jdbc = jdbc;
        this.keywordSearch = keywordSearch;
        this.vectorSearch = vectorSearch;
        this.labelSuggester = labelSuggester;
        this.candidateFilter = candidateFilter;
        this.resultsFile = Path.of(resultsFile);
    }

    /** One duplicate to score: its id (the query) and the true original's number. */
    record Pair(long duplicateId, int duplicateNumber, int originalNumber) {
    }

    public EvalReport evaluate(String repo) {
        long start = System.nanoTime();

        AnswerKey answerKey = countAnswerKey(repo);
        List<Pair> pairs = scoredPairs(repo);
        log.info("Eval {}: {} scored pairs", repo, pairs.size());

        // Run both searches, with and without the candidate filter, once per duplicate; keep the (filtered) vector
        // results for the threshold step.
        List<Integer> keywordRanks = new ArrayList<>();
        List<Integer> vectorRanks = new ArrayList<>();
        List<Integer> keywordRanksUnfiltered = new ArrayList<>();
        List<Integer> vectorRanksUnfiltered = new ArrayList<>();
        List<Double> vectorTopScores = new ArrayList<>();
        for (int i = 0; i < pairs.size(); i++) {
            Pair pair = pairs.get(i);
            keywordRanks.add(rank(keywordSearch, pair, true));
            keywordRanksUnfiltered.add(rank(keywordSearch, pair, false));
            List<SimilarIssue> vectorResults = vectorSearch.findSimilar(pair.duplicateId(), TOP_K, true);
            vectorRanks.add(Metrics.rankOf(vectorResults, pair.originalNumber()));
            vectorTopScores.add(vectorResults.isEmpty() ? Double.NEGATIVE_INFINITY : vectorResults.get(0).score());
            vectorRanksUnfiltered.add(rank(vectorSearch, pair, false));
            if ((i + 1) % 100 == 0) {
                log.info("Eval {}: {}/{} duplicates searched", repo, i + 1, pairs.size());
            }
        }
        SearchScore keyword = score(keywordSearch, keywordRanks);
        SearchScore vector = score(vectorSearch, vectorRanks);
        SearchScore keywordUnfiltered = score(keywordSearch, keywordRanksUnfiltered);
        SearchScore vectorUnfiltered = score(vectorSearch, vectorRanksUnfiltered);

        FalseAlarms falseAlarms = falseAlarms(repo, vectorRanks, vectorTopScores, true);
        FalseAlarms falseAlarmsAll = falseAlarms(repo, vectorRanks, vectorTopScores, false);
        LabelScore labels = labelScore(repo, true);
        LabelScore labelsAll = labelScore(repo, false);

        long durationMs = (System.nanoTime() - start) / 1_000_000;
        EvalReport report = new EvalReport(repo, answerKey, candidateFilter.excludedLabels(repo),
                keyword, vector, keywordUnfiltered, vectorUnfiltered, falseAlarms, falseAlarmsAll,
                labels, labelsAll, durationMs);
        writeMarkdown(report);
        log.info("Eval finished: {}", report);
        return report;
    }

    // --- the scored set ---

    AnswerKey countAnswerKey(String repo) {
        return jdbc.queryForObject("""
                SELECT count(*) AS with_duplicate_of,
                       count(*) FILTER (WHERE o.id IS NULL) AS original_not_stored,
                       count(*) FILTER (WHERE o.id IS NOT NULL AND o.created_at >= d.created_at) AS original_newer,
                       count(*) FILTER (WHERE o.id IS NOT NULL AND o.created_at < d.created_at) AS scored
                FROM issue d
                LEFT JOIN issue o ON o.repo = d.repo AND o.number = d.duplicate_of
                WHERE lower(d.repo) = lower(?) AND d.duplicate_of IS NOT NULL
                """, (rs, row) -> new AnswerKey(rs.getInt("with_duplicate_of"), rs.getInt("original_not_stored"),
                rs.getInt("original_newer"), rs.getInt("scored")), repo);
    }

    List<Pair> scoredPairs(String repo) {
        return jdbc.query("""
                SELECT d.id, d.number, o.number AS original
                FROM issue d JOIN issue o ON o.repo = d.repo AND o.number = d.duplicate_of
                WHERE lower(d.repo) = lower(?) AND o.created_at < d.created_at
                ORDER BY d.number
                """, (rs, row) -> new Pair(rs.getLong("id"), rs.getInt("number"), rs.getInt("original")), repo);
    }

    private static int rank(DuplicateSearch search, Pair pair, boolean applyCandidateFilter) {
        return Metrics.rankOf(search.findSimilar(pair.duplicateId(), TOP_K, applyCandidateFilter), pair.originalNumber());
    }

    private static SearchScore score(DuplicateSearch search, List<Integer> ranks) {
        int n = ranks.size();
        int at1 = (int) ranks.stream().filter(r -> Metrics.inTop(r, 1)).count();
        int at3 = (int) ranks.stream().filter(r -> Metrics.inTop(r, 3)).count();
        int at10 = (int) ranks.stream().filter(r -> Metrics.inTop(r, 10)).count();
        double mrr = ranks.stream().mapToDouble(Metrics::reciprocalRank).average().orElse(0);
        return new SearchScore(search.name(), n, at1, at3, at10, share(at1, n), share(at3, n), share(at10, n), mrr);
    }

    // --- false alarms ---

    /** Issues opened by someone outside the team: what a triage assistant would actually look at. */
    static final String EXTERNAL_REPORTER = "i.author_association NOT IN ('OWNER', 'MEMBER', 'COLLABORATOR')";

    private FalseAlarms falseAlarms(String repo, List<Integer> duplicateRanks, List<Double> duplicateTopScores,
                                    boolean externalOnly) {
        List<Long> sample = nonDuplicateSample(repo, externalOnly);

        List<Double> topScores = new ArrayList<>();
        for (Long id : sample) {
            List<SimilarIssue> top = vectorSearch.findSimilar(id, 1);
            if (!top.isEmpty()) {          // the very first issues of a repo have nothing earlier to compare with
                topScores.add(top.get(0).score());
            }
        }
        double threshold = Metrics.thresholdForFalseAlarmRate(topScores, MAX_FALSE_ALARM_RATE);

        int flagged = 0;
        int flaggedTop1 = 0;
        int flaggedTop3 = 0;
        for (int i = 0; i < duplicateRanks.size(); i++) {
            if (duplicateTopScores.get(i) >= threshold) {
                flagged++;
                if (Metrics.inTop(duplicateRanks.get(i), 1)) {
                    flaggedTop1++;
                }
                if (Metrics.inTop(duplicateRanks.get(i), 3)) {
                    flaggedTop3++;
                }
            }
        }
        int n = duplicateRanks.size();
        return new FalseAlarms(topScores.size(), threshold, Metrics.rateAtOrAbove(topScores, threshold),
                flagged, share(flaggedTop1, n), share(flaggedTop3, n));
    }

    /**
     * The same "random" non-duplicate issues every run: ordered by a hash of the id. Non-duplicate = no duplicate_of,
     * not closed as duplicate, no label containing "duplicate".
     */
    List<Long> nonDuplicateSample(String repo, boolean externalOnly) {
        return jdbc.queryForList("""
                SELECT i.id FROM issue i
                WHERE lower(i.repo) = lower(?)
                  AND i.duplicate_of IS NULL
                  AND coalesce(i.state_reason, '') <> 'duplicate'
                  AND NOT EXISTS (SELECT 1 FROM issue_label l
                                  WHERE l.issue_id = i.id AND lower(l.label_name) LIKE '%%duplicate%%')
                  AND (? = false OR %s)
                ORDER BY md5(i.id::text || 'm3-false-alarms')
                LIMIT ?
                """.formatted(EXTERNAL_REPORTER), Long.class, repo, externalOnly, FALSE_ALARM_SAMPLE);
    }

    // --- labels ---

    private LabelScore labelScore(String repo, boolean externalOnly) {
        // The most recent issues with their labels; keep those with at least one real (non-workflow) label.
        record Labelled(long id, Set<String> labels) {
        }
        List<Labelled> recent = jdbc.query("""
                SELECT i.id, array_agg(l.label_name) AS labels
                FROM issue i JOIN issue_label l ON l.issue_id = i.id
                WHERE lower(i.repo) = lower(?) AND (? = false OR %s)
                GROUP BY i.id, i.created_at
                ORDER BY i.created_at DESC
                LIMIT ?
                """.formatted(EXTERNAL_REPORTER),
                (rs, row) -> new Labelled(rs.getLong("id"), toSet(rs.getArray("labels"))),
                repo, externalOnly, LABEL_SAMPLE * 3);

        List<Labelled> sample = recent.stream()
                .map(l -> new Labelled(l.id(), Set.copyOf(l.labels().stream()
                        .filter(label -> !labelSuggester.isIgnored(label)).toList())))
                .filter(l -> !l.labels().isEmpty())
                .limit(LABEL_SAMPLE)
                .toList();

        int withSuggestion = 0;
        int topCorrect = 0;
        for (Labelled issue : sample) {
            List<LabelSuggestion> suggestions = labelSuggester.suggest(issue.id());
            if (!suggestions.isEmpty()) {
                withSuggestion++;
                if (issue.labels().contains(suggestions.get(0).label())) {
                    topCorrect++;
                }
            }
        }
        return new LabelScore(sample.size(), withSuggestion, topCorrect, share(topCorrect, withSuggestion),
                share(withSuggestion, sample.size()));
    }

    private static Set<String> toSet(Array array) throws SQLException {
        return Set.copyOf(Arrays.asList((String[]) array.getArray()));
    }

    private static double share(int part, int whole) {
        return whole == 0 ? 0 : (double) part / whole;
    }

    // --- results file ---

    private void writeMarkdown(EvalReport report) {
        try {
            Files.writeString(resultsFile, MarkdownReport.render(report));
            log.info("Eval results written to {}", resultsFile.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + resultsFile, e);
        }
    }
}
