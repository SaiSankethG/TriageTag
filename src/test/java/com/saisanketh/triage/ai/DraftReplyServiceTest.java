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
        return new DraftReplyService(llm, new PromptTemplates(), docs, 0.55, 0.75, 0.75, 0.90);
    }

    @Test
    void withoutRelevantSourcesThereIsNoLlmCallAndNoDraft() {
        when(docs.search(anyString(), anyString(), anyInt())).thenReturn(List.of(WEAK_DOC));
        ScriptedTriageLlm llm = new ScriptedTriageLlm();

        DraftReply reply = service(llm).draft("o/r", ISSUE, List.of(new SimilarIssue(7, "Other", 0.6)), null, null);

        assertThat(reply.status()).isEqualTo(Status.NOTHING_RELEVANT);
        assertThat(reply.sources()).isEmpty();
        assertThat(llm.prompts).isEmpty();
    }

    @Test
    void onlySourcesAboveTheThresholdsGoIntoThePrompt() {
        when(docs.search(anyString(), anyString(), anyInt())).thenReturn(List.of(GOOD_DOC, WEAK_DOC));
        ScriptedTriageLlm llm = new ScriptedTriageLlm("See https://docs.example/sql.html#embedded.");

        DraftReply reply = service(llm).draft("o/r", ISSUE,
                List.of(new SimilarIssue(7, "H2 closed early", 0.8), new SimilarIssue(8, "Weak", 0.5)), null, null);

        assertThat(reply.status()).isEqualTo(Status.OK);
        assertThat(reply.sources()).extracting(Source::url)
                .containsExactly("https://docs.example/sql.html#embedded", "https://github.com/o/r/issues/7");
        assertThat(reply.unknownLinks()).isEmpty();
        assertThat(reply.promptVersion()).isEqualTo("draft-reply-v3");
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

        DraftReply reply = service(new ScriptedTriageLlm(new LlmException("over budget"))).draft("o/r", ISSUE, List.of(), null, null);

        assertThat(reply.status()).isEqualTo(Status.UNKNOWN);
        assertThat(reply.text()).contains("over budget");
        assertThat(reply.sources()).hasSize(1);
    }

    @Test
    void theFakeLlmCitesExactlyTheSources() {
        when(docs.search(anyString(), anyString(), anyInt())).thenReturn(List.of(GOOD_DOC));

        DraftReply reply = service(new FakeTriageLlm()).draft("o/r", ISSUE, List.of(new SimilarIssue(7, "H2", 0.9)), null, null);

        assertThat(reply.text()).contains("https://docs.example/sql.html#embedded", "https://github.com/o/r/issues/7");
        assertThat(reply.unknownLinks()).isEmpty();
    }

    // --- prompt v3 inputs ---

    private static MissingDetails missing(MissingDetails.Detail... details) {
        return new MissingDetails(MissingDetails.Status.OK, java.util.Map.of(), List.of(details), null, "missing-details-v1", 0);
    }

    @Test
    void v3TellsTheModelAboutTheMediumMatchItsStateAndOnlyTheMissingDetails() {
        when(docs.search(anyString(), anyString(), anyInt())).thenReturn(List.of(GOOD_DOC));
        ScriptedTriageLlm llm = new ScriptedTriageLlm("ok");
        DraftReplyService.TopMatch top = new DraftReplyService.TopMatch(52519, "Support MockServerConnectionDetails",
                "closed", "not_planned", 0.807, null);

        service(llm).draft("spring-projects/spring-boot", ISSUE, List.of(new SimilarIssue(52519, "Support MockServerConnectionDetails", 0.807)),
                top, missing(MissingDetails.Detail.VERSION));

        Prompt prompt = llm.prompts.get(0);
        assertThat(prompt.variables().get("top_match")).contains("#52519", "Support MockServerConnectionDetails",
                "https://github.com/spring-projects/spring-boot/issues/52519", "80.7% (medium)",
                "closed as not planned", "AI duplicate check: not run");
        assertThat(prompt.variables().get("missing")).isEqualTo("Ask only for: version.");
        assertThat(prompt.text()).contains("Never mention sources", "This looks like #N");
    }

    @Test
    void v3SaysNoneForALowMatchAndAsksForNothingWhenNothingIsMissingOrUnknown() {
        DraftReplyService service = service(new ScriptedTriageLlm());
        DraftReplyService.TopMatch low = new DraftReplyService.TopMatch(7, "Other", "open", null, 0.6, null);
        DraftReplyService.TopMatch high = new DraftReplyService.TopMatch(7, "Same", "open", null, 0.95,
                new DuplicateCheck(DuplicateCheck.Verdict.YES, "same NPE", "duplicate-check-v1", 10));

        assertThat(service.topMatchText("o/r", low)).startsWith("None");
        assertThat(service.topMatchText("o/r", null)).startsWith("None");
        assertThat(service.topMatchText("o/r", high)).contains("95.0% (high)", "still open", "yes: same NPE");
        assertThat(DraftReplyService.missingText(missing())).startsWith("None").contains("Do not ask");
        assertThat(DraftReplyService.missingText(null)).startsWith("Unknown").contains("Do not ask");
        assertThat(DraftReplyService.stateText("closed", "completed")).isEqualTo("closed as completed (fixed)");
    }

    @Test
    void theFakeLlmLeadsWithTheMatchAndAsksOnlyForWhatIsMissing() {
        when(docs.search(anyString(), anyString(), anyInt())).thenReturn(List.of(GOOD_DOC));
        DraftReplyService.TopMatch top = new DraftReplyService.TopMatch(7, "H2 closed early", "closed", "completed", 0.8, null);

        DraftReply reply = service(new FakeTriageLlm()).draft("o/r", ISSUE, List.of(new SimilarIssue(7, "H2 closed early", 0.8)),
                top, missing(MissingDetails.Detail.STEPS_TO_REPRODUCE));

        assertThat(reply.text()).startsWith("This looks like #7").contains("Could you add: steps to reproduce");
        assertThat(reply.unknownLinks()).isEmpty();
    }
}
