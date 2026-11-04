package com.saisanketh.triage.ai;

/** The parts of an issue an LLM prompt needs. {@code body} may be null (GitHub allows an empty body). */
public record IssueText(int number, String title, String body) {
}
