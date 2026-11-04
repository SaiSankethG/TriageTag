package com.saisanketh.triage.github;

import com.saisanketh.triage.source.FixInfo;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubFixSourceTest {

    private final JsonMapper json = JsonMapper.builder().build();

    private FixInfo decide(String issueJson) {
        return GitHubFixSource.decide(json.readTree(issueJson));
    }

    @Test
    void aClosingCommitWithTheIssueMilestone_like52582() {
        FixInfo fix = decide("""
                {"milestone": {"title": "4.0.10"}, "timelineItems": {"nodes": [
                  {"closer": {"__typename": "Commit", "url": "https://github.com/o/r/commit/107ba4b"}}]}}""");

        assertThat(fix).isEqualTo(new FixInfo("Commit", null, "https://github.com/o/r/commit/107ba4b", "4.0.10"));
    }

    @Test
    void aClosingPullRequestsMilestoneWins() {
        FixInfo fix = decide("""
                {"milestone": {"title": "3.4.0"}, "timelineItems": {"nodes": [
                  {"closer": {"__typename": "PullRequest", "number": 77, "url": "https://github.com/o/r/pull/77",
                              "milestone": {"title": "3.4.1"}}}]}}""");

        assertThat(fix).isEqualTo(new FixInfo("PullRequest", 77, "https://github.com/o/r/pull/77", "3.4.1"));
    }

    @Test
    void theLatestCloseCountsAndAManualCloseHasNoCloser() {
        FixInfo fix = decide("""
                {"milestone": null, "timelineItems": {"nodes": [
                  {"closer": {"__typename": "Commit", "url": "u"}},
                  {"closer": null}]}}""");

        assertThat(fix).isEqualTo(FixInfo.none());
    }

    @Test
    void asksGraphQlWithTheIssueNumber() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.github.com/graphql"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.variables.number").value(52582))
                .andRespond(withSuccess("""
                        {"data": {"repository": {"issue": {"milestone": {"title": "4.0.10"},
                          "timelineItems": {"nodes": []}}}}}""", MediaType.APPLICATION_JSON));

        FixInfo fix = new GitHubFixSource(builder, "https://api.github.com", "test-token")
                .findFix("spring-projects/spring-boot", 52582);

        assertThat(fix.fixVersion()).isEqualTo("4.0.10");
        server.verify();
    }

    @Test
    void aMissingIssueIsNoneAndNoTokenIsAnError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.github.com/graphql")).andRespond(withSuccess(
                "{\"errors\": [{\"type\": \"NOT_FOUND\", \"message\": \"gone\"}]}", MediaType.APPLICATION_JSON));

        assertThat(new GitHubFixSource(builder, "https://api.github.com", "t").findFix("o/r", 1)).isEqualTo(FixInfo.none());
        assertThatThrownBy(() -> new GitHubFixSource("https://api.github.com", "").findFix("o/r", 1))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("GITHUB_TOKEN");
    }
}
