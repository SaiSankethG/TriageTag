package com.saisanketh.triage.embedding;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads and writes issue_embedding with plain SQL. pgvector reads a vector from text like "[0.1,0.2,...]",
 * so we pass a String and cast it in SQL ({@code ?::vector}); no extra Java library is needed.
 */
@Repository
@RequiredArgsConstructor
public class IssueEmbeddingRepository {

    /** The text inputs for one issue. */
    public record IssueText(long issueId, String title, String body) {
    }

    private final JdbcTemplate jdbc;

    /** Issues of the repo without an embedding from this model (none at all, or one from an older model). */
    public List<IssueText> findMissing(String repo, String model, int limit) {
        return jdbc.query("""
                SELECT i.id, i.title, i.body
                FROM issue i
                LEFT JOIN issue_embedding e ON e.issue_id = i.id AND e.model = ?
                WHERE lower(i.repo) = lower(?) AND e.issue_id IS NULL
                ORDER BY i.id
                LIMIT ?
                """, (rs, row) -> new IssueText(rs.getLong("id"), rs.getString("title"), rs.getString("body")),
                model, repo, limit);
    }

    public int countMissing(String repo, String model) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM issue i
                LEFT JOIN issue_embedding e ON e.issue_id = i.id AND e.model = ?
                WHERE lower(i.repo) = lower(?) AND e.issue_id IS NULL
                """, Integer.class, model, repo);
    }

    public Optional<IssueText> findText(long issueId) {
        return jdbc.query("SELECT id, title, body FROM issue WHERE id = ?",
                (rs, row) -> new IssueText(rs.getLong("id"), rs.getString("title"), rs.getString("body")),
                issueId).stream().findFirst();
    }

    /** Our id for an issue, by its natural key. */
    public Optional<Long> findIssueId(String source, String externalId) {
        return jdbc.queryForList("SELECT id FROM issue WHERE source = ? AND external_id = ?", Long.class,
                source, externalId).stream().findFirst();
    }

    /** Insert, or replace an existing embedding (e.g. the issue was edited, or the model changed). */
    public void saveAll(List<Long> issueIds, List<float[]> vectors, String model) {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < issueIds.size(); i++) {
            rows.add(new Object[]{issueIds.get(i), model, toVectorText(vectors.get(i))});
        }
        jdbc.batchUpdate("""
                INSERT INTO issue_embedding (issue_id, model, embedding, created_at)
                VALUES (?, ?, ?::vector, now())
                ON CONFLICT (issue_id) DO UPDATE
                    SET model = EXCLUDED.model, embedding = EXCLUDED.embedding, created_at = EXCLUDED.created_at
                """, rows);
    }

    /** float[] {0.1f, -0.2f} -> "[0.1,-0.2]", the text form pgvector understands. */
    public static String toVectorText(float[] vector) {
        StringBuilder text = new StringBuilder(vector.length * 12).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(vector[i]);
        }
        return text.append(']').toString();
    }
}
