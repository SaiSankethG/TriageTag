package com.saisanketh.triage.eval;

import com.saisanketh.triage.triage.SimilarIssue;

import java.util.Comparator;
import java.util.List;

/** The small bits of maths behind the evaluation, kept separate so they are easy to test and explain. */
public final class Metrics {

    private Metrics() {
    }

    /**
     * Where the true original appears in a result list: 1 = first, 2 = second, ...; 0 = not in the list.
     */
    public static int rankOf(List<SimilarIssue> results, int originalNumber) {
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).number() == originalNumber) {
                return i + 1;
            }
        }
        return 0;
    }

    /** recall@k for one query: 1 if the original is in the top k, else 0 (averaged over all queries later). */
    public static boolean inTop(int rank, int k) {
        return rank >= 1 && rank <= k;
    }

    /** Reciprocal rank for one query: 1/rank, or 0 if not found. MRR is the average of these. */
    public static double reciprocalRank(int rank) {
        return rank == 0 ? 0 : 1.0 / rank;
    }

    /**
     * The lowest score threshold at which at most {@code maxRate} of the given (non-duplicate) top-1 scores
     * would be flagged as "likely duplicate". Flag rule: score >= threshold.
     *
     * <p>Example with 10 scores and maxRate 0.10: at most 1 may be flagged, so the threshold sits just above the
     * 2nd-highest score: only the highest one is at or above it.
     */
    public static double thresholdForFalseAlarmRate(List<Double> nonDuplicateTopScores, double maxRate) {
        if (nonDuplicateTopScores.isEmpty()) {
            throw new IllegalArgumentException("need at least one score");
        }
        List<Double> desc = nonDuplicateTopScores.stream().sorted(Comparator.reverseOrder()).toList();
        int allowed = (int) Math.floor(maxRate * desc.size());
        if (allowed >= desc.size()) {
            return desc.get(desc.size() - 1);   // everything may be flagged
        }
        // Just above the first score that must NOT be flagged; ties with it are not flagged either.
        return Math.nextUp(desc.get(allowed));
    }

    /** Share of scores at or above the threshold. */
    public static double rateAtOrAbove(List<Double> scores, double threshold) {
        if (scores.isEmpty()) {
            return 0;
        }
        return (double) scores.stream().filter(s -> s >= threshold).count() / scores.size();
    }
}
