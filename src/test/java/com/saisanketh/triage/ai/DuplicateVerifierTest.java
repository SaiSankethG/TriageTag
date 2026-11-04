package com.saisanketh.triage.ai;

import com.saisanketh.triage.ai.DuplicateCheck.Verdict;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class DuplicateVerifierTest {

    private static final IssueText NEW = new IssueText(20, "Datasource url not picked up from yaml", "Body of 20");
    private static final IssueText OLD = new IssueText(10, "Datasource url not picked up", null);

    private static DuplicateVerifier verifier(TriageLlm llm) {
        return new DuplicateVerifier(llm, new PromptTemplates(), JsonMapper.builder().build());
    }

    private final DuplicateVerifier parser = verifier(new ScriptedTriageLlm());

    @Test
    void parsesTheThreeVerdicts() {
        assertThat(parser.parse("{\"same_problem\": \"yes\", \"reason\": \"same NPE\"}", "p"))
                .hasValueSatisfying(c -> {
                    assertThat(c.verdict()).isEqualTo(Verdict.YES);
                    assertThat(c.reason()).isEqualTo("same NPE");
                    assertThat(c.confirmed()).isTrue();
                });
        assertThat(parser.parse("{\"same_problem\": \"no\", \"reason\": \"r\"}", "p").get().verdict()).isEqualTo(Verdict.NO);
        assertThat(parser.parse("```\n{\"same_problem\": \"unsure\", \"reason\": \"r\"}\n```", "p").get().confirmed()).isFalse();
    }

    @Test
    void rejectsAnythingElse() {
        assertThat(parser.parse("{\"same_problem\": \"maybe\", \"reason\": \"r\"}", "p")).as("bad verdict").isEmpty();
        assertThat(parser.parse("{\"same_problem\": true, \"reason\": \"r\"}", "p")).as("boolean").isEmpty();
        assertThat(parser.parse("{\"same_problem\": \"yes\"}", "p")).as("no reason").isEmpty();
        assertThat(parser.parse("{\"same_problem\": \"yes\", \"reason\": \"r\", \"score\": 0.9}", "p")).as("extra").isEmpty();
        assertThat(parser.parse("yes", "p")).as("not JSON").isEmpty();
    }

    @Test
    void sendsBothIssuesAndRetriesABadAnswerOnce() {
        ScriptedTriageLlm llm = new ScriptedTriageLlm("Yes, they are the same.",
                "{\"same_problem\": \"yes\", \"reason\": \"both about spring.datasource.url\"}");

        DuplicateCheck check = verifier(llm).check("octo-org/hello", NEW, OLD);

        assertThat(check.verdict()).isEqualTo(Verdict.YES);
        assertThat(check.tokens()).isEqualTo(220);
        assertThat(check.promptVersion()).isEqualTo("duplicate-check-v1");
        assertThat(llm.prompts.get(0).text())
                .contains("<new_issue number=\"20\">", "Body of 20", "<earlier_issue number=\"10\">", "(empty)");
    }

    @Test
    void noValidAnswerOrAFailedCallIsUnknownAndNotConfirmed() {
        assertThat(verifier(new ScriptedTriageLlm("a", "b")).check("o/r", NEW, OLD).verdict()).isEqualTo(Verdict.UNKNOWN);
        DuplicateCheck failed = verifier(new ScriptedTriageLlm(new LlmException("down"))).check("o/r", NEW, OLD);
        assertThat(failed.verdict()).isEqualTo(Verdict.UNKNOWN);
        assertThat(failed.confirmed()).isFalse();
        assertThat(failed.reason()).contains("down");
    }

    @Test
    void theFakeLlmComparesTitleWords() {
        DuplicateVerifier fake = verifier(new FakeTriageLlm());
        assertThat(fake.check("o/r", NEW, OLD).verdict()).isEqualTo(Verdict.YES);   // 4 of 6 words shared
        assertThat(fake.check("o/r", NEW, new IssueText(1, "Banner colours", "")).verdict()).isEqualTo(Verdict.NO);
    }
}
