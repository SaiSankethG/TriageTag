package com.saisanketh.triage.repojob;

import com.saisanketh.triage.repojob.RepoJob.Reason;
import com.saisanketh.triage.repojob.RepoJob.Status;
import com.saisanketh.triage.repojob.RepoJob.Step;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/**
 * The repo_job table (Flyway V12), plain SQL like the outbox.
 *
 * <p>The runner's progress updates are called <em>outside</em> any transaction on purpose: each UPDATE commits at once
 * (JDBC auto-commit), so the status endpoint shows the progress while the job is still running. One transaction around a
 * 20-minute job would hide everything until the end.
 */
@Repository
@RequiredArgsConstructor
public class RepoJobRepository {

    private final JdbcTemplate jdbc;

    /**
     * Adds a QUEUED job unless the repo already has a QUEUED or RUNNING one (the partial unique index repo_job_one_open).
     *
     * @return true if a job was added
     */
    public boolean queue(String repo, Reason reason, Instant now) {
        return jdbc.update("""
                INSERT INTO repo_job (repo, reason, status, created_at) VALUES (?, ?, 'QUEUED', ?)
                ON CONFLICT ((lower(repo))) WHERE status IN ('QUEUED', 'RUNNING') DO NOTHING
                """, repo, reason.name(), Timestamp.from(now)) == 1;
    }

    /**
     * Takes the oldest QUEUED job and marks it RUNNING, in one statement. {@code FOR UPDATE SKIP LOCKED}: a second app
     * instance asking at the same moment gets a different job (or none), never the same one.
     */
    public Optional<RepoJob> claimNext(Instant now) {
        return jdbc.query("""
                UPDATE repo_job SET status = 'RUNNING', started_at = ?
                WHERE id = (SELECT id FROM repo_job WHERE status = 'QUEUED' ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED)
                RETURNING *
                """, ROW, Timestamp.from(now)).stream().findFirst();
    }

    /** A new step starts: its progress starts again from 0. */
    public void startStep(long id, Step step) {
        jdbc.update("UPDATE repo_job SET step = ?, done = 0, total = NULL WHERE id = ?", step.name(), id);
    }

    public void progress(long id, int done, Integer total) {
        jdbc.update("UPDATE repo_job SET done = ?, total = ? WHERE id = ?", done, total, id);
    }

    public void finish(long id, Status status, String note, String error, Instant now) {
        jdbc.update("UPDATE repo_job SET status = ?, note = ?, error = ?, finished_at = ? WHERE id = ?",
                status.name(), note, error, Timestamp.from(now), id);
    }

    /**
     * After a restart, a job that was RUNNING has no one working on it any more: put it back in the queue. Safe because every
     * step can run again (the backfill upserts, embeddings only fill what's missing, docs replace the old ones). Assumes one
     * app instance (the k3s Deployment has 1 replica); with several, a job running on another instance would start twice.
     */
    public int requeueRunning() {
        return jdbc.update("UPDATE repo_job SET status = 'QUEUED', started_at = NULL WHERE status = 'RUNNING'");
    }

    /** The newest job of the repo (any status), for the status endpoint. Repo names are compared ignoring case. */
    public Optional<RepoJob> latest(String repo) {
        return jdbc.query("SELECT * FROM repo_job WHERE lower(repo) = lower(?) ORDER BY id DESC LIMIT 1", ROW, repo)
                .stream().findFirst();
    }

    private static final RowMapper<RepoJob> ROW = (rs, row) -> new RepoJob(rs.getLong("id"), rs.getString("repo"),
            rs.getString("reason"), rs.getString("status"), rs.getString("step"), integer(rs, "done"),
            integer(rs, "total"), rs.getString("note"), rs.getString("error"), instant(rs, "created_at"),
            instant(rs, "started_at"), instant(rs, "finished_at"));

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }
}
