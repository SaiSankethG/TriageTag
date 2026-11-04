package com.saisanketh.triage.service;

import com.saisanketh.triage.kafka.IssueEventMessage;
import com.saisanketh.triage.kafka.KafkaTopicsConfig;
import com.saisanketh.triage.outbox.OutboxRepository;
import com.saisanketh.triage.repository.WebhookDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;

/**
 * Takes an accepted "issues" webhook: remember the delivery id and queue the event, in ONE database transaction
 * (the transactional outbox, M5 step 0).
 *
 * <p>(1) Insert the delivery row; if it already existed, this is a redelivery: stop. (2) Insert the event into
 * outbox_event. Both rows commit together or not at all. Kafka isn't called here: the OutboxRelay sends the row later.
 * So the webhook is answered quickly and still accepted while Kafka is down, and there is no gap any more between
 * "Kafka has it" and "we recorded it" (M2's version published inside the transaction and could publish twice).
 */
@Service
@RequiredArgsConstructor
public class WebhookIntakeService {

    public enum Result { ACCEPTED, DUPLICATE }

    private final WebhookDeliveryRepository deliveryRepository;
    private final OutboxRepository outbox;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Transactional
    public Result accept(String event, IssueEventMessage message) {
        Instant now = Instant.now(clock);
        boolean isNew = deliveryRepository.insertIfNew(message.deliveryId(), event, message.action(), message.repo(), now);
        if (!isNew) {
            return Result.DUPLICATE;
        }
        outbox.add(KafkaTopicsConfig.ISSUE_EVENTS, message.key(), jsonMapper.writeValueAsString(message), now);
        return Result.ACCEPTED;
    }
}
