package com.saisanketh.triage.github;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.source.IssuePage;
import com.saisanketh.triage.source.IssueSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Fetches issues from the GitHub REST API with Spring's RestClient.
 * Docs: https://docs.github.com/en/rest/issues/issues#list-repository-issues
 *
 * <p>Paging: GitHub puts the URL of the next page in the Link header (rel="next").
 * We hand that URL back as the cursor; callers never need to know it is a URL.
 *
 * <p>Rate limits: if GitHub says "slow down" (403/429 + rate-limit headers), we wait as long
 * as GitHub asks and retry the same page, at most {@link #MAX_RETRIES} times.
 * Docs: https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api
 */
@Slf4j
@Component
public class GitHubIssueSource implements IssueSource {

    /** The most GitHub allows per page. */
    static final int PAGE_SIZE = 100;
    /** How many times we retry one page after a rate-limit answer before giving up. */
    static final int MAX_RETRIES = 3;
    /** Log the remaining rate limit once every this many pages. */
    static final int LOG_EVERY_PAGES = 10;
    /** GitHub's advice when a rate-limit answer has no timing headers: wait at least a minute. */
    static final Duration DEFAULT_WAIT = Duration.ofMinutes(1);

    /** Waits for a while. An interface so tests can record the wait instead of really sleeping. */
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final RestClient restClient;
    private final String apiUrl;
    private final GitHubIssueMapper mapper;
    private final Clock clock;
    private final Sleeper sleeper;
    /** Pages fetched since startup; Atomic because two backfills could run at the same time. */
    private final AtomicInteger pagesFetched = new AtomicInteger();

    /** Used by Spring. The token comes from the GITHUB_TOKEN env var (see application.yml). */
    @Autowired
    public GitHubIssueSource(@Value("${github.api-url}") String apiUrl,
                             @Value("${github.token}") String token,
                             GitHubIssueMapper mapper) {
        this(RestClient.builder(), apiUrl, token, mapper,
                Clock.systemUTC(), duration -> Thread.sleep(duration));
    }

    /** Used by tests: a builder bound to a mock server, a fixed clock and a fake sleeper. */
    GitHubIssueSource(RestClient.Builder builder, String apiUrl, String token, GitHubIssueMapper mapper,
                      Clock clock, Sleeper sleeper) {
        this.apiUrl = apiUrl;
        this.mapper = mapper;
        this.clock = clock;
        this.sleeper = sleeper;

        builder.baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28");

        if (token == null || token.isBlank()) {
            log.warn("GITHUB_TOKEN is not set: calling GitHub without a token (limit 60 requests/hour)");
        } else {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        this.restClient = builder.build();
    }

    @Override
    public IssuePage fetchPage(String repo, String cursor) {
        ResponseEntity<List<JsonNode>> response = (cursor == null)
                ? fetchFirstPage(repo)
                : fetchNextPage(cursor);
        logRateLimitEveryFewPages(response.getHeaders());

        List<Issue> issues = new ArrayList<>();
        int skipped = 0;
        List<JsonNode> items = response.getBody() == null ? List.of() : response.getBody();
        for (JsonNode item : items) {
            if (mapper.isPullRequest(item)) {
                skipped++;               // GitHub mixes PRs into the issues list; we only keep issues
            } else {
                issues.add(mapper.toIssue(item));
            }
        }

        String nextCursor = nextUrl(response.getHeaders().getFirst(HttpHeaders.LINK));
        return new IssuePage(issues, nextCursor, skipped);
    }

    private ResponseEntity<List<JsonNode>> fetchFirstPage(String repo) {
        String[] ownerAndName = repo.split("/");
        if (ownerAndName.length != 2 || ownerAndName[0].isBlank() || ownerAndName[1].isBlank()) {
            throw new IllegalArgumentException("repo must look like 'owner/name', got: " + repo);
        }
        // Oldest first, so the replay test (M3) and a resumed backfill see issues in date order.
        return getWithRateLimitRetry("first page of " + repo, () -> restClient.get()
                .uri("/repos/{owner}/{name}/issues?state=all&per_page={size}&sort=created&direction=asc",
                        ownerAndName[0], ownerAndName[1], PAGE_SIZE)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<JsonNode>>() {}));
    }

    private ResponseEntity<List<JsonNode>> fetchNextPage(String cursor) {
        // Every request carries our token, so only ever follow URLs on the GitHub API.
        // Without this check, a bad cursor could send the token to another server.
        if (!cursor.startsWith(apiUrl + "/")) {
            throw new IllegalArgumentException("cursor is not a GitHub API URL: " + cursor);
        }
        // URI.create keeps the URL exactly as GitHub sent it (it is already encoded).
        return getWithRateLimitRetry(cursor, () -> restClient.get()
                .uri(URI.create(cursor))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<JsonNode>>() {}));
    }

    /**
     * Runs the request. If GitHub answers with a rate-limit error, waits as long as GitHub
     * asks and tries again, at most MAX_RETRIES times. Any other error is thrown as it is,
     * except a plain 403, which gets a clearer message.
     *
     * @param what    a short description for log and error messages (never contains the token)
     * @param request the HTTP call; a Supplier so we can run the same call again
     */
    private ResponseEntity<List<JsonNode>> getWithRateLimitRetry(
            String what, Supplier<ResponseEntity<List<JsonNode>>> request) {
        int retries = 0;
        while (true) {
            try {
                return request.get();
            } catch (HttpClientErrorException e) {
                if (!isRateLimited(e)) {
                    if (e.getStatusCode().value() == 403) {
                        throw new IllegalStateException("GitHub refused " + what + " with 403 (not a rate limit): "
                                + e.getResponseBodyAsString()
                                + ". Check that GITHUB_TOKEN is valid and can read this repo.", e);
                    }
                    throw e;
                }
                if (retries == MAX_RETRIES) {
                    throw new IllegalStateException("GitHub rate limit still exceeded after "
                            + MAX_RETRIES + " retries for " + what + ". Try again later.", e);
                }
                retries++;
                Duration wait = waitTime(e.getResponseHeaders());
                log.warn("GitHub rate limit hit ({}) for {}: waiting {} s, then retry {}/{}",
                        e.getStatusCode().value(), what, wait.toSeconds(), retries, MAX_RETRIES);
                sleep(wait);
            }
        }
    }

    /**
     * True if this error means "too many requests, slow down", not "you may not do this".
     * GitHub uses 429, or 403 together with rate-limit headers or a "rate limit" message.
     */
    static boolean isRateLimited(HttpClientErrorException e) {
        int status = e.getStatusCode().value();
        if (status == 429) {
            return true;
        }
        if (status != 403) {
            return false;
        }
        HttpHeaders headers = e.getResponseHeaders();
        if (headers != null) {
            if ("0".equals(headers.getFirst("X-RateLimit-Remaining"))
                    || headers.getFirst(HttpHeaders.RETRY_AFTER) != null) {
                return true;
            }
        }
        // Secondary rate limits may come without those headers; the message still says so.
        return e.getResponseBodyAsString().toLowerCase().contains("rate limit");
    }

    /**
     * How long to wait before retrying, in GitHub's order of preference:
     * 1. Retry-After (seconds), 2. until X-RateLimit-Reset when nothing is left, 3. one minute.
     */
    Duration waitTime(HttpHeaders headers) {
        if (headers != null) {
            String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
            if (retryAfter != null) {
                return Duration.ofSeconds(Long.parseLong(retryAfter.trim()));
            }
            String remaining = headers.getFirst("X-RateLimit-Remaining");
            String reset = headers.getFirst("X-RateLimit-Reset");   // a Unix time in seconds
            if ("0".equals(remaining) && reset != null) {
                long secondsLeft = Long.parseLong(reset.trim()) - clock.instant().getEpochSecond();
                return Duration.ofSeconds(Math.max(secondsLeft, 0) + 1);  // +1 s in case our clocks differ
            }
        }
        return DEFAULT_WAIT;
    }

    private void sleep(Duration wait) {
        try {
            sleeper.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // keep the "interrupted" flag for whoever asked us to stop
            throw new IllegalStateException("Interrupted while waiting for the GitHub rate limit", e);
        }
    }

    private void logRateLimitEveryFewPages(HttpHeaders headers) {
        int pages = pagesFetched.incrementAndGet();
        if (pages % LOG_EVERY_PAGES == 0) {
            String reset = headers.getFirst("X-RateLimit-Reset");
            log.info("Fetched {} pages from GitHub; rate limit remaining: {} (resets at {})",
                    pages,
                    headers.getFirst("X-RateLimit-Remaining"),
                    reset == null ? "unknown" : Instant.ofEpochSecond(Long.parseLong(reset.trim())));
        }
    }

    /**
     * Finds the rel="next" URL in a Link header, or returns null if there is none.
     * Example header (one line in reality):
     * <pre>
     * &lt;https://api.github.com/repositories/1/issues?page=2&gt;; rel="next",
     * &lt;https://api.github.com/repositories/1/issues?page=5&gt;; rel="last"
     * </pre>
     */
    static String nextUrl(String linkHeader) {
        if (linkHeader == null) {
            return null;
        }
        for (String link : linkHeader.split(",")) {      // one entry per link
            String[] parts = link.split(";");             // "<url>", " rel=\"next\""
            String url = parts[0].trim();
            for (int i = 1; i < parts.length; i++) {
                if (parts[i].trim().equals("rel=\"next\"")
                        && url.startsWith("<") && url.endsWith(">")) {
                    return url.substring(1, url.length() - 1);  // strip the < >
                }
            }
        }
        return null;
    }
}
