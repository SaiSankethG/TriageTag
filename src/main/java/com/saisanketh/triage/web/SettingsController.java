package com.saisanketh.triage.web;

import com.saisanketh.triage.ai.TokenBudget;
import com.saisanketh.triage.ai.TriageLlm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * The few settings the demo page needs to explain a report in plain words: the duplicate thresholds (to say
 * "high / medium / low"), which model is answering, and today's token budget (to say "AI checks paused until ...").
 * Read-only, no secrets.
 */
@RestController
public class SettingsController {

    /**
     * @param commentThreshold  top-1 score needed for a public comment (also: "likely duplicate" if the LLM agrees)
     * @param possibleThreshold below this, a match is "low" similarity
     * @param llmModel          e.g. "claude-haiku-5-5", or "fake" when no API key is set
     * @param budgetResetsAt    next UTC midnight: when today's token count starts again from zero
     */
    public record Settings(double commentThreshold, double possibleThreshold, String llmModel, long dailyTokenBudget,
                           long tokensUsedToday, boolean budgetReached, Instant budgetResetsAt) {
    }

    private final TokenBudget budget;
    private final TriageLlm llm;
    private final Clock clock;
    private final double commentThreshold;
    private final double possibleThreshold;

    public SettingsController(TokenBudget budget, TriageLlm llm, Clock clock,
                              @Value("${triage.duplicates.comment-threshold:0.90}") double commentThreshold,
                              @Value("${triage.duplicates.possible-threshold:0.75}") double possibleThreshold) {
        this.budget = budget;
        this.llm = llm;
        this.clock = clock;
        this.commentThreshold = commentThreshold;
        this.possibleThreshold = possibleThreshold;
    }

    /** Example: {@code curl localhost:8080/settings} */
    @GetMapping("/settings")
    public Settings settings() {
        long used = budget.usedToday();
        Instant nextMidnightUtc = LocalDate.now(clock.withZone(ZoneOffset.UTC)).plusDays(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        return new Settings(commentThreshold, possibleThreshold, llm.model(), budget.dailyLimit(), used,
                used >= budget.dailyLimit(), nextMidnightUtc);
    }
}
