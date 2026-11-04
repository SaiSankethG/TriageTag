package com.saisanketh.triage.embedding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingTextTest {

    @Test
    void titleAndBodyWithIssueTemplateCommentsRemoved() {
        String body = """
                <!--
                Thanks for raising a Spring Boot issue. Please include your version.
                -->
                The app fails to start when port 8080 is used.
                <!-- Steps to reproduce: -->
                Run it twice.""";

        assertThat(EmbeddingText.of("Startup fails", body))
                .isEqualTo("Startup fails The app fails to start when port 8080 is used. Run it twice.");
    }

    @Test
    void onlyTheFirst2000CharactersOfTheBodyAreUsed() {
        String longBody = "a".repeat(5_000);

        assertThat(EmbeddingText.of("T", longBody)).hasSize("T ".length() + 2_000);
    }

    @Test
    void aMissingBodyIsFine() {
        assertThat(EmbeddingText.of("Only a title", null)).isEqualTo("Only a title");
    }
}
