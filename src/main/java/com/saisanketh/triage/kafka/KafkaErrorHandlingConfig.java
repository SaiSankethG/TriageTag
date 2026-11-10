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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;

import java.sql.SQLTransientConnectionException;

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

    /**
     * How long a message keeps being retried while the database can't be reached (stability rehearsal, 10 Nov 2026). With the
     * 3 s connection timeout, the normal 3 quick retries gave up after ~15 s and sent the message to the DLT: a Postgres restart
     * would lose the reports of every message in flight. Now: wait 1, 2, 4 ... up to 30 s between tries, for up to 10 minutes,
     * which is under max.poll.interval.ms (15 min), so Kafka doesn't think the worker died.
     */
    static final long DATABASE_DOWN_MAX_WAIT_MS = 10 * 60 * 1000;
    static final long DATABASE_DOWN_MAX_INTERVAL_MS = 30_000;

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
        // The database can't be reached: wait for it (up to DATABASE_DOWN_MAX_WAIT) instead of giving up after ~4 s of retries.
        errorHandler.setBackOffFunction((record, exception) -> databaseUnreachable(exception) ? databaseDownBackOff() : null);
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

    /** The long, patient back-off for "the database can't be reached". */
    static BackOff databaseDownBackOff() {
        ExponentialBackOff backOff = new ExponentialBackOff(1_000, 2.0);
        backOff.setMaxInterval(DATABASE_DOWN_MAX_INTERVAL_MS);
        backOff.setMaxElapsedTime(DATABASE_DOWN_MAX_WAIT_MS);
        return backOff;
    }

    /**
     * True if somewhere in the cause chain the database couldn't be reached: no connection from the pool, a transaction couldn't
     * start, or Spring's "resource failure". Other errors (a bug, a constraint, bad data) keep the short retries: waiting longer
     * wouldn't fix them.
     */
    static boolean databaseUnreachable(Throwable exception) {
        for (Throwable t = exception; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof DataAccessResourceFailureException || t instanceof CannotCreateTransactionException
                    || t instanceof SQLTransientConnectionException) {
                return true;
            }
        }
        return false;
    }
}
