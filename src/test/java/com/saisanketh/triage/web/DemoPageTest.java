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
        assertThat(html).contains("@media (max-width: 820px)", ":focus-visible");   // dark mode lives in theme.css
        // Maintainer-first: four words instead of percentages, a one-line why, filters, and copy buttons (nothing is posted).
        assertThat(html).contains("'Likely duplicate'", "'Possible duplicate'", "'Needs info'", "'Looks new'", "function whyOf(",
                "id=\"filters\"", "id=\"open-only\"", "Copy reply", "Duplicate of #", "Open on GitHub", "This issue and its best match");
        assertThat(html).contains("body.reading main { order: -1; }");   // phones: the report first
        // The public page only reads: no admin token box, no POST.
        assertThat(html).doesNotContain("X-Admin-Token").doesNotContain("admin-token").doesNotContain("method: 'POST'")
                .doesNotContain("localStorage");
    }

    @Test
    void theAdminPageSendsTheTokenOnlyAsAHeaderAndIsNotRoutedPublicly() throws Exception {
        String admin = new ClassPathResource("static/admin.html").getContentAsString(StandardCharsets.UTF_8);

        // The admin token is sent only as a header, kept only in sessionStorage, never put in the URL or localStorage.
        assertThat(admin).contains("'X-Admin-Token': adminToken()", "sessionStorage.setItem('triage-admin-token'", "noindex");
        assertThat(admin).doesNotContain("localStorage").doesNotContain("innerHTML").doesNotContain("<script src=");
        assertThat(admin).doesNotContainPattern("URLSearchParams\\([^)]*[Tt]oken");
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
        assertThat(landing).contains("prefers-reduced-motion");
        // "How it helps": one tag colour per box (duplicates red, missing details blue, draft amber, already fixed green); bands alternate.
        assertThat(landing).containsSubsequence("benefit red", "benefit blue", "benefit amber", "benefit green")
                .contains("<section class=\"block band\"");
        assertThat(landing).contains(".steps > li { min-height: 62vh; }");   // elongated: about a screen of scrolling per step
        assertThat(landing).contains("id=\"rail\"", "addEventListener('scroll', request, { passive: true })", "function nudgeTag(");
        assertThat(landing).contains("Coming soon", "overflow-x: auto; position: relative;");  // App not public yet; no phone sideways scroll   // the step animation does not autoplay for users who turned motion off
        assertThat(landing).doesNotContain("innerHTML").doesNotContain("<script src=").doesNotContain("localStorage")
                .doesNotContain("X-Admin-Token");                 // the landing page only reads
    }

    @Test
    void everyPageSharesOneThemeAndOneHeaderBoxAndMotionStopsForReducedMotion() throws Exception {
        for (String page : new String[]{"index.html", "app.html", "guide.html", "admin.html"}) {
            String html = new ClassPathResource("static/" + page).getContentAsString(StandardCharsets.UTF_8);
            assertThat(html).as(page).contains("<link rel=\"stylesheet\" href=\"/theme.css\">").doesNotContain(":root {");
        }
        for (String page : new String[]{"index.html", "app.html", "guide.html"}) {   // same header box: nothing jumps
            assertThat(new ClassPathResource("static/" + page).getContentAsString(StandardCharsets.UTF_8)).as(page)
                    .contains(".bar-inner { max-width: 1260px; margin: 0 auto; padding: 12px 16px;");
        }
        String theme = new ClassPathResource("static/theme.css").getContentAsString(StandardCharsets.UTF_8);
        assertThat(theme).contains("@media (prefers-reduced-motion: reduce)", "animation: none !important",
                "@keyframes swing", ".stagger", "@keyframes fill", "prefers-color-scheme: dark", "body::before", "@keyframes drift", "Bricolage Grotesque", "IBM Plex Sans", "IBM Plex Mono");
    }
}
