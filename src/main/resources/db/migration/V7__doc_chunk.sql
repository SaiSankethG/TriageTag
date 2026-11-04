-- V7: the repo's documentation, cut into sections, for draft replies (M4 step 4, RAG).
CREATE TABLE doc_chunk (
    id          bigserial    PRIMARY KEY,
    repo        varchar(200) NOT NULL,   -- whose docs, 'owner/name'
    doc_version varchar(50)  NOT NULL,   -- the git tag the docs were read from, e.g. 'v4.1.2'
    source_url  text         NOT NULL,   -- the published page + #anchor of the section
    heading     text         NOT NULL,   -- 'Page title > Section > Subsection'
    text        text         NOT NULL,   -- the section's text (one piece of it if the section is long)
    embedding   vector(384)  NOT NULL,   -- same model as issue_embedding, so issues and docs can be compared
    created_at  timestamptz  NOT NULL
);
CREATE INDEX idx_doc_chunk_repo ON doc_chunk (repo);
CREATE INDEX idx_doc_chunk_hnsw ON doc_chunk USING hnsw (embedding vector_cosine_ops);
