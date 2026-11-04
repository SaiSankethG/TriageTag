package com.saisanketh.triage.eval;

import com.saisanketh.triage.ai.DuplicateCheck;
import com.saisanketh.triage.ai.DuplicateCheck.Verdict;
import com.saisanketh.triage.eval.DuplicateCheckEval.Outcome;
import com.saisanketh.triage.eval.DuplicateCheckReport.Row;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The threshold maths: one set of LLM answers, counted at several thresholds. */
class DuplicateCheckEvalTest {

    private static Outcome checked(boolean duplicate, boolean correct, double score, Verdict verdict) {
        return new Outcome(duplicate, correct, score, null, new DuplicateCheck(verdict, "r", "p", 10));
    }

    @Test
    void aStricterThresholdKeepsOnlyTheHigherScores() {
        List<Outcome> outcomes = List.of(
                checked(true, true, 0.95, Verdict.YES),     // duplicate, right match, confirmed
                checked(true, true, 0.86, Verdict.NO),      // duplicate, right match, not confirmed
                checked(true, false, 0.83, Verdict.YES),    // duplicate, other match, confirmed
                checked(false, false, 0.91, Verdict.YES),   // false alarm, confirmed
                checked(false, false, 0.84, Verdict.UNSURE),
                new Outcome(false, false, 0.70, null, null)); // below every threshold: never checked

        assertThat(DuplicateCheckEval.row(0.825, outcomes)).isEqualTo(new Row(0.825, 3, 2, 2, 1, 2, 1));
        assertThat(DuplicateCheckEval.row(0.85, outcomes)).isEqualTo(new Row(0.85, 2, 2, 1, 1, 1, 1));
        assertThat(DuplicateCheckEval.row(0.90, outcomes)).isEqualTo(new Row(0.90, 1, 1, 1, 1, 1, 1));
    }
}
