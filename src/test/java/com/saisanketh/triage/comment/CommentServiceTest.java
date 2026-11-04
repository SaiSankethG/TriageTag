package com.saisanketh.triage.comment;

import com.saisanketh.triage.report.TriageReport.Duplicate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommentServiceTest {

    @Test
    void theCommentHasTheLinkTheScoreAndSaysABotSuggestedIt() {
        String body = CommentService.body(new Duplicate(52519, "Support MockServer", "https://github.com/o/r/issues/52519", 0.934));

        assertThat(body).contains("#52519", "Support MockServer", "93%", "A bot suggested this", "The maintainers decide");
    }
}
