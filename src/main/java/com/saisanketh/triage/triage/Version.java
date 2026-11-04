package com.saisanketh.triage.triage;

import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A release version like 3.4.2, 3.5.0-M1, 3.5.0-RC2, 2.1.0.RELEASE or 3.3.0-SNAPSHOT, comparable in release order:
 * 3.5.0-SNAPSHOT < 3.5.0-M1 < 3.5.0-RC1 < 3.5.0 < 3.5.1.
 *
 * @param stage   0 snapshot, 1 milestone (M), 2 release candidate (RC), 3 release
 * @param stageNo the n in Mn / RCn (0 otherwise)
 */
public record Version(int major, int minor, int patch, int stage, int stageNo) implements Comparable<Version> {

    static final Pattern PATTERN = Pattern.compile(
            "(?i)^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:[.-](RELEASE|FINAL|GA|SNAPSHOT|BUILD-SNAPSHOT|M(\\d+)|RC(\\d+)))?$");

    private static final Comparator<Version> ORDER = Comparator.comparingInt(Version::major)
            .thenComparingInt(Version::minor).thenComparingInt(Version::patch)
            .thenComparingInt(Version::stage).thenComparingInt(Version::stageNo);

    /** Empty for anything that isn't a plain version, e.g. milestone titles like "General Backlog" or "4.x". */
    public static Optional<Version> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher m = PATTERN.matcher(text.strip());
        if (!m.matches()) {
            return Optional.empty();
        }
        int patch = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
        String qualifier = m.group(4) == null ? "" : m.group(4).toUpperCase();
        int stage;
        int stageNo = 0;
        if (m.group(5) != null) {
            stage = 1;
            stageNo = Integer.parseInt(m.group(5));
        } else if (m.group(6) != null) {
            stage = 2;
            stageNo = Integer.parseInt(m.group(6));
        } else if (qualifier.endsWith("SNAPSHOT")) {
            stage = 0;
        } else {
            stage = 3;
        }
        return Optional.of(new Version(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), patch, stage, stageNo));
    }

    @Override
    public int compareTo(Version other) {
        return ORDER.compare(this, other);
    }
}
