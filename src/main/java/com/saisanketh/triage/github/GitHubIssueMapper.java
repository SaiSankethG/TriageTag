package com.saisanketh.triage.github;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.domain.SourceType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Turns one issue from the GitHub REST API (as JSON) into an {@link Issue}.
 * Field names: https://docs.github.com/en/rest/issues/issues#get-an-issue
 */
@Component
public class GitHubIssueMapper {

    /** GitHub sends a pull_request field only on pull requests, never on real issues. */
    public boolean isPullRequest(JsonNode json) {
        return json.has("pull_request");
    }

    public Issue toIssue(JsonNode json) {
        Issue issue = new Issue();
        issue.setSource(SourceType.GITHUB);
        issue.setExternalId(String.valueOf(json.get("id").asLong()));
        issue.setRepo(repoFrom(text(json, "repository_url")));
        issue.setNumber(json.get("number").asInt());
        issue.setTitle(text(json, "title"));
        issue.setBody(text(json, "body"));
        issue.setState(text(json, "state"));
        issue.setStateReason(text(json, "state_reason"));
        issue.setIssueType(text(json.get("type"), "name"));        // "type": {"name": "Bug", ...} or null
        issue.setAuthorAssociation(text(json, "author_association"));
        issue.setMilestone(text(json.get("milestone"), "title"));  // "milestone": {"title": "3.4.2", ...} or null
        issue.setHtmlUrl(text(json, "html_url"));
        issue.setCreatedAt(instant(json, "created_at"));
        issue.setUpdatedAt(instant(json, "updated_at"));
        issue.setClosedAt(instant(json, "closed_at"));
        // duplicate_of is not in this JSON; it is filled in a later M1 step ("store the answer key").

        JsonNode labels = json.get("labels");
        if (labels != null) {
            for (JsonNode label : labels) {
                issue.getLabels().add(text(label, "name"));
            }
        }

        issue.setRaw(json.toString());  // the full JSON, unchanged, for the raw jsonb column
        return issue;
    }

    /**
     * "https://api.github.com/repos/spring-projects/spring-boot" -> "spring-projects/spring-boot".
     * Taken from GitHub's JSON (not from what the user typed) so the spelling is always the
     * same: GitHub accepts "Spring-Projects/Spring-Boot" in a URL, but we want one value per repo.
     */
    private static String repoFrom(String repositoryUrl) {
        String marker = "/repos/";
        return repositoryUrl.substring(repositoryUrl.indexOf(marker) + marker.length());
    }

    /** The text of a field, or null if the object or the field is missing or JSON null. */
    private static String text(JsonNode object, String field) {
        if (object == null || object.isNull()) {
            return null;
        }
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asString();
    }

    /** GitHub times look like "2024-03-01T10:15:30Z" (ISO-8601, UTC), which Instant.parse reads. */
    private static Instant instant(JsonNode object, String field) {
        String value = text(object, field);
        return value == null ? null : Instant.parse(value);
    }
}
