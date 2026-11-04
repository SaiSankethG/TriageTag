package com.saisanketh.triage.ai;

import com.saisanketh.triage.metrics.TriageMetrics;

/**
 * Wraps the real (or fake) TriageLlm: refuses calls once today's token budget is used up, and counts every call's tokens
 * (in the budget table and as metrics: tokens and estimated cost).
 * A "decorator": same interface, added behaviour, so no caller has to remember the budget.
 */
public class GuardedTriageLlm implements TriageLlm {

    private final TriageLlm delegate;
    private final TokenBudget budget;
    private final TriageMetrics metrics;
    private final double inputUsdPerMillion;
    private final double outputUsdPerMillion;

    /**
     * @param inputUsdPerMillion  list price per million input tokens, for the cost metric (0 for the fake)
     * @param outputUsdPerMillion list price per million output tokens
     */
    public GuardedTriageLlm(TriageLlm delegate, TokenBudget budget, TriageMetrics metrics,
                            double inputUsdPerMillion, double outputUsdPerMillion) {
        this.delegate = delegate;
        this.budget = budget;
        this.metrics = metrics;
        this.inputUsdPerMillion = inputUsdPerMillion;
        this.outputUsdPerMillion = outputUsdPerMillion;
    }

    @Override
    public String model() {
        return delegate.model();
    }

    @Override
    public LlmReply complete(Prompt prompt) {
        if (!budget.hasRoom()) {
            throw new LlmException("daily token budget of " + budget.dailyLimit() + " tokens reached; LLM step skipped");
        }
        LlmReply reply = delegate.complete(prompt);
        if (!FakeTriageLlm.MODEL.equals(delegate.model())) {
            budget.record(reply.totalTokens());   // the fake's estimated tokens cost nothing: they don't use up the budget
        }
        metrics.llmCall(delegate.model(), reply.inputTokens(), reply.outputTokens(),
                (reply.inputTokens() * inputUsdPerMillion + reply.outputTokens() * outputUsdPerMillion) / 1_000_000);
        return reply;
    }
}
