package com.saisanketh.triage.report;

import com.saisanketh.triage.docs.DocIngestService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepoPartsTest {

    private final DocIngestService docs = mock(DocIngestService.class);
    private final RepoParts parts = new RepoParts(docs, List.of(" Spring-Projects/Spring-Boot ", ""));

    @Test
    void springBootGetsEveryPart() {
        when(docs.hasDocs("spring-projects/spring-boot")).thenReturn(true);

        assertThat(parts.hasFixedHint("spring-projects/spring-boot")).isTrue();   // names compared ignoring case and spaces
        assertThat(parts.notAvailable("spring-projects/spring-boot")).isEmpty();
    }

    @Test
    void anotherRepoIsToldWhatItDoesNotGetDocsFirst() {
        when(docs.hasDocs("octo-org/hello")).thenReturn(false);

        assertThat(parts.hasFixedHint("octo-org/hello")).isFalse();
        assertThat(parts.notAvailable("octo-org/hello")).extracting(RepoParts.NotAvailable::part)
                .containsExactly("docs", "fixedHint");
        assertThat(parts.notAvailable("octo-org/hello").get(1).reason()).contains("Spring Boot");
    }

    @Test
    void aRepoWithDocsButNoVersionHintMissesOnlyTheHint() {
        when(docs.hasDocs("octo-org/docs")).thenReturn(true);
        assertThat(parts.notAvailable("octo-org/docs")).containsExactly(RepoParts.NO_FIXED_HINT);
    }
}
