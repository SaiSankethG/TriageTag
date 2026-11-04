package com.saisanketh.triage.db;

import com.saisanketh.triage.embedding.IssueEmbeddingRepository;
import com.saisanketh.triage.embedding.IssueEmbeddingRepository.IssueText;
import com.saisanketh.triage.repository.IssueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.List;

import static com.saisanketh.triage.service.TestIssues.issue;
import static org.assertj.core.api.Assertions.assertThat;

class IssueEmbeddingRepositoryDbTest extends PostgresTest {

    @Autowired
    private IssueRepository issueRepository;
    @Autowired
    private IssueEmbeddingRepository embeddingRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private static float[] vector(float value) {
        float[] v = new float[384];
        Arrays.fill(v, value);
        return v;
    }

    @Test
    void onlyIssuesWithoutAnEmbeddingFromThisModelAreMissing() {
        long one = issueRepository.saveAndFlush(issue(1, "One")).getId();
        long two = issueRepository.saveAndFlush(issue(2, "Two")).getId();

        embeddingRepository.saveAll(List.of(one), List.of(vector(0.1f)), "model-a");

        assertThat(embeddingRepository.findMissing("octo-org/hello", "model-a", 10))
                .extracting(IssueText::issueId).containsExactly(two);
        assertThat(embeddingRepository.countMissing("Octo-Org/Hello", "model-a")).isEqualTo(1);
        // With a new model, everything needs embedding again.
        assertThat(embeddingRepository.countMissing("octo-org/hello", "model-b")).isEqualTo(2);
    }

    @Test
    void savingAgainReplacesTheVectorAndTheModel() {
        long id = issueRepository.saveAndFlush(issue(1, "One")).getId();

        embeddingRepository.saveAll(List.of(id), List.of(vector(0.1f)), "model-a");
        embeddingRepository.saveAll(List.of(id), List.of(vector(-0.1f)), "model-b");

        assertThat(jdbc.queryForObject("select count(*) from issue_embedding", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select model from issue_embedding", String.class)).isEqualTo("model-b");
        // The stored vector is the new one: cosine distance to a positive vector is 2 (opposite).
        assertThat(jdbc.queryForObject("select embedding <=> ?::vector from issue_embedding", Double.class,
                IssueEmbeddingRepository.toVectorText(vector(1f)))).isGreaterThan(1.99);
    }

    @Test
    void findsTheIssueIdByItsNaturalKey() {
        long id = issueRepository.saveAndFlush(issue(1, "One")).getId();

        assertThat(embeddingRepository.findIssueId("GITHUB", "1001")).contains(id);
        assertThat(embeddingRepository.findIssueId("GITHUB", "nope")).isEmpty();
    }

    @Test
    void vectorTextIsWhatPgvectorReads() {
        assertThat(IssueEmbeddingRepository.toVectorText(new float[]{0.5f, -1.25f, 3f})).isEqualTo("[0.5,-1.25,3.0]");
    }
}
