package com.saisanketh.triage.web;

import com.saisanketh.triage.ai.FakeTriageLlm;
import com.saisanketh.triage.ai.TokenBudget;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SettingsControllerTest {

    @Test
    void showsThresholdsModelAndBudgetWithTheNextUtcMidnight() throws Exception {
        TokenBudget budget = mock(TokenBudget.class);
        when(budget.usedToday()).thenReturn(1_000L);
        when(budget.dailyLimit()).thenReturn(1_000L);
        Clock clock = Clock.fixed(Instant.parse("2026-11-04T20:00:00Z"), ZoneOffset.UTC);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new SettingsController(budget, new FakeTriageLlm(), clock, 0.9, 0.75)).build();

        mockMvc.perform(get("/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commentThreshold").value(0.9))
                .andExpect(jsonPath("$.possibleThreshold").value(0.75))
                .andExpect(jsonPath("$.llmModel").value("fake"))
                .andExpect(jsonPath("$.budgetReached").value(true))
                .andExpect(jsonPath("$.budgetResetsAt").value("2026-11-05T00:00:00Z"));   // 05:30 in India
    }
}
