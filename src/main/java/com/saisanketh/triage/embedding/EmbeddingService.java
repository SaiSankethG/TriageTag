package com.saisanketh.triage.embedding;

import java.util.List;

/**
 * Turns text into an embedding (a vector of numbers that represents its meaning).
 * Our own interface, so the model can be swapped (another local model, or an API) without touching callers.
 */
public interface EmbeddingService {

    /** Stored next to every vector, so we know which model made it (and can re-embed after a change). */
    String modelName();

    /** How many numbers each vector has. Must match the vector(N) column. */
    int dimensions();

    float[] embed(String text);

    /** Several texts at once: faster than one by one. Same order as the input. */
    List<float[]> embedAll(List<String> texts);
}
