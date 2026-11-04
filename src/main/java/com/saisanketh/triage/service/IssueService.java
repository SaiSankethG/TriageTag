package com.saisanketh.triage.service;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.repository.IssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Saves issues. "Upsert" = update if we have it, insert if we don't. */
@Service
@RequiredArgsConstructor
public class IssueService {

    private final IssueRepository issueRepository;

    /**
     * Inserts the issue, or updates our existing row for it. The match is on (source, externalId),
     * the same pair as the unique constraint, so running a backfill twice never creates duplicates.
     * One transaction: the lookup and the write succeed or fail together.
     */
    @Transactional
    public UpsertResult upsert(Issue incoming) {
        Optional<Issue> existing =
                issueRepository.findBySourceAndExternalId(incoming.getSource(), incoming.getExternalId());

        if (existing.isPresent()) {
            Issue row = existing.get();
            row.updateFrom(incoming);
            // row is "managed" (loaded in this transaction), so Hibernate would write the changes
            // at commit even without this call; calling save makes the intent obvious.
            issueRepository.save(row);
            return UpsertResult.UPDATED;
        }

        issueRepository.save(incoming);
        return UpsertResult.INSERTED;
    }

    /**
     * One page of a repo's issues, newest (highest number) first, as DTOs.
     * Two queries in total, whatever the page size: (1) the ids of this page + the total count,
     * (2) those issues with their labels. readOnly = true: Hibernate can skip dirty checking.
     */
    @Transactional(readOnly = true)
    public IssueList listIssues(String repo, int page, int size) {
        Page<Long> ids = issueRepository.findIdsByRepo(repo,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "number")));

        List<IssueSummary> issues = ids.isEmpty()
                ? List.of()
                : issueRepository.findByIdInOrderByNumberDesc(ids.getContent()).stream()
                        .map(IssueService::toSummary)
                        .toList();

        return new IssueList(issues, page, size, ids.getTotalElements());
    }

    private static IssueSummary toSummary(Issue issue) {
        return new IssueSummary(issue.getNumber(), issue.getTitle(), issue.getState(), issue.getStateReason(),
                issue.getLabels().stream().sorted().toList(),   // sorted, so the API output is stable
                issue.getCreatedAt(), issue.getDuplicateOf());
    }
}
