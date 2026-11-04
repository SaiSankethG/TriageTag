package com.saisanketh.triage.service;

/**
 * What one backfill run did. Returned as JSON by the backfill endpoint.
 *
 * @param repo       e.g. "spring-projects/spring-boot"
 * @param pages      pages fetched from the tracker
 * @param inserted   issues we didn't have before
 * @param updated    issues we already had (row overwritten with the newer data)
 * @param stale      issues where we already had newer data (e.g. from a webhook), so nothing changed
 * @param skipped    items the tracker sent that are not issues (for GitHub: pull requests)
 * @param durationMs how long the whole run took, in milliseconds
 */
public record BackfillSummary(String repo, int pages, int inserted, int updated, int stale, int skipped,
                              long durationMs) {
}
