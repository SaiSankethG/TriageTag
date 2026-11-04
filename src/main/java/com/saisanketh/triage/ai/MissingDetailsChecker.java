package com.saisanketh.triage.ai;

import com.saisanketh.triage.ai.MissingDetails.Answer;
import com.saisanketh.triage.ai.MissingDetails.Detail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Asks the LLM which of version / steps to reproduce / expected vs actual / logs a new issue is missing.
 *
 * <p>The answer must be strict JSON with exactly the four keys and allowed values. If it isn't, we ask once more (models
 * sometimes add chatter); if the second answer is bad too, or the call fails, the result is UNKNOWN instead of a guess.
 */
@Slf4j
@Service
public class MissingDetailsChecker {

    static final String PROMPT_NAME = "missing-details";
    static final String PROMPT_VERSION = "v1";
    /** Long bodies are cut: the details we look for are almost always near the top, and tokens cost money. */
    static final int MAX_BODY_CHARS = 6_000;

    private static final Set<String> KEYS = Arrays.stream(Detail.values()).map(d -> d.key).collect(Collectors.toSet());

    private final TriageLlm llm;
    private final PromptTemplates templates;
    private final JsonMapper jsonMapper;

    public MissingDetailsChecker(TriageLlm llm, PromptTemplates templates, JsonMapper jsonMapper) {
        this.llm = llm;
        this.templates = templates;
        this.jsonMapper = jsonMapper;
    }

    public MissingDetails check(String repo, String title, String body) {
        Prompt prompt = templates.render(PROMPT_NAME, PROMPT_VERSION, Map.of(
                "repo", repo,
                "title", title,
                "body", body == null ? "(empty)" : truncate(body)));
        int tokens = 0;
        for (int attempt = 1; attempt <= 2; attempt++) {
            LlmReply reply;
            try {
                reply = llm.complete(prompt);
            } catch (LlmException e) {
                log.warn("Missing-details check: LLM call failed: {}", e.getMessage());
                return MissingDetails.unknown("LLM call failed: " + e.getMessage(), prompt.id(), tokens);
            }
            tokens += reply.totalTokens();
            Optional<Map<Detail, Answer>> parsed = parse(reply.text());
            if (parsed.isPresent()) {
                List<Detail> missing = parsed.get().entrySet().stream()
                        .filter(e -> e.getValue() == Answer.MISSING).map(Map.Entry::getKey).sorted().toList();
                return new MissingDetails(MissingDetails.Status.OK, parsed.get(), missing, null, prompt.id(), tokens);
            }
            log.warn("Missing-details check: invalid answer on attempt {}", attempt);
        }
        return MissingDetails.unknown("the LLM answered with invalid JSON twice", prompt.id(), tokens);
    }

    /** Exactly the four keys, each with an allowed value; anything else is invalid. */
    Optional<Map<Detail, Answer>> parse(String answer) {
        Optional<JsonNode> json = StrictJson.object(jsonMapper, answer);
        if (json.isEmpty() || !Set.copyOf(json.get().propertyNames()).equals(KEYS)) {
            return Optional.empty();
        }
        Map<Detail, Answer> result = new EnumMap<>(Detail.class);
        for (Detail detail : Detail.values()) {
            JsonNode value = json.get().get(detail.key);
            Optional<Answer> a = value.isString() ? answer(value.asString()) : Optional.empty();
            if (a.isEmpty()) {
                return Optional.empty();
            }
            result.put(detail, a.get());
        }
        return Optional.of(result);
    }

    private static Optional<Answer> answer(String key) {
        return Arrays.stream(Answer.values()).filter(a -> a.key.equals(key)).findFirst();
    }

    static String truncate(String body) {
        return body.length() <= MAX_BODY_CHARS ? body : body.substring(0, MAX_BODY_CHARS) + "\n[... cut ...]";
    }
}
