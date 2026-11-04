package com.saisanketh.triage.web;

import com.saisanketh.triage.triage.SimilarIssue;
import com.saisanketh.triage.triage.SimilarIssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** "Which earlier issues look like this one?" */
@RestController
@RequiredArgsConstructor
public class SimilarIssueController {

    static final int MAX_LIMIT = 20;

    private final SimilarIssueService similarIssueService;

    /** Example: {@code curl 'localhost:8080/repos/spring-projects/spring-boot/issues/52529/similar?method=vector'} */
    @GetMapping("/repos/{owner}/{repo}/issues/{number}/similar")
    public ResponseEntity<List<SimilarIssue>> similar(@PathVariable String owner, @PathVariable String repo,
                                                      @PathVariable int number,
                                                      @RequestParam(defaultValue = "vector") String method,
                                                      @RequestParam(defaultValue = "3") int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        return similarIssueService.findSimilar(owner + "/" + repo, number, method, limit)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
