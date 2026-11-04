package com.saisanketh.triage.triage;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the Spring Boot version a reporter uses in an issue body, from the ways people usually write it.
 * Spring Boot-specific on purpose (the hint is built for spring-boot first); the first pattern that matches wins.
 */
public final class ReporterVersion {

    private ReporterVersion() {
    }

    private static final String V = "(\\d+\\.\\d+\\.\\d+(?:[.-](?:RELEASE|M\\d+|RC\\d+|SNAPSHOT))?)";

    static final List<Pattern> PATTERNS = List.of(
            // <artifactId>spring-boot-starter-parent</artifactId> <version>3.2.1</version>
            Pattern.compile("(?is)<artifactId>\\s*spring-boot-(?:starter-parent|dependencies)\\s*</artifactId>\\s*<version>\\s*" + V),
            // id 'org.springframework.boot' version '3.2.1'  /  id("org.springframework.boot") version "3.2.1"
            Pattern.compile("(?i)org\\.springframework\\.boot['\"]\\)?\\s+version\\s+['\"]" + V),
            // springBootVersion = '3.2.1'
            Pattern.compile("(?i)springBootVersion\\s*[=:]\\s*['\"]?" + V),
            // "Spring Boot 3.2.1", "Spring Boot version: 3.2.1", "spring-boot v3.2.1"
            Pattern.compile("(?i)spring[ -]?boot(?:[ -]version)?\\s*[:=]?\\s*v?" + V),
            // jar names and coordinates: spring-boot-autoconfigure-3.2.1.jar, spring-boot:3.2.1
            Pattern.compile("(?i)spring-boot[\\w-]*[-:]" + V));

    public static Optional<String> find(String body) {
        if (body == null) {
            return Optional.empty();
        }
        for (Pattern pattern : PATTERNS) {
            Matcher m = pattern.matcher(body);
            if (m.find()) {
                return Optional.of(m.group(1));
            }
        }
        return Optional.empty();
    }
}
