package com.saisanketh.triage.web;

import com.saisanketh.triage.report.IssueCard;
import com.saisanketh.triage.report.RepoParts;
import com.saisanketh.triage.report.TriageReportDto;
import com.saisanketh.triage.report.TriageReportRepository;
import com.saisanketh.triage.report.TriageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
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
    private final RepoParts repoParts = mock(RepoParts.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReportController(reports, triageService, repoParts)).build();
    }

    private TriageReportDto dto() {
        IssueCard card = new IssueCard("open", null, "NONE", Instant.parse("2026-11-03T00:00:00Z"), List.of("type: bug"),
                "Body", 4, null, false, false);
        return new TriageReportDto("o/r", 5, "Title", "https://github.com/o/r/issues/5", card, Instant.parse("2026-11-04T00:00:00Z"),
                json.readTree("[{\"number\": 1}]"), null, json.readTree("[]"), json.readTree("{\"status\": \"OK\"}"),
                null, json.readTree("{\"status\": \"NOTHING_RELEVANT\"}"), "fake", "missing-details-v1", 42, null, List.of());
    }

    @Test
    void returnsTheStoredReport() throws Exception {
        when(reports.find("o/r", 5)).thenReturn(Optional.of(dto()));

        mockMvc.perform(get("/reports/o/r/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicates[0].number").value(1))
                .andExpect(jsonPath("$.missing.status").value("OK"))
                .andExpect(jsonPath("$.costTokens").value(42))
                .andExpect(jsonPath("$.issue.authorAssociation").value("NONE"))
                .andExpect(jsonPath("$.issue.labels[0]").value("type: bug"));
    }

    @Test
    void saysWhichPartsThisRepoDoesNotGet() throws Exception {
        when(reports.find("o/r", 5)).thenReturn(Optional.of(dto()));
        when(repoParts.notAvailable("o/r")).thenReturn(List.of(new RepoParts.NotAvailable("fixedHint", "Spring Boot only.")));

        mockMvc.perform(get("/reports/o/r/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notAvailable[0].part").value("fixedHint"))
                .andExpect(jsonPath("$.notAvailable[0].reason").value("Spring Boot only."));

        when(triageService.create("o/r", 5)).thenReturn(Optional.of(mock(com.saisanketh.triage.report.TriageReport.class)));
        mockMvc.perform(post("/reports/o/r/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notAvailable[0].part").value("fixedHint"));
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

    @Test
    void listsRecentReportsWithALimitOfAtMost100() throws Exception {
        when(reports.recent("o/r", 100)).thenReturn(List.of(new com.saisanketh.triage.report.TriageReportRepository.ReportListItem(
                5, "Title", "open", "NONE", Instant.parse("2026-11-04T00:00:00Z"), 3, 0.81, "Older title", "YES", "Same bug.",
                List.of("VERSION"))));

        mockMvc.perform(get("/reports/o/r?limit=500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].number").value(5))
                .andExpect(jsonPath("$[0].topNumber").value(3))
                .andExpect(jsonPath("$[0].checkReason").value("Same bug."))
                .andExpect(jsonPath("$[0].missing[0]").value("VERSION"));
    }
}
