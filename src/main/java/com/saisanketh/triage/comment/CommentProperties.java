package com.saisanketh.triage.comment;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Comment-mode settings from application.yml ({@code triage.comments}).
 *
 * @param skipLabels repo ("owner/name") -> labels; an issue with any of them never gets a comment (e.g. the team's
 *                   forward-port clones: 21 of 48 confirmed false alarms in M5 step 1)
 */
@ConfigurationProperties(prefix = "triage.comments")
public record CommentProperties(Map<String, List<String>> skipLabels) {

    public CommentProperties {
        skipLabels = skipLabels == null ? Map.of() : Map.copyOf(skipLabels);
    }
}
