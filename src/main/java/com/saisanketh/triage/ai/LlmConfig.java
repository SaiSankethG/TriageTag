package com.saisanketh.triage.ai;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Chooses the TriageLlm and wraps it in the daily token budget. Wired by hand (a @Bean method), not by a LangChain4j
 * Spring Boot starter.
 *
 * <p>{@code triage.llm.provider}: "fake" = FakeTriageLlm always (tests set this); "anthropic" = the real model, and the
 * app refuses to start without ANTHROPIC_API_KEY; "auto" (the default) = the real model if the key is set, else the fake.
 * The key is read from the environment only and never logged.
 */
@Slf4j
@Configuration
public class LlmConfig {

    @Bean
    TriageLlm triageLlm(TokenBudget budget,
                        @Value("${triage.llm.provider:auto}") String provider,
                        @Value("${triage.llm.anthropic.api-key:}") String apiKey,
                        @Value("${triage.llm.anthropic.model:claude-haiku-5-5}") String model,
                        @Value("${triage.llm.anthropic.max-tokens:1024}") int maxTokens,
                        @Value("${triage.llm.anthropic.thinking:disabled}") String thinking) {
        boolean hasKey = apiKey != null && !apiKey.isBlank();
        boolean useAnthropic = switch (provider) {
            case "fake" -> false;
            case "anthropic" -> {
                if (!hasKey) {
                    throw new IllegalStateException("triage.llm.provider=anthropic needs the ANTHROPIC_API_KEY env var");
                }
                yield true;
            }
            case "auto" -> hasKey;
            default -> throw new IllegalArgumentException("triage.llm.provider must be fake, anthropic or auto: " + provider);
        };
        if (!useAnthropic) {
            log.info("LLM: FakeTriageLlm (rule-based answers, no cost); provider setting '{}'", provider);
            return new GuardedTriageLlm(new FakeTriageLlm(), budget);
        }
        log.info("LLM: Anthropic {} (max {} output tokens, thinking {}), daily budget {} tokens", model, maxTokens,
                thinking, budget.dailyLimit());
        AnthropicChatModel chatModel = AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(model)
                .maxTokens(maxTokens)
                // Short JSON answers and a 120-word draft: no extended reasoning needed, and thinking tokens are billed.
                .thinkingType(thinking)
                .timeout(Duration.ofSeconds(60))
                .maxRetries(2)
                .build();
        return new GuardedTriageLlm(new LangChain4jTriageLlm(chatModel, model), budget);
    }
}
