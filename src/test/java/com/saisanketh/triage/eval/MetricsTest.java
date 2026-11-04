package com.saisanketh.triage.eval;

import com.saisanketh.triage.triage.SimilarIssue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MetricsTest {

    private static final List<SimilarIssue> RESULTS = List.of(
            new SimilarIssue(10, "a", 0.9), new SimilarIssue(20, "b", 0.8), new SimilarIssue(30, "c", 0.7));

    @Test
    void rankIsTheOneBasedPositionOrZero() {
        assertThat(Metrics.rankOf(RESULTS, 10)).isEqualTo(1);
        assertThat(Metrics.rankOf(RESULTS, 30)).isEqualTo(3);
        assertThat(Metrics.rankOf(RESULTS, 99)).isZero();
    }

    @Test
    void inTopAndReciprocalRank() {
        assertThat(Metrics.inTop(3, 3)).isTrue();
        assertThat(Metrics.inTop(4, 3)).isFalse();
        assertThat(Metrics.inTop(0, 10)).isFalse();     // 0 = not found
        assertThat(Metrics.reciprocalRank(1)).isEqualTo(1.0);
        assertThat(Metrics.reciprocalRank(4)).isEqualTo(0.25);
        assertThat(Metrics.reciprocalRank(0)).isZero();
    }

    @Test
    void thresholdKeepsFalseAlarmsAtOrBelowTheRate() {
        List<Double> scores = List.of(0.95, 0.90, 0.85, 0.80, 0.75, 0.70, 0.65, 0.60, 0.55, 0.50);

        double threshold = Metrics.thresholdForFalseAlarmRate(scores, 0.10);   // 10 scores -> at most 1 flagged

        assertThat(Metrics.rateAtOrAbove(scores, threshold)).isEqualTo(0.10);
        assertThat(threshold).isGreaterThan(0.90).isLessThan(0.95);
    }

    @Test
    void tiesAtTheCutAreNotFlagged() {
        List<Double> scores = List.of(0.9, 0.8, 0.8, 0.8, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1);

        double threshold = Metrics.thresholdForFalseAlarmRate(scores, 0.20);   // at most 2 of 10

        assertThat(Metrics.rateAtOrAbove(scores, threshold)).isEqualTo(0.10);  // only 0.9; the three 0.8 ties stay below
    }

    @Test
    void rateOfAnEmptyListIsZero() {
        assertThat(Metrics.rateAtOrAbove(List.of(), 0.5)).isCloseTo(0.0, within(1e-9));
    }
}
