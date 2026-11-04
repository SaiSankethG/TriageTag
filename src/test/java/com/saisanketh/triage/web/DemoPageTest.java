package com.saisanketh.triage.web;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** The demo page is a static file (Spring Boot serves classpath:/static/ at /). Checks that it is there and safe. */
class DemoPageTest {

    @Test
    void theDemoPageCallsTheReportApiAndNeverInsertsHtml() throws Exception {
        String html = new ClassPathResource("static/index.html").getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("fetch(`/reports/${repo}/${number}`");
        assertThat(html).doesNotContain("innerHTML");          // text from issues and the LLM goes in as text only
        assertThat(html).doesNotContain("<script src=");       // no external scripts, no new dependency
        assertThat(html).contains("fetch('/settings')", "/repos/${repo}/issues?", "URLSearchParams(location.search)");
        assertThat(html).contains("@media (max-width: 820px)", "prefers-color-scheme: dark", ":focus-visible");
        // The admin token is sent only as a header, kept only in sessionStorage, never put in the URL or localStorage.
        assertThat(html).contains("'X-Admin-Token': adminToken()", "sessionStorage.setItem('triage-admin-token'",
                "Only existing reports can be viewed on the public demo.");
        assertThat(html).doesNotContain("localStorage");
        assertThat(html).doesNotContainPattern("URLSearchParams\\([^)]*[Tt]oken");
    }
}
