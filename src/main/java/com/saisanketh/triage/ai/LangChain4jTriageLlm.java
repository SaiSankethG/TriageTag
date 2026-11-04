package com.saisanketh.triage.ai;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

/**
 * The real TriageLlm: sends the prompt as one user message through a LangChain4j {@link ChatModel} (in this app the
 * Anthropic one, built in LlmConfig) and returns the text plus the token counts the provider reports.
 *
 * <p>Depends on LangChain4j's ChatModel interface, not on the Anthropic class, so it can be unit-tested with a stub and
 * another provider is a change in LlmConfig only.
 */
public class LangChain4jTriageLlm implements TriageLlm {

    private final ChatModel chatModel;
    private final String modelName;

    public LangChain4jTriageLlm(ChatModel chatModel, String modelName) {
        this.chatModel = chatModel;
        this.modelName = modelName;
    }

    @Override
    public String model() {
        return modelName;
    }

    @Override
    public LlmReply complete(Prompt prompt) {
        ChatResponse response;
        try {
            response = chatModel.chat(ChatRequest.builder().messages(UserMessage.from(prompt.text())).build());
        } catch (RuntimeException e) {
            // Network errors, rate limits after LangChain4j's own retries, 4xx/5xx: one exception type for our callers.
            throw new LlmException(prompt.id() + ": " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
        String text = response.aiMessage() == null ? null : response.aiMessage().text();
        if (text == null) {
            throw new LlmException(prompt.id() + ": the model returned no text (finish reason "
                    + response.finishReason() + ")");
        }
        TokenUsage usage = response.tokenUsage();
        int in = usage == null || usage.inputTokenCount() == null ? 0 : usage.inputTokenCount();
        int out = usage == null || usage.outputTokenCount() == null ? 0 : usage.outputTokenCount();
        return new LlmReply(text, in, out);
    }
}
