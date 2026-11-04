package com.saisanketh.triage.kafka;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.domain.SourceType;
import com.saisanketh.triage.embedding.IssueEmbeddingService;
import com.saisanketh.triage.github.GitHubIssueMapper;
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

import java.util.Set;

/**
 * The triage worker: reads issue events from Kafka and keeps our copy of the issue up to date.
 * For now "triage" is just that; duplicate search etc. come later.
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
        log.info("Issue event {} (partition {}, offset {}): {}", record.key(), record.partition(),
                record.offset(), result);
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
