package com.saisanketh.triage.ai;

/**
 * The LLM's verdict on "is the new issue the same root problem as this earlier one?".
 *
 * @param verdict       YES / NO / UNSURE from the model; UNKNOWN if it gave no valid answer (twice) or the call failed
 * @param reason        the model's one-sentence reason, or why the verdict is UNKNOWN
 * @param promptVersion e.g. "duplicate-check-v1"
 * @param tokens        tokens used, retries included
 */
public record DuplicateCheck(Verdict verdict, String reason, String promptVersion, int tokens) {

    public enum Verdict { YES, NO, UNSURE, UNKNOWN }

    /** Only a clear "yes" counts as confirmed (e.g. for posting a public comment). */
    public boolean confirmed() {
        return verdict == Verdict.YES;
    }
}
