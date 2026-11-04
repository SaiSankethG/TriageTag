package com.saisanketh.triage.db;

import com.saisanketh.triage.repository.IssueRepository;
import com.saisanketh.triage.service.IssueList;
import com.saisanketh.triage.service.IssueService;
import com.saisanketh.triage.service.IssueSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.saisanketh.triage.service.TestIssues.issue;
import static org.assertj.core.api.Assertions.assertThat;

class IssueSearchDbTest extends PostgresTest {

    @Autowired
    private IssueRepository issueRepository;
    @Autowired
    private IssueService issueService;

    @Test
    void findsTitlesContainingTheTextInAnyCaseNewestFirst() {
        issueRepository.saveAndFlush(issue(1, "DataSource url not picked up"));
        issueRepository.saveAndFlush(issue(2, "Banner colours"));
        issueRepository.saveAndFlush(issue(3, "Second datasource bean"));

        IssueList list = issueService.searchIssues("Octo-Org/Hello", "DATASOURCE", 0, 20);

        assertThat(list.issues()).extracting(IssueSummary::number).containsExactly(3, 1);
        assertThat(list.total()).isEqualTo(2);
        assertThat(issueService.searchIssues("octo-org/hello", "kafka", 0, 20).issues()).isEmpty();
    }
}
