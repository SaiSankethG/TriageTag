package com.saisanketh.triage.service;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.repository.IssueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;

import static com.saisanketh.triage.service.TestIssues.issue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueServiceListTest {

    @Mock
    private IssueRepository issueRepository;

    @InjectMocks
    private IssueService issueService;

    @Test
    void returnsDtosForOnePageAndTheTotal() {
        Page<Long> ids = new PageImpl<>(List.of(12L, 11L), PageRequest.of(1, 2), 45);
        when(issueRepository.findIdsByRepo(eq("octo-org/hello"), any(Pageable.class))).thenReturn(ids);
        Issue newer = issue(12, "Newer", "type: bug", "status: duplicate");
        newer.setDuplicateOf(3);
        Issue older = issue(11, "Older");
        when(issueRepository.findByIdInOrderByNumberDesc(List.of(12L, 11L))).thenReturn(List.of(newer, older));

        IssueList list = issueService.listIssues("octo-org/hello", 1, 2);

        assertThat(list.total()).isEqualTo(45);
        assertThat(list.page()).isEqualTo(1);
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.issues()).containsExactly(
                new IssueSummary(12, "Newer", "open", null, List.of("status: duplicate", "type: bug"),
                        Instant.parse("2024-01-01T00:00:00Z"), 3, "NONE", false),
                new IssueSummary(11, "Older", "open", null, List.of(),
                        Instant.parse("2024-01-01T00:00:00Z"), null, "NONE", false));
    }

    @Test
    void asksForTheRightPageNewestFirst() {
        when(issueRepository.findIdsByRepo(eq("octo-org/hello"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(3, 20), 0));

        issueService.listIssues("octo-org/hello", 3, 20);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(issueRepository).findIdsByRepo(eq("octo-org/hello"), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(3);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "number"));
    }

    @Test
    void emptyPageSkipsTheSecondQuery() {
        when(issueRepository.findIdsByRepo(eq("octo-org/nothing"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        IssueList list = issueService.listIssues("octo-org/nothing", 0, 20);

        assertThat(list.issues()).isEmpty();
        assertThat(list.total()).isZero();
        verify(issueRepository, never()).findByIdInOrderByNumberDesc(anyCollection());
    }
}
