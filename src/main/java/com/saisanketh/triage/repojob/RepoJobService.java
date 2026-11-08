package com.saisanketh.triage.repojob;

import com.saisanketh.triage.repojob.RepoJob.Reason;
import com.saisanketh.triage.repository.WebhookDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Puts "Add repository" jobs in the queue (table repo_job); {@link RepoJobRunner} does the work later. Queueing is quick, so
 * the webhook is answered well within GitHub's 10 seconds even when the App is installed on many repositories at once.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RepoJobService {

    /**
     * 'owner/name': letters, digits, '.', '_', '-' (what GitHub allows), so nothing else ever reaches a URL or the logs. Neither
     * part may start with '.', which also rules out "." and "..".
     */
    static final Pattern REPO_NAME = Pattern.compile("[\\w-][\\w.-]*/[\\w-][\\w.-]*");

    /**
     * A repository from an installation webhook.
     *
     * @param isPrivate GitHub's "private" flag. Private repos are skipped: the reports site is public, so loading one would
     *                  publish its issues. (Report access for private repos is a later piece.)
     */
    public record InstalledRepo(String fullName, boolean isPrivate) {
    }

    private final RepoJobRepository jobs;
    private final WebhookDeliveryRepository deliveries;
    private final Clock clock;

    /**
     * From an "installation" (created) or "installation_repositories" (added) webhook: one job per public repo. The delivery id
     * is recorded in the same transaction, so a redelivered webhook queues nothing (and an open job for the repo already
     * stops a second one).
     *
     * @return how many jobs were queued; -1 if this delivery was seen before
     */
    @Transactional
    public int queueInstalled(String deliveryId, String event, String action, List<InstalledRepo> repos) {
        Instant now = clock.instant();
        if (!deliveries.insertIfNew(deliveryId, event, action, null, now)) {
            return -1;
        }
        int queued = 0;
        int skippedPrivate = 0;
        for (InstalledRepo repo : repos) {
            if (repo.isPrivate()) {
                skippedPrivate++;
            } else if (REPO_NAME.matcher(repo.fullName()).matches() && jobs.queue(repo.fullName(), Reason.INSTALLED, now)) {
                queued++;
            }
        }
        if (skippedPrivate > 0) {
            log.info("Installation webhook {}: skipped {} private repositories (reports are public)", deliveryId,
                    skippedPrivate);
        }
        return queued;
    }

    /**
     * The operator asks for a repo (POST /repo-jobs/{owner}/{repo}).
     *
     * @return true if a job was queued, false if the repo already has one waiting or running
     */
    public boolean queueByAdmin(String repo) {
        if (!REPO_NAME.matcher(repo).matches()) {
            throw new IllegalArgumentException("not a repository name: " + repo);
        }
        return jobs.queue(repo, Reason.ADMIN, clock.instant());
    }
}
