package com.saisanketh.triage.triage;

import com.saisanketh.triage.source.FixInfo;
import com.saisanketh.triage.source.FixSource;
import com.saisanketh.triage.triage.FixedHint.Kind;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The "already fixed?" hint (no LLM): if the top duplicate match is closed as completed, find the version its fix
 * shipped in (GitHub's records, cached in issue_fix) and compare it with the reporter's version from the new issue's body.
 *
 * <p>If GitHub can't be asked (no token, error), the match's stored milestone is used and nothing is cached, so a later
 * call can still fetch the closer.
 */
@Slf4j
@Service
public class FixedHintService {

    private final JdbcTemplate jdbc;
    private final IssueFixRepository fixes;
    private final FixSource fixSource;
    private final Clock clock;

    public FixedHintService(JdbcTemplate jdbc, IssueFixRepository fixes, FixSource fixSource, Clock clock) {
        this.jdbc = jdbc;
        this.fixes = fixes;
        this.fixSource = fixSource;
        this.clock = clock;
    }

    /**
     * @param matchNumber  the top duplicate match (an earlier issue of the same repo)
     * @param reporterBody the NEW issue's body, where the reporter's version is looked for
     * @return empty if the match isn't closed as fixed, or no fix version is known
     */
    public Optional<FixedHint> hint(String repo, int matchNumber, String reporterBody) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, state, state_reason, milestone FROM issue WHERE lower(repo) = lower(?) AND number = ?",
                repo, matchNumber);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> match = rows.get(0);
        String stateReason = (String) match.get("state_reason");
        // Old closed issues have no state_reason; "not_planned" and "duplicate" were not fixed in that issue.
        if (!"closed".equals(match.get("state")) || (stateReason != null && !"completed".equals(stateReason))) {
            return Optional.empty();
        }

        FixInfo fix = fixInfo(repo, matchNumber, ((Number) match.get("id")).longValue(), (String) match.get("milestone"));
        Optional<Version> fixVersion = Version.parse(fix.fixVersion());
        if (fixVersion.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(build(matchNumber, fix, fixVersion.get(), ReporterVersion.find(reporterBody).orElse(null)));
    }

    private FixInfo fixInfo(String repo, int number, long issueId, String storedMilestone) {
        Optional<FixInfo> cached = fixes.find(issueId);
        if (cached.isPresent()) {
            return cached.get();
        }
        try {
            FixInfo fetched = fixSource.findFix(repo, number);
            fixes.save(issueId, fetched, clock.instant());
            return fetched;
        } catch (RuntimeException e) {
            log.warn("Fixed hint: could not ask GitHub about {}#{} ({}); using the stored milestone", repo, number,
                    e.getMessage());
            return new FixInfo(null, null, null, storedMilestone);
        }
    }

    static FixedHint build(int matchNumber, FixInfo fix, Version fixVersion, String reporterVersion) {
        Optional<Version> reporter = Version.parse(reporterVersion);
        String fixed = "#" + matchNumber + " was fixed in " + fix.fixVersion();
        if (reporter.isEmpty()) {
            return new FixedHint(matchNumber, fix.fixVersion(), null, fix.closerUrl(), Kind.VERSION_UNKNOWN,
                    fixed + ". The reporter's version isn't in the issue: ask for it.");
        }
        if (reporter.get().compareTo(fixVersion) < 0) {
            return new FixedHint(matchNumber, fix.fixVersion(), reporterVersion, fix.closerUrl(), Kind.UPGRADE,
                    fixed + "; the reporter uses " + reporterVersion + ": try upgrading to " + fix.fixVersion() + " or later.");
        }
        return new FixedHint(matchNumber, fix.fixVersion(), reporterVersion, fix.closerUrl(), Kind.SAME_OR_NEWER,
                fixed + ", but the reporter already uses " + reporterVersion
                        + ": a regression, or a different problem.");
    }
}
