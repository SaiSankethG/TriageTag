package com.saisanketh.triage.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * All of the app's own metrics, in one place, so their names and tags are easy to find. Micrometer records them;
 * /actuator/prometheus shows them in Prometheus' text format (dots become underscores, e.g. triage_llm_tokens_total).
 *
 * <ul>
 *   <li>{@code triage.webhook.to.report}: time from accepting a webhook to its stored report (histogram, for p50/p95).</li>
 *   <li>{@code triage.llm.tokens} (tags model, direction=input|output) and {@code triage.llm.cost} (USD, tag model):
 *       counters; "per day" is a query: {@code increase(triage_llm_tokens_total[1d])}.</li>
 *   <li>{@code triage.kafka.dead.letters} (tag exception): messages sent to issue-events.DLT.</li>
 *   <li>{@code triage.duplicates.suggested} (tag level = high|medium|low|none): the top match of each new report.</li>
 *   <li>{@code triage.comments} (tag decision): what comment mode decided for each new report.</li>
 *   <li>{@code triage.outbox.unsent}: outbox rows not yet sent to Kafka (gauge; grows while Kafka is down).</li>
 * </ul>
 * Kafka consumer lag comes from Spring Boot's own Kafka client metrics ({@code kafka_consumer_fetch_manager_records_lag_max}).
 */
@Component
public class TriageMetrics {

    private final MeterRegistry registry;
    private final Timer webhookToReport;

    public TriageMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.webhookToReport = Timer.builder("triage.webhook.to.report")
                .description("Time from accepting a GitHub webhook to the stored triage report")
                .publishPercentileHistogram()           // buckets, so Prometheus can compute p50/p95 over any window
                .minimumExpectedValue(Duration.ofMillis(50))
                .maximumExpectedValue(Duration.ofMinutes(2))
                .register(registry);
    }

    public void webhookToReport(Duration duration) {
        webhookToReport.record(duration);
    }

    public void llmCall(String model, int inputTokens, int outputTokens, double costUsd) {
        Counter.builder("triage.llm.tokens").tag("model", model).tag("direction", "input")
                .description("LLM tokens").register(registry).increment(inputTokens);
        Counter.builder("triage.llm.tokens").tag("model", model).tag("direction", "output")
                .description("LLM tokens").register(registry).increment(outputTokens);
        Counter.builder("triage.llm.cost").tag("model", model).baseUnit("usd")
                .description("Estimated LLM cost from list prices").register(registry).increment(costUsd);
    }

    public void deadLetter(Throwable exception) {
        Throwable cause = exception.getCause() != null ? exception.getCause() : exception;   // unwrap the listener's wrapper
        Counter.builder("triage.kafka.dead.letters").tag("exception", cause.getClass().getSimpleName())
                .description("Messages sent to the dead-letter topic").register(registry).increment();
    }

    /** @param level high / medium / low / none (no earlier issue at all) */
    public void duplicateSuggested(String level) {
        Counter.builder("triage.duplicates.suggested").tag("level", level)
                .description("Top duplicate match of each new report, by similarity level").register(registry).increment();
    }

    public void commentDecision(String decision) {
        Counter.builder("triage.comments").tag("decision", decision)
                .description("Comment-mode decision for each new report").register(registry).increment();
    }

    /** Registers a gauge that asks {@code value} each time it is read (Prometheus scrape). */
    public void gauge(String name, String description, Supplier<Number> value) {
        Gauge.builder(name, value).description(description).register(registry);
    }
}
