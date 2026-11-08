-- V12: "Add repository" jobs (backend piece 1, 8 Nov 2026). One row per request to load a repository: when the GitHub App
-- is installed on it (or added to an installation), or when the operator asks. A background runner does the steps in
-- order (load past issues -> embeddings -> reference docs) and writes its progress here, so anyone can see how far it is.
CREATE TABLE repo_job (
    id          bigserial    PRIMARY KEY,
    repo        varchar(200) NOT NULL,                  -- 'owner/name', as GitHub spells it
    reason      varchar(20)  NOT NULL CHECK (reason IN ('INSTALLED', 'ADMIN')),
    status      varchar(20)  NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'DONE', 'FAILED')),
    step        varchar(20)  CHECK (step IN ('BACKFILL', 'EMBEDDINGS', 'DOCS')),   -- the step running now (or last)
    done        integer,                                -- progress inside the step: issues loaded, issues embedded...
    total       integer,                                -- ...out of this many, when known (null = not known yet)
    note        text,                                   -- e.g. why there are no reference docs
    error       text,                                   -- why it FAILED
    created_at  timestamptz  NOT NULL,
    started_at  timestamptz,
    finished_at timestamptz
);

-- At most one waiting or running job per repository (ignoring case: GitHub names are case-insensitive): installing twice,
-- or a redelivered webhook, adds no second job.
CREATE UNIQUE INDEX repo_job_one_open ON repo_job (lower(repo)) WHERE status IN ('QUEUED', 'RUNNING');
-- "The latest job of this repo" (the status endpoint).
CREATE INDEX repo_job_repo ON repo_job (lower(repo), id DESC);
