package com.saisanketh.triage.service;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.domain.SourceType;

import java.time.Instant;
import java.util.Set;

/** Builds small, valid Issue objects for tests. */
public final class TestIssues {

    private TestIssues() {
    }

    public static Issue issue(int number, String title, String... labels) {
        Issue issue = new Issue();
        issue.setSource(SourceType.GITHUB);
        issue.setExternalId(String.valueOf(1000 + number));
        issue.setRepo("octo-org/hello");
        issue.setNumber(number);
        issue.setTitle(title);
        issue.setState("open");
        issue.setAuthorAssociation("NONE");
        issue.setHtmlUrl("https://github.com/octo-org/hello/issues/" + number);
        issue.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        issue.setUpdatedAt(Instant.parse("2024-01-02T00:00:00Z"));
        issue.setRaw("{\"number\": " + number + "}");
        issue.getLabels().addAll(Set.of(labels));
        return issue;
    }
}
