package com.saisanketh.triage.source;

/**
 * The answer to "which issue is this one a duplicate of?".
 *
 * @param outcome  what we could find out
 * @param original the original issue's number (same repo); only set when outcome is FOUND
 * @param evidence where the answer came from; only set when outcome is FOUND
 */
public record DuplicateLink(Outcome outcome, Integer original, Evidence evidence) {

    public enum Outcome {
        /** Exactly one original in the same repo: safe to store. */
        FOUND,
        /** No reliable signal (e.g. only "see #123", or nothing at all). */
        NOT_FOUND,
        /** The original is in another repo, so it can't be stored as a same-repo issue number. */
        OTHER_REPO,
        /** Signals point at more than one issue, so we can't be sure which. */
        AMBIGUOUS
    }

    public enum Evidence {
        /** The issue was closed with "close as duplicate of #N" (structured, most reliable). */
        CLOSE_REASON,
        /** The tracker recorded a "marked as duplicate" link between the two issues. */
        DUPLICATE_EVENT,
        /** A maintainer wrote e.g. "Duplicate of #N" in a comment. */
        COMMENT
    }

    public static DuplicateLink found(int original, Evidence evidence) {
        return new DuplicateLink(Outcome.FOUND, original, evidence);
    }

    public static DuplicateLink notFound() {
        return new DuplicateLink(Outcome.NOT_FOUND, null, null);
    }

    public static DuplicateLink otherRepo() {
        return new DuplicateLink(Outcome.OTHER_REPO, null, null);
    }

    public static DuplicateLink ambiguous() {
        return new DuplicateLink(Outcome.AMBIGUOUS, null, null);
    }
}
