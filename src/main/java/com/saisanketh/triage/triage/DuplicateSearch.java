package com.saisanketh.triage.triage;

import java.util.List;

/**
 * Finds earlier issues that look like a given issue. Two implementations: keyword (Postgres full-text,
 * the baseline) and vector (embeddings + pgvector). Both follow the same rule, so they can be compared fairly:
 * only issues of the SAME repo, created BEFORE the query issue (what a maintainer could have seen at that moment),
 * minus the repo's configured excluded labels (bookkeeping copies, see {@link CandidateFilter}).
 */
public interface DuplicateSearch {

    /** Short name for reports, e.g. "keyword" or "vector". */
    String name();

    /** Like {@link #findSimilar(long, int, boolean)} with the repo's candidate filter on (the normal case). */
    default List<SimilarIssue> findSimilar(long issueId, int limit) {
        return findSimilar(issueId, limit, true);
    }

    /**
     * @param issueId              our id of the query issue (it must already be stored)
     * @param limit                how many results at most
     * @param applyCandidateFilter true: leave out issues with the repo's excluded labels (see {@link CandidateFilter});
     *                             false: every earlier issue is a candidate (the evaluation reports both)
     * @return most similar first; empty if nothing matches
     */
    List<SimilarIssue> findSimilar(long issueId, int limit, boolean applyCandidateFilter);
}
