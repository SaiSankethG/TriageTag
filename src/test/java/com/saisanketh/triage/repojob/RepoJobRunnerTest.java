package com.saisanketh.triage.repojob;

import com.saisanketh.triage.docs.DocIngestService;
import com.saisanketh.triage.docs.DocIngestService.IngestSummary;
import com.saisanketh.triage.embedding.IssueEmbeddingService;
import com.saisanketh.triage.repojob.RepoJob.Status;
import com.saisanketh.triage.repojob.RepoJob.Step;
import com.saisanketh.triage.service.BackfillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepoJobRunnerTest {

    private static final Instant NOW = Instant.parse("2026-11-08T09:00:00Z");
    private static final RepoJob JOB = new RepoJob(7, "octo-org/hello", "INSTALLED", "RUNNING", null, null, null, null, null,
            NOW, NOW, null);

    private RepoJobRepository jobs;
    private BackfillService backfill;
    private IssueEmbeddingService embeddings;
    private DocIngestService docs;
    private RepoJobRunner runner;

    @BeforeEach
    void setUp() {
        jobs = mock(RepoJobRepository.class);
        backfill = mock(BackfillService.class);
        embeddings = mock(IssueEmbeddingService.class);
        docs = mock(DocIngestService.class);
        runner = new RepoJobRunner(jobs, backfill, embeddings, docs, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void runsTheStepsInOrderAndWritesTheirProgress() {
        when(backfill.backfill(eq("octo-org/hello"), any())).thenAnswer(call -> {
            IntConsumer issuesSoFar = call.getArgument(1);
            issuesSoFar.accept(100);
            issuesSoFar.accept(150);
            return null;
        });
        when(embeddings.embedMissing(eq("octo-org/hello"), any())).thenAnswer(call -> {
            BiConsumer<Integer, Integer> progress = call.getArgument(1);
            progress.accept(0, 150);
            progress.accept(150, 150);
            return null;
        });
        when(docs.hasDocs("octo-org/hello")).thenReturn(true);
        when(docs.ingest("octo-org/hello")).thenReturn(new IngestSummary("octo-org/hello", "v1", 3, 40, 10));

        runner.run(JOB);

        InOrder order = inOrder(jobs, backfill, embeddings, docs);
        order.verify(jobs).startStep(7, Step.BACKFILL);
        order.verify(jobs).progress(7, 100, null);
        order.verify(jobs).progress(7, 150, null);
        order.verify(jobs).startStep(7, Step.EMBEDDINGS);
        order.verify(jobs).progress(7, 0, 150);
        order.verify(jobs).progress(7, 150, 150);
        order.verify(jobs).startStep(7, Step.DOCS);
        order.verify(docs).ingest("octo-org/hello");
        order.verify(jobs).progress(7, 40, 40);
        order.verify(jobs).finish(7, Status.DONE, null, null, NOW);
    }

    @Test
    void aRepoWithoutReferenceDocsFinishesWithANoteInsteadOfFailing() {
        when(docs.hasDocs("octo-org/hello")).thenReturn(false);

        runner.run(JOB);

        verify(docs, never()).ingest(any());
        verify(jobs).finish(7, Status.DONE, RepoJobRunner.NO_DOCS_NOTE, null, NOW);
    }

    @Test
    void aFailingStepStopsTheJobAndRecordsWhy() {
        when(backfill.backfill(eq("octo-org/hello"), any())).thenThrow(new IllegalStateException("GitHub said 404"));

        runner.run(JOB);

        verify(embeddings, never()).embedMissing(any(), any());
        verify(jobs).finish(eq(7L), eq(Status.FAILED), isNull(), startsWith("Unexpected error (IllegalStateException)"),
                eq(NOW));
    }

    @Test
    void runNextDoesNothingWhenTheQueueIsEmpty() {
        when(jobs.claimNext(NOW)).thenReturn(Optional.empty());
        runner.runNext();
        verify(backfill, never()).backfill(any(), any());
    }

    @Test
    void thePublicReasonNeverContainsTheRawMessage() {
        assertThat(RepoJobRunner.publicReason(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                HttpHeaders.EMPTY, "{\"message\": \"secret body\"}".getBytes(), null)))
                .isEqualTo("GitHub answered 404 (repository not found or not visible to our token)");
        assertThat(RepoJobRunner.publicReason(new ResourceAccessException("I/O error on GET https://api.github.com/x")))
                .isEqualTo("Could not reach GitHub (network error)");
        assertThat(RepoJobRunner.publicReason(new DataAccessResourceFailureException("SELECT * FROM issue ...")))
                .isEqualTo("Database error");
        assertThat(RepoJobRunner.publicReason(new IllegalStateException("token=abc")))
                .doesNotContain("abc");
    }
}
