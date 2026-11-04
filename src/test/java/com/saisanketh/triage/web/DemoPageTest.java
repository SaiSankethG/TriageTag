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
    }
}
