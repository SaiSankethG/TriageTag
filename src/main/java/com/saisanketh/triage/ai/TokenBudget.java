package com.saisanketh.triage.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * The daily LLM token budget (config {@code triage.llm.daily-token-budget}). Usage is counted in the llm_usage table, so
 * it survives restarts and is shared by every instance of the app.
 *
 * <p>The check happens before a call and the count after it, so the last calls of a day can go over the budget by one
 * call's tokens per worker running at that moment (3 Kafka workers, backend piece 3). That's accepted: the budget is a cost brake,
 * not an exact meter. The count itself is one atomic upsert, so parallel calls never lose each other's tokens.
 */
@Component
public class TokenBudget {

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final long dailyLimit;

    public TokenBudget(JdbcTemplate jdbc, Clock clock,
                       @Value("${triage.llm.daily-token-budget:200000}") long dailyLimit) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.dailyLimit = dailyLimit;
    }

    public long dailyLimit() {
        return dailyLimit;
    }

    /** Tokens used so far today (UTC). */
    public long usedToday() {
        Long used = jdbc.query("SELECT tokens FROM llm_usage WHERE day = ?", (rs, row) -> rs.getLong("tokens"), today())
                .stream().findFirst().orElse(0L);
        return used;
    }

    public boolean hasRoom() {
        return usedToday() < dailyLimit;
    }

    /** Adds one call's tokens to today's row (creates the row on the first call of the day). */
    public void record(int tokens) {
        jdbc.update("""
                INSERT INTO llm_usage (day, tokens, calls) VALUES (?, ?, 1)
                ON CONFLICT (day) DO UPDATE SET tokens = llm_usage.tokens + EXCLUDED.tokens, calls = llm_usage.calls + 1
                """, today(), tokens);
    }

    private Date today() {
        return Date.valueOf(LocalDate.now(clock.withZone(ZoneOffset.UTC)));
    }
}
