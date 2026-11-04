package com.saisanketh.triage.ai;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuardedTriageLlmTest {

    private static final Prompt PROMPT = new Prompt("p", "v1", "text", Map.of());
    private final TokenBudget budget = mock(TokenBudget.class);

    @Test
    void countsTheTokensOfEveryCall() {
        when(budget.hasRoom()).thenReturn(true);
        GuardedTriageLlm llm = new GuardedTriageLlm(new ScriptedTriageLlm("answer"), budget);

        assertThat(llm.complete(PROMPT).text()).isEqualTo("answer");
        assertThat(llm.model()).isEqualTo("scripted");
        verify(budget).record(110);
    }

    @Test
    void refusesCallsOnceTheBudgetIsUsedUp() {
        when(budget.hasRoom()).thenReturn(false);
        when(budget.dailyLimit()).thenReturn(1000L);
        ScriptedTriageLlm delegate = new ScriptedTriageLlm("never used");

        assertThatThrownBy(() -> new GuardedTriageLlm(delegate, budget).complete(PROMPT))
                .isInstanceOf(LlmException.class).hasMessageContaining("daily token budget of 1000 tokens");
        assertThat(delegate.prompts).isEmpty();
        verify(budget, never()).record(org.mockito.ArgumentMatchers.anyInt());
    }
}
