package com.saisanketh.triage.triage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which earlier issues are left out of the duplicate candidates, per repo (configured, nothing hard-coded).
 *
 * <p>Why: some repos contain bookkeeping copies of real issues. spring-boot's team clones an issue for every branch it
 * is fixed on ("forward-port", label {@code status: forward-port}). The clone has the same title and text as the
 * original, so it can take the original's place in the top results; for the replay that counts as a miss, and for a
 * maintainer it's a useless link. Excluding the clone's label from the candidates fixes both.
 */
@Component
@EnableConfigurationProperties(CandidateFilterProperties.class)
public class CandidateFilter {

    /** lower-case repo -> excluded labels */
    private final Map<String, List<String>> excludedByRepo = new HashMap<>();

    public CandidateFilter(CandidateFilterProperties properties) {
        properties.excludeLabels().forEach((repo, labels) ->
                excludedByRepo.put(repo.toLowerCase(Locale.ROOT), List.copyOf(labels)));
    }

    /** Labels whose issues are never candidates in this repo; empty if nothing is configured for it. */
    public List<String> excludedLabels(String repo) {
        return excludedByRepo.getOrDefault(repo.toLowerCase(Locale.ROOT), List.of());
    }
}
