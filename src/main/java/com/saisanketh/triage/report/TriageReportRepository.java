package com.saisanketh.triage.report;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Instant;

/**
 * The triage_report table (Flyway V8). Each part is written as JSON (Jackson) into a jsonb column and read back as a
 * JsonNode: the API passes it through as it is, so old reports still read fine when the Java records change.
 */
@Repository
@RequiredArgsConstructor
public class TriageReportRepository {

    /** How much of the body the issue card gets; the page shows ~300 characters with "show more". */
    static final int BODY_START_CHARS = 1_500;

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

    /**
     * One line of the reports page's list: the issue, its best match, the AI check's answer and one-line reason, and which
     * details the reporter left out. The page turns these into one word (Likely duplicate / Possible duplicate / Needs info /
     * Looks new) and a one-line "why".
     */
    public record ReportListItem(int number, String title, String state, String authorAssociation, Instant reportCreatedAt,
                                 Integer topNumber, Double topScore, String topTitle, String checkVerdict, String checkReason,
                                 List<String> missing) {
    }

    /** The most recent reports of a repo, open issues first (that's what a maintainer still has to answer), then newest. */
    public List<ReportListItem> recent(String repo, int limit) {
        return jdbc.query("""
                SELECT i.number, i.title, i.state, i.author_association, r.created_at,
                       (r.duplicates -> 0 ->> 'number')::int AS top_number,
                       (r.duplicates -> 0 ->> 'score')::float8 AS top_score,
                       r.duplicates -> 0 ->> 'title' AS top_title,
                       r.duplicate_check ->> 'verdict' AS check_verdict,
                       r.duplicate_check ->> 'reason' AS check_reason,
                       ARRAY(SELECT d.key FROM jsonb_each_text(r.missing -> 'details') d
                             WHERE r.missing ->> 'status' = 'OK' AND d.value = 'MISSING' ORDER BY d.key) AS missing
                FROM triage_report r JOIN issue i ON i.id = r.issue_id
                WHERE lower(i.repo) = lower(?)
                ORDER BY (i.state = 'open') DESC, r.created_at DESC
                LIMIT ?
                """, (rs, row) -> new ReportListItem(rs.getInt("number"), rs.getString("title"), rs.getString("state"),
                rs.getString("author_association"), rs.getTimestamp("created_at").toInstant(),
                (Integer) rs.getObject("top_number"), (Double) rs.getObject("top_score"), rs.getString("top_title"),
                rs.getString("check_verdict"), rs.getString("check_reason"),
                Arrays.asList((String[]) rs.getArray("missing").getArray())), repo, limit);
    }

    /** The report of an issue (by repo + number), joined with the issue's title, link and card. */
    public Optional<TriageReportDto> find(String repo, int number) {
        Optional<TriageReportDto> dto = jdbc.query("""
                SELECT i.repo, i.number, i.title, i.html_url, i.state, i.state_reason, i.author_association,
                       i.created_at AS issue_created_at, left(coalesce(i.body, ''), ?) AS body_start,
                       i.duplicate_of,
                       EXISTS (SELECT 1 FROM issue o WHERE o.repo = i.repo AND o.number = i.duplicate_of) AS original_stored,
                       coalesce((SELECT o.created_at > i.created_at FROM issue o
                                 WHERE o.repo = i.repo AND o.number = i.duplicate_of), false) AS original_is_newer,
                       length(coalesce(i.body, '')) AS body_length,
                       ARRAY(SELECT l.label_name FROM issue_label l WHERE l.issue_id = i.id ORDER BY l.label_name)
                           AS issue_labels,
                       r.*
                FROM triage_report r JOIN issue i ON i.id = r.issue_id
                WHERE lower(i.repo) = lower(?) AND i.number = ?
                """, (rs, row) -> toDto(rs), BODY_START_CHARS, repo, number).stream().findFirst();
        dto.ifPresent(d -> addMatchStates(d.repo(), d.duplicates()));
        return dto;
    }

    /** How much of a matched issue's body the page shows next to the new issue. */
    static final int MATCH_BODY_CHARS = 600;

    /**
     * Each duplicate gets the matched issue's current state ("open"/"closed"), state reason, opening date and the start of
     * its body, looked up now: the page shows the new issue and its best match side by side.
     */
    private void addMatchStates(String repo, JsonNode duplicates) {
        if (duplicates == null || !duplicates.isArray() || duplicates.isEmpty()) {
            return;
        }
        List<Integer> numbers = new ArrayList<>();
        duplicates.forEach(d -> numbers.add(d.path("number").asInt()));
        Map<Integer, String[]> states = new HashMap<>();
        jdbc.query("""
                SELECT number, state, state_reason, created_at, left(coalesce(body, ''), ?) AS body_start
                FROM issue WHERE repo = ? AND number = ANY (?)
                """,
                (RowCallbackHandler) rs -> states.put(rs.getInt("number"), new String[]{rs.getString("state"),
                        rs.getString("state_reason"), rs.getTimestamp("created_at").toInstant().toString(),
                        rs.getString("body_start")}),
                MATCH_BODY_CHARS, repo, numbers.toArray(new Integer[0]));
        for (JsonNode d : duplicates) {
            String[] state = states.get(d.path("number").asInt());
            if (d instanceof ObjectNode o && state != null) {
                o.put("state", state[0]);
                o.put("stateReason", state[1]);
                o.put("createdAt", state[2]);
                o.put("bodyStart", state[3]);
            }
        }
    }

    private TriageReportDto toDto(ResultSet rs) throws SQLException {
        IssueCard card = new IssueCard(rs.getString("state"), rs.getString("state_reason"),
                rs.getString("author_association"), rs.getTimestamp("issue_created_at").toInstant(),
                Arrays.asList((String[]) rs.getArray("issue_labels").getArray()), rs.getString("body_start"),
                rs.getInt("body_length"), (Integer) rs.getObject("duplicate_of"), rs.getBoolean("original_stored"),
                rs.getBoolean("original_is_newer"));
        return new TriageReportDto(rs.getString("repo"), rs.getInt("number"), rs.getString("title"),
                rs.getString("html_url"), card, rs.getTimestamp("created_at").toInstant(),
                node(rs, "duplicates"), node(rs, "duplicate_check"), node(rs, "labels"), node(rs, "missing"),
                node(rs, "fixed_hint"), node(rs, "draft_reply"), rs.getString("llm_model"),
                rs.getString("prompt_version"), rs.getInt("cost_tokens"), rs.getString("notes"), List.of());
    }

    private JsonNode node(ResultSet rs, String column) throws SQLException {
        String text = rs.getString(column);
        return text == null ? null : json.readTree(text);
    }

    private String toJson(Object value) {
        return value == null ? null : json.writeValueAsString(value);
    }
}
