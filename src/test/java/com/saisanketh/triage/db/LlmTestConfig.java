package com.saisanketh.triage.db;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

/** The JSON mapper isn't part of the @DataJpaTest slice; the LLM services need one. */
@TestConfiguration(proxyBeanMethods = false)
public class LlmTestConfig {

    @Bean
    JsonMapper jsonMapper() {
        return JsonMapper.builder().build();
    }
}
