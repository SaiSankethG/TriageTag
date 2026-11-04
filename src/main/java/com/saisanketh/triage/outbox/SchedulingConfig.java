package com.saisanketh.triage.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on Spring's scheduler, which runs the @Scheduled methods (the outbox relay). Can be switched off with
 * {@code triage.outbox.relay-enabled=false}, e.g. for a test that wants to call the relay by hand.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "triage.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
