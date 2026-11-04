package com.saisanketh.triage.db;

import com.saisanketh.triage.kafka.IssueEventMessage;
import com.saisanketh.triage.kafka.IssueEventPublisher.PublishFailedException;
import com.saisanketh.triage.repository.WebhookDeliveryRepository;
import com.saisanketh.triage.service.WebhookIntakeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

class WebhookDeliveryDbTest extends PostgresTest {

    @Autowired
    private WebhookDeliveryRepository deliveryRepository;
    @Autowired
    private WebhookIntakeService intakeService;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theSameDeliveryIdIsOnlyInsertedOnce() {
        Instant now = Instant.parse("2026-11-04T10:00:00Z");

        boolean first = deliveryRepository.insertIfNew("d-1", "issues", "opened", "octo-org/hello", now);
        boolean second = deliveryRepository.insertIfNew("d-1", "issues", "opened", "octo-org/hello", now);

        assertThat(first).isTrue();
        assertThat(second).isFalse();                         // ON CONFLICT DO NOTHING: 0 rows, no error
        assertThat(jdbc.queryForObject("select count(*) from webhook_delivery", Integer.class)).isEqualTo(1);
    }

    /**
     * NOT_SUPPORTED: this test runs WITHOUT the usual test transaction, so the service's own @Transactional
     * really commits or rolls back, and we can see the result afterwards. (Clean-up in @AfterEach.)
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void whenKafkaFailsTheDeliveryRowIsRolledBack() {
        doThrow(new PublishFailedException("no ack from Kafka", null)).when(publisher).publishAndWait(any());
        IssueEventMessage message = new IssueEventMessage("d-rollback", "opened", "octo-org/hello",
                JsonMapper.builder().build().readTree("{\"number\": 7}"));

        assertThatThrownBy(() -> intakeService.accept("issues", message)).isInstanceOf(PublishFailedException.class);

        // The row was inserted inside the transaction, then rolled back: a GitHub redelivery will be treated as new.
        assertThat(jdbc.queryForObject("select count(*) from webhook_delivery where delivery_id = 'd-rollback'",
                Integer.class)).isZero();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from webhook_delivery where delivery_id = 'd-rollback'");
    }
}
