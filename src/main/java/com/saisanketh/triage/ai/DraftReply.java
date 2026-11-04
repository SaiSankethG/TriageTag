package com.saisanketh.triage.ai;

import java.util.List;

/**
 * A draft first reply for the maintainer, written by the LLM from retrieved sources (RAG).
 *
 * @param status        OK; NOTHING_RELEVANT (no source scored above the thresholds: no draft, instead of a guess);
 *                      UNKNOWN (the LLM call failed or was skipped)
 * @param text          the draft, or a short explanation when there is none
 * @param sources       what the LLM was given, best first
 * @param unknownLinks  links in the draft that are NOT among the sources (should be empty; shown as a warning)
 * @param promptVersion e.g. "draft-reply-v1"; null when the LLM wasn't called
 * @param tokens        tokens used
 */
public record DraftReply(Status status, String text, List<Source> sources, List<String> unknownLinks,
                         String promptVersion, int tokens) {

    public enum Status { OK, NOTHING_RELEVANT, UNKNOWN }

    /** @param kind "docs" or "issue"; @param score cosine similarity to the new issue */
    public record Source(String kind, String title, String url, double score) {
    }
}
