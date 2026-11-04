package com.saisanketh.triage.db;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.embedding.IssueEmbeddingRepository;
import com.saisanketh.triage.repository.IssueRepository;
import com.saisanketh.triage.triage.SimilarIssue;
import com.saisanketh.triage.triage.SimilarIssueService;
import com.saisanketh.triage.triage.VectorDuplicateSearch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static com.saisanketh.triage.service.TestIssues.issue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Vector search on real pgvector, with hand-made vectors (no model needed). */
class VectorDuplicateSearchDbTest extends PostgresTest {

    @Autowired
    private IssueRepository issueRepository;
    @Autowired
    private IssueEmbeddingRepository embeddingRepository;
    @Autowired
    private VectorDuplicateSearch search;
    @Autowired
    private SimilarIssueService similarIssueService;
    @Autowired
    private JdbcTemplate jdbc;

    /**
     * A 384-dim unit vector at an angle from the x axis: angle 0 = same as the query, bigger = less similar.
     * Cosine similarity between angle a and the query = cos(a).
     */
    private static float[] direction(double angleDegrees) {
        float[] v = new float[384];
        v[0] = (float) Math.cos(Math.toRadians(angleDegrees));
        v[1] = (float) Math.sin(Math.toRadians(angleDegrees));
        return v;
    }

    private long save(String repo, int number, String createdAt, double angle) {
        Issue issue = issue(number, "Issue " + number);
        issue.setRepo(repo);
        issue.setExternalId(repo + "-" + number);
        issue.setCreatedAt(Instant.parse(createdAt));
        long id = issueRepository.saveAndFlush(issue).getId();
        embeddingRepository.saveAll(List.of(id), List.of(direction(angle)), "test");
        return id;
    }

    @Test
    void returnsTheNearestEarlierIssuesOfTheSameRepoWithCosineScores() {
        save("octo-org/hello", 1, "2024-01-01T00:00:00Z", 10);    // close
        save("octo-org/hello", 2, "2024-01-02T00:00:00Z", 60);    // far
        save("octo-org/hello", 3, "2024-01-03T00:00:00Z", 30);    // middle
        save("octo-org/hello", 4, "2024-09-01T00:00:00Z", 1);     // closest, but created AFTER the query
        long query = save("octo-org/hello", 5, "2024-06-01T00:00:00Z", 0);

        List<SimilarIssue> results = search.findSimilar(query, 3);

        assertThat(results).extracting(SimilarIssue::number).containsExactly(1, 3, 2);
        assertThat(results.get(0).score()).isCloseTo(Math.cos(Math.toRadians(10)), within(1e-4));
    }

    @Test
    void theHnswIndexStillFillsTheLimitWhenTheFilterRemovesTheNearestRows() {
        // 40 issues of ANOTHER repo that are nearer than anything in our repo.
        for (int n = 100; n < 140; n++) {
            save("octo-org/other", n, "2024-01-01T00:00:00Z", 0.5);
        }
        save("octo-org/hello", 1, "2024-01-01T00:00:00Z", 40);
        save("octo-org/hello", 2, "2024-01-02T00:00:00Z", 50);
        save("octo-org/hello", 3, "2024-01-03T00:00:00Z", 70);
        long query = save("octo-org/hello", 9, "2024-06-01T00:00:00Z", 0);
        jdbc.execute("SET LOCAL enable_seqscan = off");   // force the HNSW index, like on a big table

        List<SimilarIssue> results = search.findSimilar(query, 3);

        assertThat(results).extracting(SimilarIssue::number).containsExactly(1, 2, 3);
    }

    @Test
    void anIssueWithoutAnEmbeddingHasNoResults() {
        long id = issueRepository.saveAndFlush(issue(1, "No embedding")).getId();

        assertThat(search.findSimilar(id, 3)).isEmpty();
    }

    @Test
    void theServiceFindsTheIssueByRepoAndNumberAndPicksTheMethod() {
        save("octo-org/hello", 1, "2024-01-01T00:00:00Z", 10);
        save("octo-org/hello", 2, "2024-06-01T00:00:00Z", 0);

        assertThat(similarIssueService.findSimilar("Octo-Org/Hello", 2, "vector", 3))
                .hasValueSatisfying(r -> assertThat(r).extracting(SimilarIssue::number).containsExactly(1));
        assertThat(similarIssueService.findSimilar("octo-org/hello", 999, "vector", 3)).isEmpty();
    }

    @Test
    void issuesWithAnExcludedLabelAreNotCandidatesUnlessTheFilterIsOff() {
        // PostgresTest configures "status: forward-port" as excluded for octo-org/hello.
        save("octo-org/hello", 1, "2024-01-01T00:00:00Z", 20);
        long clone = save("octo-org/hello", 2, "2024-01-02T00:00:00Z", 1);    // nearest, but a forward-port clone
        jdbc.update("INSERT INTO issue_label (issue_id, label_name) VALUES (?, 'status: forward-port')", clone);
        long query = save("octo-org/hello", 3, "2024-06-01T00:00:00Z", 0);

        assertThat(search.findSimilar(query, 3)).extracting(SimilarIssue::number).containsExactly(1);
        assertThat(search.findSimilar(query, 3, false)).extracting(SimilarIssue::number).containsExactly(2, 1);
    }
}
