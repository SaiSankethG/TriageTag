package com.saisanketh.triage.kafka;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.domain.SourceType;
import com.saisanketh.triage.embedding.IssueEmbeddingService;
import com.saisanketh.triage.github.GitHubIssueMapper;
import com.saisanketh.triage.metrics.TriageMetrics;
import com.saisanketh.triage.repository.WebhookDeliveryRepository;
import com.saisanketh.triage.report.TriageService;
import com.saisanketh.triage.service.IssueService;
import com.saisanketh.triage.service.UpsertResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;

/**
 * The triage worker: reads issue events from Kafka, keeps our copy of the issue up to date (and its embedding), and
 * creates the triage report when an issue is opened.
 *
 * <p>Safe to receive a message twice (Kafka delivers "at least once"): the upsert matches on
 * (source, externalId) and ignores older data, and deleting a row that's already gone does nothing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IssueEventConsumer {

    static final String GROUP_ID = "triage-worker";

    /**
     * Actions after which the issue no longer exists in this repo: "deleted", or "transferred" (moved to another
     * repo; GitHub gives it a new id there, under changes.new_issue). Either way we remove our row for the old one.
     */
    static final Set<String> REMOVING_ACTIONS = Set.of("deleted", "transferred");

    private final JsonMapper jsonMapper;
    private final GitHubIssueMapper issueMapper;
    private final IssueService issueService;
    private final IssueEmbeddingService embeddingService;
    private final TriageService triageService;
    private final WebhookDeliveryRepository deliveries;
    private final TriageMetrics metrics;
    private final Clock clock;

    @KafkaListener(topics = KafkaTopicsConfig.ISSUE_EVENTS, groupId = GROUP_ID)
    public void onIssueEvent(ConsumerRecord<String, String> record) {
        JsonNode message = parse(record);
        String action = message.get("action") == null ? "" : message.get("action").asString();
        JsonNode issueJson = message.get("issue");

        if (REMOVING_ACTIONS.contains(action)) {
            String externalId = String.valueOf(issueJson.get("id").asLong());
            boolean removed = issueService.delete(SourceType.GITHUB, externalId);
            log.info("Issue event {} (partition {}, offset {}): {} -> {}", record.key(), record.partition(),
                    record.offset(), action, removed ? "REMOVED" : "NOTHING TO REMOVE");
            return;
        }

        Issue issue = toIssue(record, issueJson);
        UpsertResult result = issueService.upsert(issue);   // a database error here is retried (see KafkaErrorHandlingConfig)
        if (result != UpsertResult.STALE) {
            // New or changed text -> new embedding, so the issue can be found by (and find) similar issues.
            // If this fails, the whole message is retried; the upsert above is idempotent.
            embeddingService.embedIssue(issue.getSource(), issue.getExternalId());
        }
        if ("opened".equals(action)) {
            // Only once per issue: a redelivered "opened" finds the report and doesn't pay for the LLM again.
            boolean created = issueService.findId(issue.getSource(), issue.getExternalId())
                    .map(triageService::createIfMissing).orElse(false);
            if (created) {
                recordWebhookToReport(message);
            }
        }
        log.info("Issue event {} (partition {}, offset {}): {}", record.key(), record.partition(),
                record.offset(), result);
    }

    /** Metric: from the moment the webhook was accepted (webhook_delivery.received_at) to the stored report. */
    private void recordWebhookToReport(JsonNode message) {
        JsonNode deliveryId = message.get("deliveryId");
        if (deliveryId != null) {
            deliveries.receivedAt(deliveryId.asString())
                    .ifPresent(received -> metrics.webhookToReport(Duration.between(received, clock.instant())));
        }
    }

    /** Bad data can't be fixed by retrying, so it becomes a BadIssueEventException (sent to the DLT at once). */
    private JsonNode parse(ConsumerRecord<String, String> record) {
        JsonNode message;
        try {
            message = jsonMapper.readTree(record.value());
        } catch (JacksonException e) {
            throw new BadIssueEventException("message is not JSON: key " + record.key(), e);
        }
        JsonNode issueJson = message.get("issue");
        if (issueJson == null || !issueJson.isObject() || issueJson.get("id") == null) {
            throw new BadIssueEventException("message has no issue object with an id: key " + record.key());
        }
        return message;
    }

    private Issue toIssue(ConsumerRecord<String, String> record, JsonNode issueJson) {
        try {
            return issueMapper.toIssue(issueJson);
        } catch (RuntimeException e) {
            // e.g. a required field is missing, so the mapper failed
            throw new BadIssueEventException("could not map the issue: key " + record.key(), e);
        }
    }

    /** The message itself is broken: retrying won't help. */
    public static class BadIssueEventException extends RuntimeException {
        public BadIssueEventException(String message) {
            super(message);
        }

        public BadIssueEventException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
