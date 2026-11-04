package com.saisanketh.triage.ai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The adapter only, with a stubbed LangChain4j ChatModel: no network, no key. */
class LangChain4jTriageLlmTest {

    private static final Prompt PROMPT = new Prompt("missing-details", "v1", "the prompt text", Map.of());
    private final ChatModel chatModel = mock(ChatModel.class);
    private final LangChain4jTriageLlm llm = new LangChain4jTriageLlm(chatModel, "claude-haiku-5-5");

    @Test
    void sendsThePromptAsOneUserMessageAndReturnsTextAndTokens() {
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("{\"a\": 1}")).tokenUsage(new TokenUsage(120, 15)).build());

        LlmReply reply = llm.complete(PROMPT);

        assertThat(reply).isEqualTo(new LlmReply("{\"a\": 1}", 120, 15));
        assertThat(llm.model()).isEqualTo("claude-haiku-5-5");
        ArgumentCaptor<ChatRequest> request = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel).chat(request.capture());
        assertThat(request.getValue().messages()).containsExactly(UserMessage.from("the prompt text"));
    }

    @Test
    void providerErrorsBecomeLlmExceptions() {
        when(chatModel.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("429 rate limited"));

        assertThatThrownBy(() -> llm.complete(PROMPT)).isInstanceOf(LlmException.class)
                .hasMessageContaining("missing-details-v1").hasMessageContaining("429 rate limited");
    }

    @Test
    void missingUsageCountsAsZero() {
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("ok")).build());

        assertThat(llm.complete(PROMPT).totalTokens()).isZero();
    }
}
