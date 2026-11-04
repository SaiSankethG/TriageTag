package com.saisanketh.triage.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Loads just the text of an issue for a prompt (no JPA entity, labels or raw JSON needed). */
@Repository
@RequiredArgsConstructor
public class IssueTextRepository {

    private final JdbcTemplate jdbc;

    public Optional<IssueText> byId(long id) {
        return jdbc.query("SELECT number, title, body FROM issue WHERE id = ?",
                (rs, row) -> new IssueText(rs.getInt("number"), rs.getString("title"), rs.getString("body")), id)
                .stream().findFirst();
    }

    public Optional<IssueText> byNumber(String repo, int number) {
        return jdbc.query("SELECT number, title, body FROM issue WHERE lower(repo) = lower(?) AND number = ?",
                (rs, row) -> new IssueText(rs.getInt("number"), rs.getString("title"), rs.getString("body")),
                repo, number).stream().findFirst();
    }
}
