package com.saisanketh.triage.docs;

import com.saisanketh.triage.embedding.IssueEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** The doc_chunk table (Flyway V7), plain SQL with the vector passed as text ({@code ?::vector}). */
@Repository
@RequiredArgsConstructor
public class DocChunkRepository {

    /** One section that matched a query. */
    public record DocMatch(String sourceUrl, String heading, String text, double score) {
    }

    private final JdbcTemplate jdbc;

    /** Replaces all of a repo's chunks in one transaction: readers see the old docs or the new ones, never half. */
    @Transactional
    public void replaceAll(String repo, String docVersion, List<DocSection> sections, List<float[]> vectors) {
        jdbc.update("DELETE FROM doc_chunk WHERE repo = ?", repo);
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            DocSection s = sections.get(i);
            rows.add(new Object[]{repo, docVersion, s.sourceUrl(), s.heading(), s.text(),
                    IssueEmbeddingRepository.toVectorText(vectors.get(i))});
        }
        jdbc.batchUpdate("""
                INSERT INTO doc_chunk (repo, doc_version, source_url, heading, text, embedding, created_at)
                VALUES (?, ?, ?, ?, ?, ?::vector, now())
                """, rows);
    }

    /**
     * The repo's sections nearest to the vector, most similar first. Same HNSW settings as the issue search: the
     * iterative scan keeps going until enough rows of THIS repo are found (SET LOCAL: only for this transaction).
     */
    @Transactional(readOnly = true)
    public List<DocMatch> nearest(String repo, float[] vector, int limit) {
        String v = IssueEmbeddingRepository.toVectorText(vector);
        jdbc.execute("SET LOCAL hnsw.iterative_scan = strict_order");
        return jdbc.query("""
                SELECT source_url, heading, text, 1 - (embedding <=> ?::vector) AS score
                FROM doc_chunk
                WHERE lower(repo) = lower(?)
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """, (rs, row) -> new DocMatch(rs.getString("source_url"), rs.getString("heading"), rs.getString("text"),
                rs.getDouble("score")), v, repo, v, limit);
    }

    public int count(String repo) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM doc_chunk WHERE lower(repo) = lower(?)", Integer.class, repo);
        return n == null ? 0 : n;
    }
}
