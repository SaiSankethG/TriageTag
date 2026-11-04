package com.saisanketh.triage.kafka;

import com.saisanketh.triage.domain.Issue;
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

/**
 * The triage worker: reads issue events from Kafka and stores the issue.
 * For now "triage" is just keeping our copy up to date; duplicate search etc. come later.
 *
 * <p>Safe to receive a message twice (Kafka delivers "at least once"): the upsert matches on
 * (source, externalId) and ignores older data, so a repeat changes nothing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IssueEventConsumer {

    static final String GROUP_ID = "triage-worker";

    private final JsonMapper jsonMapper;
    private final GitHubIssueMapper issueMapper;
    private final IssueService issueService;

    @KafkaListener(topics = KafkaTopicsConfig.ISSUE_EVENTS, groupId = GROUP_ID)
    public void onIssueEvent(ConsumerRecord<String, String> record) {
        Issue issue = toIssue(record);
        UpsertResult result = issueService.upsert(issue);   // a database error here is retried (see KafkaErrorHandlingConfig)
        log.info("Issue event {} (partition {}, offset {}): {}", record.key(), record.partition(),
                record.offset(), result);
    }

    /** Bad data can't be fixed by retrying, so it becomes a BadIssueEventException (sent to the DLT at once). */
    private Issue toIssue(ConsumerRecord<String, String> record) {
        try {
            JsonNode issueJson = jsonMapper.readTree(record.value()).get("issue");
            if (issueJson == null || !issueJson.isObject()) {
                throw new BadIssueEventException("message has no issue object: key " + record.key());
            }
            return issueMapper.toIssue(issueJson);
        } catch (JacksonException e) {
            throw new BadIssueEventException("message is not JSON: key " + record.key(), e);
        } catch (BadIssueEventException e) {
            throw e;
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
