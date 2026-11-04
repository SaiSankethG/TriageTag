package com.saisanketh.triage.embedding;

import java.util.regex.Pattern;

/**
 * The text we embed for an issue: title + the first 2,000 characters of the body, cleaned.
 * The same window as the keyword search (Flyway V5), so keyword vs vector is a fair comparison.
 */
public final class EmbeddingText {

    static final int MAX_BODY_CHARS = 2_000;

    /** HTML comments, e.g. GitHub issue-template instructions: <!-- Please include your Spring Boot version -->. */
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private EmbeddingText() {
    }

    public static String of(String title, String body) {
        String cleanBody = body == null ? "" : HTML_COMMENT.matcher(body).replaceAll(" ");
        if (cleanBody.length() > MAX_BODY_CHARS) {
            cleanBody = cleanBody.substring(0, MAX_BODY_CHARS);
        }
        String text = (title == null ? "" : title) + "\n" + cleanBody;
        return WHITESPACE.matcher(text).replaceAll(" ").strip();
    }
}
