package com.saisanketh.triage.db;

import com.saisanketh.triage.embedding.EmbeddingService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Beans the LLM and docs services need that aren't part of the @DataJpaTest slice: a JSON mapper, and an embedding
 * "model" that maps every text to the same unit vector (the real MiniLM model is tested on its own; DB tests store
 * hand-made vectors).
 */
@TestConfiguration(proxyBeanMethods = false)
public class LlmTestConfig {

    @Bean
    JsonMapper jsonMapper() {
        return JsonMapper.builder().build();
    }

    @Bean
    EmbeddingService embeddingService() {
        return new EmbeddingService() {
            @Override
            public String modelName() {
                return "test";
            }

            @Override
            public int dimensions() {
                return 384;
            }

            @Override
            public float[] embed(String text) {
                float[] v = new float[384];
                v[0] = 1;
                return v;
            }

            @Override
            public List<float[]> embedAll(List<String> texts) {
                return texts.stream().map(this::embed).toList();
            }
        };
    }
}
