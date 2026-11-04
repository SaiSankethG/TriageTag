package com.saisanketh.triage.source;

/**
 * Finds out which issue a duplicate issue is a duplicate of, from the tracker's own records.
 * This is the "answer key" for measuring duplicate detection later.
 */
public interface DuplicateSource {

    /**
     * @param repo   the repo as "owner/name"
     * @param number the issue number of an issue that was closed or labelled as a duplicate
     * @return FOUND with the original's number only when the tracker's data makes it clear
     */
    DuplicateLink findOriginal(String repo, int number);
}
