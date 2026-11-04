package com.saisanketh.triage.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** The outbox_event table (Flyway V10), plain SQL. */
@Repository
@RequiredArgsConstructor
public class OutboxRepository {

    private final JdbcTemplate jdbc;

    /** Called inside the caller's transaction: the row exists only if that transaction commits. */
    public void add(String topic, String key, String payload, Instant createdAt) {
        jdbc.update("INSERT INTO outbox_event (topic, message_key, payload, created_at) VALUES (?, ?, ?, ?)",
                topic, key, payload, Timestamp.from(createdAt));
    }

    /**
     * The oldest unsent rows, locked for this transaction. {@code FOR UPDATE SKIP LOCKED}: if two app instances run the
     * relay, each takes different rows instead of waiting for (or double-sending) the other's.
     */
    public List<OutboxEvent> lockUnsent(int limit) {
        return jdbc.query("""
                SELECT id, topic, message_key, payload FROM outbox_event
                WHERE sent_at IS NULL
                ORDER BY id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """, (rs, row) -> new OutboxEvent(rs.getLong("id"), rs.getString("topic"), rs.getString("message_key"),
                rs.getString("payload")), limit);
    }

    public void markSent(long id, Instant sentAt) {
        jdbc.update("UPDATE outbox_event SET sent_at = ? WHERE id = ?", Timestamp.from(sentAt), id);
    }

    public void markFailed(long id, String error) {
        jdbc.update("UPDATE outbox_event SET attempts = attempts + 1, last_error = ? WHERE id = ?", error, id);
    }

    /** Housekeeping: sent rows are only kept for a while (for debugging). */
    public int deleteSentBefore(Instant before) {
        return jdbc.update("DELETE FROM outbox_event WHERE sent_at IS NOT NULL AND sent_at < ?", Timestamp.from(before));
    }

    public int countUnsent() {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE sent_at IS NULL", Integer.class);
        return n == null ? 0 : n;
    }
}
