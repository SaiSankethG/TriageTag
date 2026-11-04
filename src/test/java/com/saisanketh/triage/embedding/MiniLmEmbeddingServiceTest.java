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
