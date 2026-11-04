-- V4: vectors for similar-issue search (M3).
-- pgvector adds the "vector" column type and distance operators (<=> is cosine distance).
CREATE EXTENSION IF NOT EXISTS vector;

-- One embedding per issue. 384 numbers = the output size of the all-MiniLM-L6-v2 model.
CREATE TABLE issue_embedding (
    issue_id   bigint       PRIMARY KEY REFERENCES issue (id) ON DELETE CASCADE,   -- deleted issue -> embedding goes too
    model      varchar(100) NOT NULL,                                             -- which model made it, so a model change can re-embed
    embedding  vector(384)  NOT NULL,
    created_at timestamptz  NOT NULL
);

-- HNSW = an approximate nearest-neighbour index (a layered graph), so "the 10 most similar issues" doesn't
-- have to compare against every row. vector_cosine_ops: the index is built for cosine distance (<=>).
CREATE INDEX idx_issue_embedding_hnsw ON issue_embedding USING hnsw (embedding vector_cosine_ops);
