package com.saisanketh.triage.ai;

import java.util.List;
import java.util.Map;

/**
 * Which of the four details a good bug report needs the reporter gave.
 *
 * @param status        OK = the LLM answered with valid JSON; UNKNOWN = it didn't (twice), or the call failed or was skipped
 * @param details       detail -> present / missing / not_applicable (empty when UNKNOWN)
 * @param missing       just the missing ones, in a fixed order: what a maintainer would ask the reporter for
 * @param note          why the status is UNKNOWN; null when OK
 * @param promptVersion e.g. "missing-details-v1"
 * @param tokens        tokens used, retries included
 */
public record MissingDetails(Status status, Map<Detail, Answer> details, List<Detail> missing, String note,
                             String promptVersion, int tokens) {

    public enum Status { OK, UNKNOWN }

    /** The JSON keys, in the order we show them. */
    public enum Detail {
        VERSION("version"),
        STEPS_TO_REPRODUCE("steps_to_reproduce"),
        EXPECTED_VS_ACTUAL("expected_vs_actual"),
        LOGS_OR_STACK_TRACE("logs_or_stack_trace");

        public final String key;

        Detail(String key) {
            this.key = key;
        }
    }

    public enum Answer {
        PRESENT("present"), MISSING("missing"), NOT_APPLICABLE("not_applicable");

        public final String key;

        Answer(String key) {
            this.key = key;
        }
    }

    static MissingDetails unknown(String note, String promptVersion, int tokens) {
        return new MissingDetails(Status.UNKNOWN, Map.of(), List.of(), note, promptVersion, tokens);
    }
}
