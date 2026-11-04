package com.saisanketh.triage.ai;

import com.saisanketh.triage.ai.DraftReply.Source;
import com.saisanketh.triage.ai.DraftReply.Status;
import com.saisanketh.triage.docs.DocChunkRepository.DocMatch;
import com.saisanketh.triage.docs.DocIngestService;
import com.saisanketh.triage.embedding.EmbeddingText;
import com.saisanketh.triage.triage.SimilarIssue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * RAG draft reply: the 5 docs sections nearest to the new issue + its 3 most similar earlier issues -> the LLM writes a
 * short reply that cites the links it used. Since prompt v3 it also gets the top duplicate match (state, score level, the
 * AI check's verdict) and the missing-details result, so the draft leads with a likely duplicate and asks only for what is
 * really missing.
 *
 * <p>Only sources above a similarity threshold are used. If none is left, there is no LLM call and no draft: "nothing
 * relevant found" is more useful to a maintainer than a confident-sounding guess. Links in the draft are checked against
 * the sources; any other link is reported in {@code unknownLinks}.
 */
@Slf4j
@Service
public class DraftReplyService {

    static final String PROMPT_NAME = "draft-reply";
    // v2: v1 drafts cited sources as "[5]" instead of their URLs.
    // v3: v2 ignored the duplicate finding, talked about "the sources I have", and asked for details that weren't missing.
    static final String PROMPT_VERSION = "v3";
    static final int DOCS = 5;
    /** How much of each docs section goes into the prompt. */
    static final int SOURCE_CHARS = 1_200;
    static final int MAX_BODY_CHARS = 4_000;
    private static final Pattern URL = Pattern.compile("https?://[^\\s)\\]>\"']+");

    private final TriageLlm llm;
    private final PromptTemplates templates;
    private final DocIngestService docs;
    private final double minDocScore;
    private final double minIssueScore;
    private final double possibleThreshold;
    private final double highThreshold;

    /**
     * The new issue's best earlier match, as the draft should mention it.
     *
     * @param state       "open" / "closed"
     * @param stateReason completed / not_planned / duplicate, or null
     * @param check       the LLM duplicate check, or null if it didn't run (score below the comment threshold)
     */
    public record TopMatch(int number, String title, String state, String stateReason, double score, DuplicateCheck check) {
    }

    public DraftReplyService(TriageLlm llm, PromptTemplates templates, DocIngestService docs,
                             @Value("${triage.draft.min-doc-score:0.55}") double minDocScore,
                             @Value("${triage.draft.min-issue-score:0.75}") double minIssueScore,
                             @Value("${triage.duplicates.possible-threshold:0.75}") double possibleThreshold,
                             @Value("${triage.duplicates.comment-threshold:0.90}") double highThreshold) {
        this.llm = llm;
        this.templates = templates;
        this.docs = docs;
        this.minDocScore = minDocScore;
        this.minIssueScore = minIssueScore;
        this.possibleThreshold = possibleThreshold;
        this.highThreshold = highThreshold;
    }

    /**
     * @param similar  the new issue's most similar earlier issues (from the vector search), best first
     * @param topMatch the best of them with its state and the LLM check; null if there is none
     * @param missing  the missing-details result; null if it isn't known
     */
    public DraftReply draft(String repo, IssueText issue, List<SimilarIssue> similar, TopMatch topMatch,
                            MissingDetails missing) {
        List<Source> sources = new ArrayList<>();
        StringBuilder sourceText = new StringBuilder();
        for (DocMatch m : docs.search(repo, EmbeddingText.of(issue.title(), issue.body()), DOCS)) {
            if (m.score() >= minDocScore) {
                sources.add(new Source("docs", m.heading(), m.sourceUrl(), m.score()));
                appendSource(sourceText, sources.size(), "docs", m.heading(), m.sourceUrl(), m.text());
            }
        }
        for (SimilarIssue s : similar) {
            if (s.score() >= minIssueScore) {
                String url = "https://github.com/" + repo + "/issues/" + s.number();
                sources.add(new Source("issue", "#" + s.number() + " " + s.title(), url, s.score()));
                appendSource(sourceText, sources.size(), "issue", "#" + s.number() + " " + s.title(), url, "");
            }
        }
        if (sources.isEmpty()) {
            return new DraftReply(Status.NOTHING_RELEVANT,
                    "No docs section or earlier issue is similar enough to base a reply on, so there is no draft.",
                    List.of(), List.of(), null, 0);
        }

        String body = issue.body() == null || issue.body().isBlank() ? "(empty)" : issue.body();
        Prompt prompt = templates.render(PROMPT_NAME, PROMPT_VERSION, Map.of(
                "repo", repo,
                "top_match", topMatchText(repo, topMatch),
                "missing", missingText(missing),
                "sources", sourceText.toString().strip(),
                "title", issue.title(),
                "body", body.length() <= MAX_BODY_CHARS ? body : body.substring(0, MAX_BODY_CHARS) + "\n[... cut ...]"));
        LlmReply reply;
        try {
            reply = llm.complete(prompt);
        } catch (LlmException e) {
            log.warn("Draft reply: LLM call failed: {}", e.getMessage());
            return new DraftReply(Status.UNKNOWN, "No draft: " + e.getMessage(), sources, List.of(), prompt.id(), 0);
        }
        String text = reply.text().strip();
        return new DraftReply(Status.OK, text, sources, unknownLinks(text, sources), prompt.id(), reply.totalTokens());
    }

    /** What the prompt says about the best match: nothing below Medium similarity, so a weak match isn't presented as one. */
    String topMatchText(String repo, TopMatch t) {
        if (t == null || t.score() < possibleThreshold) {
            return "None: no earlier issue is similar enough to mention.";
        }
        String level = t.score() >= highThreshold ? "high" : "medium";
        String check = t.check() == null ? "not run (it only runs at high similarity)"
                : t.check().verdict().name().toLowerCase(Locale.ROOT) + ": " + t.check().reason();
        return String.format(Locale.ROOT, "#%d \"%s\" (https://github.com/%s/issues/%d)%nSimilarity: %.1f%% (%s)%n"
                        + "State: %s%nAI duplicate check: %s", t.number(), t.title(), repo, t.number(), t.score() * 100,
                level, stateText(t.state(), t.stateReason()), check);
    }

    static String stateText(String state, String stateReason) {
        if (!"closed".equals(state)) {
            return "still open";
        }
        if (stateReason == null) {
            return "closed";
        }
        return switch (stateReason) {
            case "completed" -> "closed as completed (fixed)";
            case "not_planned" -> "closed as not planned";
            case "duplicate" -> "closed as a duplicate";
            default -> "closed (" + stateReason + ")";
        };
    }

    /** Only MISSING details may be asked for; unknown = ask for nothing rather than guess. */
    static String missingText(MissingDetails missing) {
        if (missing == null || missing.status() != MissingDetails.Status.OK) {
            return "Unknown (the check did not run). Do not ask for details.";
        }
        if (missing.missing().isEmpty()) {
            return "None: the reporter gave everything needed. Do not ask for more details.";
        }
        return "Ask only for: " + missing.missing().stream()
                .map(d -> d.key.replace('_', ' ')).collect(Collectors.joining(", ")) + ".";
    }

    /** "[1] (docs) Heading (url)" then the text: the header line format is also what the fake LLM reads. */
    private static void appendSource(StringBuilder out, int n, String kind, String title, String url, String text) {
        out.append('[').append(n).append("] (").append(kind).append(") ").append(title).append(" (").append(url).append(")\n");
        if (!text.isBlank()) {
            out.append(text.length() <= SOURCE_CHARS ? text : text.substring(0, SOURCE_CHARS) + " ...").append('\n');
        }
        out.append('\n');
    }

    /** Links in the draft that aren't one of the sources (trailing '.' or ',' ignored). */
    static List<String> unknownLinks(String draft, List<Source> sources) {
        Set<String> allowed = new LinkedHashSet<>();
        sources.forEach(s -> allowed.add(s.url()));
        List<String> unknown = new ArrayList<>();
        Matcher m = URL.matcher(draft);
        while (m.find()) {
            String url = m.group().replaceAll("[.,;:]+$", "");
            if (!allowed.contains(url)) {
                unknown.add(url);
            }
        }
        return unknown;
    }
}
