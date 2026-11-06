package com.saisanketh.triage.db;

import com.saisanketh.triage.ai.DraftReply;
import com.saisanketh.triage.ai.DuplicateCheck.Verdict;
import com.saisanketh.triage.ai.MissingDetails;
import com.saisanketh.triage.docs.DocChunkRepository;
import com.saisanketh.triage.docs.DocSection;
import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.embedding.IssueEmbeddingRepository;
import com.saisanketh.triage.report.TriageReport;
import com.saisanketh.triage.report.TriageReportDto;
import com.saisanketh.triage.report.TriageReportRepository;
import com.saisanketh.triage.report.TriageService;
import com.saisanketh.triage.repository.IssueRepository;
import com.saisanketh.triage.source.FixInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static com.saisanketh.triage.service.TestIssues.issue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** The whole report on real Postgres with the fake LLM; the test embedding model maps every text to the x axis. */
class TriageServiceDbTest extends PostgresTest {

    private static final String REPO = "octo-org/hello";

    @Autowired
    private IssueRepository issueRepository;
    @Autowired
    private IssueEmbeddingRepository embeddingRepository;
    @Autowired
    private DocChunkRepository docChunks;
    @Autowired
    private TriageService triageService;
    @Autowired
    private TriageReportRepository reports;
    @Autowired
    private com.saisanketh.triage.service.IssueService issueService;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired
    private com.saisanketh.triage.ai.TokenBudget budget;

    private static float[] direction(double angleDegrees) {
        float[] v = new float[384];
        v[0] = (float) Math.cos(Math.toRadians(angleDegrees));
        v[1] = (float) Math.sin(Math.toRadians(angleDegrees));
        return v;
    }

    private long save(int number, String createdAt, double angle, String title, String body, String state,
                      String milestone, String... labels) {
        Issue issue = issue(number, title, labels);
        issue.setBody(body);
        issue.setCreatedAt(Instant.parse(createdAt));
        issue.setState(state);
        issue.setStateReason("closed".equals(state) ? "completed" : null);
        issue.setMilestone(milestone);
        long id = issueRepository.saveAndFlush(issue).getId();
        embeddingRepository.saveAll(List.of(id), List.of(direction(angle)), "test");
        return id;
    }

    @Test
    void buildsAndStoresTheWholeReport() {
        save(1, "2024-01-01T00:00:00Z", 1, "Datasource url not picked up", "...", "closed", "3.4.2", "type: bug");
        save(2, "2024-01-02T00:00:00Z", 60, "Banner colours", "...", "open", null, "type: enhancement");
        long id = save(3, "2024-06-01T00:00:00Z", 0, "Datasource url not picked up from yaml",
                "Using Spring Boot 3.4.1. Steps: run it. Expected the url to be used.", "open", null);
        when(fixSource.findFix(REPO, 1)).thenReturn(new FixInfo("Commit", null, "https://c", "3.4.2"));
        docChunks.replaceAll(REPO, "v1", List.of(new DocSection("https://docs.example/sql#ds", "SQL > DataSource",
                "Set spring.datasource.url.")), List.of(direction(0)));

        TriageReport report = triageService.create(id);

        assertThat(report.duplicates()).extracting(TriageReport.Duplicate::number).containsExactly(1, 2);
        assertThat(report.duplicates().get(0).url()).isEqualTo("https://github.com/octo-org/hello/issues/1");
        assertThat(report.duplicateCheck().verdict()).isEqualTo(Verdict.YES);      // fake: 4 of 6 title words
        assertThat(report.labels()).extracting(l -> l.label()).contains("type: bug");
        assertThat(report.missing().status()).isEqualTo(MissingDetails.Status.OK);
        assertThat(report.missing().missing()).containsExactly(MissingDetails.Detail.LOGS_OR_STACK_TRACE);
        assertThat(report.fixedHint().text()).contains("fixed in 3.4.2", "try upgrading");
        assertThat(report.draftReply().status()).isEqualTo(DraftReply.Status.OK);
        assertThat(report.draftReply().text()).contains("https://docs.example/sql#ds");
        assertThat(report.llmModel()).isEqualTo("fake");
        assertThat(report.promptVersions()).isEqualTo("duplicate-check-v1,missing-details-v1,draft-reply-v3");
        assertThat(report.draftReply().text()).startsWith("This looks like #1");   // v3: the match comes first
        assertThat(report.costTokens()).isPositive();

        TriageReportDto dto = reports.find("Octo-Org/Hello", 3).orElseThrow();
        assertThat(dto.title()).isEqualTo("Datasource url not picked up from yaml");
        assertThat(dto.duplicates().get(0).get("number").asInt()).isEqualTo(1);
        assertThat(dto.duplicates().get(0).get("state").asString()).isEqualTo("closed");    // looked up when read
        assertThat(dto.duplicates().get(1).get("state").asString()).isEqualTo("open");
        assertThat(dto.issue().state()).isEqualTo("open");
        assertThat(dto.issue().authorAssociation()).isEqualTo("NONE");
        assertThat(dto.issue().bodyStart()).startsWith("Using Spring Boot 3.4.1.");
        assertThat(dto.issue().bodyLength()).isEqualTo(dto.issue().bodyStart().length());
        assertThat(dto.issue().duplicateOf()).isNull();
        assertThat(dto.issue().originalIsNewer()).isFalse();
        assertThat(dto.missing().get("missing").get(0).asString()).isEqualTo("LOGS_OR_STACK_TRACE");
        assertThat(dto.draftReply().get("sources")).hasSize(2);
        assertThat(dto.costTokens()).isEqualTo(report.costTokens());
    }

