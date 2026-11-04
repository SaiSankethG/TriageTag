package com.saisanketh.triage.web;

import com.saisanketh.triage.docs.DocChunkRepository.DocMatch;
import com.saisanketh.triage.docs.DocIngestService;
import com.saisanketh.triage.docs.DocIngestService.IngestSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Loads a repo's reference docs (for draft replies), and searches them. */
@RestController
@RequiredArgsConstructor
public class DocsController {

    private final DocIngestService docs;

    /** Example: {@code curl -X POST localhost:8080/docs/spring-projects/spring-boot} (replaces the stored docs) */
    @PostMapping("/docs/{owner}/{repo}")
    public IngestSummary ingest(@PathVariable String owner, @PathVariable String repo) {
        return docs.ingest(owner + "/" + repo);
    }

    /** Example: {@code curl "localhost:8080/docs/spring-projects/spring-boot/search?q=datasource+url"} */
    @GetMapping("/docs/{owner}/{repo}/search")
    public List<DocMatch> search(@PathVariable String owner, @PathVariable String repo, @RequestParam String q,
                                 @RequestParam(defaultValue = "5") int limit) {
        return docs.search(owner + "/" + repo, q, Math.min(limit, 20));
    }
}
