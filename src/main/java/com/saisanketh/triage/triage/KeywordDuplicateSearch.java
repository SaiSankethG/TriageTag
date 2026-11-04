package com.saisanketh.triage.triage;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Baseline #1: keyword search with Postgres full-text search (the issue.search_tsv column, Flyway V5).
 *
 * <p>How: take the query issue's own words (the lexemes in its search_tsv), join them with OR into a tsquery,
 * find earlier issues of the same repo that contain any of them, and rank by ts_rank (more shared words, and
 * words in the title, rank higher). That is roughly what a maintainer does when searching by the issue's words.
 */
@Component
@RequiredArgsConstructor
public class KeywordDuplicateSearch implements DuplicateSearch {

    /**
     * 1. q: the query issue (repo, creation time, its tsvector).
     * 2. terms: every lexeme of its tsvector, each quoted (backslashes and quotes escaped), joined with " | " (OR).
     *    Cast with ::tsquery (not to_tsquery) because the lexemes are already stemmed.
     * 3. Earlier issues of the same repo that match any term, best ts_rank first.
     */
    static final String SQL = """
            WITH q AS (
                SELECT id, repo, created_at, search_tsv FROM issue WHERE id = ?
            ), terms AS (
                SELECT string_agg('''' || replace(replace(u.lexeme, '\\', '\\\\'), '''', '''''') || '''', ' | ')::tsquery AS query
                FROM q, unnest(q.search_tsv) AS u(lexeme, positions, weights)
            )
            SELECT i.number, i.title, ts_rank(i.search_tsv, terms.query) AS score
            FROM issue i, q, terms
            WHERE i.repo = q.repo
              AND i.created_at < q.created_at
              AND i.search_tsv @@ terms.query
            ORDER BY score DESC, i.number DESC
            LIMIT ?
            """;

    private final JdbcTemplate jdbc;

    @Override
    public String name() {
        return "keyword";
    }

    @Override
    public List<SimilarIssue> findSimilar(long issueId, int limit) {
        return jdbc.query(SQL,
                (rs, row) -> new SimilarIssue(rs.getInt("number"), rs.getString("title"), rs.getDouble("score")),
                issueId, limit);
    }
}
