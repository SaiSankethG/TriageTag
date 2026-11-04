package com.saisanketh.triage.report;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * What GET /reports/{owner}/{repo}/{number} returns: the stored report plus the issue's title, link and card.
 * The parts are the stored JSON as it is (see TriageReport for their shapes), except that each duplicate also gets the
 * matched issue's CURRENT "state" and "stateReason" (it may have been closed since the report was made).
 */
public record TriageReportDto(String repo, int number, String title, String url, IssueCard issue, Instant createdAt,
                              JsonNode duplicates, JsonNode duplicateCheck, JsonNode labels, JsonNode missing,
                              JsonNode fixedHint, JsonNode draftReply, String llmModel, String promptVersions,
                              int costTokens, String notes) {
}
