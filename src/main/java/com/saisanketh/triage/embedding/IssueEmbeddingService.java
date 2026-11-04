package com.saisanketh.triage.embedding;

import com.saisanketh.triage.domain.SourceType;
import com.saisanketh.triage.embedding.IssueEmbeddingRepository.IssueText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/** Creates and stores issue embeddings: in bulk for a repo (batch job), or for one issue (Kafka worker). */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueEmbeddingService {

    /** Issues per round: big enough to keep the model busy, small enough to keep memory low. */
    static final int BATCH_SIZE = 256;

    private final EmbeddingService embeddingService;
    private final IssueEmbeddingRepository repository;

    /** Embeds every issue of the repo that has no embedding from the current model yet. */
    public EmbeddingSummary embedMissing(String repo) {
        long start = System.nanoTime();
        String model = embeddingService.modelName();
        int total = repository.countMissing(repo, model);
        log.info("Embeddings {}: {} issues to embed with {}", repo, total, model);

        int embedded = 0;
        while (true) {
            List<IssueText> batch = repository.findMissing(repo, model, BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            List<String> texts = batch.stream().map(t -> EmbeddingText.of(t.title(), t.body())).toList();
            List<float[]> vectors = embeddingService.embedAll(texts);
            repository.saveAll(batch.stream().map(IssueText::issueId).toList(), vectors, model);
            embedded += batch.size();
            log.info("Embeddings {}: {}/{} done", repo, embedded, total);
        }

        long durationMs = (System.nanoTime() - start) / 1_000_000;
        EmbeddingSummary summary = new EmbeddingSummary(repo, model, embedded, durationMs);
        log.info("Embeddings finished: {}", summary);
        return summary;
    }

    /** (Re-)embeds one issue by its natural key, e.g. after the Kafka worker stored a new or edited issue. */
    public void embedIssue(SourceType source, String externalId) {
        repository.findIssueId(source.name(), externalId).ifPresent(this::embedIssue);
    }

    /** (Re-)embeds one issue by our id. */
    public void embedIssue(long issueId) {
        repository.findText(issueId).ifPresent(text -> repository.saveAll(
                List.of(issueId),
                List.of(embeddingService.embed(EmbeddingText.of(text.title(), text.body()))),
                embeddingService.modelName()));
    }

    /**
     * @param embedded   how many issues got an embedding in this run
     * @param durationMs how long it took
     */
    public record EmbeddingSummary(String repo, String model, int embedded, long durationMs) {
    }
}
