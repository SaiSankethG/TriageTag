package com.saisanketh.triage.outbox;

import com.saisanketh.triage.kafka.IssueEventPublisher;
import com.saisanketh.triage.kafka.IssueEventPublisher.PublishFailedException;
import com.saisanketh.triage.metrics.TriageMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Sends outbox rows to Kafka, oldest first, and marks each one sent after Kafka confirmed it (acks=all).
 *
 * <ul>
 *   <li><b>Order</b>: rows go out in id order, and the batch stops at the first failure, so an issue's later event never
 *       overtakes an earlier one that is still waiting.</li>
 *   <li><b>Kafka down</b>: the row stays unsent (attempts + 1, last_error) and is tried again on the next run. Webhooks are
 *       still accepted meanwhile; they just queue up in the table.</li>
 *   <li><b>Crash after the send, before the mark</b>: the row is sent again later. That's "at least once", the same as
 *       Kafka's own delivery; the consumer is idempotent (upsert ignores older data; one report per issue).</li>
 * </ul>
 */
@Slf4j
@Component
public class OutboxRelay {

    static final int BATCH = 100;
    static final Duration KEEP_SENT = Duration.ofDays(7);

    private final OutboxRepository outbox;
    private final IssueEventPublisher publisher;
    private final Clock clock;

    public OutboxRelay(OutboxRepository outbox, IssueEventPublisher publisher, Clock clock) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.clock = clock;
    }

    /** Used by Spring: also registers the metric triage.outbox.unsent (counted by SQL each time it is scraped). */
    @Autowired
    public OutboxRelay(OutboxRepository outbox, IssueEventPublisher publisher, Clock clock, TriageMetrics metrics) {
        this(outbox, publisher, clock);
        metrics.gauge("triage.outbox.unsent", "Outbox rows not yet sent to Kafka", outbox::countUnsent);
    }

    /**
     * One run: send up to BATCH unsent rows. The rows stay locked (one transaction) until the run ends, so a second
     * instance skips them. Runs every {@code triage.outbox.poll-ms} (default 500 ms) after the previous run ended.
     *
     * @return how many rows were sent
     */
    @Scheduled(fixedDelayString = "${triage.outbox.poll-ms:500}")
    @Transactional
    public int sendPending() {
        List<OutboxEvent> events = outbox.lockUnsent(BATCH);
        int sent = 0;
        for (OutboxEvent event : events) {
            try {
                publisher.sendAndWait(event.topic(), event.key(), event.payload());
            } catch (PublishFailedException e) {
                outbox.markFailed(event.id(), e.getMessage());
                log.warn("Outbox: could not send event {} ({} more waiting): {}", event.id(),
                        events.size() - sent - 1, e.getMessage());
                break;   // keep the order: nothing after a failed row goes out before it
            }
            outbox.markSent(event.id(), clock.instant());
            sent++;
        }
        if (sent > 0) {
            log.info("Outbox: sent {} event(s)", sent);
        }
        return sent;
    }

    /** Once an hour: delete rows that were sent more than KEEP_SENT ago. */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    @Transactional
    public void deleteOldSentRows() {
        int deleted = outbox.deleteSentBefore(clock.instant().minus(KEEP_SENT));
        if (deleted > 0) {
            log.info("Outbox: deleted {} sent row(s) older than {}", deleted, KEEP_SENT);
        }
    }
}
