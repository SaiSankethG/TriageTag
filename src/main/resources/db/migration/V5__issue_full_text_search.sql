-- V5: full-text search on issues, the keyword baseline for duplicate search (M3).
-- A generated column: Postgres computes it from title and body on every insert/update; we never write it.
-- Text = title (weight A, counts most) + the first 2,000 characters of the body (weight B) with HTML comments
-- removed (GitHub issue templates are mostly <!-- ... --> instructions). The vector search embeds the same text,
-- so the comparison between the two is fair.
ALTER TABLE issue ADD COLUMN search_tsv tsvector GENERATED ALWAYS AS (
    setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('english',
        left(regexp_replace(coalesce(body, ''), '<!--.*?-->', ' ', 'g'), 2000)), 'B')
) STORED;

-- GIN = an inverted index: for every word, the list of rows that contain it. Makes "@@" (matches) fast.
CREATE INDEX idx_issue_search_tsv ON issue USING gin (search_tsv);
