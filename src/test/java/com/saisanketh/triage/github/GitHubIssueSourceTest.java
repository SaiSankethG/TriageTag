package com.saisanketh.triage.github;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.source.IssuePage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubIssueSourceTest {

    private static final String API = "https://api.github.com";
    private static final String REPO = "octo-org/hello";
    private static final String FIRST_PAGE_URL =
            API + "/repos/octo-org/hello/issues?state=all&per_page=100&sort=created&direction=asc";
    // Same shape as a real GitHub next link: the "after" cursor is already URL-encoded (%3D is "=").
    // If our code re-encoded it, the request would go to ...%253D... and this test would fail.
    private static final String SECOND_PAGE_URL =
            API + "/repositories/1/issues?state=all&per_page=100&sort=created&direction=asc"
                    + "&after=Y3Vyc29yOnYyOpLPAAAB%3D&page=2";

    /** "Now" for every test, so wait times are exact. */
    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    /** Every wait the source asked for; the fake sleeper records instead of sleeping. */
    private final List<Duration> waits = new ArrayList<>();

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        // Replaces the real HTTP client with a fake server; no network calls are made.
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private GitHubIssueSource source(String token) {
        return new GitHubIssueSource(builder, API, token, new GitHubIssueMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC), waits::add);
    }

    // --- request and paging ---

    @Test
    void firstPageSendsTheRightRequest() {
        server.expect(requestTo(FIRST_PAGE_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(header(HttpHeaders.ACCEPT, "application/vnd.github+json"))
                .andExpect(header("X-GitHub-Api-Version", "2022-11-28"))
                .andRespond(withSuccess(array(issue(1)), MediaType.APPLICATION_JSON));

        IssuePage page = source("test-token").fetchPage(REPO, null);

        assertThat(page.issues()).extracting(Issue::getNumber).containsExactly(1);
        server.verify();
    }

    @Test
    void followsTheCursorOverTwoPagesThenStops() {
        // Page 1: GitHub says there is a next page.
        server.expect(requestTo(FIRST_PAGE_URL))
                .andRespond(withSuccess(array(issue(1), issue(2)), MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.LINK, "<" + SECOND_PAGE_URL + ">; rel=\"next\""));
        // Page 2: only prev/first links, so this is the last page.
        server.expect(requestTo(SECOND_PAGE_URL))
                .andRespond(withSuccess(array(issue(3)), MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.LINK,
                                "<" + API + "/repositories/1/issues?page=1>; rel=\"prev\", "
                                        + "<" + API + "/repositories/1/issues?page=1>; rel=\"first\""));

        GitHubIssueSource source = source("test-token");
        IssuePage first = source.fetchPage(REPO, null);
        IssuePage second = source.fetchPage(REPO, first.nextCursor());

        assertThat(first.issues()).extracting(Issue::getNumber).containsExactly(1, 2);
        assertThat(first.nextCursor()).isEqualTo(SECOND_PAGE_URL);
        assertThat(second.issues()).extracting(Issue::getNumber).containsExactly(3);
        assertThat(second.nextCursor()).isNull();
        assertThat(first.skipped()).isZero();
        assertThat(second.skipped()).isZero();
        server.verify();  // exactly these 2 requests, in this order
    }

    @Test
    void skipsAndCountsPullRequests() {
        server.expect(requestTo(FIRST_PAGE_URL))
                .andRespond(withSuccess(array(issue(1), pullRequest(2), issue(3)), MediaType.APPLICATION_JSON));

        IssuePage page = source("test-token").fetchPage(REPO, null);

        assertThat(page.issues()).extracting(Issue::getNumber).containsExactly(1, 3);
        assertThat(page.skipped()).isEqualTo(1);
    }

    @Test
    void pageWithOnlyPullRequestsStillHasANextCursor() {
        server.expect(requestTo(FIRST_PAGE_URL))
                .andRespond(withSuccess(array(pullRequest(1), pullRequest(2)), MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.LINK, "<" + SECOND_PAGE_URL + ">; rel=\"next\""));

        IssuePage page = source("test-token").fetchPage(REPO, null);

        // Empty, but not the end: callers must loop on nextCursor, not on "page is empty".
        assertThat(page.issues()).isEmpty();
        assertThat(page.skipped()).isEqualTo(2);
        assertThat(page.nextCursor()).isEqualTo(SECOND_PAGE_URL);
    }

    @Test
    void noTokenMeansNoAuthorizationHeader() {
        server.expect(requestTo(FIRST_PAGE_URL))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        IssuePage page = source("").fetchPage(REPO, null);

        assertThat(page.issues()).isEmpty();
        assertThat(page.nextCursor()).isNull();
        server.verify();
    }

    // --- rate limits ---

    @Test
    void waitsUntilTheResetAndRetriesAfterARateLimit() {
        long resetIn30s = NOW.getEpochSecond() + 30;
        server.expect(requestTo(FIRST_PAGE_URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .header("X-RateLimit-Remaining", "0")
                        .header("X-RateLimit-Reset", String.valueOf(resetIn30s))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\": \"API rate limit exceeded\"}"));
        server.expect(requestTo(FIRST_PAGE_URL))
                .andRespond(withSuccess(array(issue(1)), MediaType.APPLICATION_JSON));

        IssuePage page = source("test-token").fetchPage(REPO, null);

        assertThat(waits).containsExactly(Duration.ofSeconds(31));  // until reset, +1 s safety
        assertThat(page.issues()).extracting(Issue::getNumber).containsExactly(1);
        server.verify();  // the same page was asked for twice
    }

    @Test
    void usesRetryAfterWhenGitHubSendsIt() {
        server.expect(requestTo(FIRST_PAGE_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "5"));
        server.expect(requestTo(FIRST_PAGE_URL))
                .andRespond(withSuccess(array(issue(1)), MediaType.APPLICATION_JSON));

        source("test-token").fetchPage(REPO, null);

        assertThat(waits).containsExactly(Duration.ofSeconds(5));
        server.verify();
    }

    @Test
    void secondaryRateLimitWithoutHeadersWaitsOneMinute() {
        server.expect(requestTo(FIRST_PAGE_URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\": \"You have exceeded a secondary rate limit.\"}"));
        server.expect(requestTo(FIRST_PAGE_URL))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        source("test-token").fetchPage(REPO, null);

        assertThat(waits).containsExactly(Duration.ofMinutes(1));
    }

    @Test
    void givesUpAfterThreeRetries() {
        server.expect(times(4), requestTo(FIRST_PAGE_URL))   // 1 try + 3 retries
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .header("X-RateLimit-Remaining", "0")
                        .header("X-RateLimit-Reset", String.valueOf(NOW.getEpochSecond() + 10)));

        GitHubIssueSource source = source("test-token");

        assertThatThrownBy(() -> source.fetchPage(REPO, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("after 3 retries")
                .hasMessageNotContaining("test-token");
        assertThat(waits).hasSize(3);
        server.verify();
    }

    @Test
    void forbiddenThatIsNotARateLimitFailsAtOnceWithAClearMessage() {
        server.expect(requestTo(FIRST_PAGE_URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .header("X-RateLimit-Remaining", "4999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\": \"Resource not accessible by personal access token\"}"));

        GitHubIssueSource source = source("test-token");

        assertThatThrownBy(() -> source.fetchPage(REPO, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("403 (not a rate limit)")
                .hasMessageContaining("Resource not accessible")
                .hasMessageNotContaining("test-token");
        assertThat(waits).isEmpty();
    }

    // --- errors and input checks ---

    @Test
    void unknownRepoThrows() {
        server.expect(requestTo(FIRST_PAGE_URL)).andRespond(withResourceNotFound());

        assertThatThrownBy(() -> source("test-token").fetchPage(REPO, null))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThat(waits).isEmpty();
    }

    @Test
    void rejectsRepoWithoutOwner() {
        assertThatThrownBy(() -> source("test-token").fetchPage("hello", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void neverSendsTheTokenToAnotherHost() {
        GitHubIssueSource source = source("test-token");

        assertThatThrownBy(() -> source.fetchPage(REPO, "https://evil.example.com/issues?page=2"))
                .isInstanceOf(IllegalArgumentException.class);
        // Looks like GitHub at first glance, but the host is api.github.com.evil.example.com
        assertThatThrownBy(() -> source.fetchPage(REPO, "https://api.github.com.evil.example.com/issues"))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();  // no expectations set, so this also proves no request was sent
    }

    @Test
    void nextUrlReadsTheLinkHeader() {
        assertThat(GitHubIssueSource.nextUrl(null)).isNull();
        assertThat(GitHubIssueSource.nextUrl("<https://a/2>; rel=\"next\", <https://a/9>; rel=\"last\""))
                .isEqualTo("https://a/2");
        assertThat(GitHubIssueSource.nextUrl("<https://a/1>; rel=\"prev\", <https://a/1>; rel=\"first\""))
                .isNull();
    }

    // --- small JSON builders for test data ---

    private static String issue(int number) {
        return """
                {"id": %d, "number": %d, "title": "Issue %d", "state": "open",
                 "repository_url": "https://api.github.com/repos/octo-org/hello",
                 "html_url": "https://github.com/octo-org/hello/issues/%d",
                 "author_association": "NONE", "labels": [],
                 "created_at": "2024-01-01T00:00:00Z", "updated_at": "2024-01-01T00:00:00Z"}
                """.formatted(1000 + number, number, number, number);
    }

    private static String pullRequest(int number) {
        return """
                {"id": %d, "number": %d, "title": "PR %d", "state": "open",
                 "repository_url": "https://api.github.com/repos/octo-org/hello",
                 "pull_request": {"url": "https://api.github.com/repos/octo-org/hello/pulls/%d"}}
                """.formatted(1000 + number, number, number, number);
    }

    private static String array(String... items) {
        return "[" + String.join(",", items) + "]";
    }
}
