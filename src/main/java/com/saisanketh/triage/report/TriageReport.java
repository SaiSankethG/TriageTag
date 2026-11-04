package com.saisanketh.triage.report;

import com.saisanketh.triage.ai.DraftReply;
import com.saisanketh.triage.ai.DuplicateCheck;
import com.saisanketh.triage.ai.MissingDetails;
import com.saisanketh.triage.triage.FixedHint;
import com.saisanketh.triage.triage.LabelSuggester.LabelSuggestion;

import java.time.Instant;
import java.util.List;

/**
 * Everything the assistant found for one new issue, as it is stored (one row of triage_report).
 *
 * @param duplicateCheck null when the top match scored below the comment threshold (no LLM check needed)
 * @param fixedHint      null when there is no closed, fixed top match
 * @param promptVersions the prompt files used, e.g. "missing-details-v1,draft-reply-v1"
 * @param notes          null, or why something was skipped
 */
public record TriageReport(long issueId, Instant createdAt, List<Duplicate> duplicates, DuplicateCheck duplicateCheck,
                           List<LabelSuggestion> labels, MissingDetails missing, FixedHint fixedHint,
                           DraftReply draftReply, String llmModel, String promptVersions, int costTokens,
                           String notes) {

    /** One similar earlier issue, with its link. */
    public record Duplicate(int number, String title, String url, double score) {
    }
}
