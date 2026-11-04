package com.saisanketh.triage.ai;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * A stand-in for a real LLM: no network, no key, no cost, always the same answer for the same prompt.
 * Used in tests and when no provider is configured, so the whole pipeline (prompt -> answer -> parsing -> report)
 * runs end to end. Its answers are simple rules, NOT model quality: never report numbers produced with it.
 *
 * <p>Each prompt name gets a rule (added as the prompts are built); an unknown prompt gets an empty JSON object.
 * Token counts are estimated as characters / 4, roughly what real tokenizers give for English.
 */
public class FakeTriageLlm implements TriageLlm {

    public static final String MODEL = "fake";

    private final Map<String, Function<Prompt, String>> rules = new HashMap<>();

    @Override
    public String model() {
        return MODEL;
    }

    @Override
    public LlmReply complete(Prompt prompt) {
        String answer = rules.getOrDefault(prompt.name(), p -> "{}").apply(prompt);
        return new LlmReply(answer, estimateTokens(prompt.text()), estimateTokens(answer));
    }

    /** Registers the fake answer for one prompt name. */
    protected void rule(String promptName, Function<Prompt, String> answer) {
        rules.put(promptName, answer);
    }

    static int estimateTokens(String text) {
        return (text.length() + 3) / 4;
    }
}
