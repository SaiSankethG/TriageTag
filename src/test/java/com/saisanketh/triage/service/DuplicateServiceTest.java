package com.saisanketh.triage.service;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.repository.IssueRepository;
import com.saisanketh.triage.source.DuplicateLink;
import com.saisanketh.triage.source.DuplicateLink.Evidence;
import com.saisanketh.triage.source.DuplicateSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static com.saisanketh.triage.service.TestIssues.issue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DuplicateServiceTest {

    @Mock
    private IssueRepository issueRepository;

    @Test
    void fillsOnlyClearAnswersAndCountsEveryOutcome() {
        List<Issue> candidates = List.of(withId(issue(1, "a")), withId(issue(2, "b")), withId(issue(3, "c")),
                withId(issue(4, "d")), withId(issue(5, "e")), withId(issue(6, "f")));
        when(issueRepository.findDuplicateCandidates("octo-org/hello")).thenReturn(candidates);

        // A fake tracker: one answer per issue number.
        Map<Integer, DuplicateLink> answers = Map.of(
                1, DuplicateLink.found(10, Evidence.CLOSE_REASON),
                2, DuplicateLink.found(20, Evidence.COMMENT),
                3, DuplicateLink.found(30, Evidence.DUPLICATE_EVENT),
                4, DuplicateLink.notFound(),
                5, DuplicateLink.otherRepo(),
                6, DuplicateLink.ambiguous());
        DuplicateSource source = (repo, number) -> answers.get(number);

        DuplicateSummary summary = new DuplicateService(issueRepository, source).fillDuplicates("octo-org/hello");

        assertThat(summary.checked()).isEqualTo(6);
        assertThat(summary.found()).isEqualTo(3);
        assertThat(summary.foundByCloseReason()).isEqualTo(1);
        assertThat(summary.foundByComment()).isEqualTo(1);
        assertThat(summary.foundByDuplicateEvent()).isEqualTo(1);
        assertThat(summary.notFound()).isEqualTo(1);
        assertThat(summary.otherRepo()).isEqualTo(1);
        assertThat(summary.ambiguous()).isEqualTo(1);
        // Only the 3 clear answers are written; ids are 100 + number (see withId).
        verify(issueRepository).setDuplicateOf(101L, 10);
        verify(issueRepository).setDuplicateOf(102L, 20);
        verify(issueRepository).setDuplicateOf(103L, 30);
        verify(issueRepository, never()).setDuplicateOf(104L, null);
    }

    @Test
    void nothingToDoWhenThereAreNoDuplicates() {
        when(issueRepository.findDuplicateCandidates("octo-org/hello")).thenReturn(List.of());
        DuplicateSource source = (repo, number) -> {
            throw new AssertionError("should not be called");
        };

        DuplicateSummary summary = new DuplicateService(issueRepository, source).fillDuplicates("octo-org/hello");

        assertThat(summary.checked()).isZero();
        verify(issueRepository, never()).setDuplicateOf(anyLong(), any());
    }

    private static Issue withId(Issue issue) {
        issue.setId(100L + issue.getNumber());
        return issue;
    }
}
