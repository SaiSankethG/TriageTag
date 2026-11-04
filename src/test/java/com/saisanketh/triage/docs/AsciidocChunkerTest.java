package com.saisanketh.triage.docs;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AsciidocChunkerTest {

    private static final String PAGE = "https://docs.example/ref/data/sql.html";

    @Test
    void cutsAtHeadingsWithTheirAnchorsAndHeadingPath() {
        List<DocSection> sections = AsciidocChunker.chunk(PAGE, """
                [[data.sql]]
                = SQL Databases

                Intro text.

                [[data.sql.datasource]]
                == Configure a DataSource

                [[data.sql.datasource.embedded]]
                === Embedded Database Support

                Use H2 for tests.
                """);

        assertThat(sections).extracting(DocSection::heading)
                .containsExactly("SQL Databases", "SQL Databases > Configure a DataSource > Embedded Database Support");
        assertThat(sections.get(0).sourceUrl()).isEqualTo(PAGE + "#data.sql");
        assertThat(sections.get(1).sourceUrl()).isEqualTo(PAGE + "#data.sql.datasource.embedded");
        assertThat(sections.get(1).text()).isEqualTo("Use H2 for tests.");   // the empty "Configure" section is skipped
    }

    @Test
    void codeBlocksAreKeptAsTheyAreAndCommentsAndAttributesDropped() {
        List<DocSection> sections = AsciidocChunker.chunk(PAGE, """
                = Page
                :page-aliases: old.adoc
                // a comment for doc writers
                include::partial$x.adoc[]
                Text before.
                ----
                == not a heading inside a code block
                // a Java comment, kept
                ----
                """);

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).text())
                .contains("Text before.", "== not a heading inside a code block", "// a Java comment, kept")
                .doesNotContain("page-aliases", "comment for doc writers", "include::");
    }

    @Test
    void docsMacrosBecomePlainText() {
        assertThat(AsciidocChunker.clean("Use javadoc:org.springframework.jdbc.core.JdbcTemplate[] or "
                + "configprop:spring.datasource.url[]; see xref:how-to:data-access.adoc#x[the how-to] and "
                + "{url-spring-data-site}[Spring Data] or https://h2database.com[H2]."))
                .isEqualTo("Use JdbcTemplate or `spring.datasource.url`; see the how-to and Spring Data or H2.");
    }

    @Test
    void longSectionsAreSplitWithOverlap() {
        String paragraph = "word ".repeat(100).strip();   // 499 characters
        String text = String.join("\n\n", paragraph, paragraph, paragraph, paragraph);

        List<String> pieces = AsciidocChunker.split(text);

        assertThat(pieces).hasSizeGreaterThan(1);
        assertThat(pieces).allSatisfy(p -> assertThat(p.length()).isLessThanOrEqualTo(AsciidocChunker.MAX_CHARS));
        String endOfFirst = pieces.get(0).substring(pieces.get(0).length() - 50);
        assertThat(pieces.get(1)).contains(endOfFirst);   // the overlap
    }

    @Test
    void aVeryLongParagraphIsCutToo() {
        assertThat(AsciidocChunker.split("x".repeat(5_000)))
                .allSatisfy(p -> assertThat(p.length()).isLessThanOrEqualTo(AsciidocChunker.MAX_CHARS));
    }
}
