package com.saisanketh.triage.github;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

/**
 * Receives GitHub webhooks. GitHub waits at most 10 seconds for an answer, so this does no GitHub calls
 * and no triage work: check the signature, look at the event type, hand it on, answer.
 * Docs: https://docs.github.com/en/webhooks/webhook-events-and-payloads#issues
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class GitHubWebhookController {

    /** The "issues" actions that change what we store. Others (assigned, milestoned, ...) are ignored for now. */
    static final Set<String> HANDLED_ACTIONS = Set.of("opened", "edited", "closed", "reopened", "labeled", "unlabeled");

    private final WebhookSignatureVerifier signatureVerifier;
    private final JsonMapper jsonMapper;

    /**
     * @param body the raw bytes: the signature is over these exact bytes, so we must not parse and re-write
     *             the JSON before checking it (that could change spaces or key order)
     */
    @PostMapping("/webhooks/github")
    public ResponseEntity<String> receive(
            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody(required = false) byte[] body) {

        byte[] payload = body == null ? new byte[0] : body;

        if (!signatureVerifier.isConfigured()) {
            log.error("GITHUB_WEBHOOK_SECRET is not set: refusing webhook delivery {} ({})", deliveryId, event);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("webhook secret not configured");
        }
        if (!signatureVerifier.isValid(payload, signature)) {
            // Never log the body or the signature: the request may not come from GitHub at all.
            log.warn("Rejected webhook delivery {} ({}): missing or wrong signature", deliveryId, event);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature");
        }

        if ("ping".equals(event)) {
            log.info("GitHub ping, delivery {}", deliveryId);    // sent once when the webhook is created
            return ResponseEntity.ok("pong");
        }
        if (!"issues".equals(event)) {
            return ResponseEntity.accepted().body("ignored event " + event);
        }

        JsonNode json;
        try {
            json = jsonMapper.readTree(payload);
        } catch (JacksonException e) {
            log.warn("Webhook delivery {} has a valid signature but is not JSON", deliveryId);
            return ResponseEntity.badRequest().body("body is not JSON");
        }
        String action = json.path("action").asString();
        if (!HANDLED_ACTIONS.contains(action)) {
            return ResponseEntity.accepted().body("ignored action issues/" + action);
        }

        log.info("Accepted webhook delivery {}: issues/{}", deliveryId, action);
        return ResponseEntity.ok("accepted issues/" + action);
    }
}
