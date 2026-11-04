package com.saisanketh.triage.service;

import com.saisanketh.triage.kafka.IssueEventMessage;
import com.saisanketh.triage.kafka.IssueEventPublisher;
import com.saisanketh.triage.kafka.IssueEventPublisher.PublishFailedException;
import com.saisanketh.triage.repository.WebhookDeliveryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookIntakeServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");

    @Mock
    private WebhookDeliveryRepository deliveryRepository;
    @Mock
    private IssueEventPublisher publisher;

    private final IssueEventMessage message = new IssueEventMessage("d-1", "opened", "octo-org/hello",
            JsonMapper.builder().build().readTree("{\"number\": 7}"));

    private WebhookIntakeService service() {
        return new WebhookIntakeService(deliveryRepository, publisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void aNewDeliveryIsRecordedAndPublished() {
        when(deliveryRepository.insertIfNew("d-1", "issues", "opened", "octo-org/hello", NOW)).thenReturn(true);

        assertThat(service().accept("issues", message)).isEqualTo(WebhookIntakeService.Result.ACCEPTED);
        verify(publisher).publishAndWait(message);
    }

    @Test
    void aRepeatedDeliveryIsNotPublishedAgain() {
        when(deliveryRepository.insertIfNew("d-1", "issues", "opened", "octo-org/hello", NOW)).thenReturn(false);

        assertThat(service().accept("issues", message)).isEqualTo(WebhookIntakeService.Result.DUPLICATE);
        verify(publisher, never()).publishAndWait(message);
    }

    @Test
    void aPublishFailureIsPassedOn() {
        when(deliveryRepository.insertIfNew("d-1", "issues", "opened", "octo-org/hello", NOW)).thenReturn(true);
        doThrow(new PublishFailedException("no ack", null)).when(publisher).publishAndWait(message);

        // The exception must escape the @Transactional method: that's what makes Spring roll back the row.
        assertThatThrownBy(() -> service().accept("issues", message)).isInstanceOf(PublishFailedException.class);
    }
}
