package com.saisanketh.triage.service;

/** What {@link IssueService#upsert} did with an issue. */
public enum UpsertResult {
    /** The issue was new: a row was added. */
    INSERTED,
    /** We already had the issue: its row was overwritten with the newer data. */
    UPDATED
}
