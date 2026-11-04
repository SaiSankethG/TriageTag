package com.saisanketh.triage.github;

import com.saisanketh.triage.source.DuplicateLink;
import com.saisanketh.triage.source.DuplicateLink.Evidence;
import com.saisanketh.triage.source.DuplicateSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the original of a duplicate GitHub issue, using GitHub's GraphQL API
 * (the REST API doesn't say which issue a duplicate points to; GraphQL does).
 *
 * <p>Signals, strongest first (found by looking at real spring-boot issues, see learning-notes step F):
 * <ol>
 *   <li>The issue was closed with "Close as duplicate of #N" ({@code ClosedEvent.duplicateOf}).</li>
 *   <li>A "marked as duplicate" link, and/or a maintainer comment like "Duplicate of #N".
 *       These must agree on one issue, otherwise the answer is AMBIGUOUS.</li>
 * </ol>
 * Loose wording like "see #123" is not used: it can point to anything.
 */
@Component
public class GitHubDuplicateSource implements DuplicateSource {

    static final String QUERY = """
            query($owner: String!, $name: String!, $number: Int!) {
              repository(owner: $owner, name: $name) {
                issue(number: $number) {
                  timelineItems(last: 50, itemTypes: [CLOSED_EVENT, MARKED_AS_DUPLICATE_EVENT, UNMARKED_AS_DUPLICATE_EVENT]) {
                    nodes {
                      __typename
                      ... on ClosedEvent { duplicateOf { ...Target } }
                      ... on MarkedAsDuplicateEvent { duplicate { ...Target } canonical { ...Target } }
                    }
                  }
                  comments(last: 50) { nodes { authorAssociation body } }
                }
              }
            }
            fragment Target on IssueOrPullRequest {
              ... on Issue { number repository { nameWithOwner } }
              ... on PullRequest { number repository { nameWithOwner } }
            }
            """;

    /**
     * "Duplicate of #123", "Duplicates gh-123", "This is a duplicate of https://github.com/o/r/issues/123".
     * Groups: 1 = number after '#', 2 = number after 'gh-', 3 = "owner/repo" of a URL, 4 = number of a URL.
     * Not matched on purpose: "duplicated by #123" (the other direction) and "see #123" (too vague).
     */
    static final Pattern DUPLICATE_COMMENT = Pattern.compile(
            "(?i)\\bduplicate[sd]?\\s+(?:of\\s+)?"
                    + "(?:#(\\d+)|gh-(\\d+)|https://github\\.com/([\\w.-]+/[\\w.-]+)/issues/(\\d+))");

    /** Only comments by people with write access count; anyone else could be guessing. */
    static final Set<String> MAINTAINERS = Set.of("OWNER", "MEMBER", "COLLABORATOR");

    private final RestClient restClient;
    private final boolean hasToken;

    @Autowired
    public GitHubDuplicateSource(@Value("${github.api-url}") String apiUrl,
                                 @Value("${github.token}") String token) {
        this(RestClient.builder(), apiUrl, token);
    }

    /** Used by tests, so they can pass a builder bound to a mock server. */
    GitHubDuplicateSource(RestClient.Builder builder, String apiUrl, String token) {
        this.hasToken = token != null && !token.isBlank();
        builder.baseUrl(apiUrl);
        if (hasToken) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        this.restClient = builder.build();
    }

    @Override
    public DuplicateLink findOriginal(String repo, int number) {
        if (!hasToken) {
            throw new IllegalStateException("The duplicates step needs GITHUB_TOKEN: GitHub's GraphQL API requires a token");
        }
        String[] ownerAndName = repo.split("/");
        if (ownerAndName.length != 2) {
            throw new IllegalArgumentException("repo must look like 'owner/name', got: " + repo);
        }

        // GraphQL is always a POST of {"query": ..., "variables": {...}}; variables keep the query fixed.
        Map<String, Object> request = Map.of(
                "query", QUERY,
                "variables", Map.of("owner", ownerAndName[0], "name", ownerAndName[1], "number", number));
        JsonNode response = restClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class);

        // GraphQL reports most problems with HTTP 200 and an "errors" list, so check it ourselves.
        JsonNode errors = response == null ? null : response.get("errors");
        if (errors != null && errors.size() > 0) {
            if ("NOT_FOUND".equals(text(errors.get(0).get("type")))) {
                return DuplicateLink.notFound();   // e.g. the issue was deleted or moved to another repo
            }
            throw new IllegalStateException("GitHub GraphQL error for " + repo + "#" + number + ": "
                    + text(errors.get(0).get("message")));
        }
        JsonNode issue = response == null ? null : response.path("data").path("repository").path("issue");
        if (issue == null || issue.isMissingNode() || issue.isNull()) {
            return DuplicateLink.notFound();
        }
        return decide(issue, repo, number);
    }

    /** The decision rules, on the "issue" part of the GraphQL answer. Static so tests can call it directly. */
    static DuplicateLink decide(JsonNode issue, String repo, int number) {
        Target self = new Target(repo, number);
        JsonNode lastClose = null;
        Set<Target> linkedByEvent = new LinkedHashSet<>();
        boolean unmarked = false;

        for (JsonNode event : issue.path("timelineItems").path("nodes")) {
            String type = text(event.get("__typename"));
            if ("ClosedEvent".equals(type)) {
                lastClose = event;                         // events come oldest first: keep the latest close
            } else if ("MarkedAsDuplicateEvent".equals(type)) {
                Target other = otherSide(event, self);
                if (other != null) {
                    linkedByEvent.add(other);
                }
            } else if ("UnmarkedAsDuplicateEvent".equals(type)) {
                unmarked = true;
            }
        }

        // 1. "Close as duplicate of #N" is structured data: if the latest close has it, that's the answer.
        if (lastClose != null) {
            Target closedAsDuplicateOf = target(lastClose.get("duplicateOf"));
            if (closedAsDuplicateOf != null && !closedAsDuplicateOf.equals(self)) {
                return toLink(closedAsDuplicateOf, self, Evidence.CLOSE_REASON);
            }
        }

        // 2. Otherwise "marked as duplicate" links and maintainer comments must agree on one issue.
        if (unmarked) {
            linkedByEvent.clear();   // someone undid a "marked as duplicate", so we don't trust those links
        }
        Set<Target> candidates = new LinkedHashSet<>(linkedByEvent);
        candidates.addAll(commentTargets(issue, self));

        if (candidates.isEmpty()) {
            return DuplicateLink.notFound();
        }
        if (candidates.size() > 1) {
            return DuplicateLink.ambiguous();
        }
        Target only = candidates.iterator().next();
        return toLink(only, self, linkedByEvent.contains(only) ? Evidence.DUPLICATE_EVENT : Evidence.COMMENT);
    }

    /**
     * A "marked as duplicate" event links two issues. On real data its duplicate/canonical sides are not
     * always the way round you'd expect (spring-boot #16344: the comment says "Duplicate of #16381" but the
     * event lists #16344 as canonical). So we only use it as "this issue is linked to that one".
     */
    private static Target otherSide(JsonNode event, Target self) {
        Target duplicate = target(event.get("duplicate"));
        Target canonical = target(event.get("canonical"));
        if (self.equals(duplicate)) {
            return canonical;
        }
        if (self.equals(canonical)) {
            return duplicate;
        }
        return null;
    }

    private static Set<Target> commentTargets(JsonNode issue, Target self) {
        Set<Target> targets = new LinkedHashSet<>();
        for (JsonNode comment : issue.path("comments").path("nodes")) {
            if (!MAINTAINERS.contains(text(comment.get("authorAssociation")))) {
                continue;
            }
            String body = text(comment.get("body"));
            if (body == null) {
                continue;
            }
            Matcher m = DUPLICATE_COMMENT.matcher(body);
            while (m.find()) {
                Target t;
                if (m.group(1) != null) {
                    t = new Target(self.repo(), Integer.parseInt(m.group(1)));
                } else if (m.group(2) != null) {
                    t = new Target(self.repo(), Integer.parseInt(m.group(2)));
                } else {
                    t = new Target(m.group(3), Integer.parseInt(m.group(4)));
                }
                if (!t.equals(self)) {
                    targets.add(t);
                }
            }
        }
        return targets;
    }

    private static DuplicateLink toLink(Target original, Target self, Evidence evidence) {
        return original.repo().equals(self.repo())
                ? DuplicateLink.found(original.number(), evidence)
                : DuplicateLink.otherRepo();
    }

    /** {"number": 1, "repository": {"nameWithOwner": "o/r"}} -> Target, or null if missing. */
    private static Target target(JsonNode node) {
        if (node == null || node.isNull() || node.get("number") == null) {
            return null;
        }
        String repo = text(node.path("repository").get("nameWithOwner"));
        return repo == null ? null : new Target(repo, node.get("number").asInt());
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode() ? null : node.asString();
    }

    /** One issue: repo (lower case, so "Spring-Projects/X" equals "spring-projects/x") + number. */
    record Target(String repo, int number) {
        Target {
            repo = repo.toLowerCase(Locale.ROOT);
        }
    }
}
