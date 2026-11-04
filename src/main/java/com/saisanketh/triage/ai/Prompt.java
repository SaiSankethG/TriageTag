package com.saisanketh.triage.ai;

import java.util.Map;

/**
 * One filled-in prompt, ready to send to the LLM.
 *
 * @param name      what it is for, e.g. "missing-details"
 * @param version   which version of the prompt file, e.g. "v1" (stored with every result, so results can be compared
 *                  across prompt changes)
 * @param text      the full text sent to the model
 * @param variables the values that were filled in (lets the fake LLM answer without parsing the text)
 */
public record Prompt(String name, String version, String text, Map<String, String> variables) {

    /** "missing-details-v1": the file name without ".txt", stored as prompt_version in reports. */
    public String id() {
        return name + "-" + version;
    }
}
