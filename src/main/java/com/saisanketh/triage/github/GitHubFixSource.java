package com.saisanketh.triage.github;

import com.saisanketh.triage.source.FixInfo;
import com.saisanketh.triage.source.FixSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Asks GitHub's GraphQL API what closed an issue (a pull request or a commit) and which milestone it is in.
 *
 * <p>The version: the closing PR's milestone if it has one, otherwise the issue's own milestone. In spring-boot the team
 * puts every fixed issue in the milestone of the release that contains the fix (e.g. "4.0.10"), so this is the
 * "fixed in" version; the closer is usually a commit.
 */
@Component
public class GitHubFixSource implements FixSource {

    static final String QUERY = """
            query($owner: String!, $name: String!, $number: Int!) {
              repository(owner: $owner, name: $name) {
                issue(number: $number) {
                  milestone { title }
                  timelineItems(last: 20, itemTypes: [CLOSED_EVENT]) {
                    nodes {
                      ... on ClosedEvent {
                        closer {
                          __typename
                          ... on PullRequest { number url milestone { title } }
                          ... on Commit { url }
                        }
                      }
                    }
                  }
                }
              }
            }
            """;

    private final RestClient restClient;
    private final boolean hasToken;

    @Autowired
    public GitHubFixSource(@Value("${github.api-url}") String apiUrl, @Value("${github.token}") String token) {
        this(RestClient.builder(), apiUrl, token);
    }

    /** Used by tests, so they can pass a builder bound to a mock server. */
    GitHubFixSource(RestClient.Builder builder, String apiUrl, String token) {
        this.hasToken = token != null && !token.isBlank();
        builder.baseUrl(apiUrl);
        if (hasToken) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        this.restClient = builder.build();
    }

    @Override
    public FixInfo findFix(String repo, int number) {
        if (!hasToken) {
            throw new IllegalStateException("GitHub's GraphQL API needs GITHUB_TOKEN");
        }
        String[] ownerAndName = repo.split("/");
        if (ownerAndName.length != 2) {
            throw new IllegalArgumentException("repo must look like 'owner/name', got: " + repo);
        }
        JsonNode response = restClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("query", QUERY,
                        "variables", Map.of("owner", ownerAndName[0], "name", ownerAndName[1], "number", number)))
                .retrieve()
                .body(JsonNode.class);

        JsonNode errors = response == null ? null : response.get("errors");
        if (errors != null && errors.size() > 0) {
            if ("NOT_FOUND".equals(text(errors.get(0).get("type")))) {
                return FixInfo.none();
            }
            throw new IllegalStateException("GitHub GraphQL error for " + repo + "#" + number + ": "
                    + text(errors.get(0).get("message")));
        }
        JsonNode issue = response == null ? null : response.path("data").path("repository").path("issue");
        if (issue == null || issue.isMissingNode() || issue.isNull()) {
            return FixInfo.none();
        }
        return decide(issue);
    }

    /** The latest close's closer, and the PR's milestone, else the issue's. Static so tests can call it directly. */
    static FixInfo decide(JsonNode issue) {
        JsonNode closer = null;
        for (JsonNode event : issue.path("timelineItems").path("nodes")) {   // oldest first: keep the latest
            JsonNode c = event.get("closer");
            closer = (c == null || c.isNull()) ? null : c;
        }
        String issueMilestone = text(issue.path("milestone").get("title"));
        if (closer == null) {
            return new FixInfo(null, null, null, issueMilestone);
        }
        String type = text(closer.get("__typename"));
        Integer prNumber = closer.get("number") == null ? null : closer.get("number").asInt();
        String prMilestone = text(closer.path("milestone").get("title"));
        return new FixInfo(type, prNumber, text(closer.get("url")), prMilestone != null ? prMilestone : issueMilestone);
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode() ? null : node.asString();
    }
}
