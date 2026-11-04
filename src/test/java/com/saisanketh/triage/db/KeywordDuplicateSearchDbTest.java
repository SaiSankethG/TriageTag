package com.saisanketh.triage.db;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.repository.IssueRepository;
import com.saisanketh.triage.triage.KeywordDuplicateSearch;
import com.saisanketh.triage.triage.SimilarIssue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static com.saisanketh.triage.service.TestIssues.issue;
import static org.assertj.core.api.Assertions.assertThat;

class KeywordDuplicateSearchDbTest extends PostgresTest {

    @Autowired
    private IssueRepository issueRepository;
    @Autowired
    private KeywordDuplicateSearch search;

    private Issue save(int number, String title, String body, String createdAt) {
        Issue issue = issue(number, title);
        issue.setBody(body);
        issue.setCreatedAt(Instant.parse(createdAt));
        return issueRepository.saveAndFlush(issue);
    }

    @Test
    void findsTheEarlierIssueWithTheSameWordsFirst() {
        save(1, "Actuator health endpoint returns 404", "The /actuator/health endpoint is not found.", "2024-01-01T00:00:00Z");
        save(2, "Banner is printed twice", "The startup banner shows two times.", "2024-01-02T00:00:00Z");
        save(3, "Health endpoint 404 with management port", "Actuator health is missing", "2024-01-03T00:00:00Z");
        Issue query = save(4, "Actuator health endpoint gives 404", "After upgrade, /actuator/health is 404.", "2024-02-01T00:00:00Z");

        List<SimilarIssue> results = search.findSimilar(query.getId(), 3);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).number()).isEqualTo(1);                          // most shared words, in the title too
        assertThat(results).extracting(SimilarIssue::number).doesNotContain(2);   // nothing in common
        assertThat(results).extracting(SimilarIssue::number).doesNotContain(4);   // never the issue itself
    }

    @Test
    void onlySearchesEarlierIssuesOfTheSameRepo() {
        Issue query = save(10, "Kafka listener stops consuming", "Consumer stops after rebalance", "2024-05-01T00:00:00Z");
        save(11, "Kafka listener stops consuming", "Same words, but opened LATER", "2024-06-01T00:00:00Z");
        Issue otherRepo = issue(12, "Kafka listener stops consuming");
        otherRepo.setRepo("octo-org/other");
        otherRepo.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        issueRepository.saveAndFlush(otherRepo);

        assertThat(search.findSimilar(query.getId(), 3)).isEmpty();
    }

    @Test
    void htmlCommentsFromIssueTemplatesAreIgnored() {
        save(20, "Database migration fails", "<!-- Please describe the problem and include the version -->", "2024-01-01T00:00:00Z");
        Issue query = save(21, "Something else entirely",
                "<!-- Please describe the problem and include the version --> logo colour", "2024-02-01T00:00:00Z");

        // Only the (identical) template comment is shared, and comments are stripped: no match.
        assertThat(search.findSimilar(query.getId(), 3)).isEmpty();
    }

    @Test
    void wordsWithQuotesOrBackslashesDoNotBreakTheQuery() {
        save(30, "Path C:\\temp\\app isn't found", "Can't open it", "2024-01-01T00:00:00Z");
        Issue query = save(31, "C:\\temp\\app isn't found on Windows", "It's a path problem", "2024-02-01T00:00:00Z");

        assertThat(search.findSimilar(query.getId(), 3)).extracting(SimilarIssue::number).containsExactly(30);
    }
}
