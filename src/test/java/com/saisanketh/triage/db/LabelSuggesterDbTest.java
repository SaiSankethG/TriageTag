package com.saisanketh.triage.db;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.embedding.IssueEmbeddingRepository;
import com.saisanketh.triage.repository.IssueRepository;
import com.saisanketh.triage.triage.LabelSuggester;
import com.saisanketh.triage.triage.LabelSuggester.LabelSuggestion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static com.saisanketh.triage.service.TestIssues.issue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LabelSuggesterDbTest extends PostgresTest {

    @Autowired
    private IssueRepository issueRepository;
    @Autowired
    private IssueEmbeddingRepository embeddingRepository;
    @Autowired
    private LabelSuggester labelSuggester;

    private static float[] direction(double angleDegrees) {
        float[] v = new float[384];
        v[0] = (float) Math.cos(Math.toRadians(angleDegrees));
        v[1] = (float) Math.sin(Math.toRadians(angleDegrees));
        return v;
    }

    private long save(int number, String createdAt, double angle, String... labels) {
        Issue issue = issue(number, "Issue " + number, labels);
        issue.setCreatedAt(Instant.parse(createdAt));
        long id = issueRepository.saveAndFlush(issue).getId();
        embeddingRepository.saveAll(List.of(id), List.of(direction(angle)), "test");
        return id;
    }

    @Test
    void suggestsTheLabelsThatMostOfTheSimilarityWeightAgreesOn() {
        // Similarities to the query: cos(0) = 1.0, cos(10) = 0.98, cos(20) = 0.94, cos(80) = 0.17
        save(1, "2024-01-01T00:00:00Z", 0, "type: bug", "status: waiting-for-triage");
        save(2, "2024-01-02T00:00:00Z", 10, "type: bug");
        save(3, "2024-01-03T00:00:00Z", 20, "type: enhancement");
        save(4, "2024-01-04T00:00:00Z", 80, "type: documentation");
        long query = save(5, "2024-06-01T00:00:00Z", 0);

        List<LabelSuggestion> suggestions = labelSuggester.suggest(query);

        // bug: (1.0 + 0.985) / (1.0 + 0.985 + 0.940 + 0.174) = 0.64 -> suggested; enhancement 0.30 and docs 0.06 are below 0.5.
        assertThat(suggestions).extracting(LabelSuggestion::label).containsExactly("type: bug");
        assertThat(suggestions.get(0).share()).isCloseTo(0.64, within(0.01));
    }

    @Test
    void workflowLabelsAreNeverSuggested() {
        save(1, "2024-01-01T00:00:00Z", 0, "status: waiting-for-triage");
        save(2, "2024-01-02T00:00:00Z", 5, "status: waiting-for-triage");
        long query = save(3, "2024-06-01T00:00:00Z", 0);

        assertThat(labelSuggester.suggest(query)).isEmpty();
        assertThat(labelSuggester.isIgnored("status: duplicate")).isTrue();
        assertThat(labelSuggester.isIgnored("type: bug")).isFalse();
    }

    @Test
    void noNeighboursMeansNoSuggestion() {
        long query = save(1, "2024-01-01T00:00:00Z", 0);

        assertThat(labelSuggester.suggest(query)).isEmpty();
    }
}
