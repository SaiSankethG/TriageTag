package com.saisanketh.triage.report;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

/**
 * The triage_report table (Flyway V8). Each part is written as JSON (Jackson) into a jsonb column and read back as a
 * JsonNode: the API passes it through as it is, so old reports still read fine when the Java records change.
 */
@Repository
@RequiredArgsConstructor
public class TriageReportRepository {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public boolean exists(long issueId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM triage_report WHERE issue_id = ?)", Boolean.class, issueId));
    }

    /** Insert, or replace the issue's report (POST /reports regenerates it). */
    public void save(TriageReport r) {
        jdbc.update("""
                INSERT INTO triage_report (issue_id, created_at, duplicates, duplicate_check, labels, missing,
                                           fixed_hint, draft_reply, llm_model, prompt_version, cost_tokens, notes)
                VALUES (?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?)
                ON CONFLICT (issue_id) DO UPDATE SET created_at = EXCLUDED.created_at,
                    duplicates = EXCLUDED.duplicates, duplicate_check = EXCLUDED.duplicate_check,
                    labels = EXCLUDED.labels, missing = EXCLUDED.missing, fixed_hint = EXCLUDED.fixed_hint,
                    draft_reply = EXCLUDED.draft_reply, llm_model = EXCLUDED.llm_model,
                    prompt_version = EXCLUDED.prompt_version, cost_tokens = EXCLUDED.cost_tokens, notes = EXCLUDED.notes
                """, r.issueId(), Timestamp.from(r.createdAt()), toJson(r.duplicates()), toJson(r.duplicateCheck()),
                toJson(r.labels()), toJson(r.missing()), toJson(r.fixedHint()), toJson(r.draftReply()), r.llmModel(),
                r.promptVersions(), r.costTokens(), r.notes());
    }

    /** The report of an issue (by repo + number), joined with the issue's title and link. */
    public Optional<TriageReportDto> find(String repo, int number) {
        return jdbc.query("""
                SELECT i.repo, i.number, i.title, i.html_url, r.*
                FROM triage_report r JOIN issue i ON i.id = r.issue_id
                WHERE lower(i.repo) = lower(?) AND i.number = ?
                """, (rs, row) -> toDto(rs), repo, number).stream().findFirst();
    }

    private TriageReportDto toDto(ResultSet rs) throws SQLException {
        return new TriageReportDto(rs.getString("repo"), rs.getInt("number"), rs.getString("title"),
                rs.getString("html_url"), rs.getTimestamp("created_at").toInstant(),
                node(rs, "duplicates"), node(rs, "duplicate_check"), node(rs, "labels"), node(rs, "missing"),
                node(rs, "fixed_hint"), node(rs, "draft_reply"), rs.getString("llm_model"),
                rs.getString("prompt_version"), rs.getInt("cost_tokens"), rs.getString("notes"));
    }

    private JsonNode node(ResultSet rs, String column) throws SQLException {
        String text = rs.getString(column);
        return text == null ? null : json.readTree(text);
    }

    private String toJson(Object value) {
        return value == null ? null : json.writeValueAsString(value);
    }
}
