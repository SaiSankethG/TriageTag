package com.saisanketh.triage.db;

import com.saisanketh.triage.repojob.RepoJob;
import com.saisanketh.triage.repojob.RepoJob.Reason;
import com.saisanketh.triage.repojob.RepoJob.Status;
import com.saisanketh.triage.repojob.RepoJob.Step;
import com.saisanketh.triage.repojob.RepoJobRepository;
import com.saisanketh.triage.repojob.RepoJobService;
import com.saisanketh.triage.repojob.RepoJobService.InstalledRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** repo_job against a real Postgres: the one-open-job rule, claiming, progress, restart recovery, webhook dedupe. */
class RepoJobDbTest extends PostgresTest {

    private static final Instant T = Instant.parse("2026-11-08T09:00:00Z");

    @Autowired
    private RepoJobRepository jobs;
    @Autowired
    private RepoJobService service;

    @Test
    void aRepoHasAtMostOneOpenJobIgnoringCaseButCanBeAddedAgainLater() {
        assertThat(jobs.queue("octo-org/hello", Reason.INSTALLED, T)).isTrue();
        assertThat(jobs.queue("Octo-Org/Hello", Reason.ADMIN, T)).isFalse();      // same repo, still queued

        RepoJob running = jobs.claimNext(T).orElseThrow();
        assertThat(jobs.queue("octo-org/hello", Reason.ADMIN, T)).isFalse();      // still running
        jobs.finish(running.id(), Status.DONE, null, null, T);
        assertThat(jobs.queue("octo-org/hello", Reason.ADMIN, T)).isTrue();       // finished: a new one is fine
    }

    @Test
    void jobsAreClaimedOldestFirstAndShowTheirProgress() {
        jobs.queue("octo-org/first", Reason.INSTALLED, T);
        jobs.queue("octo-org/second", Reason.INSTALLED, T);

        RepoJob job = jobs.claimNext(T.plusSeconds(1)).orElseThrow();
        assertThat(job.repo()).isEqualTo("octo-org/first");
        assertThat(job.status()).isEqualTo("RUNNING");
        assertThat(job.startedAt()).isEqualTo(T.plusSeconds(1));

        jobs.startStep(job.id(), Step.EMBEDDINGS);
        jobs.progress(job.id(), 256, 1000);
        RepoJob seen = jobs.latest("OCTO-ORG/FIRST").orElseThrow();
        assertThat(seen.step()).isEqualTo("EMBEDDINGS");
        assertThat(seen.done()).isEqualTo(256);
        assertThat(seen.total()).isEqualTo(1000);

        assertThat(jobs.claimNext(T).orElseThrow().repo()).isEqualTo("octo-org/second");
        assertThat(jobs.claimNext(T)).isEmpty();
    }

    @Test
    void afterARestartRunningJobsGoBackToTheQueue() {
        jobs.queue("octo-org/hello", Reason.INSTALLED, T);
        jobs.claimNext(T);

        assertThat(jobs.requeueRunning()).isEqualTo(1);
        RepoJob job = jobs.latest("octo-org/hello").orElseThrow();
        assertThat(job.status()).isEqualTo("QUEUED");
        assertThat(job.startedAt()).isNull();
    }

    @Test
    void anInstallWebhookQueuesPublicReposOnlyAndARedeliveryQueuesNothing() {
        List<InstalledRepo> repos = List.of(new InstalledRepo("octo-org/hello", false),
                new InstalledRepo("octo-org/secret", true), new InstalledRepo("bad name; drop table", false));

        assertThat(service.queueInstalled("delivery-1", "installation", "created", repos)).isEqualTo(1);
        assertThat(jobs.latest("octo-org/secret")).isEmpty();
        assertThat(jobs.latest("octo-org/hello").orElseThrow().reason()).isEqualTo("INSTALLED");

        assertThat(service.queueInstalled("delivery-1", "installation", "created", repos)).isEqualTo(-1);
    }

    @Test
    void theOperatorCanOnlyQueueRealRepoNames() {
        assertThat(service.queueByAdmin("octo-org/hello")).isTrue();
        assertThat(service.queueByAdmin("octo-org/hello")).isFalse();
        assertThatThrownBy(() -> service.queueByAdmin("../etc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.queueByAdmin("octo-org/..")).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.queueByAdmin("octo-org/hello.js")).isTrue();
    }
}
