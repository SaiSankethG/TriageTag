package com.saisanketh.triage.db;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.embedding.IssueEmbeddingRepository;
import com.saisanketh.triage.eval.DuplicateCheckEval;
import com.saisanketh.triage.eval.DuplicateCheckReport;
import com.saisanketh.triage.repository.IssueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static com.saisanketh.triage.service.TestIssues.issue;
import static org.assertj.core.api.Assertions.assertThat;

/** The comment-mode rule on a tiny replay set, with the fake LLM (title word overlap). Threshold 0.825 (cos ~34°). */
class DuplicateCheckEvalDbTest extends PostgresTest {

    @Autowired
    private IssueRepository issueRepository;
    @Autowired
    private IssueEmbeddingRepository embeddingRepository;
    @Autowired
    private DuplicateCheckEval eval;

    private static float[] direction(double angleDegrees) {
        float[] v = new float[384];
        v[0] = (float) Math.cos(Math.toRadians(angleDegrees));
        v[1] = (float) Math.sin(Math.toRadians(angleDegrees));
        return v;
    }

    private void save(int number, String createdAt, double angle, String title, Integer duplicateOf) {
        Issue issue = issue(number, title);
        issue.setCreatedAt(Instant.parse(createdAt));
        issue.setDuplicateOf(duplicateOf);
        long id = issueRepository.saveAndFlush(issue).getId();
        embeddingRepository.saveAll(List.of(id), List.of(direction(angle)), "test");
    }

    @Test
    void theLlmCheckKeepsTheRealDuplicateAndDropsTheLookAlike() throws Exception {
        save(1, "2024-01-01T00:00:00Z", 0, "Datasource url not picked up", null);
        // A real duplicate: very close vector, similar title -> flagged, fake LLM says yes, match is correct.
        save(2, "2024-02-01T00:00:00Z", 2, "Datasource url not picked up from yaml", 1);
        // A non-duplicate with a close vector but a different title -> flagged (false alarm), fake LLM says no.
        save(3, "2024-03-01T00:00:00Z", 3, "Banner colours wrong", null);
        // A non-duplicate far from everything -> not flagged, no LLM call.
        save(4, "2024-04-01T00:00:00Z", 80, "Something else", null);

        DuplicateCheckReport r = eval.evaluate("octo-org/hello");

        assertThat(r.duplicates()).isEqualTo(1);
        assertThat(r.nonDuplicates()).isEqualTo(3);           // #1, #3, #4 (#1 has nothing earlier: not flagged)
        assertThat(r.externalIssues()).isEqualTo(4);
        assertThat(r.rows()).extracting(DuplicateCheckReport.Row::threshold).containsExactly(0.825, 0.85, 0.90);
        DuplicateCheckReport.Row lowest = r.rows().get(0);
        assertThat(lowest.duplicatesFlagged()).isEqualTo(1);
        assertThat(lowest.duplicatesFlaggedCorrect()).isEqualTo(1);
        assertThat(lowest.duplicatesConfirmedCorrect()).isEqualTo(1);
        assertThat(lowest.nonDuplicatesFlagged()).isEqualTo(1);
        assertThat(lowest.nonDuplicatesConfirmed()).isZero();
        // Scores are ~0.999, so 0.90 counts the same issues as 0.825.
        assertThat(r.rows().get(2)).usingRecursiveComparison().ignoringFields("threshold").isEqualTo(lowest);
        assertThat(r.llmCalls()).isEqualTo(2);                // one call per flagged issue, reused for stricter thresholds
        assertThat(r.confirmedFalseAlarms()).isEmpty();
        assertThat(r.llmModel()).isEqualTo("fake");
        assertThat(Files.readString(Path.of("target/test-m4-duplicate-check.md")))
                .contains("FAKE LLM", "| 0.900 |", "Per 1,000 new external issues");
    }
}
