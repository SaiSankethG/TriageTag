package com.saisanketh.triage.github;

import com.saisanketh.triage.source.DuplicateLink;
import com.saisanketh.triage.source.DuplicateLink.Evidence;
import com.saisanketh.triage.source.DuplicateLink.Outcome;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The cases below copy the *shape* of real spring-boot issues we looked at (numbers in the test names),
 * with made-up comment text.
 */
class GitHubDuplicateSourceTest {

    private static final String REPO = "octo-org/hello";
    private final JsonMapper json = JsonMapper.builder().build();

    // --- decision rules ---

    @Test
    void closeAsDuplicateOfWins_like52529() {
        DuplicateLink link = decide(100, """
                {"timelineItems": {"nodes": [
                   {"__typename": "ClosedEvent", "duplicateOf": %s}]},
                 "comments": {"nodes": []}}
                """.formatted(target(REPO, 90)));

        assertThat(link).isEqualTo(DuplicateLink.found(90, Evidence.CLOSE_REASON));
    }

    @Test
    void theLatestCloseCounts_like52124() {
        // Closed, reopened, then closed as duplicate: events come oldest first.
        DuplicateLink link = decide(100, """
                {"timelineItems": {"nodes": [
                   {"__typename": "ClosedEvent", "duplicateOf": null},
                   {"__typename": "ClosedEvent", "duplicateOf": %s}]},
                 "comments": {"nodes": [{"authorAssociation": "MEMBER", "body": "I think this may be a duplicate of #90."}]}}
                """.formatted(target(REPO, 90)));

        assertThat(link).isEqualTo(DuplicateLink.found(90, Evidence.CLOSE_REASON));
    }

    @Test
    void maintainerCommentWithHashNumber() {
        DuplicateLink link = decide(100, comments("MEMBER", "Duplicate of #42"));

        assertThat(link).isEqualTo(DuplicateLink.found(42, Evidence.COMMENT));
    }

    @Test
    void maintainerCommentWithFullUrlToTheSameRepo_like50073() {
        DuplicateLink link = decide(100, comments("MEMBER",
                "Duplicates https://github.com/octo-org/hello/issues/49910. Please search before opening an issue."));

        assertThat(link).isEqualTo(DuplicateLink.found(49910, Evidence.COMMENT));
    }

    @Test
    void maintainerCommentWithGhPrefix() {
        DuplicateLink link = decide(100, comments("OWNER", "This is a duplicate of gh-77"));

        assertThat(link).isEqualTo(DuplicateLink.found(77, Evidence.COMMENT));
    }

    @Test
    void originalInAnotherRepoCannotBeStored_like39044() {
        DuplicateLink link = decide(100, comments("MEMBER",
                "This is a duplicate of https://github.com/octo-org/other-project/issues/31892"));

        assertThat(link.outcome()).isEqualTo(Outcome.OTHER_REPO);
    }

    @Test
    void vagueSeeIsNotEnough_like52567() {
        DuplicateLink link = decide(100, comments("MEMBER", "Yes, see #52367"));

        assertThat(link.outcome()).isEqualTo(Outcome.NOT_FOUND);
    }

    @Test
    void duplicatedByIsTheOtherDirection() {
        DuplicateLink link = decide(100, comments("MEMBER", "This was duplicated by #101"));

        assertThat(link.outcome()).isEqualTo(Outcome.NOT_FOUND);
    }

    @Test
    void commentsByNonMaintainersAreIgnored() {
        DuplicateLink link = decide(100, comments("NONE", "Duplicate of #42"));

        assertThat(link.outcome()).isEqualTo(Outcome.NOT_FOUND);
    }

    @Test
    void twoDifferentOriginalsAreAmbiguous() {
        DuplicateLink link = decide(100, comments("MEMBER", "Duplicate of #42 and also duplicate of #43"));

        assertThat(link.outcome()).isEqualTo(Outcome.AMBIGUOUS);
    }

    @Test
    void markedAsDuplicateEventIsReadAsALinkInEitherDirection_like16344() {
        // Real case: comment on #16344 says "Duplicate of #16381", but the event lists #16344 as canonical.
        DuplicateLink link = decide(16344, """
                {"timelineItems": {"nodes": [
                   {"__typename": "MarkedAsDuplicateEvent", "duplicate": %s, "canonical": %s},
                   {"__typename": "ClosedEvent", "duplicateOf": null}]},
                 "comments": {"nodes": [{"authorAssociation": "MEMBER", "body": "Duplicate of #16381"}]}}
                """.formatted(target(REPO, 16381), target(REPO, 16344)));

        assertThat(link).isEqualTo(DuplicateLink.found(16381, Evidence.DUPLICATE_EVENT));
    }

