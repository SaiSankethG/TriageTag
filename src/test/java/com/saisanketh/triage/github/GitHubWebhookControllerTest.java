package com.saisanketh.triage.github;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GitHubWebhookControllerTest {

    private static final String SECRET = "test-webhook-secret";

    private MockMvc mockMvc(String secret) {
        GitHubWebhookController controller =
                new GitHubWebhookController(new WebhookSignatureVerifier(secret), JsonMapper.builder().build());
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** Sends a webhook the way GitHub does, signed with the given secret. */
    private ResultActions send(MockMvc mockMvc, String event, String body, String signWith) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        var request = post("/webhooks/github")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-GitHub-Event", event)
                .header("X-GitHub-Delivery", "72d3162e-cc78-11e3-81ab-4c9367dc0958")
                .content(bytes);
        if (signWith != null) {
            request.header("X-Hub-Signature-256", WebhookSignatureVerifier.sign(signWith, bytes));
        }
        return mockMvc.perform(request);
    }

    @Test
    void pingIsAnsweredWith200() throws Exception {
        send(mockMvc(SECRET), "ping", "{\"zen\": \"Keep it logically awesome.\"}", SECRET)
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
    }

    @Test
    void handledIssueActionsAreAccepted() throws Exception {
        for (String action : GitHubWebhookController.HANDLED_ACTIONS) {
            send(mockMvc(SECRET), "issues", "{\"action\": \"" + action + "\", \"issue\": {}}", SECRET)
                    .andExpect(status().isOk())
                    .andExpect(content().string("accepted issues/" + action));
        }
    }

    @Test
    void otherIssueActionsAreIgnoredWith202() throws Exception {
        send(mockMvc(SECRET), "issues", "{\"action\": \"assigned\", \"issue\": {}}", SECRET)
                .andExpect(status().isAccepted());
    }

    @Test
    void otherEventsAreIgnoredWith202() throws Exception {
        send(mockMvc(SECRET), "push", "{\"ref\": \"refs/heads/main\"}", SECRET)
                .andExpect(status().isAccepted());
    }

    @Test
    void wrongSignatureIs401() throws Exception {
        send(mockMvc(SECRET), "issues", "{\"action\": \"opened\"}", "some-other-secret")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingSignatureIs401() throws Exception {
        send(mockMvc(SECRET), "issues", "{\"action\": \"opened\"}", null)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signatureIsCheckedBeforeTheEventType() throws Exception {
        // Even a ping must be signed: otherwise anyone could probe the endpoint.
        send(mockMvc(SECRET), "ping", "{}", null)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withoutASecretEveryWebhookIsRefused() throws Exception {
        send(mockMvc(""), "issues", "{\"action\": \"opened\"}", "whatever-the-sender-used")
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void signedButNotJsonIs400() throws Exception {
        send(mockMvc(SECRET), "issues", "this is not json", SECRET)
                .andExpect(status().isBadRequest());
    }
}
