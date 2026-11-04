package com.saisanketh.triage.web;

import com.saisanketh.triage.report.TriageReportDto;
import com.saisanketh.triage.report.TriageReportRepository;
import com.saisanketh.triage.report.TriageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportControllerTest {

    private final TriageReportRepository reports = mock(TriageReportRepository.class);
    private final TriageService triageService = mock(TriageService.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReportController(reports, triageService)).build();
    }

    private TriageReportDto dto() {
        return new TriageReportDto("o/r", 5, "Title", "https://github.com/o/r/issues/5", Instant.parse("2026-11-04T00:00:00Z"),
                json.readTree("[{\"number\": 1}]"), null, json.readTree("[]"), json.readTree("{\"status\": \"OK\"}"),
                null, json.readTree("{\"status\": \"NOTHING_RELEVANT\"}"), "fake", "missing-details-v1", 42, null);
    }

    @Test
    void returnsTheStoredReport() throws Exception {
        when(reports.find("o/r", 5)).thenReturn(Optional.of(dto()));

        mockMvc.perform(get("/reports/o/r/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicates[0].number").value(1))
                .andExpect(jsonPath("$.missing.status").value("OK"))
                .andExpect(jsonPath("$.costTokens").value(42));
    }

    @Test
    void noReportIs404AndPostCreatesOne() throws Exception {
        when(reports.find("o/r", 6)).thenReturn(Optional.empty());
        mockMvc.perform(get("/reports/o/r/6")).andExpect(status().isNotFound());

        when(triageService.create("o/r", 5)).thenReturn(Optional.of(mock(com.saisanketh.triage.report.TriageReport.class)));
        when(reports.find("o/r", 5)).thenReturn(Optional.of(dto()));
        mockMvc.perform(post("/reports/o/r/5")).andExpect(status().isOk()).andExpect(jsonPath("$.number").value(5));
        verify(triageService).create("o/r", 5);

        when(triageService.create("o/r", 7)).thenReturn(Optional.empty());
        mockMvc.perform(post("/reports/o/r/7")).andExpect(status().isNotFound());
    }
}
