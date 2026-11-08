package com.saisanketh.triage.docs;

import com.saisanketh.triage.docs.DocChunkRepository.DocMatch;
import com.saisanketh.triage.embedding.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Loads a repo's reference docs (AsciiDoc at a fixed tag), cuts them into sections, embeds them and stores them
 * in doc_chunk; and finds the sections nearest to a text. Run again to reload (the old chunks are replaced).
 */
@Slf4j
@Service
@EnableConfigurationProperties(DocsProperties.class)
public class DocIngestService {

    static final int BATCH = 64;

    public record IngestSummary(String repo, String tag, int pages, int chunks, long durationMs) {
    }

    private final DocSource docSource;
    private final DocChunkRepository chunks;
    private final EmbeddingService embeddingService;
    /** lower-case repo -> where its docs are */
    private final Map<String, DocsProperties.Source> sources;

    public DocIngestService(DocSource docSource, DocChunkRepository chunks, EmbeddingService embeddingService,
                            DocsProperties properties) {
        this.docSource = docSource;
        this.chunks = chunks;
        this.embeddingService = embeddingService;
        this.sources = properties.sources().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue));
    }

    /** True if application.yml says where this repo's reference docs are (triage.docs.sources). */
    public boolean hasDocs(String repo) {
        return sources.containsKey(repo.toLowerCase(Locale.ROOT));
    }

    public IngestSummary ingest(String repo) {
        long start = System.nanoTime();
        DocsProperties.Source source = sources.get(repo.toLowerCase(Locale.ROOT));
        if (source == null) {
            throw new IllegalArgumentException("no docs configured for " + repo + " (triage.docs.sources)");
        }

        List<DocSection> sections = new ArrayList<>();
        int pages = 0;
        for (String path : docSource.listFiles(repo, source.tag())) {
            String pageUrl = pageUrl(source, path);
            if (pageUrl != null) {
                sections.addAll(AsciidocChunker.chunk(pageUrl, docSource.read(repo, source.tag(), path)));
                pages++;
            }
        }
        log.info("Docs {}@{}: {} pages, {} sections; embedding...", repo, source.tag(), pages, sections.size());

        List<float[]> vectors = new ArrayList<>();
        for (int i = 0; i < sections.size(); i += BATCH) {
            vectors.addAll(embeddingService.embedAll(sections.subList(i, Math.min(sections.size(), i + BATCH)).stream()
                    .map(DocIngestService::embeddingText).toList()));
        }
        chunks.replaceAll(repo, source.tag(), sections, vectors);

        IngestSummary summary = new IngestSummary(repo, source.tag(), pages, sections.size(),
                (System.nanoTime() - start) / 1_000_000);
        log.info("Docs loaded: {}", summary);
        return summary;
    }

    /** The sections nearest to a text (e.g. a new issue's title + body), most similar first. */
    public List<DocMatch> search(String repo, String text, int limit) {
        return chunks.nearest(repo, embeddingService.embed(text), limit);
    }

    /** The heading goes first: it is short, specific, and always inside the model's 256-word-piece window. */
    static String embeddingText(DocSection s) {
        return s.heading() + "\n" + s.text();
    }

    /**
     * {root}/{module}/pages/{p}.adoc -> {siteUrl}/{module}/{p}.html ("ROOT" has no module folder in the URL).
     * Null for files that aren't pages of a configured module (images, partials, other modules...) or are excluded.
     */
    static String pageUrl(DocsProperties.Source source, String path) {
        for (String module : source.modules()) {
            String prefix = source.root() + "/" + module + "/pages/";
            if (path.startsWith(prefix) && path.endsWith(".adoc")) {
                String page = path.substring(prefix.length(), path.length() - ".adoc".length());
                if (source.excludePages().contains(module + "/" + page)) {
                    return null;
                }
                return source.siteUrl() + "/" + ("ROOT".equals(module) ? "" : module + "/") + page + ".html";
            }
        }
        return null;
    }
}
