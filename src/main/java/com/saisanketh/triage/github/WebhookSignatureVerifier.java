package com.saisanketh.triage.github;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Checks that a webhook really comes from GitHub. GitHub signs the exact request body with our shared
 * secret (HMAC-SHA256) and sends {@code X-Hub-Signature-256: sha256=<hex>}. We compute the same and compare.
 * Docs: https://docs.github.com/en/webhooks/using-webhooks/validating-webhook-deliveries
 */
@Component
public class WebhookSignatureVerifier {

    private static final String PREFIX = "sha256=";

    private final String secret;

    /** The secret comes from the GITHUB_WEBHOOK_SECRET env var (see application.yml); never from a file in the repo. */
    public WebhookSignatureVerifier(@Value("${github.webhook-secret}") String secret) {
        this.secret = secret;
    }

    /** False when no secret is set: then every webhook must be refused, because nothing can be checked. */
    public boolean isConfigured() {
        return secret != null && !secret.isBlank();
    }

    /**
     * @param body            the request body exactly as received (not re-serialized JSON)
     * @param signatureHeader the X-Hub-Signature-256 header, may be null
     */
    public boolean isValid(byte[] body, String signatureHeader) {
        if (!isConfigured() || signatureHeader == null || !signatureHeader.startsWith(PREFIX)) {
            return false;
        }
        byte[] expected = sign(secret, body).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signatureHeader.getBytes(StandardCharsets.UTF_8);
        // Constant-time: takes as long for a signature that's wrong in the 1st character as in the 64th,
        // so an attacker can't guess the right signature one character at a time by measuring response times.
        return MessageDigest.isEqual(expected, actual);
    }

    /** "sha256=" + lowercase hex of HMAC-SHA256(body, secret), the same format GitHub sends. Public for tests. */
    public static String sign(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return PREFIX + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available in this JVM", e);   // every JVM has it
        }
    }
}
