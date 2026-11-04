package com.saisanketh.triage.github;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureVerifierTest {

    // GitHub's own example: https://docs.github.com/en/webhooks/using-webhooks/validating-webhook-deliveries
    private static final String DOCS_SECRET = "It's a Secret to Everybody";
    private static final byte[] DOCS_PAYLOAD = "Hello, World!".getBytes(StandardCharsets.UTF_8);
    private static final String DOCS_SIGNATURE =
            "sha256=757107ea0eb2509fc211221cce984b8a37570b6d7586c22c46f4379c8b043e17";

    private final WebhookSignatureVerifier verifier = new WebhookSignatureVerifier(DOCS_SECRET);

    @Test
    void computesTheSameSignatureAsGitHubsDocumentedExample() {
        assertThat(WebhookSignatureVerifier.sign(DOCS_SECRET, DOCS_PAYLOAD)).isEqualTo(DOCS_SIGNATURE);
    }

    @Test
    void acceptsGitHubsDocumentedExample() {
        assertThat(verifier.isValid(DOCS_PAYLOAD, DOCS_SIGNATURE)).isTrue();
    }

    @Test
    void rejectsAChangedBody() {
        byte[] changed = "Hello, World?".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.isValid(changed, DOCS_SIGNATURE)).isFalse();
    }

    @Test
    void rejectsAWrongOrMissingSignature() {
        assertThat(verifier.isValid(DOCS_PAYLOAD, "sha256=0000")).isFalse();
        assertThat(verifier.isValid(DOCS_PAYLOAD, DOCS_SIGNATURE.replace("sha256=", "sha1="))).isFalse();
        assertThat(verifier.isValid(DOCS_PAYLOAD, DOCS_SIGNATURE.toUpperCase())).isFalse();
        assertThat(verifier.isValid(DOCS_PAYLOAD, null)).isFalse();
    }

    @Test
    void withoutASecretNothingIsValid() {
        WebhookSignatureVerifier unconfigured = new WebhookSignatureVerifier("");

        assertThat(unconfigured.isConfigured()).isFalse();
        // Even a correctly formed signature must not pass when we have nothing to check it against.
        assertThat(unconfigured.isValid(DOCS_PAYLOAD, DOCS_SIGNATURE)).isFalse();
    }
}
