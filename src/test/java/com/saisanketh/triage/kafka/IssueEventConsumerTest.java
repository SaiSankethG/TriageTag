package com.saisanketh.triage.kafka;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.domain.SourceType;
import com.saisanketh.triage.embedding.IssueEmbeddingService;
import com.saisanketh.triage.github.GitHubIssueMapper;
import com.saisanketh.triage.kafka.IssueEventConsumer.BadIssueEventException;
import com.saisanketh.triage.metrics.TriageMetrics;
import com.saisanketh.triage.report.TriageService;
import com.saisanketh.triage.repository.WebhookDeliveryRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.saisanketh.triage.service.IssueService;
import com.saisanketh.triage.service.UpsertResult;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IssueEventConsumerTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final IssueService issueService = mock(IssueService.class);
    private final IssueEmbeddingService embeddingService = mock(IssueEmbeddingService.class);
    private final TriageService triageService = mock(TriageService.class);
    private final WebhookDeliveryRepository deliveries = mock(WebhookDeliveryRepository.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final IssueEventConsumer consumer = new IssueEventConsumer(json, new GitHubIssueMapper(), issueService,
            embeddingService, triageService, deliveries, new TriageMetrics(registry),
            Clock.fixed(Instant.parse("2026-11-04T10:00:05Z"), ZoneOffset.UTC));

    private static ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("issue-events", 1, 42L, "Codertocat/Hello-World#1", value);
    }

    /** The message the webhook receiver would publish for GitHub's documented "issues opened" example. */
    private String messageFromFixture() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/github/webhook-issues-opened.json")) {
            var webhook = json.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            return json.writeValueAsString(new IssueEventMessage(
                    "d-1", "opened", "Codertocat/Hello-World", webhook.get("issue")));
        }
    }

    @Test
    void mapsTheIssueAndUpsertsIt() throws IOException {
        when(issueService.upsert(any())).thenReturn(UpsertResult.INSERTED);

        consumer.onIssueEvent(record(messageFromFixture()));

        ArgumentCaptor<Issue> issue = ArgumentCaptor.forClass(Issue.class);
        verify(issueService).upsert(issue.capture());
        assertThat(issue.getValue().getRepo()).isEqualTo("Codertocat/Hello-World");
        assertThat(issue.getValue().getNumber()).isEqualTo(1);
        assertThat(issue.getValue().getTitle()).isEqualTo("Spelling error in the README file");
        assertThat(issue.getValue().getLabels()).containsExactly("bug");
        verify(embeddingService).embedIssue(SourceType.GITHUB, "444500041");   // new text -> new embedding
    }

    @Test
    void aStaleEventIsNotReEmbedded() throws IOException {
        when(issueService.upsert(any())).thenReturn(UpsertResult.STALE);

        consumer.onIssueEvent(record(messageFromFixture()));

        verifyNoInteractions(embeddingService);
    }

    @Test
    void aDeletedIssueIsRemoved() {
        consumer.onIssueEvent(record("""
                {"deliveryId": "d-2", "action": "deleted", "repo": "Codertocat/Hello-World",
                 "issue": {"id": 444500041, "number": 1, "title": "Spelling error in the README file"}}
                """));

        verify(issueService).delete(SourceType.GITHUB, "444500041");
        verify(issueService, never()).upsert(any());
    }

    @Test
    void aTransferredIssueIsRemovedFromTheOldRepo() {
        // GitHub sends the OLD issue as "issue" and the new one (new id, new repo) under "changes".
        consumer.onIssueEvent(record("""
                {"deliveryId": "d-3", "action": "transferred", "repo": "Codertocat/Hello-World",
                 "issue": {"id": 444500041, "number": 1},
                 "changes": {"new_issue": {"id": 999000111, "number": 5},
                             "new_repository": {"full_name": "Codertocat/Other-Repo"}}}
                """));

        verify(issueService).delete(SourceType.GITHUB, "444500041");
        verify(issueService, never()).upsert(any());
    }

    @Test
    void notJsonIsABadEvent() {
        assertThatThrownBy(() -> consumer.onIssueEvent(record("this is not json")))
                .isInstanceOf(BadIssueEventException.class);
        verifyNoInteractions(issueService);
    }

    @Test
    void noIssueObjectIsABadEvent() {
        assertThatThrownBy(() -> consumer.onIssueEvent(record("{\"deliveryId\": \"d-1\"}")))
                .isInstanceOf(BadIssueEventException.class);
    }

    @Test
    void anIssueTheMapperCannotReadIsABadEvent() {
        // No id, no number, no repository_url: the mapper fails.
        assertThatThrownBy(() -> consumer.onIssueEvent(record("{\"issue\": {\"title\": \"x\"}}")))
                .isInstanceOf(BadIssueEventException.class);
    }

    @Test
    void aDatabaseErrorIsNotTurnedIntoABadEvent() throws IOException {
        // Database problems must stay as they are, so the error handler RETRIES them.
        when(issueService.upsert(any())).thenThrow(new IllegalStateException("database is down"));
        String message = messageFromFixture();

        assertThatThrownBy(() -> consumer.onIssueEvent(record(message)))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(BadIssueEventException.class);
    }

    @Test
    void anOpenedIssueGetsATriageReport() throws IOException {
        when(issueService.upsert(any())).thenReturn(UpsertResult.INSERTED);
        when(issueService.findId(SourceType.GITHUB, "444500041")).thenReturn(java.util.Optional.of(7L));

        consumer.onIssueEvent(record(messageFromFixture()));

        verify(triageService).createIfMissing(7L);
    }

    @Test
    void aNewReportRecordsTheTimeSinceTheWebhookWasAccepted() throws IOException {
        when(issueService.upsert(any())).thenReturn(UpsertResult.INSERTED);
        when(issueService.findId(SourceType.GITHUB, "444500041")).thenReturn(java.util.Optional.of(7L));
        when(triageService.createIfMissing(7L)).thenReturn(true);
        when(deliveries.receivedAt("d-1")).thenReturn(java.util.Optional.of(Instant.parse("2026-11-04T10:00:00Z")));

        consumer.onIssueEvent(record(messageFromFixture()));

        var timer = registry.get("triage.webhook.to.report").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(5.0);
    }

    @Test
    void otherActionsDoNotCreateAReport() throws IOException {
        when(issueService.upsert(any())).thenReturn(UpsertResult.UPDATED);
        String edited = messageFromFixture().replace("\"action\":\"opened\"", "\"action\":\"edited\"");

        consumer.onIssueEvent(record(edited));

        verifyNoInteractions(triageService);
    }
}
