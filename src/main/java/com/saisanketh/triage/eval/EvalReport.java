package com.saisanketh.triage.eval;

/**
 * Everything the replay evaluation measured for one repo.
 *
 * <p>False alarms and labels are measured on issues opened by people OUTSIDE the team (author_association not
 * OWNER/MEMBER/COLLABORATOR): those are what a triage assistant sees. Large repos also contain many team bookkeeping
 * issues (spring-boot: forward-port clones, "Upgrade to X" dependency issues) that have a near-identical earlier twin;
 * the "...AllIssues" numbers include them, for transparency.
 *
 * @param answerKey             how the scored set was built from the answer key
 * @param keyword               recall@k and MRR of the keyword baseline
 * @param vector                recall@k and MRR of the vector search
 * @param falseAlarms           external reporters: how often non-duplicates would be flagged; threshold for <= 10%
 * @param falseAlarmsAllIssues  the same on all issues, team bookkeeping included
 * @param labels                external reporters: precision of the top suggested label
 * @param labelsAllIssues       the same on all issues
 */
public record EvalReport(String repo, AnswerKey answerKey, SearchScore keyword, SearchScore vector,
                         FalseAlarms falseAlarms, FalseAlarms falseAlarmsAllIssues,
                         LabelScore labels, LabelScore labelsAllIssues, long durationMs) {

    /**
     * @param withDuplicateOf   issues in the repo with duplicate_of filled
     * @param originalNotStored the original's number isn't a stored issue (most likely a pull request)
     * @param originalNewer     the "original" was created after the duplicate (a time-ordered search can't find it)
     * @param scored            what's left: the pairs we score
     */
    public record AnswerKey(int withDuplicateOf, int originalNotStored, int originalNewer, int scored) {
    }

    /** recall@k = share of scored duplicates whose true original is in the top k; MRR = mean of 1/rank (0 if not in top 10). */
    public record SearchScore(String method, int scored, int hitsAt1, int hitsAt3, int hitsAt10,
                              double recallAt1, double recallAt3, double recallAt10, double mrr) {
    }

    /**
     * @param sample                  random non-duplicate issues checked (those with at least one earlier issue)
     * @param threshold               vector top-1 similarity at or above which we'd say "likely duplicate"
     * @param falseAlarmRate          share of the sample at or above the threshold (target <= 10%)
     * @param duplicatesFlagged       scored duplicates whose top-1 score is at or above the threshold
     * @param recallAtThresholdTop1   flagged AND the true original is the top-1 result, as a share of scored
     * @param recallAtThresholdTop3   flagged AND the true original is in the top 3, as a share of scored
     */
    public record FalseAlarms(int sample, double threshold, double falseAlarmRate, int duplicatesFlagged,
                              double recallAtThresholdTop1, double recallAtThresholdTop3) {
    }

    /**
     * @param issues         the most recent issues that have at least one real (non-workflow) label
     * @param withSuggestion how many got a suggestion above the vote threshold
     * @param topCorrect     how many of those had the top suggested label among their real labels
     * @param precision      topCorrect / withSuggestion
     * @param coverage       withSuggestion / issues
     */
    public record LabelScore(int issues, int withSuggestion, int topCorrect, double precision, double coverage) {
    }
}
