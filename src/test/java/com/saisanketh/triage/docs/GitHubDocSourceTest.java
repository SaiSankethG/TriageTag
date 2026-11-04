package com.saisanketh.triage.docs;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubDocSourceTest {

    private final RestClient.Builder apiBuilder = RestClient.builder();
    private final RestClient.Builder rawBuilder = RestClient.builder();
    private final MockRestServiceServer apiServer = MockRestServiceServer.bindTo(apiBuilder).build();
    private final MockRestServiceServer rawServer = MockRestServiceServer.bindTo(rawBuilder).build();
    private final GitHubDocSource source = new GitHubDocSource(apiBuilder, rawBuilder, "https://api.github.com",
            "test-token", "https://raw.githubusercontent.com");

    @Test
    void listsFilesOfTheTreeWithTheToken() {
        apiServer.expect(requestTo("https://api.github.com/repos/o/r/git/trees/v1.0.0?recursive=1"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andRespond(withSuccess("""
                        {"truncated": false, "tree": [
                          {"path": "docs", "type": "tree"}, {"path": "docs/a.adoc", "type": "blob"}]}""",
                        MediaType.APPLICATION_JSON));

        assertThat(source.listFiles("o/r", "v1.0.0")).containsExactly("docs/a.adoc");
    }

    @Test
    void readsRawFilesWithoutTheTokenAndRetriesANetworkError() {
        rawServer.expect(requestTo("https://raw.githubusercontent.com/o/r/v1.0.0/docs/modules/a.adoc"))
                .andRespond(withException(new IOException("connection reset")));
        rawServer.expect(requestTo("https://raw.githubusercontent.com/o/r/v1.0.0/docs/modules/a.adoc"))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess("= A", MediaType.TEXT_PLAIN));

        assertThat(source.read("o/r", "v1.0.0", "docs/modules/a.adoc")).isEqualTo("= A");
    }

    @Test
    void refusesPathsThatCouldLeaveTheRepo() {
        assertThatThrownBy(() -> source.read("o/r", "v1", "../secret")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> source.read("o/r", "v1", "a?b=c")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> source.listFiles("o/r/x", "v1")).isInstanceOf(IllegalArgumentException.class);
    }
}
