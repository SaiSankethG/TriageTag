package com.saisanketh.triage.triage;

/**
 * One candidate "this might be a duplicate of" result.
 *
 * @param number the earlier issue's number in the same repo
 * @param title  its title
 * @param score  how similar: higher is more similar. The scale depends on the search
 *               (keyword: ts_rank, roughly 0-1; vector: cosine similarity, -1 to 1)
 */
public record SimilarIssue(int number, String title, double score) {
}
