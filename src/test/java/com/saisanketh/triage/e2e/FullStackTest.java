package com.saisanketh.triage.e2e;

import com.saisanketh.triage.db.PostgresContainerConfig;
import com.saisanketh.triage.kafka.IssueEventMessage;
import com.saisanketh.triage.service.IssueService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.fail;

/**
 * Base class for tests of the whole app with a real Postgres AND a real Kafka (Testcontainers).
 * {@code @SpringBootTest} starts everything: web server (random port), Kafka listener, Flyway, all beans.
 * All subclasses share one context, so the two containers start once.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"github.webhook-secret=" + FullStackTest.WEBHOOK_SECRET, "github.token="})
@Import({PostgresContainerConfig.class, KafkaContainerConfig.class})
public abstract class FullStackTest {

    /** A test-only secret; the real one only ever comes from the GITHUB_WEBHOOK_SECRET env var. */
    protected static final String WEBHOOK_SECRET = "e2e-test-secret";
    private static final Duration MAX_WAIT = Duration.ofSeconds(10);

    /** The real IssueService, wrapped so a test can make one call fail (to test retries). */
    @MockitoSpyBean
    protected IssueService issueService;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    protected ConsumerFactory<String, String> consumerFactory;
    @Autowired
    protected JsonMapper jsonMapper;

    @BeforeEach
    void emptyTheTables() {
        jdbc.update("delete from issue_label");
        jdbc.update("delete from issue");
        jdbc.update("delete from webhook_delivery");
    }

    @AfterEach
    void backToTheRealIssueService() {
        Mockito.reset(issueService);
    }

    protected int countRows(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    /** Checks the condition every 100 ms until it's true; fails the test after 10 s. */
    protected static void waitUntil(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + MAX_WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        fail("waited " + MAX_WAIT.toSeconds() + " s, but still not true: " + what);
    }

    /** Reads every message on a topic (from the start) for up to 10 s, with a fresh consumer group. */
    protected List<ConsumerRecord<String, String>> readTopic(String topic, int expectedAtLeast) {
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        try (Consumer<String, String> consumer = consumerFactory.createConsumer("test-reader-" + UUID.randomUUID(), null)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + MAX_WAIT.toNanos();
            while (records.size() < expectedAtLeast && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(200)).forEach(records::add);
            }
        }
        return records;
    }

    /** GitHub's documented "issues opened" example (public data), as a parsed webhook body. */
    protected JsonNode webhookFixture() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/github/webhook-issues-opened.json")) {
            return jsonMapper.readTree(in.readAllBytes());
        }
    }

    /** The Kafka message the webhook receiver would publish for the fixture. */
    protected IssueEventMessage fixtureMessage(String deliveryId) throws IOException {
        return new IssueEventMessage(deliveryId, "opened", "Codertocat/Hello-World", webhookFixture().get("issue"));
    }
}
