package com.saisanketh.triage.triage;

import com.saisanketh.triage.source.FixInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/** Reads and writes the issue_fix table (Flyway V6) with plain SQL. */
@Repository
@RequiredArgsConstructor
public class IssueFixRepository {

    private final JdbcTemplate jdbc;

    public Optional<FixInfo> find(long issueId) {
        return jdbc.query("SELECT closer_type, closer_number, closer_url, fix_version FROM issue_fix WHERE issue_id = ?",
                (rs, row) -> new FixInfo(rs.getString("closer_type"), (Integer) rs.getObject("closer_number"),
                        rs.getString("closer_url"), rs.getString("fix_version")), issueId).stream().findFirst();
    }

    /** Insert, or replace what an earlier fetch stored. */
    public void save(long issueId, FixInfo fix, Instant fetchedAt) {
        jdbc.update("""
                INSERT INTO issue_fix (issue_id, closer_type, closer_number, closer_url, fix_version, fetched_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (issue_id) DO UPDATE SET closer_type = EXCLUDED.closer_type,
                    closer_number = EXCLUDED.closer_number, closer_url = EXCLUDED.closer_url,
                    fix_version = EXCLUDED.fix_version, fetched_at = EXCLUDED.fetched_at
                """, issueId, fix.closerType(), fix.closerNumber(), fix.closerUrl(), fix.fixVersion(),
                Timestamp.from(fetchedAt));
    }
}