    @Test
    void aWeakTopMatchGetsNoLlmCheckNoHintAndNoDraft() {
        save(1, "2024-01-01T00:00:00Z", 70, "Banner colours", "...", "closed", "3.4.2");
        long id = save(2, "2024-06-01T00:00:00Z", 0, "Datasource url", "", "open", null);

        TriageReport report = triageService.create(id);

        assertThat(report.duplicates()).hasSize(1);                 // still shown privately
        assertThat(report.duplicateCheck()).isNull();
        assertThat(report.fixedHint()).isNull();
        assertThat(report.draftReply().status()).isEqualTo(DraftReply.Status.NOTHING_RELEVANT);
        assertThat(report.promptVersions()).isEqualTo("missing-details-v1");
    }

    @Test
    void createIfMissingRunsOnlyOncePerIssue() {
        long id = save(1, "2024-01-01T00:00:00Z", 0, "First issue", "", "open", null);

        assertThat(triageService.createIfMissing(id)).isTrue();
        assertThat(triageService.createIfMissing(id)).isFalse();
        assertThat(triageService.create("octo-org/hello", 999)).isEmpty();
        assertThat(reports.find("octo-org/hello", 999)).isEmpty();
    }

    @Test
    void overTheDailyBudgetTheLlmStepsAreSkippedAndTheReportSaysSo() {
        budget.record(999_999_999);   // today's budget is used up
        save(1, "2024-01-01T00:00:00Z", 1, "Datasource url not picked up", "...", "open", null, "type: bug");
        long id = save(2, "2024-06-01T00:00:00Z", 0, "Datasource url not picked up from yaml", "...", "open", null);

        TriageReport report = triageService.create(id);

        assertThat(report.notes()).contains("daily token budget");
        assertThat(report.missing().status()).isEqualTo(MissingDetails.Status.UNKNOWN);
        assertThat(report.missing().note()).contains("daily token budget");
        assertThat(report.duplicateCheck().verdict()).isEqualTo(Verdict.UNKNOWN);
        assertThat(report.duplicates()).isNotEmpty();                 // no LLM needed
        assertThat(report.labels()).isNotEmpty();
        assertThat(report.costTokens()).isZero();
    }

    @Test
    void recentReportsListTheIssueAndItsBestMatchAndListsFlagWhichIssuesHaveAReport() {
        save(1, "2024-01-01T00:00:00Z", 1, "Datasource url not picked up", "...", "open", null);
        long id = save(2, "2024-06-01T00:00:00Z", 0, "Datasource url not picked up from yaml", "...", "open", null);
        triageService.create(id);

        var recent = reports.recent("Octo-Org/Hello", 10);

        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).number()).isEqualTo(2);
        assertThat(recent.get(0).topNumber()).isEqualTo(1);
        assertThat(recent.get(0).topScore()).isGreaterThan(0.99);
        assertThat(recent.get(0).topTitle()).isEqualTo("Datasource url not picked up");
        assertThat(recent.get(0).missing()).isNotNull();
        var match = reports.find("octo-org/hello", 2).orElseThrow().duplicates().get(0);
        assertThat(match.path("bodyStart").asString()).isEqualTo("...");    // the side-by-side view shows the match's text
        assertThat(issueService.listIssues("octo-org/hello", 0, 10).issues())
                .extracting(s -> s.number() + ":" + s.hasReport()).containsExactly("2:true", "1:false");
    }

    @Test
    void recentReportsPutOpenIssuesFirst() {
        save(1, "2024-01-01T00:00:00Z", 1, "Logger visibility", "...", "open", null);
        long closed = save(2, "2024-02-01T00:00:00Z", 2, "Logger visibility again", "...", "closed", null);
        long open = save(3, "2024-03-01T00:00:00Z", 3, "Logger visibility once more", "...", "open", null);
        triageService.create(open);
        triageService.create(closed);                                    // the newest report, but the issue is closed

        assertThat(reports.recent("octo-org/hello", 10)).extracting(r -> r.number()).containsExactly(3, 2);
    }

    @Test
    void theCardSaysWhatMaintainersDecidedLaterAndWhetherTheOriginalCameAfter() {
        long older = save(1, "2024-01-01T00:00:00Z", 1, "Logger visibility", "...", "closed", null);
        save(2, "2024-02-01T00:00:00Z", 2, "Logger visibility again", "...", "open", null);
        jdbc.update("UPDATE issue SET duplicate_of = 2 WHERE id = ?", older);   // closed in favour of the NEWER #2
        triageService.create(older);

        var card = reports.find("octo-org/hello", 1).orElseThrow().issue();

        assertThat(card.duplicateOf()).isEqualTo(2);
        assertThat(card.originalStored()).isTrue();
        assertThat(card.originalIsNewer()).isTrue();

        jdbc.update("UPDATE issue SET duplicate_of = 999 WHERE id = ?", older);    // e.g. a pull request: not stored
        assertThat(reports.find("octo-org/hello", 1).orElseThrow().issue().originalStored()).isFalse();
    }
}
