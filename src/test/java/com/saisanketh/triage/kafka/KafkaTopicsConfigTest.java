package com.saisanketh.triage.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTopicsConfigTest {

    private final KafkaTopicsConfig config = new KafkaTopicsConfig();

    @Test
    void issueEventsHasThreePartitions() {
        NewTopic topic = config.issueEventsTopic();

        assertThat(topic.name()).isEqualTo("issue-events");
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    void deadLetterTopicHasTheSamePartitionsAsTheMainTopic() {
        NewTopic dlt = config.issueEventsDeadLetterTopic();

        assertThat(dlt.name()).isEqualTo("issue-events.DLT");
        assertThat(dlt.numPartitions()).isEqualTo(config.issueEventsTopic().numPartitions());
    }
}
