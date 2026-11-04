-- V11: comment mode (M5 step 4).

-- Per-repo mode. No row = SHADOW (private reports only). SUGGEST = may post ONE comment on a new issue.
-- Changed with SQL on purpose (no HTTP endpoint): turning on public comments is a deliberate act by the operator.
CREATE TABLE repo_setting (
    repo varchar(200) PRIMARY KEY,                       -- 'owner/name', lower case
    mode varchar(20)  NOT NULL DEFAULT 'SHADOW' CHECK (mode IN ('SHADOW', 'SUGGEST'))
);

-- One row per issue we decided to comment on. The row is written BEFORE posting (status PENDING), and issue_id is
-- unique, so a second attempt (Kafka redelivery, a second app instance) finds the row and never posts again.
CREATE TABLE posted_comment (
    issue_id     bigint       PRIMARY KEY REFERENCES issue (id) ON DELETE CASCADE,
    report_id    bigint       NOT NULL REFERENCES triage_report (id) ON DELETE CASCADE,
    status       varchar(20)  NOT NULL CHECK (status IN ('PENDING', 'POSTED', 'FAILED')),
    comment_id   bigint,                                 -- GitHub's id of the comment, once posted
    comment_url  text,
    error        text,                                   -- why posting failed (FAILED)
    created_at   timestamptz  NOT NULL,
    posted_at    timestamptz
);
