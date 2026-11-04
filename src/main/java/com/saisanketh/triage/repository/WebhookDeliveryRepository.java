package com.saisanketh.triage.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/**
 * Remembers which webhook deliveries we already accepted. Plain SQL (JdbcTemplate) instead of JPA because the
 * whole job is one Postgres statement, {@code INSERT ... ON CONFLICT DO NOTHING}. It runs in the same
 * transaction as the JPA code, because Spring gives both the same database connection.
 */
@Repository
@RequiredArgsConstructor
public class WebhookDeliveryRepository {

    private final JdbcTemplate jdbc;

    /**
     * @return true if this delivery id is new (a row was inserted), false if we have seen it before
     */
    /** When we accepted this delivery; empty if it isn't stored (e.g. a message published by hand). */
    public Optional<Instant> receivedAt(String deliveryId) {
        return jdbc.query("SELECT received_at FROM webhook_delivery WHERE delivery_id = ?",
                (rs, row) -> rs.getTimestamp("received_at").toInstant(), deliveryId).stream().findFirst();
    }

    public boolean insertIfNew(String deliveryId, String event, String action, String repo, Instant receivedAt) {
        int rows = jdbc.update("""
                INSERT INTO webhook_delivery (delivery_id, event, action, repo, received_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (delivery_id) DO NOTHING
                """, deliveryId, event, action, repo, Timestamp.from(receivedAt));
        return rows == 1;
    }
}
