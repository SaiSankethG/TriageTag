package com.saisanketh.triage.ai;

import com.saisanketh.triage.ai.DuplicateCheck.Verdict;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Asks the LLM whether a new issue and its top duplicate candidate are the same root problem (sai's M3 decision 3).
 *
 * <p>Why: vector similarity alone flags many look-alikes (at a 10% false-alarm budget only ~14% of duplicates get a correct
 * flag). Before a public comment, a second opinion that reads both texts should remove most false alarms. Same strict-JSON
 * rules as the missing-details check: exactly the keys "same_problem" and "reason", one retry, then UNKNOWN.
 */
@Slf4j
@Service
public class DuplicateVerifier {

    static final String PROMPT_NAME = "duplicate-check";
    static final String PROMPT_VERSION = "v1";
    /** Two issues go into one prompt, so each body gets half of the missing-details budget. */
    static final int MAX_BODY_CHARS = 3_000;

    private static final Set<String> KEYS = Set.of("same_problem", "reason");

    private final TriageLlm llm;
    private final PromptTemplates templates;
    private final JsonMapper jsonMapper;

    public DuplicateVerifier(TriageLlm llm, PromptTemplates templates, JsonMapper jsonMapper) {
        this.llm = llm;
        this.templates = templates;
        this.jsonMapper = jsonMapper;
    }

    public DuplicateCheck check(String repo, IssueText newIssue, IssueText candidate) {
        Prompt prompt = templates.render(PROMPT_NAME, PROMPT_VERSION, Map.of(
                "repo", repo,
                "new_number", String.valueOf(newIssue.number()),
                "new_title", newIssue.title(),
                "new_body", body(newIssue),
                "candidate_number", String.valueOf(candidate.number()),
                "candidate_title", candidate.title(),
                "candidate_body", body(candidate)));
        int tokens = 0;
        for (int attempt = 1; attempt <= 2; attempt++) {
            LlmReply reply;
            try {
                reply = llm.complete(prompt);
            } catch (LlmException e) {
                log.warn("Duplicate check: LLM call failed: {}", e.getMessage());
                return new DuplicateCheck(Verdict.UNKNOWN, "LLM call failed: " + e.getMessage(), prompt.id(), tokens);
            }
            tokens += reply.totalTokens();
            Optional<DuplicateCheck> parsed = parse(reply.text(), prompt.id());
            if (parsed.isPresent()) {
                DuplicateCheck c = parsed.get();
                return new DuplicateCheck(c.verdict(), c.reason(), c.promptVersion(), tokens);
            }
            log.warn("Duplicate check: invalid answer on attempt {}", attempt);
        }
        return new DuplicateCheck(Verdict.UNKNOWN, "the LLM answered with invalid JSON twice", prompt.id(), tokens);
    }

    /** Exactly {"same_problem": "yes|no|unsure", "reason": "..."}; anything else is invalid. Tokens are filled in later. */
    Optional<DuplicateCheck> parse(String answer, String promptVersion) {
        Optional<JsonNode> json = StrictJson.object(jsonMapper, answer);
        if (json.isEmpty() || !Set.copyOf(json.get().propertyNames()).equals(KEYS)) {
            return Optional.empty();
        }
        JsonNode same = json.get().get("same_problem");
        JsonNode reason = json.get().get("reason");
        if (!same.isString() || !reason.isString()) {
            return Optional.empty();
        }
        Verdict verdict = switch (same.asString()) {
            case "yes" -> Verdict.YES;
            case "no" -> Verdict.NO;
            case "unsure" -> Verdict.UNSURE;
            default -> null;
        };
        return verdict == null ? Optional.empty()
                : Optional.of(new DuplicateCheck(verdict, reason.asString(), promptVersion, 0));
    }

    private static String body(IssueText issue) {
        String body = issue.body() == null || issue.body().isBlank() ? "(empty)" : issue.body();
        return body.length() <= MAX_BODY_CHARS ? body : body.substring(0, MAX_BODY_CHARS) + "\n[... cut ...]";
    }
}
