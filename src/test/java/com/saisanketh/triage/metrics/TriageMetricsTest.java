package com.saisanketh.triage.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.ListenerExecutionFailedException;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TriageMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final TriageMetrics metrics = new TriageMetrics(registry);

    @Test
    void recordsEachMetricUnderItsNameAndTags() {
        metrics.webhookToReport(Duration.ofMillis(800));
        metrics.duplicateSuggested("medium");
        metrics.duplicateSuggested("medium");
        metrics.commentDecision("SHADOW_MODE");

        assertThat(registry.get("triage.webhook.to.report").timer().count()).isEqualTo(1);
        assertThat(registry.get("triage.duplicates.suggested").tag("level", "medium").counter().count()).isEqualTo(2);
        assertThat(registry.get("triage.comments").tag("decision", "SHADOW_MODE").counter().count()).isEqualTo(1);
    }

    @Test
    void deadLettersAreTaggedWithTheRealCauseNotTheListenerWrapper() {
        metrics.deadLetter(new ListenerExecutionFailedException("listener failed", new IllegalStateException("bad")));

        assertThat(registry.get("triage.kafka.dead.letters").tag("exception", "IllegalStateException").counter().count())
                .isEqualTo(1);
    }

    @Test
    void aGaugeReadsItsValueEachTime() {
        AtomicInteger unsent = new AtomicInteger(3);
        metrics.gauge("triage.outbox.unsent", "unsent rows", unsent::get);
        unsent.set(5);

        assertThat(registry.get("triage.outbox.unsent").gauge().value()).isEqualTo(5);
    }
}
