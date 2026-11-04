package com.saisanketh.triage.e2e;

import com.saisanketh.triage.kafka.IssueEventPublisher;
import com.saisanketh.triage.outbox.OutboxRelay;
import com.saisanketh.triage.outbox.OutboxRepository;
import com.saisanketh.triage.service.WebhookIntakeService;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The outbox with a real Postgres and a real Kafka: a webhook is accepted while Kafka is unreachable, waits in the
 * outbox, and reaches the worker once Kafka is back.
 *
 * <p>"Kafka down" = a relay whose producer points at a port where nothing listens; "Kafka up" = a relay with the app's
 * real producer. The scheduled relay is switched off for this test class, so only the test decides when rows are sent.
 */
@TestPropertySource(properties = "triage.outbox.relay-enabled=false")
class OutboxKafkaDownEndToEndTest extends FullStackTest {

    @Autowired
    private WebhookIntakeService intakeService;
    @Autowired
    private OutboxRepository outbox;
    @Autowired
    private OutboxRelay realRelay;

    @Test
    void anEventWaitsInTheOutboxWhileKafkaIsDownAndArrivesWhenItIsBack() throws Exception {
        jdbc.update("delete from outbox_event");
        String delivery = UUID.randomUUID().toString();

        // 1. The webhook path never touches Kafka: accepted and queued.
        assertThat(intakeService.accept("issues", fixtureMessage(delivery))).isEqualTo(WebhookIntakeService.Result.ACCEPTED);
        assertThat(outbox.countUnsent()).isEqualTo(1);

        // 2. Kafka down: the send fails, the row stays unsent with the error recorded, no issue row appears.
        DefaultKafkaProducerFactory<String, String> deadFactory = new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1",
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 1000,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
        try {
            OutboxRelay relayWithKafkaDown = new OutboxRelay(outbox,
                    new IssueEventPublisher(new KafkaTemplate<>(deadFactory)), Clock.systemUTC());
            assertThat(relayWithKafkaDown.sendPending()).isZero();
        } finally {
            deadFactory.destroy();
        }
        assertThat(outbox.countUnsent()).isEqualTo(1);
        assertThat(countRows("select attempts from outbox_event")).isEqualTo(1);
        assertThat(countRows("select count(*) from issue")).isZero();

        // 3. Kafka up: the same row is sent, marked, and the worker stores the issue.
        assertThat(realRelay.sendPending()).isEqualTo(1);
        assertThat(outbox.countUnsent()).isZero();
        waitUntil("the worker stored the issue", () -> countRows(
                "select count(*) from issue where repo = 'Codertocat/Hello-World' and number = 1") == 1);
    }
}
