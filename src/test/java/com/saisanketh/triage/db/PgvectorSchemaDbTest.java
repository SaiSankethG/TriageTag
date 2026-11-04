package com.saisanketh.triage.db;

import com.saisanketh.triage.repository.IssueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;

import static com.saisanketh.triage.service.TestIssues.issue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** V4 on a fresh database: pgvector is installed, vectors can be stored, compared and are deleted with the issue. */
class PgvectorSchemaDbTest extends PostgresTest {

    @Autowired
    private IssueRepository issueRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void storesAVectorAndComparesWithCosineDistance() {
        long id = issueRepository.saveAndFlush(issue(1, "With an embedding")).getId();
        jdbc.update("insert into issue_embedding (issue_id, model, embedding, created_at) values (?, 'test', ?::vector, now())",
                id, vector(1.0f));

        // Same direction -> cosine distance 0; opposite direction -> 2.
        Double same = jdbc.queryForObject("select embedding <=> ?::vector from issue_embedding", Double.class, vector(1.0f));
        Double opposite = jdbc.queryForObject("select embedding <=> ?::vector from issue_embedding", Double.class, vector(-1.0f));

        assertThat(same).isCloseTo(0.0, within(1e-6));
        assertThat(opposite).isCloseTo(2.0, within(1e-6));
    }

    @Test
    void deletingTheIssueDeletesItsEmbedding() {
        long id = issueRepository.saveAndFlush(issue(1, "Will be deleted")).getId();
        jdbc.update("insert into issue_embedding (issue_id, model, embedding, created_at) values (?, 'test', ?::vector, now())",
                id, vector(0.5f));

        jdbc.update("delete from issue where id = ?", id);

        assertThat(jdbc.queryForObject("select count(*) from issue_embedding", Integer.class)).isZero();
    }

    @Test
    void theHnswIndexExists() {
        assertThat(jdbc.queryForObject(
                "select indexdef from pg_indexes where indexname = 'idx_issue_embedding_hnsw'", String.class))
                .contains("hnsw").contains("vector_cosine_ops");
    }

    /** A 384-number vector with every value = v, written the way pgvector reads it: "[v,v,...]". */
    private static String vector(float v) {
        float[] values = new float[384];
        Arrays.fill(values, v);
        return Arrays.toString(values).replace(" ", "");
    }
}
