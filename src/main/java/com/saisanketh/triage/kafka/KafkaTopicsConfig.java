package com.saisanketh.triage.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * The Kafka topics this app uses. Spring's KafkaAdmin creates every NewTopic bean at startup
 * if it doesn't exist yet (and leaves existing ones alone).
 */
@Configuration
public class KafkaTopicsConfig {

    /** Every GitHub "issues" webhook we accept becomes one message here. */
    public static final String ISSUE_EVENTS = "issue-events";
    /** Messages that still fail after the retries end up here, so they don't block the main topic. */
    public static final String ISSUE_EVENTS_DLT = ISSUE_EVENTS + ".DLT";

    /**
     * 3 partitions: up to 3 consumers in one group can work in parallel. Messages with the same key
     * (one issue) always go to the same partition, so one issue's events stay in order.
     */
    static final int PARTITIONS = 3;
    /** 1 copy of each message: we have a single broker. Production would use 3. */
    static final int REPLICAS = 1;

    @Bean
    NewTopic issueEventsTopic() {
        return TopicBuilder.name(ISSUE_EVENTS).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    /**
     * Same number of partitions as the main topic: by default a failed message from partition 2
     * is sent to partition 2 of the dead-letter topic.
     */
    @Bean
    NewTopic issueEventsDeadLetterTopic() {
        return TopicBuilder.name(ISSUE_EVENTS_DLT).partitions(PARTITIONS).replicas(REPLICAS).build();
    }
}
