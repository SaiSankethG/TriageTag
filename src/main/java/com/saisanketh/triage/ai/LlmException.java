package com.saisanketh.triage.ai;

/** The LLM call didn't produce an answer (provider down, timeout, over budget...). */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
