package com.saisanketh.triage.db;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.repository.IssueRepository;
import com.saisanketh.triage.source.FixInfo;
import com.saisanketh.triage.triage.FixedHint;
import com.saisanketh.triage.triage.FixedHint.Kind;
import com.saisanketh.triage.triage.FixedHintService;
import com.saisanketh.triage.triage.IssueFixRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static com.saisanketh.triage.service.TestIssues.issue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FixedHintServiceDbTest extends PostgresTest {

    private static final String REPO = "octo-org/hello";

    @Autowired
    private IssueRepository issueRepository;
    @Autowired
    private FixedHintService service;
    @Autowired
    private IssueFixRepository fixes;

    private long save(int number, String state, String stateReason, String milestone) {
        Issue issue = issue(number, "Issue " + number);
        issue.setState(state);
        issue.setStateReason(stateReason);
        issue.setMilestone(milestone);
        return issueRepository.saveAndFlush(issue).getId();
    }

    @Test
    void fetchesTheFixOnceThenUsesTheCachedRow() {
        long id = save(1, "closed", "completed", "3.4.0");
        when(fixSource.findFix(REPO, 1)).thenReturn(new FixInfo("Commit", null, "https://c", "3.4.2"));

        Optional<FixedHint> first = service.hint(REPO, 1, "Using Spring Boot 3.4.1");
        Optional<FixedHint> second = service.hint(REPO, 1, "Using Spring Boot 3.4.1");

        assertThat(first).hasValueSatisfying(h -> {
            assertThat(h.kind()).isEqualTo(Kind.UPGRADE);
            assertThat(h.fixVersion()).isEqualTo("3.4.2");
            assertThat(h.closerUrl()).isEqualTo("https://c");
            assertThat(h.text()).contains("#1 was fixed in 3.4.2", "3.4.1", "try upgrading");
        });
        assertThat(second).isEqualTo(first);
        verify(fixSource, times(1)).findFix(REPO, 1);
        assertThat(fixes.find(id)).isPresent();
    }

    @Test
    void whenGitHubFailsTheStoredMilestoneIsUsedAndNothingIsCached() {
        long id = save(2, "closed", null, "2.7.0");   // old issues have no state_reason
        when(fixSource.findFix(anyString(), anyInt())).thenThrow(new IllegalStateException("no token"));

        assertThat(service.hint(REPO, 2, "no version here")).hasValueSatisfying(h -> {
            assertThat(h.kind()).isEqualTo(Kind.VERSION_UNKNOWN);
            assertThat(h.fixVersion()).isEqualTo("2.7.0");
        });
        assertThat(fixes.find(id)).isEmpty();
    }

    @Test
    void noHintForOpenOrNotFixedOrUnknownIssuesOrWithoutAVersion() {
        save(3, "open", null, "3.4.0");
        save(4, "closed", "not_planned", "3.4.0");
        save(5, "closed", "completed", "General Backlog");
        when(fixSource.findFix(REPO, 5)).thenReturn(new FixInfo(null, null, null, "General Backlog"));

        assertThat(service.hint(REPO, 3, "")).isEmpty();
        assertThat(service.hint(REPO, 4, "")).isEmpty();
        assertThat(service.hint(REPO, 5, "")).isEmpty();
        assertThat(service.hint(REPO, 999, "")).isEmpty();
        verify(fixSource, never()).findFix(REPO, 3);
    }

    @Test
    void aReporterAlreadyOnTheFixedVersionGetsARegressionHint() {
        save(6, "closed", "completed", "3.4.2");
        when(fixSource.findFix(REPO, 6)).thenReturn(new FixInfo(null, null, null, "3.4.2"));

        assertThat(service.hint(REPO, 6, "Spring Boot 3.5.0").orElseThrow().kind()).isEqualTo(Kind.SAME_OR_NEWER);
    }
}
