package com.saisanketh.triage.service;

import java.util.List;

/**
 * One page of a repo's issues for the read API, newest first.
 *
 * @param issues the issues on this page
 * @param page   page number, starting at 0
 * @param size   page size asked for
 * @param total  how many issues the repo has in our database (all pages)
 */
public record IssueList(List<IssueSummary> issues, int page, int size, long total) {
}
