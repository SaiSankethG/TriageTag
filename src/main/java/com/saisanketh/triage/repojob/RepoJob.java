package com.saisanketh.triage.repojob;

import java.time.Instant;

/**
 * One "Add repository" job (table repo_job, Flyway V12), as the status endpoint shows it.
 *
 * @param reason INSTALLED (the GitHub App was installed on the repo) or ADMIN (the operator asked)
 * @param status QUEUED, RUNNING, DONE or FAILED
 * @param step   BACKFILL, EMBEDDINGS or DOCS: the step running now (or the last one); null before the first
 * @param done   progress inside the step (issues loaded, issues embedded, doc sections stored)
 * @param total  out of this many, when known; null while unknown (GitHub doesn't say how many issues a repo has)
 * @param note   something worth knowing about a finished job, e.g. that the repo has no reference docs
 * @param error  why it failed
 */
public record RepoJob(long id, String repo, String reason, String status, String step, Integer done, Integer total,
                      String note, String error, Instant createdAt, Instant startedAt, Instant finishedAt) {

    public enum Reason { INSTALLED, ADMIN }

    public enum Status { QUEUED, RUNNING, DONE, FAILED }

    public enum Step { BACKFILL, EMBEDDINGS, DOCS }
}
