package com.saisanketh.triage.service;

import com.saisanketh.triage.kafka.IssueEventMessage;
import com.saisanketh.triage.kafka.IssueEventPublisher;
import com.saisanketh.triage.repository.WebhookDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Takes an accepted "issues" webhook: remember the delivery id and put the event on Kafka, together.
 *
 * <p>One transaction: (1) insert the delivery row; if it already existed, this is a redelivery: stop.
 * (2) publish to Kafka and wait for the ack. If (2) throws, the transaction rolls back, so the delivery
 * row disappears and a later "Redeliver" from GitHub is processed normally.
 * Known gap: if Kafka confirms but the commit then fails, the message is out and the row isn't, so a
 * redelivery publishes twice. The consumer's upsert is idempotent, so that's harmless here. The proper
 * fix is the transactional outbox pattern (see learning-notes, M2 step 3).
 */
@Service
@RequiredArgsConstructor
public class WebhookIntakeService {

    public enum Result { ACCEPTED, DUPLICATE }

    private final WebhookDeliveryRepository deliveryRepository;
    private final IssueEventPublisher publisher;
    private final Clock clock;

    @Transactional
    public Result accept(String event, IssueEventMessage message) {
        boolean isNew = deliveryRepository.insertIfNew(
                message.deliveryId(), event, message.action(), message.repo(), Instant.now(clock));
        if (!isNew) {
            return Result.DUPLICATE;
        }
        publisher.publishAndWait(message);   // throws -> rollback -> the caller answers 500
        return Result.ACCEPTED;
    }
}
