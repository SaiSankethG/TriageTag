package com.saisanketh.triage.web;

import com.saisanketh.triage.service.BackfillService;
import com.saisanketh.triage.service.BackfillSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

/**
 * HTTP entry point for loading a repo's past issues.
 *
 * <p>Synchronous on purpose (for now): the request stays open until the whole backfill is done and
 * then returns the summary. Simple to use and test; the trade-off is a long-running request for big
 * repos. See docs/learning-notes.md, step E.
 */
@RestController
@RequiredArgsConstructor
public class BackfillController {

    private final BackfillService backfillService;

    /** Example: {@code curl -X POST localhost:8080/backfill/spring-projects/spring-petclinic} */
    @PostMapping("/backfill/{owner}/{repo}")
    public BackfillSummary backfill(@PathVariable String owner, @PathVariable String repo) {
        return backfillService.backfill(owner + "/" + repo);
    }

    /** The tracker says the repo doesn't exist (or isn't visible to our token). */
    @ExceptionHandler(HttpClientErrorException.NotFound.class)
    public ProblemDetail repoNotFound(HttpClientErrorException.NotFound e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "Repository not found (or not visible with the current token)");
    }

    /** Bad input, e.g. a malformed repo name. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
