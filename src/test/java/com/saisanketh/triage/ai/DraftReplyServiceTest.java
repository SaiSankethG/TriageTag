package com.saisanketh.triage.ai;

import com.saisanketh.triage.ai.DraftReply.Source;
import com.saisanketh.triage.ai.DraftReply.Status;
import com.saisanketh.triage.docs.DocChunkRepository.DocMatch;
import com.saisanketh.triage.docs.DocIngestService;
import com.saisanketh.triage.triage.SimilarIssue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DraftReplyServiceTest {

    private static final IssueText ISSUE = new IssueText(50, "Embedded H2 not shut down between tests", "Body");
    private static final DocMatch GOOD_DOC = new DocMatch("https://docs.example/sql.html#embedded",
            "SQL Databases > Embedded Database Support", "Use DB_CLOSE_ON_EXIT=FALSE.", 0.7);
    private static final DocMatch WEAK_DOC = new DocMatch("https://docs.example/banner.html", "Banner", "...", 0.3);

    private final DocIngestService docs = mock(DocIngestService.class);

    private DraftReplyService service(TriageLlm llm) {
        return new DraftReplyService(llm, new PromptTemplates(), docs, 0.55, 0.75);
    }

    @Test
    void withoutRelevantSourcesThereIsNoLlmCallAndNoDraft() {
        when(docs.search(anyString(), anyString(), anyInt())).thenReturn(List.of(WEAK_DOC));
        ScriptedTriageLlm llm = new ScriptedTriageLlm();

        DraftReply reply = service(llm).draft("o/r", ISSUE, List.of(new SimilarIssue(7, "Other", 0.6)));

        assertThat(reply.status()).isEqualTo(Status.NOTHING_RELEVANT);
        assertThat(reply.sources()).isEmpty();
        assertThat(llm.prompts).isEmpty();
    }

    @Test
    void onlySourcesAboveTheThresholdsGoIntoThePrompt() {
        when(docs.search(anyString(), anyString(), anyInt())).thenReturn(List.of(GOOD_DOC, WEAK_DOC));
        ScriptedTriageLlm llm = new ScriptedTriageLlm("See https://docs.example/sql.html#embedded.");

        DraftReply reply = service(llm).draft("o/r", ISSUE,
                List.of(new SimilarIssue(7, "H2 closed early", 0.8), new SimilarIssue(8, "Weak", 0.5)));

        assertThat(reply.status()).isEqualTo(Status.OK);
        assertThat(reply.sources()).extracting(Source::url)
                .containsExactly("https://docs.example/sql.html#embedded", "https://github.com/o/r/issues/7");
        assertThat(reply.unknownLinks()).isEmpty();
        assertThat(reply.promptVersion()).isEqualTo("draft-reply-v1");
        assertThat(llm.prompts.get(0).variables().get("sources"))
                .contains("[1] (docs) SQL Databases > Embedded Database Support (https://docs.example/sql.html#embedded)",
                        "Use DB_CLOSE_ON_EXIT=FALSE.", "[2] (issue) #7 H2 closed early (https://github.com/o/r/issues/7)")
                .doesNotContain("Banner", "Weak");
    }

    @Test
    void linksThatAreNotSourcesAreReported() {
        List<Source> sources = List.of(new Source("docs", "t", "https://docs.example/a.html", 0.7));

        assertThat(DraftReplyService.unknownLinks("See https://docs.example/a.html, and https://made.up/x.", sources))
                .containsExactly("https://made.up/x");
    }

    @Test
    void aFailedLlmCallGivesUnknownButKeepsTheSources() {
        when(docs.search(anyString(), anyString(), anyInt())).thenReturn(List.of(GOOD_DOC));

        DraftReply reply = service(new ScriptedTriageLlm(new LlmException("over budget"))).draft("o/r", ISSUE, List.of());

        assertThat(reply.status()).isEqualTo(Status.UNKNOWN);
        assertThat(reply.text()).contains("over budget");
        assertThat(reply.sources()).hasSize(1);
    }

    @Test
    void theFakeLlmCitesExactlyTheSources() {
        when(docs.search(anyString(), anyString(), anyInt())).thenReturn(List.of(GOOD_DOC));

        DraftReply reply = service(new FakeTriageLlm()).draft("o/r", ISSUE, List.of(new SimilarIssue(7, "H2", 0.9)));

        assertThat(reply.text()).contains("https://docs.example/sql.html#embedded", "https://github.com/o/r/issues/7");
        assertThat(reply.unknownLinks()).isEmpty();
    }
}
