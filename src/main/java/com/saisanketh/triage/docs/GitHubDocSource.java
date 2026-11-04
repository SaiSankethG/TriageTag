package com.saisanketh.triage.docs;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists files with GitHub's git trees API (one call, recursive) and reads them from raw.githubusercontent.com.
 * Docs: https://docs.github.com/en/rest/git/trees
 *
 * <p>The token is sent only to the API host. Raw file reads go without it (the repos are public, and a token must never
 * travel to a second host just because it is convenient).
 */
@Component
public class GitHubDocSource implements DocSource {

    static final int MAX_ATTEMPTS = 3;

    private final RestClient api;
    private final RestClient raw;

    @Autowired
    public GitHubDocSource(@Value("${github.api-url}") String apiUrl, @Value("${github.token}") String token,
                           @Value("${github.raw-url:https://raw.githubusercontent.com}") String rawUrl) {
        this(RestClient.builder(), RestClient.builder(), apiUrl, token, rawUrl);
    }

    GitHubDocSource(RestClient.Builder apiBuilder, RestClient.Builder rawBuilder, String apiUrl, String token,
                    String rawUrl) {
        apiBuilder.baseUrl(apiUrl).defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json");
        if (token != null && !token.isBlank()) {
            apiBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        this.api = apiBuilder.build();
        this.raw = rawBuilder.baseUrl(rawUrl).build();
    }

    @Override
    public List<String> listFiles(String repo, String tag) {
        String uriPath = "/repos/" + checked(repo, REPO) + "/git/trees/" + checked(tag, TAG);
        JsonNode tree = api.get().uri(b -> b.path(uriPath).queryParam("recursive", 1).build())
                .retrieve().body(JsonNode.class);
        if (tree == null) {
            return List.of();
        }
        if (tree.path("truncated").asBoolean(false)) {
            throw new IllegalStateException("GitHub truncated the file tree of " + repo + "@" + tag);
        }
        List<String> paths = new ArrayList<>();
        for (JsonNode entry : tree.path("tree")) {
            if ("blob".equals(entry.path("type").asString())) {
                paths.add(entry.path("path").asString());
            }
        }
        return paths;
    }

    @Override
    public String read(String repo, String tag, String path) {
        String uriPath = "/" + checked(repo, REPO) + "/" + checked(tag, TAG) + "/" + checked(path, PATH);
        // ~125 small downloads in a row: a network blip (seen: a dropped connection, a failed DNS lookup) shouldn't
        // fail the whole load, so each file gets up to MAX_ATTEMPTS tries.
        for (int attempt = 1; ; attempt++) {
            try {
                return raw.get().uri(b -> b.path(uriPath).build()).retrieve().body(String.class);
            } catch (ResourceAccessException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }

    static final String REPO = "[\\w.-]+/[\\w.-]+";
    static final String TAG = "[\\w.-]+";
    static final String PATH = "[\\w.-]+(/[\\w.-]+)*";

    /**
     * The parts are put into the URL path as they are (a "/" in "owner/name" or in a file path must stay a "/"), so they
     * are checked first: only letters, digits, '.', '_', '-' (and '/' between parts). No "..", no "?", no "#".
     */
    static String checked(String value, String allowed) {
        if (value == null || !value.matches(allowed) || value.contains("..")) {
            throw new IllegalArgumentException("not allowed in a GitHub path: " + value);
        }
        return value;
    }
}
