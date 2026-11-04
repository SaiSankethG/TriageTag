package com.saisanketh.triage.db;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.repository.IssueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static com.saisanketh.triage.service.TestIssues.issue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The repository's SQL against a real Postgres built by the Flyway migrations. */
class IssueRepositoryDbTest extends PostgresTest {

    @Autowired
    private IssueRepository issueRepository;

    @Test
    void uniqueConstraintRejectsASecondRowWithTheSameSourceAndExternalId() {
        issueRepository.saveAndFlush(issue(1, "First"));
        Issue sameKey = issue(1, "Same source and external id");   // issue(1, ...) -> externalId "1001"

        // saveAndFlush sends the INSERT now, so Postgres checks uq_issue_source_external immediately.
        assertThatThrownBy(() -> issueRepository.saveAndFlush(sameKey))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findDuplicateCandidatesFindsLabelOnlyAndStateReasonOnlyDuplicates() {
        Issue labelOnly = issue(1, "Old duplicate, label only", "status: duplicate");
        labelOnly.setStateReason("completed");              // what GitHub shows on old duplicates
        Issue reasonOnly = issue(2, "Closed as duplicate, no label");
        reasonOnly.setStateReason("duplicate");
        Issue notADuplicate = issue(3, "Normal bug", "type: bug");
        Issue otherRepo = issue(4, "Other repo", "status: duplicate");
        otherRepo.setRepo("octo-org/other");
        issueRepository.saveAllAndFlush(List.of(labelOnly, reasonOnly, notADuplicate, otherRepo));

        List<Issue> candidates = issueRepository.findDuplicateCandidates("Octo-Org/Hello");   // case-insensitive

        assertThat(candidates).extracting(Issue::getNumber).containsExactly(1, 2);
    }

    @Test
    void findIdsByRepoPagesInTheDatabaseAndCountsAllRows() {
        for (int number = 1; number <= 5; number++) {
            issueRepository.save(issue(number, "Issue " + number));
        }
        issueRepository.flush();

        Page<Long> secondPage = issueRepository.findIdsByRepo("octo-org/hello",
                PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "number")));
        List<Integer> numbers = issueRepository.findByIdInOrderByNumberDesc(secondPage.getContent())
                .stream().map(Issue::getNumber).toList();

        assertThat(secondPage.getTotalElements()).isEqualTo(5);
        assertThat(secondPage.getTotalPages()).isEqualTo(3);
        assertThat(numbers).containsExactly(3, 2);           // newest first: page 0 = 5,4; page 1 = 3,2
    }

    @Test
    void sourceIsNoLongerLimitedToGithubByACheckConstraint() {
        // Hibernate had added CHECK (source = 'GITHUB'); the migrations don't have it. The Java enum has no
        // JIRA yet, so insert the row with plain SQL.
        int rows = jdbc.update("""
                insert into issue (source, external_id, repo, number, title, state, author_association,
                                   html_url, created_at, updated_at, raw)
                values ('JIRA', 'KAFKA-1', 'apache/kafka', 1, 'A Jira issue', 'open', 'NONE',
                        'https://issues.apache.org/jira/browse/KAFKA-1', now(), now(), '{}'::jsonb)
                """);

        assertThat(rows).isEqualTo(1);
    }

    @Autowired
    private JdbcTemplate jdbc;
}
