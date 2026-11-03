package com.saisanketh.triage.github;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * Fetches issues from the GitHub REST API with Spring's RestClient.
 * Docs: https://docs.github.com/en/rest/issues/issues#list-repository-issues
 */
@Slf4j
@Component
public class GitHubIssueSource implements IssueSource {

    /** The most GitHub allows per page. */
    static final int PAGE_SIZE = 100;

    private final RestClient restClient;

    /** Used by Spring. The token comes from the GITHUB_TOKEN env var (see application.yml). */
    @Autowired
    public GitHubIssueSource(@Value("${github.api-url}") String apiUrl,
                             @Value("${github.token}") String token) {
        this(RestClient.builder(), apiUrl, token);
    }

    /** Used by tests, so they can pass a builder bound to a mock server. */
    GitHubIssueSource(RestClient.Builder builder, String apiUrl, String token) {
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
    public IssuePage fetchPage(String owner, String repo) {
        ResponseEntity<List<JsonNode>> response = restClient.get()
                .uri("/repos/{owner}/{repo}/issues?state=all&per_page={size}", owner, repo, PAGE_SIZE)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<JsonNode>>() {});

        List<JsonNode> items = response.getBody() == null ? List.of() : response.getBody();
        String linkHeader = response.getHeaders().getFirst(HttpHeaders.LINK);
        return new IssuePage(items, linkHeader);
    }
}
