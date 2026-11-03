package com.saisanketh.triage.source;

/**
 * Something that can fetch issues from an issue tracker, one page at a time.
 * Each tracker has its own implementation; callers only see {@link IssuePage}.
 *
 * <p>How to read every page:
 * <pre>
 * String cursor = null;                     // null = start at the first page
 * do {
 *     IssuePage page = source.fetchPage("spring-projects/spring-boot", cursor);
 *     ... use page.issues() and page.skipped() ...
 *     cursor = page.nextCursor();
 * } while (cursor != null);                 // null = that was the last page
 * </pre>
 * Keep going until the cursor is null, not until a page is empty: a page can have
 * no issues (every item on it was skipped) and still have a next page.
 */
public interface IssueSource {

    /**
     * @param repo   the repo as "owner/name", e.g. "spring-projects/spring-boot"
     * @param cursor null for the first page, otherwise the nextCursor of the previous page.
     *               Treat it as an opaque value: don't build or change it.
     */
    IssuePage fetchPage(String repo, String cursor);
}
