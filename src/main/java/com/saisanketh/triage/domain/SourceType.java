package com.saisanketh.triage.domain;

/**
 * Where an issue came from. Stored as text in issue.source.
 * Only GitHub for now; JIRA can be added later without a schema change.
 */
public enum SourceType {
    GITHUB
}
