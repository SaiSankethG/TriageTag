package com.saisanketh.triage.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Puts messages on Kafka and waits until the broker has stored them. Used by the outbox relay (M5). */
@Slf4j
@Component
@RequiredArgsConstructor
public class IssueEventPublisher {

    /** How long we wait for the broker's "stored it" answer. GitHub itself waits at most 10 s for our reply. */
    static final long ACK_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Sends the message and blocks until Kafka confirms it (acks=all) or the timeout passes.
     *
     * @param value the message, already JSON text
     * @throws PublishFailedException if Kafka didn't confirm in time; the caller must then not mark it as sent
     */
    public void sendAndWait(String topic, String key, String value) {
        try {
            SendResult<String, String> result = kafkaTemplate.send(topic, key, value)
                    .get(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.debug("Published {} to {} partition {} offset {}", key, topic,
                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PublishFailedException("interrupted while waiting for Kafka", e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            throw new PublishFailedException("Kafka did not confirm " + key + ": " + e.getClass().getSimpleName(), e);
        }
    }

    /** Kafka didn't store the message. */
    public static class PublishFailedException extends RuntimeException {
        public PublishFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
