package com.saisanketh.triage.ai;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

/** Reads an LLM answer that must be ONE JSON object. Shared by every prompt that asks for JSON. */
final class StrictJson {

    private StrictJson() {
    }

    /**
     * The answer as a JSON object, or empty if it isn't one. The only leniency: a Markdown code fence around it
     * (```json ... ```) is removed, because models often add one even when told not to. Any other text = invalid.
     */
    static Optional<JsonNode> object(JsonMapper mapper, String answer) {
        if (answer == null) {
            return Optional.empty();
        }
        String text = answer.strip();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline < 0 || !text.endsWith("```")) {
                return Optional.empty();
            }
            text = text.substring(firstNewline + 1, text.length() - 3).strip();
        }
        try {
            JsonNode node = mapper.readTree(text);
            return node != null && node.isObject() ? Optional.of(node) : Optional.empty();
        } catch (JacksonException e) {
            return Optional.empty();   // not JSON, or JSON followed by more text
        }
    }
}
