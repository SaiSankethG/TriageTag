package com.saisanketh.triage.comment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/** Posts an issue comment as the GitHub App. Docs: https://docs.github.com/en/rest/issues/comments#create-an-issue-comment */
@Component
public class GitHubCommentClient {

    /** What GitHub returns for a new comment. */
    public record PostedComment(long id, String url) {
    }

    private final RestClient restClient;

    @Autowired
    public GitHubCommentClient(@Value("${github.api-url}") String apiUrl) {
        this(RestClient.builder(), apiUrl);
    }

    GitHubCommentClient(RestClient.Builder builder, String apiUrl) {
        this.restClient = builder.baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    /** @param installationToken the App's token for this repo (never logged) */
    public PostedComment post(String repo, int number, String body, String installationToken) {
        String[] ownerAndName = repo.split("/");
        if (ownerAndName.length != 2) {
            throw new IllegalArgumentException("repo must look like 'owner/name', got: " + repo);
        }
        JsonNode response = restClient.post()
                .uri("/repos/{owner}/{name}/issues/{number}/comments", ownerAndName[0], ownerAndName[1], number)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + installationToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("body", body))
                .retrieve()
                .body(JsonNode.class);
        if (response == null || response.get("id") == null) {
            throw new IllegalStateException("GitHub returned no comment id for " + repo + "#" + number);
        }
        return new PostedComment(response.get("id").asLong(), response.path("html_url").asString());
    }
}
