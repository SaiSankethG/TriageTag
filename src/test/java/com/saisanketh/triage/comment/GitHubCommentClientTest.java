package com.saisanketh.triage.comment;

import com.saisanketh.triage.comment.GitHubCommentClient.PostedComment;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubCommentClientTest {

    @Test
    void postsTheBodyWithTheInstallationToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.github.com/repos/octo-org/hello/issues/7/comments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ghs_test"))
                .andExpect(jsonPath("$.body").value("hello"))
                .andRespond(withSuccess("{\"id\": 99, \"html_url\": \"https://github.com/octo-org/hello/issues/7#issuecomment-99\"}",
                        MediaType.APPLICATION_JSON));

        PostedComment posted = new GitHubCommentClient(builder, "https://api.github.com")
                .post("octo-org/hello", 7, "hello", "ghs_test");

        assertThat(posted.id()).isEqualTo(99);
        assertThat(posted.url()).endsWith("#issuecomment-99");
        server.verify();
    }
}
