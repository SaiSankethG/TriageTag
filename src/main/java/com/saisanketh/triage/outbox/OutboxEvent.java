package com.saisanketh.triage.outbox;

/** One row of outbox_event that still has to be sent. */
public record OutboxEvent(long id, String topic, String key, String payload) {
}
