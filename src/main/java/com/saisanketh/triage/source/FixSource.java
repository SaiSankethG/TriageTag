package com.saisanketh.triage.source;

/** Finds out what closed an issue and in which version the fix shipped. */
public interface FixSource {

    /**
     * @return what the tracker knows; {@link FixInfo#none()} if the issue doesn't exist (any more)
     * @throws IllegalStateException if the tracker can't be asked (e.g. no token)
     */
    FixInfo findFix(String repo, int number);
}
