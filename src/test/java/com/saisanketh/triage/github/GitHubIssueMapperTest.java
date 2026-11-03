package com.saisanketh.triage.github;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.domain.SourceType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubIssueMapperTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final GitHubIssueMapper mapper = new GitHubIssueMapper();

    @Test
    void mapsEveryFieldOfAClosedDuplicate() throws IOException {
        JsonNode node = readFixture("issue-closed-duplicate.json");

        Issue issue = mapper.toIssue(node);

        assertThat(issue.getId()).isNull();                         // set by the database on save
        assertThat(issue.getSource()).isEqualTo(SourceType.GITHUB);
        assertThat(issue.getExternalId()).isEqualTo("2800000042");  // bigger than an int: read as long
        assertThat(issue.getRepo()).isEqualTo("octo-org/hello");
        assertThat(issue.getNumber()).isEqualTo(42);
        assertThat(issue.getTitle()).isEqualTo("Startup fails when port 8080 is in use");
        assertThat(issue.getBody()).isEqualTo("Steps to reproduce: start two instances.");
        assertThat(issue.getState()).isEqualTo("closed");
        assertThat(issue.getStateReason()).isEqualTo("duplicate");
        assertThat(issue.getIssueType()).isEqualTo("Bug");
        assertThat(issue.getAuthorAssociation()).isEqualTo("CONTRIBUTOR");
        assertThat(issue.getMilestone()).isEqualTo("3.4.2");
        assertThat(issue.getHtmlUrl()).isEqualTo("https://github.com/octo-org/hello/issues/42");
        assertThat(issue.getCreatedAt()).isEqualTo(Instant.parse("2024-03-01T10:15:30Z"));
        assertThat(issue.getUpdatedAt()).isEqualTo(Instant.parse("2024-03-02T08:00:00Z"));
        assertThat(issue.getClosedAt()).isEqualTo(Instant.parse("2024-03-02T07:59:59Z"));
        assertThat(issue.getDuplicateOf()).isNull();                // filled in a later step
        assertThat(issue.getLabels()).containsExactlyInAnyOrder("type: bug", "status: duplicate");
    }

    @Test
    void mapsEveryFieldOfAnOpenIssue() throws IOException {
        JsonNode node = readFixture("issue-open-reopened.json");

        Issue issue = mapper.toIssue(node);

        assertThat(issue.getId()).isNull();
        assertThat(issue.getSource()).isEqualTo(SourceType.GITHUB);
        assertThat(issue.getExternalId()).isEqualTo("2800000044");
        assertThat(issue.getRepo()).isEqualTo("octo-org/hello");
        assertThat(issue.getNumber()).isEqualTo(44);
        assertThat(issue.getTitle()).isEqualTo("Support YAML anchors in config files");
        assertThat(issue.getBody()).isEmpty();                      // GitHub sent "", not null
        assertThat(issue.getState()).isEqualTo("open");
        assertThat(issue.getStateReason()).isEqualTo("reopened");   // open issues can have a reason too
        assertThat(issue.getIssueType()).isEqualTo("Feature");
        assertThat(issue.getAuthorAssociation()).isEqualTo("MEMBER");
        assertThat(issue.getMilestone()).isNull();
        assertThat(issue.getHtmlUrl()).isEqualTo("https://github.com/octo-org/hello/issues/44");
        assertThat(issue.getCreatedAt()).isEqualTo(Instant.parse("2025-06-10T14:20:00Z"));
        assertThat(issue.getUpdatedAt()).isEqualTo(Instant.parse("2025-07-01T09:30:15Z"));
        assertThat(issue.getClosedAt()).isNull();
        assertThat(issue.getDuplicateOf()).isNull();
        assertThat(issue.getLabels()).containsExactly("type: enhancement");
        assertThat(json.readTree(issue.getRaw())).isEqualTo(node);
    }

    @Test
    void keepsTheFullJsonInRaw() throws IOException {
        JsonNode node = readFixture("issue-closed-duplicate.json");

        Issue issue = mapper.toIssue(node);

        // Fields we don't map (user, comments, label colors...) must still be in raw.
        assertThat(json.readTree(issue.getRaw())).isEqualTo(node);
    }

    @Test
    void mapsJsonNullsToJavaNulls() throws IOException {
        JsonNode node = readFixture("issue-open-minimal.json");

        Issue issue = mapper.toIssue(node);

        assertThat(issue.getState()).isEqualTo("open");
        assertThat(issue.getBody()).isNull();
        assertThat(issue.getStateReason()).isNull();
        assertThat(issue.getIssueType()).isNull();
        assertThat(issue.getMilestone()).isNull();
        assertThat(issue.getClosedAt()).isNull();
        assertThat(issue.getLabels()).isEmpty();
    }

    @Test
    void detectsPullRequests() {
        JsonNode pullRequest = json.readTree("{\"number\": 5, \"pull_request\": {\"url\": \"x\"}}");
        JsonNode realIssue = json.readTree("{\"number\": 6}");

        assertThat(mapper.isPullRequest(pullRequest)).isTrue();
        assertThat(mapper.isPullRequest(realIssue)).isFalse();
    }

    private JsonNode readFixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/github/" + name)) {
            return json.readTree(in);
        }
    }
}
