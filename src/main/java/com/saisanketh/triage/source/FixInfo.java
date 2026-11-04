package com.saisanketh.triage.source;

/**
 * How a closed issue was fixed, from the tracker's records. Every field may be null.
 *
 * @param closerType   "PullRequest" or "Commit": what closed the issue
 * @param closerNumber the pull request's number (null for a commit)
 * @param closerUrl    link to the PR or commit
 * @param fixVersion   the milestone the fix shipped in, e.g. "3.4.2"
 */
public record FixInfo(String closerType, Integer closerNumber, String closerUrl, String fixVersion) {

    public static FixInfo none() {
        return new FixInfo(null, null, null, null);
    }
}
