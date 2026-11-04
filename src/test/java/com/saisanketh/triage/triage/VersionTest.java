package com.saisanketh.triage.triage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VersionTest {

    private static Version v(String text) {
        return Version.parse(text).orElseThrow();
    }

    @Test
    void ordersReleasesAndPreReleases() {
        assertThat(v("3.5.0-SNAPSHOT")).isLessThan(v("3.5.0-M1"));
        assertThat(v("3.5.0-M1")).isLessThan(v("3.5.0-M2"));
        assertThat(v("3.5.0-M2")).isLessThan(v("3.5.0-RC1"));
        assertThat(v("3.5.0-RC1")).isLessThan(v("3.5.0"));
        assertThat(v("3.5.0")).isLessThan(v("3.5.1"));
        assertThat(v("3.4.10")).isGreaterThan(v("3.4.9"));     // numbers, not text
        assertThat(v("2.1.0.RELEASE")).isEqualByComparingTo(v("2.1.0"));
        assertThat(v("v3.2")).isEqualByComparingTo(v("3.2.0"));
    }

    @Test
    void milestoneTitlesThatAreNotVersionsAreEmpty() {
        assertThat(Version.parse("General Backlog")).isEmpty();
        assertThat(Version.parse("4.x")).isEmpty();
        assertThat(Version.parse(null)).isEmpty();
    }
}
