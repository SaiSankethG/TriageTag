package com.saisanketh.triage.ai;

/**
 * Wraps the real (or fake) TriageLlm: refuses calls once today's token budget is used up, and counts every call's tokens.
 * A "decorator": same interface, added behaviour, so no caller has to remember the budget.
 */
public class GuardedTriageLlm implements TriageLlm {

    private final TriageLlm delegate;
    private final TokenBudget budget;

    public GuardedTriageLlm(TriageLlm delegate, TokenBudget budget) {
        this.delegate = delegate;
        this.budget = budget;
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
        budget.record(reply.totalTokens());
        return reply;
    }
}
