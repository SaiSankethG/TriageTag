package com.saisanketh.triage.kafka;

import tools.jackson.databind.JsonNode;

/**
 * The value of one message on the issue-events topic (written as JSON).
 *
 * @param deliveryId GitHub's X-GitHub-Delivery id, for tracing a message back to a webhook
 * @param action     e.g. "opened", "labeled"
 * @param repo       "owner/name"
 * @param issue      the full "issue" object exactly as GitHub sent it (the consumer maps it to our Issue)
 */
public record IssueEventMessage(String deliveryId, String action, String repo, JsonNode issue) {

    /** The message key: same issue -> same key -> same partition -> events for one issue stay in order. */
    public String key() {
        return repo + "#" + issue.path("number").asInt();
    }
}
