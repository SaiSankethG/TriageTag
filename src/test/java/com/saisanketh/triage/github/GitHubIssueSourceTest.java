package com.saisanketh.triage.github;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubIssueSourceTest {

    private static final String API = "https://api.github.com";
    private static final String ISSUES_URL = API + "/repos/octo-org/hello/issues?state=all&per_page=100";

    private static final String TWO_ISSUES_JSON = """
            [
              {"id": 1001, "number": 1, "title": "First issue"},
              {"id": 1002, "number": 2, "title": "Second issue"}
            ]
            """;

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        // Replaces the real HTTP client with a fake server; no network calls are made.
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void fetchPageSendsTheRightRequestAndReturnsItemsAndLinkHeader() {
        String link = "<" + API + "/repositories/1/issues?state=all&per_page=100&page=2>; rel=\"next\"";
        server.expect(requestTo(ISSUES_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(header(HttpHeaders.ACCEPT, "application/vnd.github+json"))
                .andExpect(header("X-GitHub-Api-Version", "2022-11-28"))
                .andRespond(withSuccess(TWO_ISSUES_JSON, MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.LINK, link));

        GitHubIssueSource source = new GitHubIssueSource(builder, API, "test-token");
        IssuePage page = source.fetchPage("octo-org", "hello");

        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).get("number").asInt()).isEqualTo(1);
        assertThat(page.items().get(1).get("title").asString()).isEqualTo("Second issue");
        assertThat(page.linkHeader()).isEqualTo(link);
        server.verify();
    }

    @Test
    void lastPageHasNoLinkHeader() {
        server.expect(requestTo(ISSUES_URL))
                .andRespond(withSuccess(TWO_ISSUES_JSON, MediaType.APPLICATION_JSON));

        IssuePage page = new GitHubIssueSource(builder, API, "test-token").fetchPage("octo-org", "hello");

        assertThat(page.linkHeader()).isNull();
    }

    @Test
    void noTokenMeansNoAuthorizationHeader() {
        server.expect(requestTo(ISSUES_URL))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        IssuePage page = new GitHubIssueSource(builder, API, "").fetchPage("octo-org", "hello");

        assertThat(page.items()).isEmpty();
        server.verify();
    }

    @Test
    void unknownRepoThrows() {
        server.expect(requestTo(ISSUES_URL)).andRespond(withResourceNotFound());

        GitHubIssueSource source = new GitHubIssueSource(builder, API, "test-token");

        assertThatThrownBy(() -> source.fetchPage("octo-org", "hello"))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }
}
