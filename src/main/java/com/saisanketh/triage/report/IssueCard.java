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
 */
public record IssueCard(String state, String stateReason, String authorAssociation, Instant createdAt,
                        List<String> labels, String bodyStart, int bodyLength) {
}
