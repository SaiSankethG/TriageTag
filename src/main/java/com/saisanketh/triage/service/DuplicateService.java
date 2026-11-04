package com.saisanketh.triage.service;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.repository.IssueRepository;
import com.saisanketh.triage.source.DuplicateLink;
import com.saisanketh.triage.source.DuplicateSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Fills issue.duplicate_of (the answer key): for every stored issue that was closed or labelled as a
 * duplicate, ask the tracker which issue it duplicates, and store the answer only when it is clear.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DuplicateService {

    private static final int LOG_EVERY = 100;

    private final IssueRepository issueRepository;
    private final DuplicateSource duplicateSource;

    public DuplicateSummary fillDuplicates(String repo) {
        long start = System.nanoTime();
        List<Issue> candidates = issueRepository.findDuplicateCandidates(repo);
        log.info("Duplicates {}: {} issues closed or labelled as duplicate", repo, candidates.size());

        int found = 0;
        int byCloseReason = 0;
        int byDuplicateEvent = 0;
        int byComment = 0;
        int notFound = 0;
        int otherRepo = 0;
        int ambiguous = 0;

        int done = 0;
        for (Issue issue : candidates) {
            DuplicateLink link = duplicateSource.findOriginal(repo, issue.getNumber());
            switch (link.outcome()) {
                case FOUND -> {
                    issueRepository.setDuplicateOf(issue.getId(), link.original());
                    found++;
                    switch (link.evidence()) {
                        case CLOSE_REASON -> byCloseReason++;
                        case DUPLICATE_EVENT -> byDuplicateEvent++;
                        case COMMENT -> byComment++;
                    }
                }
                case NOT_FOUND -> notFound++;
                case OTHER_REPO -> otherRepo++;
                case AMBIGUOUS -> ambiguous++;
            }
            done++;
            if (done % LOG_EVERY == 0) {
                log.info("Duplicates {}: {}/{} checked, {} found so far", repo, done, candidates.size(), found);
            }
        }

        long durationMs = (System.nanoTime() - start) / 1_000_000;
        DuplicateSummary summary = new DuplicateSummary(repo, candidates.size(), found,
                byCloseReason, byDuplicateEvent, byComment, notFound, otherRepo, ambiguous, durationMs);
        log.info("Duplicates finished: {}", summary);
        return summary;
    }
}
