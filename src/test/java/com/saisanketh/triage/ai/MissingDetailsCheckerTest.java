package com.saisanketh.triage.ai;

import com.saisanketh.triage.ai.MissingDetails.Answer;
import com.saisanketh.triage.ai.MissingDetails.Detail;
import com.saisanketh.triage.ai.MissingDetails.Status;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class MissingDetailsCheckerTest {

    static final String GOOD = """
            {"version": "present", "steps_to_reproduce": "missing", "expected_vs_actual": "missing", "logs_or_stack_trace": "not_applicable"}""";

    private static MissingDetailsChecker checker(TriageLlm llm) {
        return new MissingDetailsChecker(llm, new PromptTemplates(), JsonMapper.builder().build());
    }

    private final MissingDetailsChecker parser = checker(new ScriptedTriageLlm());

    // --- parsing ---

    @Test
    void parsesAValidAnswer() {
        assertThat(parser.parse(GOOD)).hasValueSatisfying(m -> {
            assertThat(m).containsEntry(Detail.VERSION, Answer.PRESENT);
            assertThat(m).containsEntry(Detail.LOGS_OR_STACK_TRACE, Answer.NOT_APPLICABLE);
        });
    }

    @Test
    void acceptsAMarkdownCodeFenceAroundTheJson() {
        assertThat(parser.parse("```json\n" + GOOD + "\n```")).isPresent();
    }

    @Test
    void rejectsEverythingElse() {
        assertThat(parser.parse("Sure! Here is the JSON: " + GOOD)).as("chatter before").isEmpty();
        assertThat(parser.parse(GOOD + " Hope this helps")).as("chatter after").isEmpty();
        assertThat(parser.parse(GOOD.replace("\"version\": \"present\", ", ""))).as("missing key").isEmpty();
        assertThat(parser.parse(GOOD.replace("}", ", \"extra\": \"present\"}"))).as("extra key").isEmpty();
        assertThat(parser.parse(GOOD.replace("\"present\"", "\"yes\""))).as("bad value").isEmpty();
        assertThat(parser.parse(GOOD.replace("\"present\"", "true"))).as("not a string").isEmpty();
        assertThat(parser.parse("[" + GOOD + "]")).as("array").isEmpty();
        assertThat(parser.parse("")).as("empty").isEmpty();
        assertThat(parser.parse(null)).as("null").isEmpty();
    }

    // --- calling the LLM ---

    @Test
    void aValidFirstAnswerListsWhatIsMissing() {
        ScriptedTriageLlm llm = new ScriptedTriageLlm(GOOD);

        MissingDetails result = checker(llm).check("octo-org/hello", "Crash on start", "It crashes.");

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.missing()).containsExactly(Detail.STEPS_TO_REPRODUCE, Detail.EXPECTED_VS_ACTUAL);
        assertThat(result.promptVersion()).isEqualTo("missing-details-v1");
        assertThat(result.tokens()).isEqualTo(110);
        assertThat(llm.prompts.get(0).text()).contains("<title>Crash on start</title>", "It crashes.", "octo-org/hello");
    }

    @Test
    void aBadAnswerIsRetriedOnce() {
        ScriptedTriageLlm llm = new ScriptedTriageLlm("I think the version is missing.", GOOD);

        MissingDetails result = checker(llm).check("o/r", "t", "b");

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.tokens()).isEqualTo(220);   // both calls count
        assertThat(llm.prompts).hasSize(2);
    }

    @Test
    void twoBadAnswersGiveUnknownNotAGuess() {
        MissingDetails result = checker(new ScriptedTriageLlm("nope", "{\"version\": \"present\"}")).check("o/r", "t", "b");

        assertThat(result.status()).isEqualTo(Status.UNKNOWN);
        assertThat(result.details()).isEmpty();
        assertThat(result.note()).contains("invalid JSON twice");
    }

    @Test
    void aFailedCallGivesUnknownWithTheReason() {
        MissingDetails result = checker(new ScriptedTriageLlm(new LlmException("timeout"))).check("o/r", "t", null);

        assertThat(result.status()).isEqualTo(Status.UNKNOWN);
        assertThat(result.note()).contains("timeout");
    }

    @Test
    void longBodiesAreCut() {
        String body = "x".repeat(MissingDetailsChecker.MAX_BODY_CHARS + 100);
        assertThat(MissingDetailsChecker.truncate(body)).hasSize(MissingDetailsChecker.MAX_BODY_CHARS + "\n[... cut ...]".length());
        assertThat(MissingDetailsChecker.truncate("short")).isEqualTo("short");
    }

    @Test
    void theFakeLlmGivesAValidAnswerThroughTheRealPrompt() {
        MissingDetails result = checker(new FakeTriageLlm()).check("o/r", "NPE in 3.4.1",
                "Steps: run the app. Expected it to start.\njava.lang.NullPointerException\n    at com.example.App.main(App.java:3)");

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.missing()).isEmpty();
        assertThat(checker(new FakeTriageLlm()).check("o/r", "Add a feature", "Please add it").missing())
                .contains(Detail.VERSION, Detail.LOGS_OR_STACK_TRACE);
    }
}
