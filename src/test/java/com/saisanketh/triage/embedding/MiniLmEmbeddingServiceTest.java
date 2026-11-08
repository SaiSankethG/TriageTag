package com.saisanketh.triage.embedding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the real model (in-process, ~1 s to load): checks the shape and that meaning really drives similarity. */
class MiniLmEmbeddingServiceTest {

    private static final MiniLmEmbeddingService service = new MiniLmEmbeddingService();

    @Test
    void producesVectorsOfTheDeclaredSize() {
        assertThat(service.embed("Actuator health endpoint returns 404")).hasSize(service.dimensions());
        assertThat(service.dimensions()).isEqualTo(384);
        assertThat(service.modelName()).isEqualTo("all-MiniLM-L6-v2");
    }

    @Test
    void similarIssuesAreCloserThanUnrelatedOnes() {
        List<float[]> vectors = service.embedAll(List.of(
                "Application fails to start because port 8080 is already in use",
                "Startup error: web server can't bind, the port is taken by another process",
                "Add support for YAML anchors in configuration files"));

        double samePlace = cosine(vectors.get(0), vectors.get(1));
        double different = cosine(vectors.get(0), vectors.get(2));

        // Different words, same meaning -> more similar than shared-domain but different meaning.
        assertThat(samePlace).isGreaterThan(different);
    }

    @Test
    void embedAllKeepsTheInputOrder() {
        List<float[]> batch = service.embedAll(List.of("first text", "second text"));

        assertThat(cosine(batch.get(0), service.embed("first text"))).isGreaterThan(0.999);
        assertThat(cosine(batch.get(1), service.embed("second text"))).isGreaterThan(0.999);
    }

    /** Backend piece 3: 3 Kafka workers share this one model, so it must give the same answers when called from 3 threads at once. */
    @Test
    void threeThreadsAtOnceGetTheSameVectorsAsOneAtATime() throws Exception {
        List<String> texts = List.of("Actuator health endpoint returns 404", "Port 8080 already in use",
                "YAML anchors are not supported", "Caffeine cache ignores AsyncCacheLoader", "Docker Compose support fails",
                "Flyway migration runs twice");
        List<float[]> expected = texts.stream().map(service::embed).toList();

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(3);
        try {
            List<java.util.concurrent.Future<float[]>> results = new java.util.ArrayList<>();
            for (int round = 0; round < 5; round++) {
                for (String text : texts) {
                    results.add(pool.submit(() -> service.embed(text)));
                }
            }
            for (int i = 0; i < results.size(); i++) {
                assertThat(cosine(results.get(i).get(), expected.get(i % texts.size()))).isGreaterThan(0.9999);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
