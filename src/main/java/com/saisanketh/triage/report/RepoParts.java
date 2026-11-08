package com.saisanketh.triage.report;

import com.saisanketh.triage.docs.DocIngestService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which report parts a repository gets (backend piece 4). Most parts work for any repo (similar issues, labels, missing details,
 * a draft from earlier issues). Two were built for spring-boot and are switched on per repo:
 * <ul>
 *   <li><b>"Already fixed?" hint</b> ({@code triage.fixed-hint.repos}): it reads the reporter's <em>Spring Boot</em> version
 *       from the issue (ReporterVersion). On another repo it would never find a version and always say "ask for the version",
 *       which is wrong, so it doesn't run there.</li>
 *   <li><b>Reference docs</b> ({@code triage.docs.sources}): only repos with docs set up get docs in their draft replies;
 *       the others' drafts use earlier issues only.</li>
 * </ul>
 * The reports page shows a missing part as "Not available for this repository" with the reason, instead of leaving it out.
 */
@Component
public class RepoParts {

    /**
     * A report part that this repository doesn't get.
     *
     * @param part   the report's field it is about: "fixedHint" or "docs"
     * @param reason one plain sentence for the reports page
     */
    public record NotAvailable(String part, String reason) {
    }

    static final NotAvailable NO_FIXED_HINT = new NotAvailable("fixedHint",
            "The \"already fixed?\" check reads the reporter's Spring Boot version, so it is only set up for Spring Boot.");
    static final NotAvailable NO_DOCS = new NotAvailable("docs",
            "No reference docs are set up for this repository, so draft replies are based on earlier issues only.");

    private final DocIngestService docs;
    /** lower-case "owner/name" */
    private final Set<String> fixedHintRepos;

    public RepoParts(DocIngestService docs,
                     @Value("${triage.fixed-hint.repos:spring-projects/spring-boot}") List<String> fixedHintRepos) {
        this.docs = docs;
        this.fixedHintRepos = fixedHintRepos.stream().map(r -> r.trim().toLowerCase(Locale.ROOT)).filter(r -> !r.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean hasFixedHint(String repo) {
        return fixedHintRepos.contains(repo.toLowerCase(Locale.ROOT));
    }

    public boolean hasDocs(String repo) {
        return docs.hasDocs(repo);
    }

    /** The parts this repo doesn't get, in the order the reports page shows them (draft first, then the fixed hint). */
    public List<NotAvailable> notAvailable(String repo) {
        List<NotAvailable> missing = new ArrayList<>();
        if (!hasDocs(repo)) {
            missing.add(NO_DOCS);
        }
        if (!hasFixedHint(repo)) {
            missing.add(NO_FIXED_HINT);
        }
        return List.copyOf(missing);
    }
}
