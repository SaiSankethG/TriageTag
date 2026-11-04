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
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RAG draft reply: the 5 docs sections nearest to the new issue + its 3 most similar earlier issues -> the LLM writes a
 * short reply that cites the links it used.
 *
 * <p>Only sources above a similarity threshold are used. If none is left, there is no LLM call and no draft: "nothing
 * relevant found" is more useful to a maintainer than a confident-sounding guess. Links in the draft are checked against
 * the sources; any other link is reported in {@code unknownLinks}.
 */
@Slf4j
@Service
public class DraftReplyService {

    static final String PROMPT_NAME = "draft-reply";
    static final String PROMPT_VERSION = "v1";
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

    public DraftReplyService(TriageLlm llm, PromptTemplates templates, DocIngestService docs,
                             @Value("${triage.draft.min-doc-score:0.55}") double minDocScore,
                             @Value("${triage.draft.min-issue-score:0.75}") double minIssueScore) {
        this.llm = llm;
        this.templates = templates;
        this.docs = docs;
        this.minDocScore = minDocScore;
        this.minIssueScore = minIssueScore;
    }

    /** @param similar the new issue's most similar earlier issues (from the vector search), best first */
    public DraftReply draft(String repo, IssueText issue, List<SimilarIssue> similar) {
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
