package com.saisanketh.triage.db;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.embedding.IssueEmbeddingRepository;
import com.saisanketh.triage.eval.EvalReport;
import com.saisanketh.triage.eval.EvalService;
import com.saisanketh.triage.repository.IssueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static com.saisanketh.triage.service.TestIssues.issue;
import static org.assertj.core.api.Assertions.assertThat;

/** A tiny replay on real Postgres: checks the scored set and that recall is counted the right way. */
class EvalServiceDbTest extends PostgresTest {

    @Autowired
    private IssueRepository issueRepository;
    @Autowired
    private IssueEmbeddingRepository embeddingRepository;
    @Autowired
    private EvalService evalService;

    private static float[] direction(double angleDegrees) {
        float[] v = new float[384];
        v[0] = (float) Math.cos(Math.toRadians(angleDegrees));
        v[1] = (float) Math.sin(Math.toRadians(angleDegrees));
        return v;
    }

    private void save(int number, String createdAt, double angle, String title, Integer duplicateOf, String... labels) {
        Issue issue = issue(number, title, labels);
        issue.setCreatedAt(Instant.parse(createdAt));
        issue.setDuplicateOf(duplicateOf);
        long id = issueRepository.saveAndFlush(issue).getId();
        embeddingRepository.saveAll(List.of(id), List.of(direction(angle)), "test");
    }

    @Test
    void scoresOnlyPairsWhoseOriginalIsStoredAndOlder() throws Exception {
        save(1, "2024-01-01T00:00:00Z", 0, "Datasource url not picked up", null, "type: bug");
        save(2, "2024-01-02T00:00:00Z", 90, "Banner colours", null, "type: enhancement");
        // Scored: duplicate of #1, nearest vector and shared words -> rank 1 for both methods.
        save(3, "2024-02-01T00:00:00Z", 2, "Datasource url not picked up from yaml", 1, "status: duplicate", "type: bug");
        // Excluded: original #999 isn't stored (e.g. a pull request).
        save(4, "2024-02-02T00:00:00Z", 45, "Something", 999, "status: duplicate");
        // Excluded: "original" #6 is newer than the duplicate.
        save(5, "2024-02-03T00:00:00Z", 60, "Older one closed in favour of a newer one", 6, "status: duplicate");
        save(6, "2024-03-01T00:00:00Z", 61, "The newer, better report", null, "type: bug");

        EvalReport report = evalService.evaluate("octo-org/hello");

        assertThat(report.answerKey().withDuplicateOf()).isEqualTo(3);
        assertThat(report.answerKey().originalNotStored()).isEqualTo(1);
        assertThat(report.answerKey().originalNewer()).isEqualTo(1);
        assertThat(report.answerKey().scored()).isEqualTo(1);
        assertThat(report.vector().hitsAt1()).isEqualTo(1);
        assertThat(report.vector().mrr()).isEqualTo(1.0);
        assertThat(report.keyword().hitsAt1()).isEqualTo(1);
        assertThat(report.labels().issues()).isPositive();
        // External reporters are a subset of all issues (TestIssues are by "NONE", i.e. external).
        assertThat(report.falseAlarmsAllIssues().sample()).isGreaterThanOrEqualTo(report.falseAlarms().sample());
        assertThat(Files.readString(Path.of("target/test-m3-results.md"))).contains("Scored pairs");
    }
}
