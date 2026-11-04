package com.saisanketh.triage.ai;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FakeTriageLlmTest {

    @Test
    void anUnknownPromptGetsAnEmptyJsonObjectAndEstimatedTokens() {
        LlmReply reply = new FakeTriageLlm().complete(new Prompt("unknown", "v1", "12345678", Map.of()));

        assertThat(reply.text()).isEqualTo("{}");
        assertThat(reply.inputTokens()).isEqualTo(2);    // 8 characters / 4
        assertThat(reply.outputTokens()).isEqualTo(1);
        assertThat(reply.totalTokens()).isEqualTo(3);
    }

    @Test
    void aRegisteredRuleAnswersItsPrompt() {
        FakeTriageLlm llm = new FakeTriageLlm();
        llm.rule("echo", p -> p.variables().get("x"));

        assertThat(llm.complete(new Prompt("echo", "v1", "...", Map.of("x", "hi"))).text()).isEqualTo("hi");
        assertThat(llm.model()).isEqualTo("fake");
    }
}
