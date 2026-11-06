package com.saisanketh.triage.web;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** The repositories the assistant knows, for the landing page and the reports page's repository switcher. */
@RestController
@RequiredArgsConstructor
public class RepoController {

    /**
     * @param issues    issues stored for the repo
     * @param triaged   how many of them have a triage report
     * @param newestIssue when the newest stored issue was opened
     */
    public record RepoSummary(String repo, long issues, long triaged, Instant newestIssue) {
    }

    private final JdbcTemplate jdbc;

    /** Example: {@code curl localhost:8080/repos}. Most triaged first, then most issues. */
    @GetMapping("/repos")
    public List<RepoSummary> repos() {
        return jdbc.query("""
                SELECT i.repo, count(*) AS issues, count(r.issue_id) AS triaged, max(i.created_at) AS newest
                FROM issue i LEFT JOIN triage_report r ON r.issue_id = i.id
                GROUP BY i.repo
                ORDER BY triaged DESC, issues DESC
                """, (rs, row) -> new RepoSummary(rs.getString("repo"), rs.getLong("issues"), rs.getLong("triaged"),
                rs.getTimestamp("newest").toInstant()));
    }
}
