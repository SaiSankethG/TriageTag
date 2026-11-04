package com.saisanketh.triage.comment;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;

/** The repo_setting and posted_comment tables (Flyway V11), plain SQL. */
@Repository
@RequiredArgsConstructor
public class CommentRepository {

    public enum Mode { SHADOW, SUGGEST }

    private final JdbcTemplate jdbc;

    /** SHADOW unless the operator set SUGGEST for this repo (no row = SHADOW). */
    public Mode mode(String repo) {
        return jdbc.queryForList("SELECT mode FROM repo_setting WHERE repo = ?", String.class,
                repo.toLowerCase(Locale.ROOT)).stream().findFirst().map(Mode::valueOf).orElse(Mode.SHADOW);
    }

    /**
     * Claims the issue for one comment, BEFORE posting. Returns false if a row already exists (posted, failed, or being
     * posted by someone else): the caller must then not post. ON CONFLICT DO NOTHING makes this safe under concurrency.
     */
    public boolean reserve(long issueId, long reportId, Instant now) {
        return jdbc.update("""
                INSERT INTO posted_comment (issue_id, report_id, status, created_at) VALUES (?, ?, 'PENDING', ?)
                ON CONFLICT (issue_id) DO NOTHING
                """, issueId, reportId, Timestamp.from(now)) == 1;
    }

    public void markPosted(long issueId, long commentId, String url, Instant now) {
        jdbc.update("UPDATE posted_comment SET status = 'POSTED', comment_id = ?, comment_url = ?, posted_at = ? "
                + "WHERE issue_id = ?", commentId, url, Timestamp.from(now), issueId);
    }

    public void markFailed(long issueId, String error) {
        jdbc.update("UPDATE posted_comment SET status = 'FAILED', error = ? WHERE issue_id = ?", error, issueId);
    }
}