    @Test
    void eventAndCommentThatDisagreeAreAmbiguous() {
        DuplicateLink link = decide(100, """
                {"timelineItems": {"nodes": [
                   {"__typename": "MarkedAsDuplicateEvent", "duplicate": %s, "canonical": %s}]},
                 "comments": {"nodes": [{"authorAssociation": "MEMBER", "body": "Duplicate of #43"}]}}
                """.formatted(target(REPO, 100), target(REPO, 42)));

        assertThat(link.outcome()).isEqualTo(Outcome.AMBIGUOUS);
    }

    @Test
    void anUnmarkedDuplicateEventIsNotTrusted() {
        DuplicateLink link = decide(100, """
                {"timelineItems": {"nodes": [
                   {"__typename": "MarkedAsDuplicateEvent", "duplicate": %s, "canonical": %s},
                   {"__typename": "UnmarkedAsDuplicateEvent"}]},
                 "comments": {"nodes": []}}
                """.formatted(target(REPO, 100), target(REPO, 42)));

        assertThat(link.outcome()).isEqualTo(Outcome.NOT_FOUND);
    }

    @Test
    void aCommentPointingAtItselfIsIgnored() {
        DuplicateLink link = decide(100, comments("MEMBER", "Duplicate of #100"));

        assertThat(link.outcome()).isEqualTo(Outcome.NOT_FOUND);
    }

    // --- the HTTP call ---

    @Test
    void sendsAGraphQlPostWithVariablesAndReadsTheAnswer() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.github.com/graphql"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.variables.owner").value("octo-org"))
                .andExpect(jsonPath("$.variables.name").value("hello"))
                .andExpect(jsonPath("$.variables.number").value(100))
                .andRespond(withSuccess("""
                        {"data": {"repository": {"issue": {
                           "timelineItems": {"nodes": [{"__typename": "ClosedEvent", "duplicateOf": %s}]},
                           "comments": {"nodes": []}}}}}
                        """.formatted(target(REPO, 90)), MediaType.APPLICATION_JSON));

        GitHubDuplicateSource source = new GitHubDuplicateSource(builder, "https://api.github.com", "test-token");

        assertThat(source.findOriginal(REPO, 100)).isEqualTo(DuplicateLink.found(90, Evidence.CLOSE_REASON));
        server.verify();
    }

    @Test
    void issueThatNoLongerExistsIsNotFound() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.github.com/graphql"))
                .andRespond(withSuccess("""
                        {"data": {"repository": {"issue": null}},
                         "errors": [{"type": "NOT_FOUND", "message": "Could not resolve to an Issue"}]}
                        """, MediaType.APPLICATION_JSON));

        GitHubDuplicateSource source = new GitHubDuplicateSource(builder, "https://api.github.com", "test-token");

        assertThat(source.findOriginal(REPO, 100).outcome()).isEqualTo(Outcome.NOT_FOUND);
    }

    @Test
    void otherGraphQlErrorsStopWithTheMessage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.github.com/graphql"))
                .andRespond(withSuccess("""
                        {"errors": [{"type": "RATE_LIMITED", "message": "API rate limit exceeded"}]}
                        """, MediaType.APPLICATION_JSON));

        GitHubDuplicateSource source = new GitHubDuplicateSource(builder, "https://api.github.com", "test-token");

        assertThatThrownBy(() -> source.findOriginal(REPO, 100))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API rate limit exceeded")
                .hasMessageNotContaining("test-token");
    }

    @Test
    void needsAToken() {
        GitHubDuplicateSource source = new GitHubDuplicateSource(RestClient.builder(), "https://api.github.com", "");

        assertThatThrownBy(() -> source.findOriginal(REPO, 100))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GITHUB_TOKEN");
    }

    // --- helpers ---

    private DuplicateLink decide(int number, String issueJson) {
        JsonNode issue = json.readTree(issueJson);
        return GitHubDuplicateSource.decide(issue, REPO, number);
    }

    private static String comments(String association, String body) {
        return """
                {"timelineItems": {"nodes": [{"__typename": "ClosedEvent", "duplicateOf": null}]},
                 "comments": {"nodes": [{"authorAssociation": "%s", "body": "%s"}]}}
                """.formatted(association, body);
    }

    private static String target(String repo, int number) {
        return """
                {"number": %d, "repository": {"nameWithOwner": "%s"}}""".formatted(number, repo);
    }
}
