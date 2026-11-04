package com.saisanketh.triage.docs;

import com.saisanketh.triage.embedding.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocIngestServiceTest {

    private static final DocsProperties.Source SOURCE = new DocsProperties.Source("v1.0.0", "docs/modules",
            List.of("ROOT", "reference"), "https://docs.example/1.0", List.of("ROOT/redirect"));

    @Test
    void mapsPagePathsToPublishedUrls() {
        assertThat(DocIngestService.pageUrl(SOURCE, "docs/modules/reference/pages/data/sql.adoc"))
                .isEqualTo("https://docs.example/1.0/reference/data/sql.html");
        assertThat(DocIngestService.pageUrl(SOURCE, "docs/modules/ROOT/pages/index.adoc"))
                .isEqualTo("https://docs.example/1.0/index.html");
        assertThat(DocIngestService.pageUrl(SOURCE, "docs/modules/ROOT/pages/redirect.adoc")).as("excluded").isNull();
        assertThat(DocIngestService.pageUrl(SOURCE, "docs/modules/how-to/pages/x.adoc")).as("module not loaded").isNull();
        assertThat(DocIngestService.pageUrl(SOURCE, "docs/modules/reference/partials/x.adoc")).as("not a page").isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadsChunksEmbedsAndReplacesTheRepoDocs() {
        DocSource docSource = mock(DocSource.class);
        when(docSource.listFiles("o/r", "v1.0.0")).thenReturn(List.of(
                "docs/modules/reference/pages/a.adoc", "docs/modules/reference/images/x.png", "README.md"));
        when(docSource.read("o/r", "v1.0.0", "docs/modules/reference/pages/a.adoc"))
                .thenReturn("= A\n\nFirst.\n\n[[a.b]]\n== B\n\nSecond.\n");
        EmbeddingService embeddings = mock(EmbeddingService.class);
        when(embeddings.embedAll(anyList())).thenAnswer(inv -> ((List<String>) inv.getArgument(0)).stream()
                .map(t -> new float[]{1f}).toList());
        DocChunkRepository repository = mock(DocChunkRepository.class);

        DocIngestService service = new DocIngestService(docSource, repository, embeddings,
                new DocsProperties(Map.of("O/R", SOURCE)));
        DocIngestService.IngestSummary summary = service.ingest("o/r");

        assertThat(summary.pages()).isEqualTo(1);
        assertThat(summary.chunks()).isEqualTo(2);
        ArgumentCaptor<List<DocSection>> sections = ArgumentCaptor.forClass(List.class);
        verify(repository).replaceAll(eq("o/r"), eq("v1.0.0"), sections.capture(), anyList());
        assertThat(sections.getValue()).extracting(DocSection::sourceUrl)
                .containsExactly("https://docs.example/1.0/reference/a.html", "https://docs.example/1.0/reference/a.html#a.b");
        assertThatThrownBy(() -> service.ingest("x/y")).hasMessageContaining("no docs configured");
    }
}
