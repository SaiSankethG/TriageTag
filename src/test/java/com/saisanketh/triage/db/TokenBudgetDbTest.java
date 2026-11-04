package com.saisanketh.triage.db;

import com.saisanketh.triage.ai.TokenBudget;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBudgetDbTest extends PostgresTest {

    @Autowired
    private JdbcTemplate jdbc;

    private TokenBudget budgetAt(String instant, long limit) {
        return new TokenBudget(jdbc, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC), limit);
    }

    @Test
    void addsUpTokensPerUtcDayAndStopsAtTheLimit() {
        TokenBudget day1 = budgetAt("2026-11-04T10:00:00Z", 1000);
        assertThat(day1.hasRoom()).isTrue();

        day1.record(600);
        day1.record(300);
        assertThat(day1.usedToday()).isEqualTo(900);
        assertThat(day1.hasRoom()).isTrue();

        day1.record(200);   // the last call may go over by its own tokens
        assertThat(day1.usedToday()).isEqualTo(1100);
        assertThat(day1.hasRoom()).isFalse();
        assertThat(jdbc.queryForObject("SELECT calls FROM llm_usage WHERE day = DATE '2026-11-04'", Integer.class)).isEqualTo(3);

        TokenBudget day2 = budgetAt("2026-11-05T00:30:00Z", 1000);   // a new UTC day starts at zero
        assertThat(day2.usedToday()).isZero();
        assertThat(day2.hasRoom()).isTrue();
    }
}
