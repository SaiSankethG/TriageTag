package com.saisanketh.triage.kafka;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.github.GitHubIssueMapper;
import com.saisanketh.triage.kafka.IssueEventConsumer.BadIssueEventException;
import com.saisanketh.triage.service.IssueService;
import com.saisanketh.triage.service.UpsertResult;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IssueEventConsumerTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final IssueService issueService = mock(IssueService.class);
    private final IssueEventConsumer consumer = new IssueEventConsumer(json, new GitHubIssueMapper(), issueService);

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
}
