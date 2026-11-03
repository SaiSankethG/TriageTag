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
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches issues from the GitHub REST API with Spring's RestClient.
 * Docs: https://docs.github.com/en/rest/issues/issues#list-repository-issues
 *
 * <p>Paging: GitHub puts the URL of the next page in the Link header (rel="next").
 * We hand that URL back as the cursor; callers never need to know it is a URL.
 */
@Slf4j
@Component
public class GitHubIssueSource implements IssueSource {

    /** The most GitHub allows per page. */
    static final int PAGE_SIZE = 100;

    private final RestClient restClient;
    private final String apiUrl;
    private final GitHubIssueMapper mapper;

    /** Used by Spring. The token comes from the GITHUB_TOKEN env var (see application.yml). */
    @Autowired
    public GitHubIssueSource(@Value("${github.api-url}") String apiUrl,
                             @Value("${github.token}") String token,
                             GitHubIssueMapper mapper) {
        this(RestClient.builder(), apiUrl, token, mapper);
    }

    /** Used by tests, so they can pass a builder bound to a mock server. */
    GitHubIssueSource(RestClient.Builder builder, String apiUrl, String token, GitHubIssueMapper mapper) {
        this.apiUrl = apiUrl;
        this.mapper = mapper;

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

        List<Issue> issues = new ArrayList<>();
        List<JsonNode> items = response.getBody() == null ? List.of() : response.getBody();
        for (JsonNode item : items) {
            if (!mapper.isPullRequest(item)) {
                issues.add(mapper.toIssue(item));
            }
        }

        String nextCursor = nextUrl(response.getHeaders().getFirst(HttpHeaders.LINK));
        return new IssuePage(issues, nextCursor);
    }

    private ResponseEntity<List<JsonNode>> fetchFirstPage(String repo) {
        String[] ownerAndName = repo.split("/");
        if (ownerAndName.length != 2 || ownerAndName[0].isBlank() || ownerAndName[1].isBlank()) {
            throw new IllegalArgumentException("repo must look like 'owner/name', got: " + repo);
        }
        return restClient.get()
                .uri("/repos/{owner}/{name}/issues?state=all&per_page={size}",
                        ownerAndName[0], ownerAndName[1], PAGE_SIZE)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<JsonNode>>() {});
    }

    private ResponseEntity<List<JsonNode>> fetchNextPage(String cursor) {
        // Every request carries our token, so only ever follow URLs on the GitHub API.
        // Without this check, a bad cursor could send the token to another server.
        if (!cursor.startsWith(apiUrl + "/")) {
            throw new IllegalArgumentException("cursor is not a GitHub API URL: " + cursor);
        }
        // URI.create keeps the URL exactly as GitHub sent it (it is already encoded).
        return restClient.get()
                .uri(URI.create(cursor))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<JsonNode>>() {});
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
