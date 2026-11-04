package com.saisanketh.triage.triage;

import java.util.List;

/**
 * Finds earlier issues that look like a given issue. Two implementations: keyword (Postgres full-text,
 * the baseline) and vector (embeddings + pgvector). Both follow the same rule, so they can be compared fairly:
 * only issues of the SAME repo, created BEFORE the query issue (what a maintainer could have seen at that moment).
 */
public interface DuplicateSearch {

    /** Short name for reports, e.g. "keyword" or "vector". */
    String name();

    /**
     * @param issueId our id of the query issue (it must already be stored)
     * @param limit   how many results at most
     * @return most similar first; empty if nothing matches
     */
    List<SimilarIssue> findSimilar(long issueId, int limit);
}
