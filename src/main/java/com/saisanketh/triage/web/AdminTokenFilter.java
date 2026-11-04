package com.saisanketh.triage.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * Protects every endpoint that changes data or spends money (backfill, embeddings, docs, eval, POST /reports: GitHub API
 * quota, disk, LLM tokens) with a shared admin token: header {@code X-Admin-Token} must equal the TRIAGE_ADMIN_TOKEN env var.
 *
 * <ul>
 *   <li>Applies to every non-read request (anything but GET, HEAD, OPTIONS) except {@code POST /webhooks/github}, which has
 *       its own HMAC signature check.</li>
 *   <li>Token not configured: 503 for those requests (fail closed, like the webhook secret), with a log line saying why.</li>
 *   <li>Missing or wrong token: 401. The comparison is constant-time ({@link MessageDigest#isEqual}), so response timing
 *       doesn't reveal how many characters were right.</li>
 *   <li>GET requests (the demo page, reports, search, health) stay open.</li>
 * </ul>
 * A plain servlet filter instead of Spring Security: one shared secret for a handful of operator endpoints doesn't need users,
 * sessions or roles.
 */
@Slf4j
@Component
public class AdminTokenFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Admin-Token";
    static final Set<String> READ_METHODS = Set.of(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name());
    static final String WEBHOOK_PATH = "/webhooks/github";

    private final byte[] adminToken;

    public AdminTokenFilter(@Value("${triage.admin-token:}") String adminToken) {
        this.adminToken = adminToken == null || adminToken.isBlank() ? null : adminToken.getBytes(StandardCharsets.UTF_8);
        if (this.adminToken == null) {
            log.warn("TRIAGE_ADMIN_TOKEN is not set: admin endpoints (POST backfill/embeddings/docs/eval/reports) answer 503");
        }
    }

    /** Reads and the webhook are not this filter's business. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return READ_METHODS.contains(request.getMethod())
                || (HttpMethod.POST.matches(request.getMethod()) && WEBHOOK_PATH.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (adminToken == null) {
            log.warn("Refused {} {}: TRIAGE_ADMIN_TOKEN is not set", request.getMethod(), request.getRequestURI());
            reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "admin token not configured on the server");
            return;
        }
        String given = request.getHeader(HEADER);
        if (given == null || !MessageDigest.isEqual(adminToken, given.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Refused {} {}: missing or wrong {}", request.getMethod(), request.getRequestURI(), HEADER);
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "missing or wrong " + HEADER);
            return;
        }
        chain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.getWriter().write(message);
    }
}
