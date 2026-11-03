package com.saisanketh.triage.service;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.repository.IssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
