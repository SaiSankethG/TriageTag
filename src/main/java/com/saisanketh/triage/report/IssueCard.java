package com.saisanketh.triage.report;

import java.time.Instant;
import java.util.List;

/**
 * The issue itself, as the demo page shows it above the report.
 *
 * @param authorAssociation GitHub's word for who opened it: NONE / CONTRIBUTOR (outside the team), MEMBER / OWNER /
 *                          COLLABORATOR (the team)
 * @param bodyStart         the first {@link TriageReportRepository#BODY_START_CHARS} characters of the body ("" if empty)
 * @param bodyLength        the full body's length, so the page knows whether there is more to show on GitHub
 * @param duplicateOf       what maintainers decided later: the original's number if they closed this issue as a duplicate
 *                          (the answer key), else null
 * @param originalStored    false if that original isn't a stored issue (most likely a pull request), so it can't be suggested
 * @param originalIsNewer   true if that original was opened AFTER this issue, so a search of earlier issues can't find it
 */
public record IssueCard(String state, String stateReason, String authorAssociation, Instant createdAt,
                        List<String> labels, String bodyStart, int bodyLength, Integer duplicateOf,
                        boolean originalStored, boolean originalIsNewer) {
}
