package com.saisanketh.triage.outbox;

import com.saisanketh.triage.kafka.IssueEventPublisher;
import com.saisanketh.triage.kafka.IssueEventPublisher.PublishFailedException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");
    private final OutboxRepository outbox = mock(OutboxRepository.class);
    private final IssueEventPublisher publisher = mock(IssueEventPublisher.class);
    private final OutboxRelay relay = new OutboxRelay(outbox, publisher, Clock.fixed(NOW, ZoneOffset.UTC));

    private static OutboxEvent event(long id) {
        return new OutboxEvent(id, "issue-events", "o/r#" + id, "{\"n\": " + id + "}");
    }

    @Test
    void sendsInOrderAndMarksEachRowAfterKafkaConfirmed() {
        when(outbox.lockUnsent(OutboxRelay.BATCH)).thenReturn(List.of(event(1), event(2)));

        assertThat(relay.sendPending()).isEqualTo(2);

        InOrder order = inOrder(publisher, outbox);
        order.verify(publisher).sendAndWait("issue-events", "o/r#1", "{\"n\": 1}");
        order.verify(outbox).markSent(1, NOW);
        order.verify(publisher).sendAndWait("issue-events", "o/r#2", "{\"n\": 2}");
        order.verify(outbox).markSent(2, NOW);
    }

    @Test
    void stopsAtTheFirstFailureSoNothingOvertakesIt() {
        when(outbox.lockUnsent(OutboxRelay.BATCH)).thenReturn(List.of(event(1), event(2), event(3)));
        doThrow(new PublishFailedException("Kafka did not confirm o/r#2", null))
                .when(publisher).sendAndWait("issue-events", "o/r#2", "{\"n\": 2}");

        assertThat(relay.sendPending()).isEqualTo(1);

        verify(outbox).markSent(1, NOW);
        verify(outbox).markFailed(2, "Kafka did not confirm o/r#2");
        verify(outbox, never()).markSent(2, NOW);
        verify(publisher, never()).sendAndWait("issue-events", "o/r#3", "{\"n\": 3}");
    }

    @Test
    void nothingToSendIsFine() {
        when(outbox.lockUnsent(OutboxRelay.BATCH)).thenReturn(List.of());

        assertThat(relay.sendPending()).isZero();
        verify(outbox, never()).markSent(anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void oldSentRowsAreDeletedAfterSevenDays() {
        relay.deleteOldSentRows();

        verify(outbox).deleteSentBefore(Instant.parse("2026-10-28T10:00:00Z"));
    }
}
