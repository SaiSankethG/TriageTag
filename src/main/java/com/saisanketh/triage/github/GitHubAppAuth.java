package com.saisanketh.triage.github;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Authenticates as a GitHub App, to post comments as the App (comment mode) instead of as a person.
 * Docs: https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app
 *
 * <ol>
 *   <li><b>App JWT</b>: a JSON Web Token signed with the App's private key (RS256), valid for 9 minutes. It proves "I am App
 *       123" but can't act on repos.</li>
 *   <li><b>Installation token</b>: with the JWT, ask GitHub which installation covers the repo, then for a token for that
 *       installation. It acts on the repos the App was installed on, with the App's permissions, and expires after 1 hour.</li>
 *   <li><b>Cache</b>: the installation token is reused until 5 minutes before it expires.</li>
 * </ol>
 * The private key is read from a file whose path comes from GITHUB_APP_PRIVATE_KEY_PATH; it is never in the repo. Tokens
 * and JWTs are never logged.
 */
@Slf4j
@Component
public class GitHubAppAuth {

    static final Duration JWT_LIFETIME = Duration.ofMinutes(9);          // GitHub allows at most 10
    static final Duration CLOCK_DRIFT = Duration.ofSeconds(60);          // GitHub's advice: iat 60 s in the past
    static final Duration REFRESH_BEFORE_EXPIRY = Duration.ofMinutes(5);

    private final RestClient restClient;
    private final String appId;
    private final Path privateKeyPath;
    private final Clock clock;
    private PrivateKey privateKey;   // loaded on first use

    /** installation id -> its token, until shortly before expiry */
    private final Map<Long, CachedToken> tokens = new HashMap<>();
    /** repo ("owner/name") -> installation id; an installation doesn't change for a repo */
    private final Map<String, Long> installations = new HashMap<>();

    record CachedToken(String token, Instant expiresAt) {
    }

    @Autowired
    public GitHubAppAuth(@Value("${github.api-url}") String apiUrl,
                         @Value("${github.app.id:}") String appId,
                         @Value("${github.app.private-key-path:}") String privateKeyPath,
                         Clock clock) {
        this(RestClient.builder(), apiUrl, appId, privateKeyPath, clock);
    }

    GitHubAppAuth(RestClient.Builder builder, String apiUrl, String appId, String privateKeyPath, Clock clock) {
        this.restClient = builder.baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
        this.appId = appId == null ? "" : appId.strip();
        this.privateKeyPath = privateKeyPath == null || privateKeyPath.isBlank() ? null : Path.of(privateKeyPath);
        this.clock = clock;
    }

    /** True if an App id and a readable key file are configured (comment mode needs both). */
    public boolean isConfigured() {
        return !appId.isEmpty() && privateKeyPath != null && Files.isReadable(privateKeyPath);
    }

    /** A token to act on this repo as the App; cached until 5 minutes before it expires. */
    public synchronized String installationToken(String repo) {
        long installationId = installations.computeIfAbsent(repo, this::findInstallationId);
        CachedToken cached = tokens.get(installationId);
        if (cached != null && clock.instant().isBefore(cached.expiresAt().minus(REFRESH_BEFORE_EXPIRY))) {
            return cached.token();
        }
        JsonNode response = restClient.post()
                .uri("/app/installations/{id}/access_tokens", installationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + appJwt())
                .retrieve()
                .body(JsonNode.class);
        if (response == null || response.get("token") == null) {
            throw new IllegalStateException("GitHub returned no installation token for installation " + installationId);
        }
        CachedToken fresh = new CachedToken(response.get("token").asString(),
                Instant.parse(response.get("expires_at").asString()));
        tokens.put(installationId, fresh);
        log.info("GitHub App: new installation token for {} (installation {}), expires {}", repo, installationId,
                fresh.expiresAt());
        return fresh.token();
    }

    private long findInstallationId(String repo) {
        String[] ownerAndName = repo.split("/");
        if (ownerAndName.length != 2) {
            throw new IllegalArgumentException("repo must look like 'owner/name', got: " + repo);
        }
        JsonNode installation = restClient.get()
                .uri("/repos/{owner}/{name}/installation", ownerAndName[0], ownerAndName[1])
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + appJwt())
                .retrieve()
                .body(JsonNode.class);
        if (installation == null || installation.get("id") == null) {
            throw new IllegalStateException("the GitHub App is not installed on " + repo);
        }
        return installation.get("id").asLong();
    }

    /** header.payload.signature, each part base64url without padding; RS256 = RSA signature over SHA-256. */
    String appJwt() {
        if (!isConfigured()) {
            throw new IllegalStateException("GitHub App not configured: set GITHUB_APP_ID and GITHUB_APP_PRIVATE_KEY_PATH");
        }
        Instant now = clock.instant();
        String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"iat\":" + now.minus(CLOCK_DRIFT).getEpochSecond()
                + ",\"exp\":" + now.plus(JWT_LIFETIME).getEpochSecond()
                + ",\"iss\":\"" + appId + "\"}");
        String signingInput = header + "." + payload;
        try {
            Signature rsa = Signature.getInstance("SHA256withRSA");
            rsa.initSign(privateKey());
            rsa.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(rsa.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("could not sign the GitHub App JWT", e);
        }
    }

    private PrivateKey privateKey() {
        if (privateKey == null) {
            try {
                privateKey = PemKeys.readRsaPrivateKey(Files.readString(privateKeyPath));
            } catch (IOException e) {
                throw new UncheckedIOException("could not read the GitHub App private key file", e);
            }
        }
        return privateKey;
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
