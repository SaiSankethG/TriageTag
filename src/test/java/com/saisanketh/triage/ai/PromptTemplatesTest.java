package com.saisanketh.triage.ai;

import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Uses src/test/resources/prompts/greeting-v1.txt. */
class PromptTemplatesTest {

    private final PromptTemplates templates = new PromptTemplates();

    @Test
    void fillsEveryPlaceholderAndKeepsNameAndVersion() {
        Prompt prompt = templates.render("greeting", "v1", Map.of("name", "sai", "place", "Hyderabad"));

        assertThat(prompt.text()).isEqualTo("Say hello to sai.\nAgain: sai, from Hyderabad.\n");
        assertThat(prompt.id()).isEqualTo("greeting-v1");
        assertThat(prompt.variables()).containsEntry("name", "sai");
    }

    @Test
    void valuesAreInsertedAsTheyAreEvenWithDollarSignsAndBraces() {
        Prompt prompt = templates.render("greeting", "v1", Map.of("name", "$1 {{place}}", "place", "x"));

        assertThat(prompt.text()).startsWith("Say hello to $1 {{place}}.");
    }

    @Test
    void aMissingValueIsAnError() {
        assertThatThrownBy(() -> templates.render("greeting", "v1", Map.of("name", "sai")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("{{place}}");
    }

    @Test
    void anUnusedValueIsAnError() {
        assertThatThrownBy(() -> templates.render("greeting", "v1", Map.of("name", "a", "place", "b", "typo", "c")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("typo");
    }

    @Test
    void anUnknownPromptFileIsAnError() {
        assertThatThrownBy(() -> templates.render("nope", "v9", Map.of()))
                .isInstanceOf(UncheckedIOException.class).hasMessageContaining("prompts/nope-v9.txt");
    }
}
