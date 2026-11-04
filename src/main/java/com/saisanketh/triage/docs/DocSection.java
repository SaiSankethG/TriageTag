package com.saisanketh.triage.docs;

/**
 * One piece of a docs page, ready to embed.
 *
 * @param sourceUrl the published page + "#anchor" of its section (no anchor if the section has none)
 * @param heading   "Page title > Section > Subsection"
 * @param text      the cleaned text (a part of the section if it was long)
 */
public record DocSection(String sourceUrl, String heading, String text) {
}
