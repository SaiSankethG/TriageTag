package com.saisanketh.triage.service;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.source.IssuePage;
import com.saisanketh.triage.source.IssueSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.IntConsumer;

/**
 * Loads all of a repo's issues: fetches every page from the {@link IssueSource} and upserts each issue.
 *
 * <p>Why a separate class from {@link IssueService}: {@code @Transactional} on {@code upsert} only works
 * when the call comes from <em>another</em> bean (it goes through Spring's proxy). If this loop lived
 * in IssueService and called {@code this.upsert(...)}, the transaction would silently not start.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackfillService {

    private static final int LOG_EVERY_PAGES = 10;

    private final IssueSource issueSource;   // only an interface: this class knows nothing about GitHub
    private final IssueService issueService;

    public BackfillSummary backfill(String repo) {
        return backfill(repo, loaded -> { });
    }

    /**
     * Same, and reports progress after every page.
     *
     * @param issuesSoFar called after each page with the number of issues stored so far (for the "Add repository" job's
     *                    status; GitHub doesn't say up front how many issues a repo has, so there is no total)
     */
    public BackfillSummary backfill(String repo, IntConsumer issuesSoFar) {
        long start = System.nanoTime();   // nanoTime is for measuring durations; it never jumps like wall-clock time
        int pages = 0;
        int inserted = 0;
        int updated = 0;
        int stale = 0;
        int skipped = 0;

        String cursor = null;                 // null = first page
        do {
            IssuePage page = issueSource.fetchPage(repo, cursor);
            pages++;
            skipped += page.skipped();
            for (Issue issue : page.issues()) {
                switch (issueService.upsert(issue)) {
                    case INSERTED -> inserted++;
                    case UPDATED -> updated++;
                    case STALE -> stale++;   // we already had newer data, e.g. from a webhook
                }
            }
            issuesSoFar.accept(inserted + updated + stale);
            if (pages % LOG_EVERY_PAGES == 0) {
                log.info("Backfill {}: {} pages, {} inserted, {} updated, {} skipped so far",
                        repo, pages, inserted, updated, skipped);
            }
            cursor = page.nextCursor();
        } while (cursor != null);            // only a null cursor means "no more pages"

        long durationMs = (System.nanoTime() - start) / 1_000_000;
        BackfillSummary summary = new BackfillSummary(repo, pages, inserted, updated, stale, skipped, durationMs);
        log.info("Backfill finished: {}", summary);
        return summary;
    }
}
