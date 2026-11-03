# Database schema v1 (M1)

Decided 3 Nov 2026. Source-neutral, so Jira can be added later without changes.

```sql
CREATE TABLE issue (
  id                 bigserial    PRIMARY KEY,   -- our own id
  source             varchar(20)  NOT NULL,      -- 'GITHUB' (later 'JIRA')
  external_id        varchar(64)  NOT NULL,      -- GitHub's id; text so Jira keys like 'KAFKA-123' fit
  repo               varchar(200) NOT NULL,      -- 'spring-projects/spring-boot'
  number             integer      NOT NULL,      -- issue number, e.g. 52529
  title              text         NOT NULL,
  body               text,                       -- GitHub allows an empty body
  state              varchar(20)  NOT NULL,      -- open / closed
  state_reason       varchar(30),                -- completed / not_planned / duplicate / reopened; NULL while open AND on old closed issues (pre-2022), so the replay also uses the "status: duplicate" label
  issue_type         varchar(50),                -- Bug / Feature; NULL when the repo doesn't use issue types
  author_association varchar(30)  NOT NULL,      -- OWNER / MEMBER / CONTRIBUTOR / NONE ...
  milestone          varchar(100),               -- e.g. '3.4.2'; for the "already fixed?" hint
  html_url           text         NOT NULL,      -- link shown in the triage report
  created_at         timestamptz  NOT NULL,      -- replay test runs in this order
  updated_at         timestamptz  NOT NULL,      -- next backfill fetches only what changed
  closed_at          timestamptz,                -- NULL while open
  raw                jsonb        NOT NULL,      -- full GitHub JSON, so we can re-process without re-downloading
  CONSTRAINT uq_issue_source_external UNIQUE (source, external_id)  -- re-runs update, never duplicate
);

CREATE INDEX idx_issue_repo_created ON issue (repo, created_at);   -- replay in date order per repo

CREATE TABLE issue_label (
  issue_id   bigint       NOT NULL REFERENCES issue(id) ON DELETE CASCADE,
  label_name varchar(100) NOT NULL,                -- e.g. 'type: bug'
  PRIMARY KEY (issue_id, label_name)               -- same label can't be added twice
);

CREATE INDEX idx_issue_label_name ON issue_label (label_name);     -- "all issues labelled X"
```

## Why these choices (interview notes)
- `timestamptz`, not `timestamp`: GitHub sends UTC times; timestamptz stores the exact moment, whatever the server's time zone.
- `external_id` is text, not a number: GitHub ids are numbers, but Jira keys are not.
- UNIQUE (`source`, `external_id`) is what makes the backfill safe to re-run (upsert).
- Labels in their own table: normalized, indexed, maps to JPA `@ElementCollection`.
- `raw` JSONB: keeps everything GitHub sent, so a new feature can use more fields later without another download.
- Not stored: author login (less personal data), URLs we can rebuild, reactions, assignees.

## JPA mapping hints (for the entity you write)
- `timestamptz` -> `Instant` (or `OffsetDateTime`)
- `jsonb` -> `String` with `@JdbcTypeCode(SqlTypes.JSON)`
- `source` -> an enum `SourceType { GITHUB }` (the name `IssueSource` is kept for the fetching interface) with `@Enumerated(EnumType.STRING)`
- labels -> `@ElementCollection` + `@CollectionTable(name = "issue_label")`
