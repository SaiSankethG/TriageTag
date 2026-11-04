package com.saisanketh.triage.web;

import com.saisanketh.triage.service.IssueList;
import com.saisanketh.triage.service.IssueService;
import com.saisanketh.triage.service.IssueSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IssueControllerTest {

    @Mock
    private IssueService issueService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new IssueController(issueService)).build();
    }

    @Test
    void returnsAPageOfIssuesAsJson() throws Exception {
        IssueSummary summary = new IssueSummary(7, "Startup fails", "closed", "duplicate",
                List.of("status: duplicate"), Instant.parse("2024-03-01T10:15:30Z"), 3);
        when(issueService.listIssues("octo-org/hello", 0, 20)).thenReturn(new IssueList(List.of(summary), 0, 20, 1));

        mockMvc.perform(get("/repos/octo-org/hello/issues"))   // defaults: page=0, size=20
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.issues[0].number").value(7))
                .andExpect(jsonPath("$.issues[0].stateReason").value("duplicate"))
                .andExpect(jsonPath("$.issues[0].labels[0]").value("status: duplicate"))
                .andExpect(jsonPath("$.issues[0].createdAt").value("2024-03-01T10:15:30Z"))
                .andExpect(jsonPath("$.issues[0].duplicateOf").value(3))
                // the entity's other fields (raw JSON, body...) are not part of the API
                .andExpect(jsonPath("$.issues[0].raw").doesNotExist())
                .andExpect(jsonPath("$.issues[0].body").doesNotExist());
    }

    @Test
    void readsPageAndSizeFromTheQueryString() throws Exception {
        when(issueService.listIssues("octo-org/hello", 2, 50)).thenReturn(new IssueList(List.of(), 2, 50, 0));

        mockMvc.perform(get("/repos/octo-org/hello/issues?page=2&size=50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(50));
    }

    @Test
    void rejectsPagesThatAreTooBig() throws Exception {
        mockMvc.perform(get("/repos/octo-org/hello/issues?size=1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("size must be between 1 and 100"));
    }

    @Test
    void rejectsNegativePages() throws Exception {
        mockMvc.perform(get("/repos/octo-org/hello/issues?page=-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void qSearchesTitles() throws Exception {
        when(issueService.searchIssues("octo-org/hello", "datasource", 0, 20)).thenReturn(new IssueList(List.of(), 0, 20, 0));

        mockMvc.perform(get("/repos/octo-org/hello/issues").param("q", "  datasource "))   // trimmed
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        verify(issueService).searchIssues("octo-org/hello", "datasource", 0, 20);
    }
}
