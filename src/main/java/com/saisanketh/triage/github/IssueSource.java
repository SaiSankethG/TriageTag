package com.saisanketh.triage.github;

/**
 * Something that can fetch issues from an issue tracker.
 * GitHub is the only one today; a Jira source could implement this later.
 */
public interface IssueSource {

    /** Fetches the first page of a repo's issues (open and closed). */
    IssuePage fetchPage(String owner, String repo);
}
