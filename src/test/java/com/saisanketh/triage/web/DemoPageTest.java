package com.saisanketh.triage.web;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** The pages are static files (Spring Boot serves classpath:/static/ at /): landing at /, reports at /app.html. Checks they are there and safe. */
class DemoPageTest {

    @Test
    void theReportsPageCallsTheReportApiAndNeverInsertsHtml() throws Exception {
        String html = new ClassPathResource("static/app.html").getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("fetch(`/reports/${repo}/${number}`");
        assertThat(html).doesNotContain("innerHTML");          // text from issues and the LLM goes in as text only
        assertThat(html).doesNotContain("<script src=");       // no external scripts, no new dependency
        assertThat(html).contains("fetch('/settings')", "fetch('/repos')", "/repos/${repo}/issues?", "URLSearchParams(location.search)");
        assertThat(html).contains("@media (max-width: 820px)", "prefers-color-scheme: dark", ":focus-visible");
        // The admin token is sent only as a header, kept only in sessionStorage, never put in the URL or localStorage.
        assertThat(html).contains("'X-Admin-Token': adminToken()", "sessionStorage.setItem('triage-admin-token'",
                "Only existing reports can be viewed on the public demo.");
        assertThat(html).doesNotContain("localStorage");
        assertThat(html).doesNotContainPattern("URLSearchParams\\([^)]*[Tt]oken");
    }

    @Test
    void theGuidePageIsThereAndLinkedFromTheReportsPage() throws Exception {
        String guide = new ClassPathResource("static/guide.html").getContentAsString(StandardCharsets.UTF_8);
        String page = new ClassPathResource("static/app.html").getContentAsString(StandardCharsets.UTF_8);

        assertThat(page).contains("href=\"/guide.html\"", "href=\"/\"", "fetch(`/reports/${repo}?limit=50`)");
        assertThat(guide).contains("How to read a report", "What is private, what is public").doesNotContain("<script");
    }

    @Test
    void theLandingPageShowsALiveSampleAndSendsCheckIssuesToTheReportsPage() throws Exception {
        String landing = new ClassPathResource("static/index.html").getContentAsString(StandardCharsets.UTF_8);

        assertThat(landing).contains("href=\"/app.html\">Check issues</a>", "fetch('/repos')", "fetch(`/reports/${repo}?limit=50`)",
                "Who it's for", "Individual developers", "Engineering teams", "How to use it");
        assertThat(landing).contains("prefers-reduced-motion");   // the step animation does not autoplay for users who turned motion off
        assertThat(landing).doesNotContain("innerHTML").doesNotContain("<script src=").doesNotContain("localStorage")
                .doesNotContain("X-Admin-Token");                 // the landing page only reads
    }
}
