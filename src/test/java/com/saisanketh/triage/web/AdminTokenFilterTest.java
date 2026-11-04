package com.saisanketh.triage.web;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminTokenFilterTest {

    /** Stand-ins for the real endpoints: one admin POST, the webhook, one GET. */
    @RestController
    static class Endpoints {
        @PostMapping("/reports/o/r/1")
        String create() {
            return "created";
        }

        @PostMapping("/webhooks/github")
        String webhook() {
            return "webhook";
        }

        @GetMapping("/reports/o/r/1")
        String read() {
            return "report";
        }
    }

    private static MockMvc mvc(String configuredToken) {
        return MockMvcBuilders.standaloneSetup(new Endpoints()).addFilters(new AdminTokenFilter(configuredToken)).build();
    }

    @Test
    void theRightTokenPasses() throws Exception {
        mvc("s3cret").perform(post("/reports/o/r/1").header("X-Admin-Token", "s3cret"))
                .andExpect(status().isOk()).andExpect(content().string("created"));
    }

    @Test
    void aMissingOrWrongTokenIs401() throws Exception {
        mvc("s3cret").perform(post("/reports/o/r/1")).andExpect(status().isUnauthorized());
        mvc("s3cret").perform(post("/reports/o/r/1").header("X-Admin-Token", "s3cre")).andExpect(status().isUnauthorized());
        mvc("s3cret").perform(post("/reports/o/r/1").header("X-Admin-Token", "s3cret ")).andExpect(status().isUnauthorized());
    }

    @Test
    void withoutAConfiguredTokenAdminCallsAre503() throws Exception {
        mvc("").perform(post("/reports/o/r/1").header("X-Admin-Token", "")).andExpect(status().isServiceUnavailable());
        mvc(null).perform(post("/reports/o/r/1").header("X-Admin-Token", "anything")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void theWebhookAndReadsAreNotAffected() throws Exception {
        mvc("s3cret").perform(post("/webhooks/github")).andExpect(status().isOk()).andExpect(content().string("webhook"));
        mvc("s3cret").perform(get("/reports/o/r/1")).andExpect(status().isOk());
        mvc(null).perform(get("/reports/o/r/1")).andExpect(status().isOk());
    }
}
