package com.saisanketh.triage.docs;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cuts an AsciiDoc page into sections (at "=", "==", "===" headings) and long sections into overlapping pieces.
 *
 * <p>Size: at most {@link #MAX_CHARS} characters (~300 tokens) per piece, {@link #OVERLAP_CHARS} repeated from the end of
 * the previous piece. The embedding model (all-MiniLM-L6-v2) reads only the first 256 word pieces of a text, so bigger
 * pieces would be cut off by the model anyway.
 *
 * <p>Cleaning: comments, attribute lines and include/ifdef directives are dropped; Spring's docs macros become plain text
 * ({@code javadoc:org.x.JdbcTemplate[]} -> JdbcTemplate, {@code xref:page.adoc#id[Text]} -> Text). Code blocks are kept:
 * a property name or a snippet is often exactly what matches an issue.
 */
public final class AsciidocChunker {

    static final int MAX_CHARS = 1_200;
    static final int OVERLAP_CHARS = 200;

    private static final Pattern HEADING = Pattern.compile("^(={1,6}) (.+)$");
    private static final Pattern ANCHOR = Pattern.compile("^\\[\\[([\\w.\\-]+)(?:,[^]]*)?]]$|^\\[#([\\w.\\-]+)]$");
    private static final Pattern DELIMITER = Pattern.compile("^(-{4,}|\\.{4,}|={4,}|\\*{4,}|_{4,}|\\+{4,})$");
    private static final Pattern SKIPPED = Pattern.compile("^(//.*|:[\\w-]+:.*|include::.*|ifdef::.*|ifndef::.*|endif::.*)$");

    private static final Pattern JAVADOC = Pattern.compile("javadoc:(?:[\\w.]+\\.)?([\\w$]+)(?:#[^\\[\\s]*)?\\[([^]]*)]");
    private static final Pattern CONFIGPROP = Pattern.compile("configprop:([\\w.\\-\\[\\]*]+?)(?:\\+\\+)?\\[[^]]*]");
    private static final Pattern XREF = Pattern.compile("xref:[^\\[\\s]+\\[([^]]*)]");
    private static final Pattern LINK = Pattern.compile("(?:link:|\\{url-[\\w-]+}|https?://[^\\[\\s]+)\\[([^]]*)]");

    private AsciidocChunker() {
    }

    /** A section while reading: its anchor, heading path and lines. */
    private record Section(String anchor, String heading, List<String> lines) {
    }

    /** @param pageUrl the published URL of this page (each piece adds its section's #anchor) */
    public static List<DocSection> chunk(String pageUrl, String adoc) {
        List<Section> sections = new ArrayList<>();
        List<String> path = new ArrayList<>();   // titles of the current heading levels
        Section current = new Section(null, "", new ArrayList<>());
        String pendingAnchor = null;
        String openDelimiter = null;             // inside ---- / .... blocks, "=" lines are content, not headings

        for (String line : adoc.split("\n", -1)) {
            String trimmed = line.strip();
            if (openDelimiter == null) {
                Matcher anchor = ANCHOR.matcher(trimmed);
                Matcher heading = HEADING.matcher(trimmed);
                if (anchor.matches()) {
                    pendingAnchor = anchor.group(1) != null ? anchor.group(1) : anchor.group(2);
                    continue;
                }
                if (heading.matches()) {
                    sections.add(current);
                    int level = heading.group(1).length();
                    while (path.size() >= level) {
                        path.remove(path.size() - 1);
                    }
                    while (path.size() < level - 1) {
                        path.add("");               // a skipped level: keep positions right
                    }
                    path.add(clean(heading.group(2)));
                    current = new Section(pendingAnchor,
                            String.join(" > ", path.stream().filter(p -> !p.isEmpty()).toList()), new ArrayList<>());
                    pendingAnchor = null;
                    continue;
                }
                if (SKIPPED.matcher(trimmed).matches()) {
                    continue;
                }
            }
            if (DELIMITER.matcher(trimmed).matches()) {
                openDelimiter = openDelimiter == null ? trimmed : (trimmed.equals(openDelimiter) ? null : openDelimiter);
            }
            pendingAnchor = trimmed.isEmpty() ? pendingAnchor : null;
            current.lines().add(clean(line));
        }
        sections.add(current);

        List<DocSection> result = new ArrayList<>();
        for (Section s : sections) {
            String body = String.join("\n", s.lines()).replaceAll("\n{3,}", "\n\n").strip();
            if (body.isEmpty()) {
                continue;                           // a heading with nothing under it (its subsections have the text)
            }
            String url = s.anchor() == null ? pageUrl : pageUrl + "#" + s.anchor();
            for (String piece : split(body)) {
                result.add(new DocSection(url, s.heading(), piece));
            }
        }
        return result;
    }

    /** Macros -> their plain text. */
    static String clean(String line) {
        String s = JAVADOC.matcher(line).replaceAll(m -> Matcher.quoteReplacement(m.group(2).isEmpty() ? m.group(1) : m.group(2)));
        s = CONFIGPROP.matcher(s).replaceAll(m -> Matcher.quoteReplacement("`" + m.group(1) + "`"));
        s = XREF.matcher(s).replaceAll(m -> Matcher.quoteReplacement(m.group(1)));
        s = LINK.matcher(s).replaceAll(m -> Matcher.quoteReplacement(m.group(1)));
        return s;
    }

    /**
     * Pieces of at most MAX_CHARS, cut at paragraph breaks where possible; each new piece starts with the last
     * OVERLAP_CHARS of the previous one (from a word start), so a sentence at the border is in both.
     */
    static List<String> split(String text) {
        List<String> pieces = new ArrayList<>();
        StringBuilder piece = new StringBuilder();
        for (String paragraph : text.split("\n\n")) {
            for (String part : hardSplit(paragraph)) {
                if (piece.length() > 0 && piece.length() + 2 + part.length() > MAX_CHARS) {
                    pieces.add(piece.toString());
                    piece = new StringBuilder(tail(piece.toString()));
                }
                if (piece.length() > 0) {
                    piece.append("\n\n");
                }
                piece.append(part);
            }
        }
        if (!piece.isEmpty()) {
            pieces.add(piece.toString());
        }
        return pieces;
    }

    /** A paragraph longer than what fits next to an overlap is cut into plain slices. */
    private static List<String> hardSplit(String paragraph) {
        int size = MAX_CHARS - OVERLAP_CHARS - 2;
        List<String> parts = new ArrayList<>();
        for (int start = 0; start < paragraph.length(); start += size) {
            parts.add(paragraph.substring(start, Math.min(paragraph.length(), start + size)));
        }
        return parts;
    }

    private static String tail(String text) {
        if (text.length() <= OVERLAP_CHARS) {
            return text;
        }
        String tail = text.substring(text.length() - OVERLAP_CHARS);
        int space = tail.indexOf(' ');
        return space < 0 ? tail : tail.substring(space + 1);
    }
}
