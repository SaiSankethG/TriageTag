package com.saisanketh.triage.source;

import com.saisanketh.triage.domain.Issue;

import java.util.List;

/**
 * One page of issues from a tracker.
 *
 * @param issues     the issues on this page, already mapped (not saved yet)
 * @param nextCursor pass this to fetchPage to get the next page; null on the last page
 */
public record IssuePage(List<Issue> issues, String nextCursor) {
}
