package com.saisanketh.triage.report;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

/**
 * What GET /reports/{owner}/{repo}/{number} returns: the stored report plus the issue's title, link and card.
 * The parts are the stored JSON as it is (see TriageReport for their shapes), except that each duplicate also gets the
 * matched issue's CURRENT "state" and "stateReason" (it may have been closed since the report was made).
 * {@code notAvailable}: report parts this repository doesn't get (backend piece 4, {@link RepoParts}), for the page to say so.
 */
public record TriageReportDto(String repo, int number, String title, String url, IssueCard issue, Instant createdAt,
                              JsonNode duplicates, JsonNode duplicateCheck, JsonNode labels, JsonNode missing,
                              JsonNode fixedHint, JsonNode draftReply, String llmModel, String promptVersions,
                              int costTokens, String notes, List<RepoParts.NotAvailable> notAvailable) {

    /** The same report with the parts this repo doesn't get (worked out when the report is read, from today's settings). */
    public TriageReportDto withNotAvailable(List<RepoParts.NotAvailable> parts) {
        return new TriageReportDto(repo, number, title, url, issue, createdAt, duplicates, duplicateCheck, labels, missing,
                fixedHint, draftReply, llmModel, promptVersions, costTokens, notes, List.copyOf(parts));
    }
}
