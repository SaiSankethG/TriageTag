package com.saisanketh.triage.triage;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Similar-issue search with embeddings: cosine similarity between issue vectors (pgvector, HNSW index).
 *
 * <p>Two queries: (1) the query issue's vector, repo and creation time; (2) the nearest earlier issues of the same
 * repo, ordered by cosine distance with the vector as a parameter, so Postgres can use the HNSW index.
 *
 * <p>HNSW + WHERE: the index returns the nearest vectors first and the filter (same repo, created before) is applied
 * after. Without help, a filtered query can return fewer than {@code limit} rows. pgvector 0.8's iterative index scan
 * ({@code hnsw.iterative_scan = strict_order}) keeps scanning until enough rows pass the filter, in exact distance order.
 * Both settings are SET LOCAL: they only last for this transaction.
 */
@Component
@RequiredArgsConstructor
public class VectorDuplicateSearch implements DuplicateSearch {

    /** How many candidates the index looks at per step (default 40). Higher = better recall, a bit slower. */
    static final int EF_SEARCH = 100;

    /** One nearest earlier issue, with our id (needed to look up its labels). */
    public record Neighbour(long id, int number, String title, double score) {
    }

    private final JdbcTemplate jdbc;

    @Override
    public String name() {
        return "vector";
    }

    @Override
    @Transactional(readOnly = true)
    public List<SimilarIssue> findSimilar(long issueId, int limit) {
        return findNeighbours(issueId, limit).stream()
                .map(n -> new SimilarIssue(n.number(), n.title(), n.score()))
                .toList();
    }

    /** The nearest earlier issues of the same repo, most similar first. Empty if the issue has no embedding. */
    @Transactional(readOnly = true)
    public List<Neighbour> findNeighbours(long issueId, int limit) {
        List<Map<String, Object>> query = jdbc.queryForList("""
                SELECT i.repo, i.created_at, e.embedding::text AS vector
                FROM issue i JOIN issue_embedding e ON e.issue_id = i.id
                WHERE i.id = ?
                """, issueId);
        if (query.isEmpty()) {
            return List.of();   // no embedding yet: nothing to compare with
        }
        String repo = (String) query.get(0).get("repo");
        Object createdAt = query.get(0).get("created_at");   // passed straight back as a query parameter
        String vector = (String) query.get(0).get("vector");

        jdbc.execute("SET LOCAL hnsw.iterative_scan = strict_order");
        jdbc.execute("SET LOCAL hnsw.ef_search = " + EF_SEARCH);
        return jdbc.query("""
                SELECT i.id, i.number, i.title, 1 - (e.embedding <=> ?::vector) AS score
                FROM issue_embedding e JOIN issue i ON i.id = e.issue_id
                WHERE i.repo = ? AND i.created_at < ?
                ORDER BY e.embedding <=> ?::vector
                LIMIT ?
                """,
                (rs, row) -> new Neighbour(rs.getLong("id"), rs.getInt("number"), rs.getString("title"),
                        rs.getDouble("score")),
                vector, repo, createdAt, vector, limit);
    }
}
