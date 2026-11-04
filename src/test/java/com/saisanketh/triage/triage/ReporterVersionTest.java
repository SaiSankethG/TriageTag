package com.saisanketh.triage.triage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReporterVersionTest {

    @Test
    void findsTheUsualWaysOfWritingTheVersion() {
        assertThat(ReporterVersion.find("""
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.2.1</version>
                </parent>""")).contains("3.2.1");
        assertThat(ReporterVersion.find("plugins { id 'org.springframework.boot' version '3.3.0-RC1' }")).contains("3.3.0-RC1");
        assertThat(ReporterVersion.find("id(\"org.springframework.boot\") version \"3.4.2\"")).contains("3.4.2");
        assertThat(ReporterVersion.find("springBootVersion = '2.7.18'")).contains("2.7.18");
        assertThat(ReporterVersion.find("Since upgrading to Spring Boot 3.1.5 the app fails")).contains("3.1.5");
        assertThat(ReporterVersion.find("Spring Boot version: 2.1.0.RELEASE")).contains("2.1.0.RELEASE");
        assertThat(ReporterVersion.find("at spring-boot-autoconfigure-3.0.2.jar")).contains("3.0.2");
    }

    @Test
    void otherVersionsAreNotTakenForSpringBoot() {
        assertThat(ReporterVersion.find("Java 21.0.1 and Hibernate 6.4.1")).isEmpty();
        assertThat(ReporterVersion.find(null)).isEmpty();
    }
}
