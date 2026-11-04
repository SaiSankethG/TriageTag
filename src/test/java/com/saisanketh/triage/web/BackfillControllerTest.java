package com.saisanketh.triage.web;

import com.saisanketh.triage.service.BackfillService;
import com.saisanketh.triage.service.BackfillSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.HttpClientErrorException;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BackfillControllerTest {

    @Mock
    private BackfillService backfillService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // "Standalone": only this controller plus Spring MVC's normal JSON handling; no app context, no DB.
        mockMvc = MockMvcBuilders.standaloneSetup(new BackfillController(backfillService)).build();
    }

    @Test
    void runsTheBackfillAndReturnsTheSummaryAsJson() throws Exception {
        when(backfillService.backfill("octo-org/hello"))
                .thenReturn(new BackfillSummary("octo-org/hello", 2, 3, 0, 1, 1234));

        mockMvc.perform(post("/backfill/octo-org/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repo").value("octo-org/hello"))
                .andExpect(jsonPath("$.pages").value(2))
                .andExpect(jsonPath("$.inserted").value(3))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.durationMs").value(1234));
    }

    @Test
    void unknownRepoGives404() throws Exception {
        when(backfillService.backfill("octo-org/missing"))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        mockMvc.perform(post("/backfill/octo-org/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Repository not found (or not visible with the current token)"));
    }

    @Test
    void onlyPostIsAllowed() throws Exception {
        // A GET must not start a backfill (e.g. a browser prefetching a link).
        mockMvc.perform(get("/backfill/octo-org/hello"))
                .andExpect(status().isMethodNotAllowed());
    }
}
