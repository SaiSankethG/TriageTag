package com.saisanketh.triage.service;

/**
 * What one "fill duplicate_of" run did. checked = found + notFound + otherRepo + ambiguous.
 *
 * @param repo                 e.g. "spring-projects/spring-boot"
 * @param checked              issues closed or labelled as duplicates that we looked up
 * @param found                duplicate_of filled in
 * @param foundByCloseReason   ...from "close as duplicate of #N"
 * @param foundByDuplicateEvent ...from a "marked as duplicate" link
 * @param foundByComment       ...from a maintainer comment like "Duplicate of #N"
 * @param notFound             no reliable signal
 * @param otherRepo            the original is in another repo (can't store it as a number)
 * @param ambiguous            signals point at more than one issue
 * @param durationMs           how long the run took
 */
public record DuplicateSummary(String repo, int checked, int found,
                               int foundByCloseReason, int foundByDuplicateEvent, int foundByComment,
                               int notFound, int otherRepo, int ambiguous, long durationMs) {
}
