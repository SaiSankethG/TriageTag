# Database schema v1 (M1), plus webhook_delivery (M2)

Decided 3 Nov 2026. Source-neutral, so Jira can be added later without changes.
Since M2 the schema is managed by Flyway (`src/main/resources/db/migration`): V1 = this schema,
V2 drops Hibernate's `CHECK (source = 'GITHUB')`, V3 adds `webhook_delivery`, V4 adds pgvector + `issue_embedding`.

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
  state_reason       varchar(30),                -- completed / not_planned / duplicate / reopened; usually NULL while open. Old closed duplicates show 'completed' (not NULL), so the replay also uses the "status: duplicate" label
  issue_type         varchar(50),                -- Bug / Feature; NULL when the repo doesn't use issue types
  author_association varchar(30)  NOT NULL,      -- OWNER / MEMBER / CONTRIBUTOR / NONE ...
  milestone          varchar(100),               -- e.g. '3.4.2'; for the "already fixed?" hint
  html_url           text         NOT NULL,      -- link shown in the triage report
  created_at         timestamptz  NOT NULL,      -- replay test runs in this order
  updated_at         timestamptz  NOT NULL,      -- next backfill fetches only what changed
  closed_at          timestamptz,                -- NULL while open
  duplicate_of       integer,                    -- answer key: number of the original issue (same repo); NULL unless closed as a duplicate. Filled in M1 step 'store the answer key'
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

-- Added in M3 (Flyway V4): pgvector + one embedding per issue (all-MiniLM-L6-v2 -> 384 numbers).
CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE issue_embedding (
  issue_id   bigint       PRIMARY KEY REFERENCES issue(id) ON DELETE CASCADE,
  model      varchar(100) NOT NULL,       -- which model made it, so a model change can re-embed
  embedding  vector(384)  NOT NULL,
  created_at timestamptz  NOT NULL
);
CREATE INDEX idx_issue_embedding_hnsw ON issue_embedding USING hnsw (embedding vector_cosine_ops);

-- Added in M2 (Flyway V3): one row per accepted GitHub webhook delivery.
CREATE TABLE webhook_delivery (
  delivery_id varchar(64)  PRIMARY KEY,     -- X-GitHub-Delivery (a UUID); the PK makes a redelivery a no-op
  event       varchar(50)  NOT NULL,        -- X-GitHub-Event, e.g. 'issues'
  action      varchar(50),                  -- e.g. 'opened'
  repo        varchar(200),                 -- 'owner/name'
  received_at timestamptz  NOT NULL
);

-- Added in M4 (Flyway V6): how a closed issue was fixed, fetched from GitHub once and cached ("already fixed?" hint).
CREATE TABLE issue_fix (
  issue_id      bigint       PRIMARY KEY REFERENCES issue(id) ON DELETE CASCADE,
  closer_type   varchar(20),     -- 'PullRequest' / 'Commit'; null if unknown
  closer_number integer,         -- PR number (null for a commit)
  closer_url    text,
  fix_version   varchar(100),    -- the PR's milestone, else the issue's milestone
  fetched_at    timestamptz  NOT NULL
);

-- Added in M4 (Flyway V7): the repo's reference docs, cut into sections, for draft replies (RAG).
CREATE TABLE doc_chunk (
  id          bigserial    PRIMARY KEY,
  repo        varchar(200) NOT NULL,
  doc_version varchar(50)  NOT NULL,    -- git tag the docs were read from, e.g. 'v4.1.2'
  source_url  text         NOT NULL,    -- published page + #anchor of the section
  heading     text         NOT NULL,    -- 'Page > Section > Subsection'
  text        text         NOT NULL,
  embedding   vector(384)  NOT NULL,    -- same model as issue_embedding
  created_at  timestamptz  NOT NULL
);
CREATE INDEX idx_doc_chunk_repo ON doc_chunk (repo);
CREATE INDEX idx_doc_chunk_hnsw ON doc_chunk USING hnsw (embedding vector_cosine_ops);
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
