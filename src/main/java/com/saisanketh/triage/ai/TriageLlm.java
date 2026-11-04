package com.saisanketh.triage.ai;

/**
 * Our own boundary around "send a prompt to an LLM, get text back". Everything else (which prompt, how to read the
 * answer, retries) lives in our services, so the provider can change without touching them.
 *
 * <p>Implementations: {@link FakeTriageLlm} (tests, and the app when no API key is set); the real provider, through
 * LangChain4j, is added at STOP A.
 */
public interface TriageLlm {

    /** The model's name, stored with every report, e.g. "fake" or a provider's model id. */
    String model();

    /**
     * @throws LlmException if the call fails (network, provider error, budget); callers decide what to show instead
     */
    LlmReply complete(Prompt prompt);
}
