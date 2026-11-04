package com.saisanketh.triage.triage;

/**
 * "The similar issue was already fixed": a hint for the maintainer, built without an LLM.
 *
 * @param matchNumber     the similar (closed) issue
 * @param fixVersion      the version its fix shipped in, e.g. "3.4.2"
 * @param reporterVersion the version the new issue's reporter uses, or null if the body doesn't say
 * @param closerUrl       the PR or commit that closed it, or null
 * @param kind            UPGRADE: reporter is on an older version; SAME_OR_NEWER: reporter already has the fix
 *                        (a regression, or a different problem); VERSION_UNKNOWN: reporter's version not found
 * @param text            one sentence for the report
 */
public record FixedHint(int matchNumber, String fixVersion, String reporterVersion, String closerUrl, Kind kind,
                        String text) {

    public enum Kind { UPGRADE, SAME_OR_NEWER, VERSION_UNKNOWN }
}
