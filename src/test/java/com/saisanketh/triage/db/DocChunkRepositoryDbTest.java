package com.saisanketh.triage.db;

import com.saisanketh.triage.docs.DocChunkRepository;
import com.saisanketh.triage.docs.DocChunkRepository.DocMatch;
import com.saisanketh.triage.docs.DocSection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DocChunkRepositoryDbTest extends PostgresTest {

    @Autowired
    private DocChunkRepository repository;

    private static float[] direction(double angleDegrees) {
        float[] v = new float[384];
        v[0] = (float) Math.cos(Math.toRadians(angleDegrees));
        v[1] = (float) Math.sin(Math.toRadians(angleDegrees));
        return v;
    }

    @Test
    void findsTheNearestSectionsOfTheRepoAndReplacesOnReload() {
        repository.replaceAll("o/r", "v1", List.of(
                new DocSection("https://d/a#x", "A > X", "near"),
                new DocSection("https://d/b", "B", "far")), List.of(direction(10), direction(80)));
        repository.replaceAll("o/other", "v1", List.of(new DocSection("https://d/o", "O", "other repo")),
                List.of(direction(0)));

        List<DocMatch> matches = repository.nearest("O/R", direction(0), 5);

        assertThat(matches).extracting(DocMatch::text).containsExactly("near", "far");
        assertThat(matches.get(0).score()).isCloseTo(Math.cos(Math.toRadians(10)), within(1e-4));

        repository.replaceAll("o/r", "v2", List.of(new DocSection("https://d/c", "C", "new")), List.of(direction(5)));
        assertThat(repository.count("o/r")).isEqualTo(1);
        assertThat(repository.count("o/other")).isEqualTo(1);
    }
}
