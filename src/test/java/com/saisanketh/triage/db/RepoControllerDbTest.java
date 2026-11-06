package com.saisanketh.triage.db;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.repository.IssueRepository;
import com.saisanketh.triage.web.RepoController;
import com.saisanketh.triage.web.RepoController.RepoSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static com.saisanketh.triage.service.TestIssues.issue;
import static org.assertj.core.api.Assertions.assertThat;

class RepoControllerDbTest extends PostgresTest {

    @Autowired
    private IssueRepository issueRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void listsEachRepoWithItsIssueAndTriagedCountsMostTriagedFirst() {
        Issue a1 = issue(1, "One");
        Issue a2 = issue(2, "Two");
        a2.setCreatedAt(Instant.parse("2024-05-01T00:00:00Z"));
        Issue b1 = issue(1, "Other repo");
        b1.setRepo("octo-org/other");
        b1.setExternalId("other-1");
        issueRepository.saveAndFlush(a1);
        long a2id = issueRepository.saveAndFlush(a2).getId();
        issueRepository.saveAndFlush(b1);
        jdbc.update("INSERT INTO triage_report (issue_id, created_at, duplicates, labels, missing, draft_reply, llm_model, "
                + "prompt_version, cost_tokens) VALUES (?, ?, '[]', '[]', '{}', '{}', 'fake', 'p', 0)", a2id, Timestamp.from(Instant.now()));

        List<RepoSummary> repos = new RepoController(jdbc).repos();

        assertThat(repos).extracting(RepoSummary::repo).containsExactly("octo-org/hello", "octo-org/other");
        assertThat(repos.get(0).issues()).isEqualTo(2);
        assertThat(repos.get(0).triaged()).isEqualTo(1);
        assertThat(repos.get(0).newestIssue()).isEqualTo(Instant.parse("2024-05-01T00:00:00Z"));
    }
}
