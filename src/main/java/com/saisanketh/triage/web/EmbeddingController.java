package com.saisanketh.triage.web;

import com.saisanketh.triage.embedding.IssueEmbeddingService;
import com.saisanketh.triage.embedding.IssueEmbeddingService.EmbeddingSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Batch job: embed every stored issue of a repo that has no embedding yet. Safe to run again (only missing rows). */
@RestController
@RequiredArgsConstructor
public class EmbeddingController {

    private final IssueEmbeddingService embeddingService;

    /** Example: {@code curl -X POST localhost:8080/embeddings/spring-projects/spring-boot} */
    @PostMapping("/embeddings/{owner}/{repo}")
    public EmbeddingSummary embedMissing(@PathVariable String owner, @PathVariable String repo) {
        return embeddingService.embedMissing(owner + "/" + repo);
    }
}
