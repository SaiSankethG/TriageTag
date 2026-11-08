package com.saisanketh.triage.e2e;

import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Backend piece 3: the triage worker runs 3 consumers, and once Kafka has balanced the group each one reads exactly one of the
 * 3 partitions of issue-events (so all three work in parallel and none sits idle).
 */
class WorkerConcurrencyTest extends FullStackTest {

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Test
    void threeWorkersEachReadOnePartition() throws Exception {
        MessageListenerContainer container = registry.getListenerContainers().stream()
                .filter(c -> "triage-worker".equals(c.getGroupId())).findFirst().orElseThrow();
        assertThat(container).isInstanceOf(ConcurrentMessageListenerContainer.class);
        ConcurrentMessageListenerContainer<?, ?> workers = (ConcurrentMessageListenerContainer<?, ?>) container;
        assertThat(workers.getConcurrency()).isEqualTo(3);

        waitUntil("every partition has a worker", () -> partitionsPerWorker(workers).stream().mapToInt(Integer::intValue)
                .sum() == 3);
        assertThat(partitionsPerWorker(workers)).containsExactly(1, 1, 1);
    }

    private static List<Integer> partitionsPerWorker(ConcurrentMessageListenerContainer<?, ?> workers) {
        return workers.getContainers().stream()
                .map(MessageListenerContainer::getAssignedPartitions)
                .map(p -> p == null ? 0 : (int) p.stream().map(TopicPartition::topic)
                        .filter("issue-events"::equals).count())
                .toList();
    }
}
