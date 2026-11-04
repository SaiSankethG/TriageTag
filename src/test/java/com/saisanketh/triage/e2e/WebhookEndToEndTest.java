package com.saisanketh.triage.e2e;

import com.saisanketh.triage.github.WebhookSignatureVerifier;
import com.saisanketh.triage.kafka.KafkaTopicsConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole path without GitHub: signed HTTP webhook -> receiver -> Kafka -> triage-worker -> Postgres.
 * The payload is GitHub's documented "issues opened" example (public data).
 */
class WebhookEndToEndTest extends FullStackTest {

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aSignedIssuesOpenedWebhookEndsUpAsAnIssueRow() throws Exception {
        String delivery = UUID.randomUUID().toString();

        long start = System.nanoTime();
        HttpResponse<String> response = postWebhook(fixtureBytes(), delivery, WEBHOOK_SECRET);
        long responseMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("accepted issues/opened");
        assertThat(responseMs).isLessThan(5_000);       // GitHub gives up after 10 s
        System.out.println("Webhook receiver answered in " + responseMs + " ms");

        // The consumer works asynchronously, so poll the database.
        waitUntil("the issue row exists", () -> countRows(
                "select count(*) from issue where repo = 'Codertocat/Hello-World' and number = 1") == 1);
        assertThat(jdbc.queryForObject("select title from issue", String.class))
                .isEqualTo("Spelling error in the README file");
        assertThat(jdbc.queryForList("select label_name from issue_label", String.class)).containsExactly("bug");
        assertThat(countRows("select count(*) from webhook_delivery where delivery_id = '" + delivery + "'"))
                .isEqualTo(1);
        // The worker also embedded it with the real model, so it can be found by similarity search.
        waitUntil("the issue has an embedding", () -> countRows(
                "select count(*) from issue_embedding where model = 'all-MiniLM-L6-v2'") == 1);
        // ... and created its triage report (fake LLM in tests).
        waitUntil("the issue has a triage report", () -> countRows(
                "select count(*) from triage_report where llm_model = 'fake'") == 1);
    }

    @Test
    void theSameDeliveryTwiceGivesOneIssueRowAndOneDeliveryRow() throws Exception {
        String delivery = UUID.randomUUID().toString();

        HttpResponse<String> first = postWebhook(fixtureBytes(), delivery, WEBHOOK_SECRET);
        waitUntil("the issue row exists", () -> countRows("select count(*) from issue") == 1);
        HttpResponse<String> again = postWebhook(fixtureBytes(), delivery, WEBHOOK_SECRET);   // like GitHub's "Redeliver"

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(again.statusCode()).isEqualTo(200);
        assertThat(again.body()).isEqualTo("duplicate delivery");
        assertThat(countRows("select count(*) from issue")).isEqualTo(1);
        assertThat(countRows("select count(*) from webhook_delivery")).isEqualTo(1);
        // And only one message for this delivery reached Kafka.
        assertThat(messagesFor(delivery, Duration.ofSeconds(3))).hasSize(1);
    }

    @Test
    void aBadSignatureIs401AndNothingReachesKafka() throws Exception {
        String delivery = UUID.randomUUID().toString();

        HttpResponse<String> response = postWebhook(fixtureBytes(), delivery, "not-the-right-secret");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(messagesFor(delivery, Duration.ofSeconds(3))).isEmpty();
        assertThat(countRows("select count(*) from webhook_delivery")).isZero();
        assertThat(countRows("select count(*) from issue")).isZero();
    }

    @Test
    void aDeletedIssueDisappearsWithItsLabels() throws Exception {
        postWebhook(fixtureBytes(), UUID.randomUUID().toString(), WEBHOOK_SECRET);
        waitUntil("the issue row exists", () -> countRows("select count(*) from issue") == 1);

        HttpResponse<String> response = postWebhook(fixtureWithAction("deleted"), UUID.randomUUID().toString(),
                WEBHOOK_SECRET);

        assertThat(response.statusCode()).isEqualTo(200);
        waitUntil("the issue row is gone", () -> countRows("select count(*) from issue") == 0);
        assertThat(countRows("select count(*) from issue_label")).isZero();
    }

    @Test
    void aTransferredIssueDisappearsFromTheOldRepo() throws Exception {
        postWebhook(fixtureBytes(), UUID.randomUUID().toString(), WEBHOOK_SECRET);
        waitUntil("the issue row exists", () -> countRows("select count(*) from issue") == 1);

        HttpResponse<String> response = postWebhook(fixtureWithAction("transferred"), UUID.randomUUID().toString(),
                WEBHOOK_SECRET);

        assertThat(response.statusCode()).isEqualTo(200);
        waitUntil("the issue row is gone", () -> countRows(
                "select count(*) from issue where repo = 'Codertocat/Hello-World'") == 0);
    }

    // --- helpers ---

    private byte[] fixtureBytes() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/github/webhook-issues-opened.json")) {
            return in.readAllBytes();
        }
    }

    /** The same documented payload with another action (the issue object is what deleted/transferred also carry). */
    private byte[] fixtureWithAction(String action) throws IOException {
        String json = new String(fixtureBytes(), java.nio.charset.StandardCharsets.UTF_8);
        return json.replace("\"action\": \"opened\"", "\"action\": \"" + action + "\"")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** POSTs the bytes the way GitHub does, with X-Hub-Signature-256 computed with the given secret. */
    private HttpResponse<String> postWebhook(byte[] body, String deliveryId, String signWith) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/webhooks/github"))
                .header("Content-Type", "application/json")
                .header("X-GitHub-Event", "issues")
                .header("X-GitHub-Delivery", deliveryId)
                .header("X-Hub-Signature-256", WebhookSignatureVerifier.sign(signWith, body))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** All messages on issue-events whose deliveryId is this one, reading for the given time. */
    private List<ConsumerRecord<String, String>> messagesFor(String deliveryId, Duration readFor) {
        return readTopic(KafkaTopicsConfig.ISSUE_EVENTS, Integer.MAX_VALUE, readFor).stream()
                .filter(r -> r.value().contains("\"deliveryId\":\"" + deliveryId + "\""))
                .toList();
    }
}
