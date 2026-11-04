package com.saisanketh.triage.triage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateFilterTest {

    private final CandidateFilter filter = new CandidateFilter(new CandidateFilterProperties(
            Map.of("Spring-Projects/Spring-Boot", List.of("status: forward-port"))));

    @Test
    void returnsTheConfiguredLabelsIgnoringCase() {
        assertThat(filter.excludedLabels("spring-projects/spring-boot")).containsExactly("status: forward-port");
    }

    @Test
    void aRepoWithoutSettingsExcludesNothing() {
        assertThat(filter.excludedLabels("octo-org/other")).isEmpty();
    }

    @Test
    void noSettingsAtAllIsFine() {
        assertThat(new CandidateFilter(new CandidateFilterProperties(null)).excludedLabels("a/b")).isEmpty();
    }
}
