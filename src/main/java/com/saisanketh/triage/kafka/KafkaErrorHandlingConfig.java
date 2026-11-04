package com.saisanketh.triage.kafka;

import com.saisanketh.triage.kafka.IssueEventConsumer.BadIssueEventException;
import com.saisanketh.triage.metrics.TriageMetrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * What happens when the consumer throws. Spring Boot gives this error handler to every @KafkaListener.
 *
 * <ul>
 *   <li>Temporary problems (e.g. the database blinked): retry the same message 3 times, waiting
 *       0.5 s, 1 s, 2 s in between.</li>
 *   <li>Still failing, or a broken message (BadIssueEventException: retrying can't fix bad data): send it to
 *       issue-events.DLT, on the same partition number, and move on. So one bad message never blocks
 *       the partition behind it.</li>
 * </ul>
 */
@Slf4j
@Configuration
public class KafkaErrorHandlingConfig {

    static final int MAX_RETRIES = 3;

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate, TriageMetrics metrics) {
        DeadLetterPublishingRecoverer sendToDeadLetterTopic = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(KafkaTopicsConfig.ISSUE_EVENTS_DLT, record.partition()));

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backOff.setInitialInterval(500);
        backOff.setMultiplier(2.0);

        // Count every dead letter (metric triage.kafka.dead.letters), then send it.
        ConsumerRecordRecoverer countAndSend = (record, exception) -> {
            // Without this line a dead letter is silent in the log: found by the M6 load test, where only the metric showed it.
            log.error("Sending {} (partition {}, offset {}) to {}: {}", record.key(), record.partition(), record.offset(),
                    KafkaTopicsConfig.ISSUE_EVENTS_DLT, rootMessage(exception));
            metrics.deadLetter(exception);
            sendToDeadLetterTopic.accept(record, exception);
        };
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(countAndSend, backOff);
        errorHandler.addNotRetryableExceptions(BadIssueEventException.class);
        return errorHandler;
    }

    /** The innermost cause's type and message (the listener wraps the real exception). */
    static String rootMessage(Throwable exception) {
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
