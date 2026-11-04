package com.saisanketh.triage.report;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * What GET /reports/{owner}/{repo}/{number} returns: the stored report plus the issue's title and link.
 * The parts are the stored JSON as it is (see TriageReport for their shapes).
 */
public record TriageReportDto(String repo, int number, String title, String url, Instant createdAt,
                              JsonNode duplicates, JsonNode duplicateCheck, JsonNode labels, JsonNode missing,
                              JsonNode fixedHint, JsonNode draftReply, String llmModel, String promptVersions,
                              int costTokens, String notes) {
}
