package com.saisanketh.triage.repojob;

import com.saisanketh.triage.docs.DocIngestService;
import com.saisanketh.triage.embedding.IssueEmbeddingService;
import com.saisanketh.triage.repojob.RepoJob.Status;
import com.saisanketh.triage.repojob.RepoJob.Step;
import com.saisanketh.triage.service.BackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;

/**
 * Runs "Add repository" jobs, one at a time, oldest first: load the repo's past issues, embed them, then load its reference
 * docs. Progress is written to repo_job after every page or batch (GET /repos/{owner}/{repo}/job shows it).
 *
 * <ul>
 *   <li><b>One at a time</b>: a job takes minutes and uses GitHub quota and CPU (the embedding model). Two at once would only
 *       compete for both.</li>
 *   <li><b>Its own scheduler thread</b>: application.yml gives Spring's scheduler 3 threads (this runner, the outbox relay and
 *       the relay's hourly clean-up), so a 20-minute job never holds up the relay (which sends webhooks to Kafka every 500 ms).
 *       Like every @Scheduled method it only runs when scheduling is on ({@code triage.outbox.relay-enabled}, SchedulingConfig).</li>
 *   <li><b>A failure</b> marks the job FAILED with the reason; nothing is retried by itself (a wrong repo name or a missing
 *       token won't fix itself). The operator queues it again; every step is safe to repeat.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RepoJobRunner {

    /** Shown on a finished job whose repo has no reference docs configured: reports for it have no draft reply. */
    static final String NO_DOCS_NOTE = "No reference docs are set up for this repository, so its reports have no draft reply.";

    private final RepoJobRepository jobs;
    private final BackfillService backfill;
    private final IssueEmbeddingService embeddings;
    private final DocIngestService docs;
    private final Clock clock;

    /**
     * After a restart: jobs that were RUNNING when the app stopped go back to the queue. Runs when this bean is created, which
     * is before the scheduler starts, so {@link #runNext()} can never claim a job that is then put back and run twice.
     */
    @PostConstruct
    public void requeueInterrupted() {
        int requeued = jobs.requeueRunning();
        if (requeued > 0) {
            log.info("Repo jobs: {} interrupted job(s) queued again", requeued);
        }
    }

    /** Looks for a queued job every {@code triage.repo-jobs.poll-ms} (default 5 s) after the previous run ended. */
    @Scheduled(fixedDelayString = "${triage.repo-jobs.poll-ms:5000}", initialDelay = 10_000)
    public void runNext() {
        jobs.claimNext(clock.instant()).ifPresent(this::run);
    }

    void run(RepoJob job) {
        long id = job.id();
        String repo = job.repo();
        log.info("Repo job {}: adding {} ({})", id, repo, job.reason());
        try {
            jobs.startStep(id, Step.BACKFILL);
            backfill.backfill(repo, loaded -> jobs.progress(id, loaded, null));

            jobs.startStep(id, Step.EMBEDDINGS);
            embeddings.embedMissing(repo, (done, total) -> jobs.progress(id, done, total));

            jobs.startStep(id, Step.DOCS);
            String note = null;
            if (docs.hasDocs(repo)) {
                int sections = docs.ingest(repo).chunks();
                jobs.progress(id, sections, sections);
            } else {
                note = NO_DOCS_NOTE;
            }
            jobs.finish(id, Status.DONE, note, null, clock.instant());
            log.info("Repo job {}: {} added", id, repo);
        } catch (RuntimeException e) {
            // The full exception goes to the log only; the job row (shown on a public endpoint) gets a short plain reason.
            log.warn("Repo job {}: adding {} failed", id, repo, e);
            jobs.finish(id, Status.FAILED, null, publicReason(e), clock.instant());
        }
    }

    /**
     * A short reason that is safe to show to anyone: never the raw message, which can hold SQL, a whole HTTP response body or
     * server settings.
     */
    static String publicReason(RuntimeException e) {
        if (e instanceof RestClientResponseException http) {
            return "GitHub answered " + http.getStatusCode().value()
                    + (http.getStatusCode().value() == 404 ? " (repository not found or not visible to our token)" : "");
        }
        if (e instanceof ResourceAccessException) {
            return "Could not reach GitHub (network error)";
        }
        if (e instanceof DataAccessException) {
            return "Database error";
        }
        return "Unexpected error (" + e.getClass().getSimpleName() + "); details are in the server log";
    }
}
