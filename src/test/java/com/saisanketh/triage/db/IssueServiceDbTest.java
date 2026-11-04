package com.saisanketh.triage.db;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.domain.SourceType;
import com.saisanketh.triage.repository.IssueRepository;
import com.saisanketh.triage.service.IssueList;
import com.saisanketh.triage.service.IssueService;
import com.saisanketh.triage.service.UpsertResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static com.saisanketh.triage.service.TestIssues.issue;
import static org.assertj.core.api.Assertions.assertThat;

/** IssueService against a real Postgres: what Mockito tests can't show (real SQL, real label rows). */
class IssueServiceDbTest extends PostgresTest {

    @Autowired
    private IssueService issueService;
    @Autowired
    private IssueRepository issueRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void upsertTwiceKeepsOneRowAndReplacesTheLabels() {
        UpsertResult first = issueService.upsert(issue(1, "Old title", "old-label", "type: bug"));
        flushAndForget();
        UpsertResult second = issueService.upsert(issue(1, "New title", "type: bug", "status: duplicate"));
        flushAndForget();

        assertThat(first).isEqualTo(UpsertResult.INSERTED);
        assertThat(second).isEqualTo(UpsertResult.UPDATED);
        assertThat(jdbc.queryForObject("select count(*) from issue", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select title from issue", String.class)).isEqualTo("New title");
        assertThat(jdbc.queryForList("select label_name from issue_label order by label_name", String.class))
                .containsExactly("status: duplicate", "type: bug");          // "old-label" row was deleted
    }

    @Test
    void listIssuesRunsThreeSqlStatementsWhateverThePageSize() {
        for (int number = 1; number <= 25; number++) {
            issueRepository.save(issue(number, "Issue " + number, "label-a", "label-b"));
        }
        flushAndForget();                                  // empty the cache so every read really hits the DB
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        IssueList page = issueService.listIssues("octo-org/hello", 0, 20);

        assertThat(page.issues()).hasSize(20);
        assertThat(page.issues().get(0).labels()).containsExactly("label-a", "label-b");   // labels really loaded
        assertThat(page.total()).isEqualTo(25);
        // ids of the page + count + issues with labels. With N+1 it would be 2 + 20 = 22.
        assertThat(stats.getPrepareStatementCount()).isEqualTo(3);
    }

    @Test
    void aLateOlderEventDoesNotUndoANewerOne() {
        Issue newer = issue(1, "Closed as duplicate", "status: duplicate");
        newer.setState("closed");
        newer.setUpdatedAt(Instant.parse("2024-06-01T12:00:00Z"));
        Issue older = issue(1, "Still open", "status: waiting-for-triage");
        older.setUpdatedAt(Instant.parse("2024-06-01T11:00:00Z"));

        issueService.upsert(newer);
        flushAndForget();
        UpsertResult late = issueService.upsert(older);     // e.g. a redelivered "opened" webhook
        flushAndForget();

        assertThat(late).isEqualTo(UpsertResult.STALE);
        assertThat(jdbc.queryForObject("select state from issue", String.class)).isEqualTo("closed");
        assertThat(jdbc.queryForList("select label_name from issue_label", String.class))
                .containsExactly("status: duplicate");
    }

    @Test
    void deleteRemovesTheIssueAndItsLabels() {
        issueService.upsert(issue(1, "To be deleted", "type: bug", "status: duplicate"));
        issueService.upsert(issue(2, "Stays"));
        flushAndForget();

        boolean removed = issueService.delete(SourceType.GITHUB, "1001");
        flushAndForget();
        boolean removedAgain = issueService.delete(SourceType.GITHUB, "1001");   // e.g. Kafka delivered it twice

        assertThat(removed).isTrue();
        assertThat(removedAgain).isFalse();
        assertThat(jdbc.queryForList("select number from issue", Integer.class)).containsExactly(2);
        assertThat(countLabelRows()).isZero();
    }

    private int countLabelRows() {
        return jdbc.queryForObject("select count(*) from issue_label", Integer.class);
    }

    /** Write pending changes to the DB, then forget all loaded entities (like starting a new request). */
    private void flushAndForget() {
        entityManager.flush();
        entityManager.clear();
    }
}
