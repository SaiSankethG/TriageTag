package com.saisanketh.triage.embedding;

import com.saisanketh.triage.domain.SourceType;
import com.saisanketh.triage.embedding.IssueEmbeddingRepository.IssueText;
import com.saisanketh.triage.embedding.IssueEmbeddingService.EmbeddingSummary;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IssueEmbeddingServiceTest {

    /** A fake model: every text becomes [length of the text], so tests can see what was embedded. */
    static class FakeEmbeddingService implements EmbeddingService {
        final List<String> embedded = new ArrayList<>();

        @Override
        public String modelName() {
            return "fake";
        }

        @Override
        public int dimensions() {
            return 1;
        }

        @Override
        public float[] embed(String text) {
            embedded.add(text);
            return new float[]{text.length()};
        }

        @Override
        public List<float[]> embedAll(List<String> texts) {
            return texts.stream().map(this::embed).toList();
        }
    }

    private final IssueEmbeddingRepository repository = mock(IssueEmbeddingRepository.class);
    private final FakeEmbeddingService model = new FakeEmbeddingService();
    private final IssueEmbeddingService service = new IssueEmbeddingService(model, repository);

    @Test
    void embedsMissingIssuesBatchByBatchUntilNoneAreLeft() {
        when(repository.countMissing("octo-org/hello", "fake")).thenReturn(3);
        when(repository.findMissing(eq("octo-org/hello"), eq("fake"), anyInt()))
                .thenReturn(List.of(new IssueText(1, "One", "<!-- template --> body"), new IssueText(2, "Two", null)))
                .thenReturn(List.of(new IssueText(3, "Three", "x")))
                .thenReturn(List.of());

        EmbeddingSummary summary = service.embedMissing("octo-org/hello");

        assertThat(summary.embedded()).isEqualTo(3);
        assertThat(summary.model()).isEqualTo("fake");
        assertThat(model.embedded).containsExactly("One body", "Two", "Three x");   // cleaned text, template removed
        verify(repository).saveAll(eq(List.of(1L, 2L)), org.mockito.ArgumentMatchers.anyList(), eq("fake"));
        verify(repository).saveAll(eq(List.of(3L)), org.mockito.ArgumentMatchers.anyList(), eq("fake"));
    }

    @Test
    void embedsOneIssueByItsKey() {
        when(repository.findIssueId("GITHUB", "1001")).thenReturn(Optional.of(7L));
        when(repository.findText(7L)).thenReturn(Optional.of(new IssueText(7, "Title", "Body")));

        service.embedIssue(SourceType.GITHUB, "1001");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<float[]>> vectors = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(eq(List.of(7L)), vectors.capture(), eq("fake"));
        assertThat(vectors.getValue().get(0)).containsExactly("Title Body".length());
    }

    @Test
    void anUnknownIssueIsSkipped() {
        when(repository.findIssueId("GITHUB", "nope")).thenReturn(Optional.empty());

        service.embedIssue(SourceType.GITHUB, "nope");

        verify(repository, never()).saveAll(org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyString());
    }
}
