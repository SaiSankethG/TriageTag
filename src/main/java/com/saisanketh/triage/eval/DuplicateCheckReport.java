package com.saisanketh.triage.eval;

import java.util.List;

/**
 * Score-only vs score + LLM check, on the replay set (external reporters only: what comment mode would see).
 * The LLM is called once per issue whose top-1 score passes the LOWEST threshold; stricter thresholds are computed from
 * the same answers (an issue flagged at 0.90 is also flagged at 0.825), so they cost nothing extra.
 *
 * @param duplicates            scored duplicates opened by external reporters
 * @param nonDuplicates         random non-duplicates (the M3 false-alarm sample)
 * @param externalIssues        all issues by external reporters in the repo (for the per-1,000 estimate)
 * @param rows                  one row per threshold, lowest first
 * @param confirmedFalseAlarms  non-duplicates the LLM confirmed at the lowest threshold: for reading by hand
 * @param confirmedOtherMatches duplicates the LLM confirmed whose top match is NOT the marked original
 * @param unknownVerdicts       LLM checks without a valid answer (counted as "not confirmed")
 */
public record DuplicateCheckReport(String repo, String llmModel, String promptVersion,
                                   int duplicates, int nonDuplicates, int externalIssues, List<Row> rows,
                                   List<Item> confirmedFalseAlarms, List<Item> confirmedOtherMatches,
                                   int unknownVerdicts, int llmCalls, long tokens, long durationMs) {

    /**
     * Counts at one threshold. "Flagged" = top-1 score >= threshold; "confirmed" = flagged AND the LLM said "yes";
     * "correct" = the top-1 match is the marked original.
     */
    public record Row(double threshold, int duplicatesFlagged, int duplicatesFlaggedCorrect, int duplicatesConfirmed,
                      int duplicatesConfirmedCorrect, int nonDuplicatesFlagged, int nonDuplicatesConfirmed) {
    }

    /** One checked pair: the issue, its top match, the score and the LLM's verdict. */
    public record Item(int number, String title, int matchNumber, String matchTitle, double score, String verdict,
                       String reason) {
    }
}
