package com.saisanketh.triage.deploy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** The dashboard file parses and every query uses a metric the app really exports (no typos in metric names). */
class GrafanaDashboardTest {

    static final List<String> KNOWN = List.of("triage_webhook_to_report_seconds_bucket", "triage_webhook_to_report_seconds_count",
            "triage_llm_tokens_total", "triage_llm_cost_usd_total", "triage_kafka_dead_letters_total", "triage_outbox_unsent",
            "kafka_consumer_fetch_manager_records_lag_max", "triage_duplicates_suggested_total", "triage_comments_total");

    @Test
    void everyPanelQueriesAKnownMetric() throws Exception {
        JsonNode dashboard = JsonMapper.builder().build()
                .readTree(Files.readString(Path.of("deploy/grafana/triage-dashboard.json")));
        List<String> used = new ArrayList<>();
        for (JsonNode panel : dashboard.get("panels")) {
            for (JsonNode target : panel.get("targets")) {
                Matcher m = Pattern.compile("\\b((?:triage|kafka)_[a-z_]+)").matcher(target.get("expr").asString());
                while (m.find()) {
                    used.add(m.group(1));
                }
            }
        }
        assertThat(dashboard.get("panels").size()).isEqualTo(9);
        assertThat(used).isNotEmpty().allSatisfy(name -> assertThat(KNOWN).contains(name));
        assertThat(used).containsAll(KNOWN);   // and every metric has a panel
    }
}
