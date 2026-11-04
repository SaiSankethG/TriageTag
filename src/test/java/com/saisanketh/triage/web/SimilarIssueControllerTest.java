package com.saisanketh.triage.web;

import com.saisanketh.triage.triage.SimilarIssue;
import com.saisanketh.triage.triage.SimilarIssueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SimilarIssueControllerTest {

    private final SimilarIssueService service = mock(SimilarIssueService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SimilarIssueController(service)).build();
    }

    @Test
    void returnsNumberTitleAndScore() throws Exception {
        when(service.findSimilar("octo-org/hello", 52529, "vector", 3))
                .thenReturn(Optional.of(List.of(new SimilarIssue(52519, "The original", 0.91))));

        mockMvc.perform(get("/repos/octo-org/hello/issues/52529/similar"))   // defaults: vector, 3
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].number").value(52519))
                .andExpect(jsonPath("$[0].title").value("The original"))
                .andExpect(jsonPath("$[0].score").value(0.91));
    }

    @Test
    void unknownIssueIs404() throws Exception {
        when(service.findSimilar("octo-org/hello", 1, "keyword", 3)).thenReturn(Optional.empty());

        mockMvc.perform(get("/repos/octo-org/hello/issues/1/similar?method=keyword"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownMethodIs400() throws Exception {
        when(service.findSimilar("octo-org/hello", 1, "magic", 3))
                .thenThrow(new IllegalArgumentException("method must be one of [keyword, vector], got: magic"));

        mockMvc.perform(get("/repos/octo-org/hello/issues/1/similar?method=magic"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void limitIsCapped() throws Exception {
        mockMvc.perform(get("/repos/octo-org/hello/issues/1/similar?limit=500"))
                .andExpect(status().isBadRequest());
    }
}
