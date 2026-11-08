package com.saisanketh.triage.service;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.domain.SourceType;
import com.saisanketh.triage.repository.IssueRepository;
import com.saisanketh.triage.source.IssuePage;
import com.saisanketh.triage.source.IssueSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.saisanketh.triage.service.TestIssues.issue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackfillServiceTest {

    @Mock
    private IssueRepository issueRepository;

    /** What the mocked repository has "saved", keyed like the unique constraint: source + externalId. */
    private final Map<String, Issue> table = new HashMap<>();
    /** Every cursor the backfill asked for, in order. */
    private final List<String> cursorsAsked = new ArrayList<>();

    private BackfillService backfillService;

    @BeforeEach
    void setUp() {
        // Make the mock behave like a tiny in-memory table, so a second run can find the first run's rows.
        when(issueRepository.findBySourceAndExternalId(any(), any())).thenAnswer(call ->
                Optional.ofNullable(table.get(key(call.getArgument(0), call.getArgument(1)))));
        when(issueRepository.save(any(Issue.class))).thenAnswer(call -> {
            Issue saved = call.getArgument(0);
            table.put(key(saved.getSource(), saved.getExternalId()), saved);
            return saved;
        });

        backfillService = new BackfillService(new TwoPageSource(), new IssueService(issueRepository));
    }

    @Test
    void firstRunInsertsEverythingAndCountsPagesAndSkipped() {
        BackfillSummary summary = backfillService.backfill("octo-org/hello");

        assertThat(summary.repo()).isEqualTo("octo-org/hello");
        assertThat(summary.pages()).isEqualTo(2);
        assertThat(summary.inserted()).isEqualTo(3);
        assertThat(summary.updated()).isZero();
        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.durationMs()).isNotNegative();
        assertThat(cursorsAsked).containsExactly(null, "page-2");   // started at null, followed the cursor
        assertThat(table).hasSize(3);
    }

    @Test
    void reportsTheIssuesStoredSoFarAfterEveryPage() {
        List<Integer> progress = new ArrayList<>();
        backfillService.backfill("octo-org/hello", progress::add);
        assertThat(progress).hasSize(2).last().isEqualTo(3);
    }

    @Test
    void secondRunOfTheSameDataInsertsNothing() {
        backfillService.backfill("octo-org/hello");

        BackfillSummary second = backfillService.backfill("octo-org/hello");

        assertThat(second.inserted()).isZero();
        assertThat(second.updated()).isEqualTo(3);
        assertThat(table).hasSize(3);                               // still 3 rows, no duplicates
    }

    private static String key(SourceType source, String externalId) {
        return source + ":" + externalId;
    }

    /**
     * A fake IssueSource with two pages. It builds new Issue objects on every call,
     * like the real source does, so the second run really is "the same data, fetched again".
     */
    private class TwoPageSource implements IssueSource {
        @Override
        public IssuePage fetchPage(String repo, String cursor) {
            cursorsAsked.add(cursor);
            if (cursor == null) {
                return new IssuePage(List.of(issue(1, "One"), issue(2, "Two")), "page-2", 1);
            }
            return new IssuePage(List.of(issue(3, "Three")), null, 0);
        }
    }
}
