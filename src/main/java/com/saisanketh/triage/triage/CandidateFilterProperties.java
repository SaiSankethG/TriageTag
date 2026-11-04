package com.saisanketh.triage.triage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Per-repo settings for which earlier issues may be returned as duplicate candidates, from application.yml:
 * <pre>
 * triage:
 *   candidates:
 *     exclude-labels:
 *       "[spring-projects/spring-boot]":
 *         - "status: forward-port"
 * </pre>
 * The square brackets keep the "/" in the repo name as part of the map key (without them Spring would drop it).
 *
 * @param excludeLabels repo ("owner/name") -> labels; an issue with any of these labels is never a candidate
 */
@ConfigurationProperties(prefix = "triage.candidates")
public record CandidateFilterProperties(Map<String, List<String>> excludeLabels) {

    public CandidateFilterProperties {
        excludeLabels = excludeLabels == null ? Map.of() : Map.copyOf(excludeLabels);
    }
}
