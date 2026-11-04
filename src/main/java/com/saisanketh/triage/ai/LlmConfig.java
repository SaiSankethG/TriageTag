package com.saisanketh.triage.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the TriageLlm. Until a provider is added (STOP A), always the fake one.
 * Wired by hand (a @Bean method), not by a LangChain4j Spring Boot starter.
 */
@Slf4j
@Configuration
public class LlmConfig {

    @Bean
    TriageLlm triageLlm() {
        log.info("No LLM provider configured: using FakeTriageLlm (rule-based answers, no cost)");
        return new FakeTriageLlm();
    }
}
