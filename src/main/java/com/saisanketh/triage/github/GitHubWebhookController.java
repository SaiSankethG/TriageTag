package com.saisanketh.triage.github;

import com.saisanketh.triage.kafka.IssueEventMessage;
import com.saisanketh.triage.repojob.RepoJobService;
import com.saisanketh.triage.repojob.RepoJobService.InstalledRepo;
import com.saisanketh.triage.service.WebhookIntakeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    static final Set<String> HANDLED_ACTIONS = Set.of("opened", "edited", "closed", "reopened", "labeled", "unlabeled",
            "deleted", "transferred");

    /**
     * GitHub App events that add repositories, and where the payload lists them: "installation"/created (the App was installed;
     * "repositories" lists what it can see) and "installation_repositories"/added (repos added to an installation later).
     * Docs: https://docs.github.com/en/webhooks/webhook-events-and-payloads#installation
     */
    static final Map<String, String> REPO_LIST_FIELD = Map.of(
            "installation/created", "repositories",
            "installation_repositories/added", "repositories_added");

    private final WebhookSignatureVerifier signatureVerifier;
    private final JsonMapper jsonMapper;
    private final WebhookIntakeService intakeService;
    private final RepoJobService repoJobs;

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
        boolean installEvent = "installation".equals(event) || "installation_repositories".equals(event);
        if (!"issues".equals(event) && !installEvent) {
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
        if (installEvent) {
            return installation(event, action, deliveryId, json);
        }
        if (!HANDLED_ACTIONS.contains(action)) {
            return ResponseEntity.accepted().body("ignored action issues/" + action);
        }

        if (deliveryId == null || deliveryId.isBlank()) {
            return ResponseEntity.badRequest().body("missing X-GitHub-Delivery header");
        }
        JsonNode issue = json.get("issue");
        JsonNode repoName = json.path("repository").get("full_name");
        if (issue == null || !issue.isObject() || repoName == null || !repoName.isString()) {
            return ResponseEntity.badRequest().body("issues event without issue or repository.full_name");
        }

        // Private repositories are not triaged (piece 1b): the reports site is public, so a report would publish a private
        // issue. A missing flag counts as private, the same rule as for installations. 202 = received on purpose, don't resend.
        if (json.path("repository").path("private").asBoolean(true)) {
            log.info("Ignored webhook delivery {}: issues/{} in a private repository", deliveryId, action);
            return ResponseEntity.accepted().body("ignored: private repository");
        }

        IssueEventMessage message = new IssueEventMessage(deliveryId, action, repoName.asString(), issue);
        try {
            if (intakeService.accept(event, message) == WebhookIntakeService.Result.DUPLICATE) {
                log.info("Ignored repeated webhook delivery {}", deliveryId);
                return ResponseEntity.ok("duplicate delivery");
            }
        } catch (DataAccessException e) {
            // The database is down or failed: nothing was saved (one transaction), so GitHub's "Redeliver" works later.
            log.error("Could not store webhook delivery {}: {}", deliveryId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("could not queue the event; it can be redelivered");
        }

        log.info("Accepted webhook delivery {}: issues/{} for {}", deliveryId, action, message.key());
        return ResponseEntity.ok("accepted issues/" + action);
    }

    /** The App was installed on repositories, or repositories were added: queue an "Add repository" job for each. */
    private ResponseEntity<String> installation(String event, String action, String deliveryId, JsonNode json) {
        String field = REPO_LIST_FIELD.get(event + "/" + action);
        if (field == null) {
            return ResponseEntity.accepted().body("ignored action " + event + "/" + action);   // e.g. deleted, suspend
        }
        if (deliveryId == null || deliveryId.isBlank()) {
            return ResponseEntity.badRequest().body("missing X-GitHub-Delivery header");
        }
        List<InstalledRepo> repos = new ArrayList<>();
        for (JsonNode repo : json.path(field)) {
            JsonNode name = repo.get("full_name");
            if (name != null && name.isString()) {
                // A repo whose "private" flag is missing is treated as private: never publish what we can't be sure is public.
                repos.add(new InstalledRepo(name.asString(), repo.path("private").asBoolean(true)));
            }
        }
        int queued;
        try {
            queued = repoJobs.queueInstalled(deliveryId, event, action, repos);
        } catch (DataAccessException e) {
            log.error("Could not queue repositories from webhook delivery {}: {}", deliveryId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("could not queue the repositories; it can be redelivered");
        }
        if (queued < 0) {
            log.info("Ignored repeated webhook delivery {}", deliveryId);
            return ResponseEntity.ok("duplicate delivery");
        }
        log.info("Accepted webhook delivery {}: {}/{}, {} of {} repositories queued", deliveryId, event, action, queued,
                repos.size());
        return ResponseEntity.ok("queued " + queued + " of " + repos.size() + " repositories");
    }
}
