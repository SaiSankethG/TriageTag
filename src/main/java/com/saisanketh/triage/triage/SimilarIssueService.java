package com.saisanketh.triage.triage;

import com.saisanketh.triage.triage.LabelSuggester.LabelSuggestion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Looks up an issue by repo + number and runs a duplicate search or the label suggester on it. */
@Service
public class SimilarIssueService {

    private final JdbcTemplate jdbc;
    /** "keyword" -> KeywordDuplicateSearch, "vector" -> VectorDuplicateSearch (every DuplicateSearch bean, by name). */
    private final Map<String, DuplicateSearch> searches;
    private final LabelSuggester labelSuggester;

    public SimilarIssueService(JdbcTemplate jdbc, List<DuplicateSearch> searches, LabelSuggester labelSuggester) {
        this.jdbc = jdbc;
        this.searches = searches.stream().collect(Collectors.toMap(DuplicateSearch::name, Function.identity()));
        this.labelSuggester = labelSuggester;
    }

    /**
     * @return empty if the repo has no issue with that number
     * @throws IllegalArgumentException for an unknown method name
     */
    public Optional<List<SimilarIssue>> findSimilar(String repo, int number, String method, int limit) {
        DuplicateSearch search = searches.get(method);
        if (search == null) {
            throw new IllegalArgumentException("method must be one of " + searches.keySet() + ", got: " + method);
        }
        return findIssueId(repo, number).map(id -> search.findSimilar(id, limit));
    }

    /** @return empty if the repo has no issue with that number */
    public Optional<List<LabelSuggestion>> suggestLabels(String repo, int number) {
        return findIssueId(repo, number).map(labelSuggester::suggest);
    }

    public Optional<Long> findIssueId(String repo, int number) {
        return jdbc.queryForList("SELECT id FROM issue WHERE lower(repo) = lower(?) AND number = ?", Long.class,
                repo, number).stream().findFirst();
    }
}
