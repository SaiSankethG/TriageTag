package com.saisanketh.triage.github;

import com.saisanketh.triage.kafka.IssueEventMessage;
import org.springframework.dao.DataAccessResourceFailureException;
import com.saisanketh.triage.service.WebhookIntakeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GitHubWebhookControllerTest {

    private static final String SECRET = "test-webhook-secret";
    private static final String DELIVERY = "72d3162e-cc78-11e3-81ab-4c9367dc0958";

    private WebhookIntakeService intakeService;

    @BeforeEach
    void setUp() {
        intakeService = mock(WebhookIntakeService.class);
        when(intakeService.accept(any(), any())).thenReturn(WebhookIntakeService.Result.ACCEPTED);
    }

    private MockMvc mockMvc(String secret) {
        GitHubWebhookController controller = new GitHubWebhookController(
                new WebhookSignatureVerifier(secret), JsonMapper.builder().build(), intakeService);
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static String issuesEvent(String action) {
        return """
                {"action": "%s",
                 "issue": {"number": 7, "title": "Something broke"},
                 "repository": {"full_name": "octo-org/hello"}}
                """.formatted(action);
    }

    /** Sends a webhook the way GitHub does, signed with the given secret (null = no signature header). */
    private ResultActions send(MockMvc mockMvc, String event, String body, String signWith, String deliveryId)
            throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequestBuilder request = post("/webhooks/github")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-GitHub-Event", event)
                .content(bytes);
        if (deliveryId != null) {
            request.header("X-GitHub-Delivery", deliveryId);
        }
        if (signWith != null) {
            request.header("X-Hub-Signature-256", WebhookSignatureVerifier.sign(signWith, bytes));
        }
        return mockMvc.perform(request);
    }

    private ResultActions send(MockMvc mockMvc, String event, String body, String signWith) throws Exception {
        return send(mockMvc, event, body, signWith, DELIVERY);
    }

    // --- signature and routing (step 2) ---

    @Test
    void pingIsAnsweredWith200() throws Exception {
        send(mockMvc(SECRET), "ping", "{\"zen\": \"Keep it logically awesome.\"}", SECRET)
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
        verifyNoInteractions(intakeService);
    }

    @Test
    void handledIssueActionsAreAccepted() throws Exception {
        for (String action : GitHubWebhookController.HANDLED_ACTIONS) {
            send(mockMvc(SECRET), "issues", issuesEvent(action), SECRET)
                    .andExpect(status().isOk())
                    .andExpect(content().string("accepted issues/" + action));
        }
    }

    @Test
    void otherIssueActionsAreIgnoredWith202() throws Exception {
        send(mockMvc(SECRET), "issues", issuesEvent("assigned"), SECRET)
                .andExpect(status().isAccepted());
        verifyNoInteractions(intakeService);
    }

    @Test
    void otherEventsAreIgnoredWith202() throws Exception {
        send(mockMvc(SECRET), "push", "{\"ref\": \"refs/heads/main\"}", SECRET)
                .andExpect(status().isAccepted());
        verifyNoInteractions(intakeService);
    }

    @Test
    void wrongSignatureIs401() throws Exception {
        send(mockMvc(SECRET), "issues", issuesEvent("opened"), "some-other-secret")
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(intakeService);   // nothing reaches Kafka
    }

    @Test
    void missingSignatureIs401() throws Exception {
        send(mockMvc(SECRET), "issues", issuesEvent("opened"), null)
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(intakeService);
    }

    @Test
    void signatureIsCheckedBeforeTheEventType() throws Exception {
        // Even a ping must be signed: otherwise anyone could probe the endpoint.
        send(mockMvc(SECRET), "ping", "{}", null)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withoutASecretEveryWebhookIsRefused() throws Exception {
        send(mockMvc(""), "issues", issuesEvent("opened"), "whatever-the-sender-used")
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(intakeService);
    }

    @Test
    void signedButNotJsonIs400() throws Exception {
        send(mockMvc(SECRET), "issues", "this is not json", SECRET)
                .andExpect(status().isBadRequest());
    }

    // --- hand-off to the intake service (step 3) ---

    @Test
    void passesTheIssueOnWithTheRightKey() throws Exception {
        send(mockMvc(SECRET), "issues", issuesEvent("labeled"), SECRET)
                .andExpect(status().isOk());

        ArgumentCaptor<IssueEventMessage> message = ArgumentCaptor.forClass(IssueEventMessage.class);
        verify(intakeService).accept(eq("issues"), message.capture());
        assertThat(message.getValue().deliveryId()).isEqualTo(DELIVERY);
        assertThat(message.getValue().action()).isEqualTo("labeled");
        assertThat(message.getValue().repo()).isEqualTo("octo-org/hello");
        assertThat(message.getValue().key()).isEqualTo("octo-org/hello#7");
        assertThat(message.getValue().issue().path("title").asString()).isEqualTo("Something broke");
    }

    @Test
    void aRepeatedDeliveryIsAnswered200AndNotQueuedAgain() throws Exception {
        when(intakeService.accept(any(), any())).thenReturn(WebhookIntakeService.Result.DUPLICATE);

        send(mockMvc(SECRET), "issues", issuesEvent("opened"), SECRET)
                .andExpect(status().isOk())
                .andExpect(content().string("duplicate delivery"));
    }

    @Test
    void aDatabaseFailureIs500SoGitHubCanRedeliver() throws Exception {
        when(intakeService.accept(any(), any()))
                .thenThrow(new DataAccessResourceFailureException("database down"));

        send(mockMvc(SECRET), "issues", issuesEvent("opened"), SECRET)
                .andExpect(status().isInternalServerError());
    }

    @Test
    void missingDeliveryIdIs400() throws Exception {
        send(mockMvc(SECRET), "issues", issuesEvent("opened"), SECRET, null)
                .andExpect(status().isBadRequest());
        verify(intakeService, never()).accept(any(), any());
    }

    @Test
    void issuesEventWithoutRepositoryIs400() throws Exception {
        send(mockMvc(SECRET), "issues", "{\"action\": \"opened\", \"issue\": {\"number\": 1}}", SECRET)
                .andExpect(status().isBadRequest());
    }
}
