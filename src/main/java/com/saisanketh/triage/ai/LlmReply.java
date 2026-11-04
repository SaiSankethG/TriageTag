package com.saisanketh.triage.ai;

/**
 * What the LLM answered, and what it cost.
 *
 * @param text         the model's answer
 * @param inputTokens  tokens in the prompt (as reported by the provider; estimated by the fake)
 * @param outputTokens tokens in the answer
 */
public record LlmReply(String text, int inputTokens, int outputTokens) {

    public int totalTokens() {
        return inputTokens + outputTokens;
    }
}
