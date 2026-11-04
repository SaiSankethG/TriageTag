package com.saisanketh.triage.service;

import com.saisanketh.triage.kafka.IssueEventMessage;
import com.saisanketh.triage.kafka.KafkaTopicsConfig;
import com.saisanketh.triage.outbox.OutboxRepository;
import com.saisanketh.triage.repository.WebhookDeliveryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookIntakeServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");
    private final JsonMapper json = JsonMapper.builder().build();

    @Mock
    private WebhookDeliveryRepository deliveryRepository;
    @Mock
    private OutboxRepository outbox;

    private final IssueEventMessage message = new IssueEventMessage("d-1", "opened", "octo-org/hello",
            json.readTree("{\"number\": 7}"));

    private WebhookIntakeService service() {
        return new WebhookIntakeService(deliveryRepository, outbox, json, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void aNewDeliveryIsRecordedAndQueuedInTheOutbox() {
        when(deliveryRepository.insertIfNew("d-1", "issues", "opened", "octo-org/hello", NOW)).thenReturn(true);

        assertThat(service().accept("issues", message)).isEqualTo(WebhookIntakeService.Result.ACCEPTED);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(outbox).add(eq(KafkaTopicsConfig.ISSUE_EVENTS), eq("octo-org/hello#7"), payload.capture(), eq(NOW));
        assertThat(json.readValue(payload.getValue(), IssueEventMessage.class)).isEqualTo(message);   // the same message
    }

    @Test
    void aRepeatedDeliveryIsNotQueuedAgain() {
        when(deliveryRepository.insertIfNew("d-1", "issues", "opened", "octo-org/hello", NOW)).thenReturn(false);

        assertThat(service().accept("issues", message)).isEqualTo(WebhookIntakeService.Result.DUPLICATE);
        verify(outbox, never()).add(anyString(), anyString(), anyString(), any());
    }
}
