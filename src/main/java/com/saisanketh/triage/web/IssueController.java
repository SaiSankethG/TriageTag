package com.saisanketh.triage.web;

import com.saisanketh.triage.service.IssueList;
import com.saisanketh.triage.service.IssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read API for stored issues. */
@RestController
@RequiredArgsConstructor
public class IssueController {

    static final int MAX_PAGE_SIZE = 100;

    private final IssueService issueService;

    /**
     * Example: {@code curl 'localhost:8080/repos/spring-projects/spring-petclinic/issues?page=0&size=20'}.
     * With {@code q}, only issues whose title contains that text (the demo page's search box).
     */
    @GetMapping("/repos/{owner}/{repo}/issues")
    public IssueList list(@PathVariable String owner, @PathVariable String repo,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "20") int size,
                          @RequestParam(required = false) String q) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be 0 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (q != null && !q.isBlank()) {
            return issueService.searchIssues(owner + "/" + repo, q.strip(), page, size);
        }
        return issueService.listIssues(owner + "/" + repo, page, size);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
