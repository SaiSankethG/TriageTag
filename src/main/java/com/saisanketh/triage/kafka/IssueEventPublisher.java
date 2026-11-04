package com.saisanketh.triage.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Puts issue events on Kafka and waits until the broker has stored them. */
@Slf4j
@Component
@RequiredArgsConstructor
public class IssueEventPublisher {

    /** How long we wait for the broker's "stored it" answer. GitHub itself waits at most 10 s for our reply. */
    static final long ACK_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;

    /**
     * Sends the message and blocks until Kafka confirms it (acks=all) or the timeout passes.
     *
     * @throws PublishFailedException if Kafka didn't confirm in time; the caller must then not report success
     */
    public void publishAndWait(IssueEventMessage message) {
        String value = jsonMapper.writeValueAsString(message);
        try {
            SendResult<String, String> result = kafkaTemplate
                    .send(KafkaTopicsConfig.ISSUE_EVENTS, message.key(), value)
                    .get(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("Published delivery {} as {} to partition {} offset {}", message.deliveryId(), message.key(),
                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PublishFailedException("interrupted while waiting for Kafka", e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            throw new PublishFailedException("Kafka did not confirm delivery " + message.deliveryId(), e);
        }
    }

    /** Kafka didn't store the message. */
    public static class PublishFailedException extends RuntimeException {
        public PublishFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
