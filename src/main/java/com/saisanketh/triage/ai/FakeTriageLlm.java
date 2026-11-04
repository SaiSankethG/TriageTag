package com.saisanketh.triage.ai;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

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

    public FakeTriageLlm() {
        rule(MissingDetailsChecker.PROMPT_NAME, FakeTriageLlm::missingDetails);
        rule(DuplicateVerifier.PROMPT_NAME, FakeTriageLlm::duplicateCheck);
    }

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

    // --- the rules ---

    private static final Pattern VERSION = Pattern.compile("\\b\\d+\\.\\d+(\\.\\d+)?\\b");
    private static final Pattern STEPS = Pattern.compile("(?i)steps|reproduc|repro\\b|sample (project|app)|minimal");
    private static final Pattern EXPECTED = Pattern.compile("(?i)expect|instead|should");
    private static final Pattern LOGS = Pattern.compile("(?i)exception|stack ?trace|caused by|\\berror\\b|\\n\\s+at [\\w.$]+\\(");

    /** Keyword rules: "present" if a typical word or pattern for the detail appears in the title or body. */
    static String missingDetails(Prompt prompt) {
        String text = prompt.variables().get("title") + "\n" + prompt.variables().get("body");
        return "{\"version\": \"%s\", \"steps_to_reproduce\": \"%s\", \"expected_vs_actual\": \"%s\", \"logs_or_stack_trace\": \"%s\"}"
                .formatted(found(VERSION, text), found(STEPS, text), found(EXPECTED, text), found(LOGS, text));
    }

    private static String found(Pattern pattern, String text) {
        return pattern.matcher(text).find() ? "present" : "missing";
    }

    /**
     * Title word overlap (Jaccard: shared words / all words, words of 3+ letters): >= 0.5 "yes", >= 0.25 "unsure",
     * else "no".
     */
    static String duplicateCheck(Prompt prompt) {
        Set<String> a = words(prompt.variables().get("new_title"));
        Set<String> b = words(prompt.variables().get("candidate_title"));
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        Set<String> shared = new HashSet<>(a);
        shared.retainAll(b);
        double overlap = union.isEmpty() ? 0 : (double) shared.size() / union.size();
        String verdict = overlap >= 0.5 ? "yes" : overlap >= 0.25 ? "unsure" : "no";
        return String.format(Locale.ROOT, "{\"same_problem\": \"%s\", \"reason\": \"fake LLM: title word overlap %.2f\"}",
                verdict, overlap);
    }

    private static Set<String> words(String text) {
        Set<String> words = new HashSet<>();
        for (String w : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (w.length() >= 3) {
                words.add(w);
            }
        }
        return words;
    }
}
