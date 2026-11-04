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
 * repo, ordered by cosine distance with the vector as a parameter, so Postgres can use the HNSW index. Issues with
 * one of the repo's excluded labels ({@link CandidateFilter}) are left out, like any other filter.
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
    private final CandidateFilter candidateFilter;

    @Override
    public String name() {
        return "vector";
    }

    /**
     * Overrides the interface's default method only to put @Transactional on it. Without this, a call to
     * findSimilar(id, limit) goes through the Spring proxy WITHOUT a transaction (the default method has no annotation),
     * then calls the 3-argument method on the object itself, which skips the proxy. The SET LOCAL settings below then do
     * nothing (outside a transaction they last for zero statements), the iterative scan is off, and a filtered search
     * can come back empty. Found in M4 step 0: 35 of 1,000 false-alarm samples had no result.
     */
    @Override
    @Transactional(readOnly = true)
    public List<SimilarIssue> findSimilar(long issueId, int limit) {
        return findSimilar(issueId, limit, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SimilarIssue> findSimilar(long issueId, int limit, boolean applyCandidateFilter) {
        return findNeighbours(issueId, limit, applyCandidateFilter).stream()
                .map(n -> new SimilarIssue(n.number(), n.title(), n.score()))
                .toList();
    }

    /** The nearest earlier candidates of the same repo (candidate filter on), most similar first. */
    @Transactional(readOnly = true)
    public List<Neighbour> findNeighbours(long issueId, int limit) {
        return findNeighbours(issueId, limit, true);
    }

    /** The nearest earlier issues of the same repo, most similar first. Empty if the issue has no embedding. */
    @Transactional(readOnly = true)
    public List<Neighbour> findNeighbours(long issueId, int limit, boolean applyCandidateFilter) {
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
        List<String> excluded = applyCandidateFilter ? candidateFilter.excludedLabels(repo) : List.of();

        jdbc.execute("SET LOCAL hnsw.iterative_scan = strict_order");
        jdbc.execute("SET LOCAL hnsw.ef_search = " + EF_SEARCH);
        return jdbc.query("""
                SELECT i.id, i.number, i.title, 1 - (e.embedding <=> ?::vector) AS score
                FROM issue_embedding e JOIN issue i ON i.id = e.issue_id
                WHERE i.repo = ? AND i.created_at < ?
                  AND NOT EXISTS (SELECT 1 FROM issue_label l WHERE l.issue_id = i.id AND l.label_name = ANY (?))
                ORDER BY e.embedding <=> ?::vector
                LIMIT ?
                """,
                (rs, row) -> new Neighbour(rs.getLong("id"), rs.getInt("number"), rs.getString("title"),
                        rs.getDouble("score")),
                vector, repo, createdAt, excluded.toArray(new String[0]), vector, limit);
    }
}
