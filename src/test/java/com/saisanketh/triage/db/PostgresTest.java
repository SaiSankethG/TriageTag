package com.saisanketh.triage.db;

import com.saisanketh.triage.ClockConfig;
import com.saisanketh.triage.ai.DuplicateVerifier;
import com.saisanketh.triage.ai.IssueTextRepository;
import com.saisanketh.triage.ai.LlmConfig;
import com.saisanketh.triage.ai.PromptTemplates;
import com.saisanketh.triage.eval.DuplicateCheckEval;
import com.saisanketh.triage.ai.DraftReplyService;
import com.saisanketh.triage.ai.MissingDetailsChecker;
import com.saisanketh.triage.ai.TokenBudget;
import com.saisanketh.triage.docs.DocChunkRepository;
import com.saisanketh.triage.docs.DocIngestService;
import com.saisanketh.triage.docs.DocSource;
import com.saisanketh.triage.report.TriageReportRepository;
import com.saisanketh.triage.report.TriageService;
import com.saisanketh.triage.embedding.IssueEmbeddingRepository;
import com.saisanketh.triage.eval.EvalService;
import com.saisanketh.triage.kafka.IssueEventPublisher;
import com.saisanketh.triage.repository.WebhookDeliveryRepository;
import com.saisanketh.triage.service.IssueService;
import com.saisanketh.triage.service.WebhookIntakeService;
import com.saisanketh.triage.source.FixSource;
import com.saisanketh.triage.triage.CandidateFilter;
import com.saisanketh.triage.triage.FixedHintService;
import com.saisanketh.triage.triage.IssueFixRepository;
import com.saisanketh.triage.triage.KeywordDuplicateSearch;
import com.saisanketh.triage.triage.LabelSuggester;
import com.saisanketh.triage.triage.SimilarIssueService;
import com.saisanketh.triage.triage.VectorDuplicateSearch;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Base class for tests against a real Postgres (Testcontainers).
 *
 * <ul>
 *   <li>{@code @DataJpaTest}: starts only the JPA part of the app (entities, repositories, Flyway), not the web layer.
 *       Each test runs in a transaction that is rolled back at the end, so tests don't see each other's rows.</li>
 *   <li>{@code replace = NONE}: use our Postgres container, don't swap in an in-memory database.</li>
 *   <li>Flyway runs V1, V2... on the empty container, and Hibernate {@code validate} then checks the entities
 *       against that schema, so every real-DB test also tests the migrations.</li>
 *   <li>All subclasses have the same configuration, so Spring reuses one context and one container for all of them.</li>
 *   <li>Kafka and GitHub aren't part of these tests: the publisher and the fix source are Mockito mocks ({@code @MockitoBean}).</li>
 * </ul>
 */
@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "triage.eval.results-file=target/test-m3-results.md",
        "triage.candidates.exclude-labels[octo-org/hello]=status: forward-port",
        "triage.eval.duplicate-check-file=target/test-m4-duplicate-check.md"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresContainerConfig.class, ClockConfig.class, IssueService.class,
        WebhookDeliveryRepository.class, WebhookIntakeService.class, KeywordDuplicateSearch.class,
        IssueEmbeddingRepository.class, VectorDuplicateSearch.class, SimilarIssueService.class,
        LabelSuggester.class, EvalService.class, CandidateFilter.class,
        LlmTestConfig.class, LlmConfig.class, PromptTemplates.class, IssueTextRepository.class, DuplicateVerifier.class,
        DuplicateCheckEval.class, IssueFixRepository.class, FixedHintService.class,
        DocChunkRepository.class, DocIngestService.class, DraftReplyService.class, MissingDetailsChecker.class,
        TriageReportRepository.class, TriageService.class, TokenBudget.class})
public abstract class PostgresTest {

    @MockitoBean
    protected IssueEventPublisher publisher;

    /** GitHub isn't called from DB tests: the "what fixed this issue" lookup is a mock. */
    @MockitoBean
    protected FixSource fixSource;

    @MockitoBean
    protected DocSource docSource;
}
