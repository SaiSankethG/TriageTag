package com.saisanketh.triage.kafka;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.util.backoff.BackOffExecution;

import java.sql.SQLTransientConnectionException;

import static org.assertj.core.api.Assertions.assertThat;

/** Stability rehearsal: messages wait for an unreachable database instead of going to the DLT after a few seconds. */
class KafkaErrorHandlingConfigTest {

    @Test
    void anUnreachableDatabaseIsRecognisedEvenWhenWrapped() {
        assertThat(KafkaErrorHandlingConfig.databaseUnreachable(new ListenerExecutionFailedException("listener failed",
                new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection",
                        new SQLTransientConnectionException("Connection is not available, request timed out after 3000ms")))))
                .isTrue();
        assertThat(KafkaErrorHandlingConfig.databaseUnreachable(new CannotCreateTransactionException("no connection")))
                .isTrue();
    }

    @Test
    void otherErrorsKeepTheShortRetries() {
        assertThat(KafkaErrorHandlingConfig.databaseUnreachable(
                new ListenerExecutionFailedException("listener failed", new DataIntegrityViolationException("duplicate key"))))
                .isFalse();
        assertThat(KafkaErrorHandlingConfig.databaseUnreachable(new IllegalStateException("a bug"))).isFalse();
    }

    @Test
    void theHandlerPicksThePatientBackOffOnlyForAnUnreachableDatabase() {
        assertThat(KafkaErrorHandlingConfig.backOffFor(new CannotCreateTransactionException("no connection"))).isNotNull();
        assertThat(KafkaErrorHandlingConfig.backOffFor(new IllegalStateException("a bug"))).isNull();   // null = default retries
    }

    @Test
    void theDatabaseBackOffWaitsUpTo30sBetweenTriesForAbout10Minutes() {
        BackOffExecution execution = KafkaErrorHandlingConfig.databaseDownBackOff().start();
        long total = 0;
        long longest = 0;
        int tries = 0;
        for (long wait = execution.nextBackOff(); wait != BackOffExecution.STOP; wait = execution.nextBackOff()) {
            total += wait;
            longest = Math.max(longest, wait);
            tries++;
        }
        assertThat(execution.nextBackOff()).isEqualTo(BackOffExecution.STOP);
        assertThat(longest).isEqualTo(30_000);
        assertThat(total).isBetween(9 * 60_000L, 11 * 60_000L);
        assertThat(tries).isGreaterThan(20);
    }
}
