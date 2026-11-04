package com.saisanketh.triage.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * all-MiniLM-L6-v2, a small sentence-embedding model (384 numbers per text), run inside the JVM with ONNX Runtime.
 * The model file ships inside the LangChain4j jar: no API key, no network, no cost per call.
 * Created by hand (no LangChain4j Spring Boot starter), so Spring Boot 4 compatibility of the starter doesn't matter.
 */
@Component
public class MiniLmEmbeddingService implements EmbeddingService {

    static final String MODEL_NAME = "all-MiniLM-L6-v2";
    static final int DIMENSIONS = 384;

    private final EmbeddingModel model = new AllMiniLmL6V2EmbeddingModel();   // loads the model once (~1 s)

    @Override
    public String modelName() {
        return MODEL_NAME;
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public float[] embed(String text) {
        return model.embed(text).content().vector();
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        List<TextSegment> segments = texts.stream().map(TextSegment::from).toList();
        return model.embedAll(segments).content().stream().map(Embedding::vector).toList();
    }
}
