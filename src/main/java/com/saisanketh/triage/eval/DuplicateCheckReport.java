package com.saisanketh.triage.eval;

/**
 * Score-only vs score + LLM check, on the replay set (external reporters only: what comment mode would see).
 * "Flagged" = the top-1 vector score is at or above the comment threshold; "confirmed" = flagged AND the LLM said "yes".
 *
 * @param duplicates                 scored duplicates opened by external reporters
 * @param duplicatesFlagged          of those, flagged by score alone
 * @param duplicatesFlaggedCorrect   flagged AND the top-1 match is the true original (a correct comment)
 * @param duplicatesConfirmed        flagged and confirmed by the LLM
 * @param duplicatesConfirmedCorrect confirmed AND the top-1 match is the true original
 * @param nonDuplicates              random non-duplicates (the M3 false-alarm sample)
 * @param nonDuplicatesFlagged       of those, flagged by score alone (false alarms)
 * @param nonDuplicatesConfirmed     flagged and confirmed by the LLM (false alarms left)
 * @param unknownVerdicts            LLM checks without a valid answer (counted as "not confirmed")
 * @param llmCalls                   checks run (one per flagged issue)
 * @param tokens                     tokens used by all checks
 */
public record DuplicateCheckReport(String repo, double threshold, String llmModel, String promptVersion,
                                   int duplicates, int duplicatesFlagged, int duplicatesFlaggedCorrect,
                                   int duplicatesConfirmed, int duplicatesConfirmedCorrect,
                                   int nonDuplicates, int nonDuplicatesFlagged, int nonDuplicatesConfirmed,
                                   int unknownVerdicts, int llmCalls, long tokens, long durationMs) {
}
