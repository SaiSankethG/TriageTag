package com.saisanketh.triage.db;

import com.saisanketh.triage.outbox.OutboxEvent;
import com.saisanketh.triage.outbox.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRepositoryDbTest extends PostgresTest {

    private static final Instant T = Instant.parse("2026-11-04T10:00:00Z");

    @Autowired
    private OutboxRepository outbox;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void unsentRowsComeOldestFirstAndLeaveOnceSentOrFailedRowsStay() {
        outbox.add("issue-events", "o/r#1", "{\"a\": 1}", T);
        outbox.add("issue-events", "o/r#2", "{\"a\": 2}", T);
        outbox.add("issue-events", "o/r#1", "{\"a\": 3}", T);

        List<OutboxEvent> unsent = outbox.lockUnsent(10);
        assertThat(unsent).extracting(OutboxEvent::payload).containsExactly("{\"a\": 1}", "{\"a\": 2}", "{\"a\": 3}");
        assertThat(outbox.lockUnsent(2)).hasSize(2);

        outbox.markSent(unsent.get(0).id(), T.plusSeconds(1));
        outbox.markFailed(unsent.get(1).id(), "Kafka did not confirm");
        outbox.markFailed(unsent.get(1).id(), "Kafka did not confirm again");

        assertThat(outbox.lockUnsent(10)).extracting(OutboxEvent::id)
                .containsExactly(unsent.get(1).id(), unsent.get(2).id());
        assertThat(outbox.countUnsent()).isEqualTo(2);
        assertThat(jdbc.queryForMap("SELECT attempts, last_error FROM outbox_event WHERE id = ?", unsent.get(1).id()))
                .containsEntry("attempts", 2).containsEntry("last_error", "Kafka did not confirm again");
    }

    @Test
    void onlySentRowsOlderThanTheCutoffAreDeleted() {
        outbox.add("t", "k", "{}", T);
        outbox.add("t", "k", "{}", T);
        outbox.add("t", "k", "{}", T);
        List<OutboxEvent> rows = outbox.lockUnsent(10);
        outbox.markSent(rows.get(0).id(), T.minusSeconds(8 * 24 * 3600));   // sent 8 days ago
        outbox.markSent(rows.get(1).id(), T);                                // sent just now

        assertThat(outbox.deleteSentBefore(T.minusSeconds(7 * 24 * 3600))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event", Integer.class)).isEqualTo(2);
    }
}
