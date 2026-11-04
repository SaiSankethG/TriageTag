-- V3: one row per GitHub webhook delivery we accepted. The primary key on delivery_id (GitHub's
-- X-GitHub-Delivery header, a UUID) is what makes a redelivered webhook a no-op.
CREATE TABLE webhook_delivery (
    delivery_id varchar(64)  PRIMARY KEY,
    event       varchar(50)  NOT NULL,      -- X-GitHub-Event, e.g. 'issues'
    action      varchar(50),                -- e.g. 'opened'
    repo        varchar(200),               -- 'owner/name'
    received_at timestamptz  NOT NULL
);
