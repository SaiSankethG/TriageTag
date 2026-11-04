package com.saisanketh.triage.e2e;

import com.saisanketh.triage.kafka.IssueEventMessage;
import com.saisanketh.triage.kafka.KafkaTopicsConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/** The consumer's error handling with a real Kafka: retries, dead-letter topic, no blocked partition. */
class KafkaErrorHandlingTest extends FullStackTest {

    @Test
    void aBrokenMessageGoesToTheDeadLetterTopicAndDoesNotBlockTheNextOne() throws Exception {
        IssueEventMessage good = fixtureMessage("d-good");
        String key = good.key();   // same key for both -> same partition -> the bad one is in front of the good one

        kafkaTemplate.send(KafkaTopicsConfig.ISSUE_EVENTS, key, "this is not json").get();
        kafkaTemplate.send(KafkaTopicsConfig.ISSUE_EVENTS, key, jsonMapper.writeValueAsString(good)).get();

        // The good message behind the bad one is still processed.
        waitUntil("the issue row exists", () -> countRows("select count(*) from issue") == 1);

        // The bad one is on the DLT, with the reason in a header added by Spring.
        List<ConsumerRecord<String, String>> dead = readTopic(KafkaTopicsConfig.ISSUE_EVENTS_DLT, 1);
        assertThat(dead).extracting(ConsumerRecord::value).contains("this is not json");
        ConsumerRecord<String, String> record = dead.stream()
                .filter(r -> "this is not json".equals(r.value())).findFirst().orElseThrow();
        // Spring wraps listener errors in ListenerExecutionFailedException; our exception is the "cause" header.
        Header reason = record.headers().lastHeader("kafka_dlt-exception-cause-fqcn");
        assertThat(new String(reason.value(), StandardCharsets.UTF_8)).endsWith("BadIssueEventException");
    }

    @Test
    void aTemporaryDatabaseErrorIsRetriedAndThenSucceeds() throws Exception {
        // The first upsert fails as if the database blinked; the retry calls the real method.
        doThrow(new TransientDataAccessResourceException("database blinked"))
                .doCallRealMethod()
                .when(issueService).upsert(any());
        IssueEventMessage message = fixtureMessage("d-retry");

        kafkaTemplate.send(KafkaTopicsConfig.ISSUE_EVENTS, message.key(), jsonMapper.writeValueAsString(message)).get();

        waitUntil("the issue row exists after a retry", () -> countRows("select count(*) from issue") == 1);
        verify(issueService, atLeast(2)).upsert(any());   // 1 failure + 1 successful retry
    }
}
