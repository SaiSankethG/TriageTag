package com.saisanketh.triage.ai;

import com.saisanketh.triage.metrics.TriageMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuardedTriageLlmTest {

    private static final Prompt PROMPT = new Prompt("p", "v1", "text", Map.of());
    private final TokenBudget budget = mock(TokenBudget.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void countsTheTokensOfEveryCall() {
        when(budget.hasRoom()).thenReturn(true);
        GuardedTriageLlm llm = new GuardedTriageLlm(new ScriptedTriageLlm("answer"), budget, new TriageMetrics(registry), 0.10, 0.50);

        assertThat(llm.complete(PROMPT).text()).isEqualTo("answer");
        assertThat(llm.model()).isEqualTo("scripted");
        verify(budget).record(110);
        // Metrics: 100 input + 10 output tokens; cost = (100 * 0.10 + 10 * 0.50) / 1,000,000 USD
        assertThat(registry.get("triage.llm.tokens").tag("model", "scripted").tag("direction", "input").counter().count())
                .isEqualTo(100);
        assertThat(registry.get("triage.llm.tokens").tag("direction", "output").counter().count()).isEqualTo(10);
        assertThat(registry.get("triage.llm.cost").tag("model", "scripted").counter().count()).isCloseTo(15e-6, within(1e-12));
    }

    @Test
    void refusesCallsOnceTheBudgetIsUsedUp() {
        when(budget.hasRoom()).thenReturn(false);
        when(budget.dailyLimit()).thenReturn(1000L);
        ScriptedTriageLlm delegate = new ScriptedTriageLlm("never used");

        assertThatThrownBy(() -> new GuardedTriageLlm(delegate, budget, new TriageMetrics(registry), 0.10, 0.50).complete(PROMPT))
                .isInstanceOf(LlmException.class).hasMessageContaining("daily token budget of 1000 tokens");
        assertThat(delegate.prompts).isEmpty();
        verify(budget, never()).record(org.mockito.ArgumentMatchers.anyInt());
    }
}
