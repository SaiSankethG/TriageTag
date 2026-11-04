package com.saisanketh.triage.comment;

import com.saisanketh.triage.comment.CommentRepository.Mode;
import com.saisanketh.triage.comment.GitHubCommentClient.PostedComment;
import com.saisanketh.triage.github.GitHubAppAuth;
import com.saisanketh.triage.metrics.TriageMetrics;
import com.saisanketh.triage.report.TriageReport;
import com.saisanketh.triage.report.TriageReport.Duplicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Comment mode (off by default): after a new issue's report is made, maybe post ONE public comment pointing at the likely
 * duplicate. Every check below must pass, in this order (cheapest and most common first):
 * <ol>
 *   <li>the repo's mode is SUGGEST (default SHADOW = never comment);</li>
 *   <li>the GitHub App is configured (id + key file);</li>
 *   <li>the issue is still open and was opened by someone outside the team;</li>
 *   <li>it has none of the repo's skip labels (bookkeeping clones);</li>
 *   <li>the top match scores at least the comment threshold (0.90) AND the LLM check said "same root problem";</li>
 *   <li>no comment row exists yet for the issue (reserved before posting, so at most one comment, ever).</li>
 * </ol>
 * A failed post is recorded as FAILED and not retried: a missing comment is better than a duplicate one.
 */
@Slf4j
@Service
@EnableConfigurationProperties(CommentProperties.class)
public class CommentService {

    public enum Decision {
        POSTED, SHADOW_MODE, NOT_CONFIGURED, CLOSED, TEAM_AUTHOR, SKIP_LABEL, NO_MATCH, BELOW_THRESHOLD, NOT_CONFIRMED,
        ALREADY_HANDLED, FAILED
    }

    static final Set<String> TEAM = Set.of("OWNER", "MEMBER", "COLLABORATOR");

    private final JdbcTemplate jdbc;
    private final CommentRepository comments;
    private final GitHubAppAuth appAuth;
    private final GitHubCommentClient client;
    private final Clock clock;
    private final TriageMetrics metrics;
    private final double threshold;
    /** lower-case repo -> labels whose issues never get a comment */
    private final Map<String, List<String>> skipLabels;

    public CommentService(JdbcTemplate jdbc, CommentRepository comments, GitHubAppAuth appAuth,
                          GitHubCommentClient client, Clock clock, TriageMetrics metrics, CommentProperties properties,
                          @Value("${triage.duplicates.comment-threshold:0.90}") double threshold) {
        this.jdbc = jdbc;
        this.comments = comments;
        this.appAuth = appAuth;
        this.client = client;
        this.clock = clock;
        this.metrics = metrics;
        this.threshold = threshold;
        this.skipLabels = properties.skipLabels().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue));
    }

    /** The issue as the checks need it. */
    private record IssueFacts(String repo, int number, String state, String authorAssociation, Set<String> labels) {
    }

    public Decision maybeComment(TriageReport report) {
        Decision decision = decide(report);
        metrics.commentDecision(decision.name());
        return decision;
    }

    private Decision decide(TriageReport report) {
        IssueFacts issue = jdbc.queryForObject("""
                SELECT i.repo, i.number, i.state, i.author_association,
                       ARRAY(SELECT l.label_name FROM issue_label l WHERE l.issue_id = i.id) AS labels
                FROM issue i WHERE i.id = ?
                """, (rs, row) -> new IssueFacts(rs.getString("repo"), rs.getInt("number"), rs.getString("state"),
                rs.getString("author_association"),
                new HashSet<>(Arrays.asList((String[]) rs.getArray("labels").getArray()))), report.issueId());

        Decision decision = check(issue, report);
        if (decision != null) {
            log.debug("No comment on {}#{}: {}", issue.repo(), issue.number(), decision);
            return decision;
        }
        Long reportId = jdbc.queryForObject("SELECT id FROM triage_report WHERE issue_id = ?", Long.class,
                report.issueId());
        if (!comments.reserve(report.issueId(), reportId, clock.instant())) {
            return Decision.ALREADY_HANDLED;
        }
        Duplicate top = report.duplicates().get(0);
        try {
            PostedComment posted = client.post(issue.repo(), issue.number(), body(top), appAuth.installationToken(issue.repo()));
            comments.markPosted(report.issueId(), posted.id(), posted.url(), clock.instant());
            log.info("Comment mode: posted on {}#{} -> #{} ({})", issue.repo(), issue.number(), top.number(), posted.url());
            return Decision.POSTED;
        } catch (RuntimeException e) {
            comments.markFailed(report.issueId(), e.getClass().getSimpleName() + ": " + e.getMessage());
            log.warn("Comment mode: posting on {}#{} failed, not retried: {}", issue.repo(), issue.number(), e.getMessage());
            return Decision.FAILED;
        }
    }

    /** null = every check passed. */
    private Decision check(IssueFacts issue, TriageReport report) {
        if (comments.mode(issue.repo()) != Mode.SUGGEST) {
            return Decision.SHADOW_MODE;
        }
        if (!appAuth.isConfigured()) {
            return Decision.NOT_CONFIGURED;
        }
        if (!"open".equals(issue.state())) {
            return Decision.CLOSED;
        }
        if (TEAM.contains(issue.authorAssociation())) {
            return Decision.TEAM_AUTHOR;
        }
        if (skipLabels.getOrDefault(issue.repo().toLowerCase(Locale.ROOT), List.of()).stream()
                .anyMatch(issue.labels()::contains)) {
            return Decision.SKIP_LABEL;
        }
        if (report.duplicates().isEmpty()) {
            return Decision.NO_MATCH;
        }
        if (report.duplicates().get(0).score() < threshold) {
            return Decision.BELOW_THRESHOLD;
        }
        if (report.duplicateCheck() == null || !report.duplicateCheck().confirmed()) {
            return Decision.NOT_CONFIRMED;
        }
        return null;
    }

    /** The comment: the link, the score, and that a bot suggested it and maintainers decide. */
    static String body(Duplicate top) {
        return String.format(Locale.ROOT, """
                This looks like it may be a duplicate of #%d (%s), similarity %.0f%%.

                *A bot suggested this after an automated check; it can be wrong. The maintainers decide. \
                If your problem is different, a short note on how it differs helps.*""",
                top.number(), top.title(), top.score() * 100);
    }
}
