-- V8: one triage report per issue (M4 step 5): everything the assistant tells the maintainer, as created.
-- The parts are jsonb: they are read as a whole by the API and the demo page, never queried field by field.
CREATE TABLE triage_report (
    id              bigserial    PRIMARY KEY,
    issue_id        bigint       NOT NULL UNIQUE REFERENCES issue (id) ON DELETE CASCADE,  -- one report per issue
    created_at      timestamptz  NOT NULL,
    duplicates      jsonb        NOT NULL,   -- top 3 similar earlier issues: number, title, url, score
    duplicate_check jsonb,                   -- LLM "same root problem?" on the top match; null if below the threshold
    labels          jsonb        NOT NULL,   -- suggested labels with their vote share
    missing         jsonb        NOT NULL,   -- missing-details check
    fixed_hint      jsonb,                   -- "already fixed in X?" hint; null if none
    draft_reply     jsonb        NOT NULL,   -- RAG draft: status, text, sources
    llm_model       varchar(100) NOT NULL,   -- e.g. 'fake'
    prompt_version  text         NOT NULL,   -- the prompt files used, e.g. 'missing-details-v1,draft-reply-v1'
    cost_tokens     integer      NOT NULL,   -- all LLM tokens for this report
    notes           text                     -- e.g. why LLM steps were skipped
);
