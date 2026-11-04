-- V10: transactional outbox (M5 step 0). The webhook receiver writes the event here in the SAME transaction as the
-- webhook_delivery row; a separate relay sends unsent rows to Kafka and marks them sent.
CREATE TABLE outbox_event (
    id          bigserial    PRIMARY KEY,                 -- also the send order
    topic       varchar(200) NOT NULL,
    message_key varchar(300) NOT NULL,                    -- Kafka key ('owner/repo#number'): same issue -> same partition
    payload     text         NOT NULL,                    -- the message as JSON text, sent as it is
    created_at  timestamptz  NOT NULL,
    sent_at     timestamptz,                              -- null until Kafka confirmed it
    attempts    integer      NOT NULL DEFAULT 0,          -- failed send attempts
    last_error  text                                      -- why the last attempt failed
);
-- The relay only ever looks for unsent rows in id order: a small partial index covers exactly that.
CREATE INDEX idx_outbox_event_unsent ON outbox_event (id) WHERE sent_at IS NULL;
