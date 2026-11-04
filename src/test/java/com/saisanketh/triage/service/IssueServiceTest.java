package com.saisanketh.triage.service;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.domain.SourceType;
import com.saisanketh.triage.repository.IssueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static com.saisanketh.triage.service.TestIssues.issue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueServiceTest {

    @Mock
    private IssueRepository issueRepository;   // a fake: no database involved

    @InjectMocks
    private IssueService issueService;         // the real service, given the fake repository

    @Test
    void insertsAnIssueWeHaveNotSeen() {
        Issue incoming = issue(1, "New issue");
        when(issueRepository.findBySourceAndExternalId(SourceType.GITHUB, "1001")).thenReturn(Optional.empty());

        UpsertResult result = issueService.upsert(incoming);

        assertThat(result).isEqualTo(UpsertResult.INSERTED);
        verify(issueRepository).save(incoming);
    }

    @Test
    void updatesTheExistingRowInsteadOfInsertingAgain() {
        Issue existing = issue(1, "Old title", "old-label");
        existing.setId(7L);
        existing.setDuplicateOf(123);              // filled earlier by the answer-key step
        Issue incoming = issue(1, "New title", "type: bug", "status: duplicate");
        incoming.setState("closed");
        when(issueRepository.findBySourceAndExternalId(SourceType.GITHUB, "1001")).thenReturn(Optional.of(existing));

        UpsertResult result = issueService.upsert(incoming);

        assertThat(result).isEqualTo(UpsertResult.UPDATED);
        assertThat(existing.getId()).isEqualTo(7L);                 // same row
        assertThat(existing.getTitle()).isEqualTo("New title");
        assertThat(existing.getState()).isEqualTo("closed");
        assertThat(existing.getLabels()).containsExactlyInAnyOrder("type: bug", "status: duplicate");  // replaced
        assertThat(existing.getDuplicateOf()).isEqualTo(123);       // not wiped by the null in incoming
        verify(issueRepository).save(existing);
        verify(issueRepository, never()).save(incoming);
    }

    @Test
    void anOlderEventDoesNotOverwriteNewerData() {
        Issue stored = issue(1, "Newer title");
        stored.setUpdatedAt(Instant.parse("2024-06-01T12:00:00Z"));
        Issue older = issue(1, "Older title");
        older.setUpdatedAt(Instant.parse("2024-06-01T11:59:59Z"));
        when(issueRepository.findBySourceAndExternalId(SourceType.GITHUB, "1001")).thenReturn(Optional.of(stored));

        UpsertResult result = issueService.upsert(older);

        assertThat(result).isEqualTo(UpsertResult.STALE);
        assertThat(stored.getTitle()).isEqualTo("Newer title");
        verify(issueRepository, never()).save(any());
    }

    @Test
    void theSameUpdatedAtOverwrites() {
        Issue stored = issue(1, "Title");
        Issue sameTime = issue(1, "Title, edited within the same second");   // TestIssues: same updatedAt
        when(issueRepository.findBySourceAndExternalId(SourceType.GITHUB, "1001")).thenReturn(Optional.of(stored));

        assertThat(issueService.upsert(sameTime)).isEqualTo(UpsertResult.UPDATED);
        assertThat(stored.getTitle()).isEqualTo("Title, edited within the same second");
    }
}
