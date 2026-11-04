package com.saisanketh.triage.triage;

import com.saisanketh.triage.triage.VectorDuplicateSearch.Neighbour;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Suggests labels for an issue from the labels of similar earlier issues: a vote, weighted by similarity.
 *
 * <p>Example: the 10 nearest issues have similarities 0.9, 0.8, ... If the ones labelled "type: bug" together
 * have 70% of the total similarity, "type: bug" gets a share of 0.70. A label is suggested only if its share is at
 * least the threshold (default 0.5: "more than half of the similar issues, by weight, had it").
 * Workflow labels (prefix "status:" by default) are ignored: they say where an issue is in the process, not what it is about.
 */
@Service
public class LabelSuggester {

    static final int NEIGHBOURS = 10;

    /** @param label the label; @param share its weighted vote share, 0 to 1 */
    public record LabelSuggestion(String label, double share) {
    }

    private final VectorDuplicateSearch vectorSearch;
    private final JdbcTemplate jdbc;
    private final double threshold;
    private final List<String> ignoredPrefixes;

    public LabelSuggester(VectorDuplicateSearch vectorSearch, JdbcTemplate jdbc,
                          @Value("${triage.labels.threshold:0.5}") double threshold,
                          @Value("${triage.labels.ignore-prefixes:status:}") List<String> ignoredPrefixes) {
        this.vectorSearch = vectorSearch;
        this.jdbc = jdbc;
        this.threshold = threshold;
        this.ignoredPrefixes = ignoredPrefixes;
    }

    /** Labels whose share is at least the threshold, highest share first. */
    @Transactional(readOnly = true)
    public List<LabelSuggestion> suggest(long issueId) {
        List<Neighbour> neighbours = vectorSearch.findNeighbours(issueId, NEIGHBOURS);
        double totalWeight = neighbours.stream().mapToDouble(n -> Math.max(n.score(), 0)).sum();
        if (totalWeight == 0) {
            return List.of();
        }

        Map<Long, Double> weightById = new HashMap<>();
        neighbours.forEach(n -> weightById.put(n.id(), Math.max(n.score(), 0)));

        Map<String, Double> votes = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT issue_id, label_name FROM issue_label WHERE issue_id = ANY (?)",
                (Object) weightById.keySet().toArray(new Long[0]))) {
            String label = (String) row.get("label_name");
            if (!isIgnored(label)) {
                votes.merge(label, weightById.get(((Number) row.get("issue_id")).longValue()), Double::sum);
            }
        }

        return votes.entrySet().stream()
                .map(e -> new LabelSuggestion(e.getKey(), e.getValue() / totalWeight))
                .filter(s -> s.share() >= threshold)
                .sorted(Comparator.comparingDouble(LabelSuggestion::share).reversed()
                        .thenComparing(LabelSuggestion::label))
                .toList();
    }

    public boolean isIgnored(String label) {
        return ignoredPrefixes.stream().anyMatch(prefix -> !prefix.isBlank() && label.startsWith(prefix));
    }
}
