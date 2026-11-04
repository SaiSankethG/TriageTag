package com.saisanketh.triage.web;

import com.saisanketh.triage.eval.DuplicateCheckEval;
import com.saisanketh.triage.eval.DuplicateCheckReport;
import com.saisanketh.triage.eval.EvalReport;
import com.saisanketh.triage.eval.EvalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Runs the replay evaluation (a few minutes on spring-boot) and returns the numbers; also writes docs/m3-results.md. */
@RestController
@RequiredArgsConstructor
public class EvalController {

    private final EvalService evalService;
    private final DuplicateCheckEval duplicateCheckEval;

    /** Example: {@code curl -X POST localhost:8080/eval/spring-projects/spring-boot} */
    @PostMapping("/eval/{owner}/{repo}")
    public EvalReport evaluate(@PathVariable String owner, @PathVariable String repo) {
        return evalService.evaluate(owner + "/" + repo);
    }

    /**
     * Score-only vs score + LLM check of the top match (comment-mode rule); calls the LLM once per flagged issue.
     * Example: {@code curl -X POST localhost:8080/eval/spring-projects/spring-boot/duplicate-check}
     */
    @PostMapping("/eval/{owner}/{repo}/duplicate-check")
    public DuplicateCheckReport duplicateCheck(@PathVariable String owner, @PathVariable String repo) {
        return duplicateCheckEval.evaluate(owner + "/" + repo);
    }
}
