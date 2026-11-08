package com.saisanketh.triage.web;

import com.saisanketh.triage.repojob.RepoJob;
import com.saisanketh.triage.repojob.RepoJobRepository;
import com.saisanketh.triage.repojob.RepoJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Add repository" jobs: their progress (public, under /repos like the other read endpoints) and a way for the operator to
 * start one by hand (POST, needs X-Admin-Token, and not routed by the public Ingress).
 */
@RestController
@RequiredArgsConstructor
public class RepoJobController {

    private final RepoJobRepository jobs;
    private final RepoJobService service;

    /** Example: {@code curl localhost:8080/repos/spring-projects/spring-boot/job}. 404 if the repo never had a job. */
    @GetMapping("/repos/{owner}/{repo}/job")
    public ResponseEntity<RepoJob> latest(@PathVariable String owner, @PathVariable String repo) {
        return ResponseEntity.of(jobs.latest(owner + "/" + repo));
    }

    /**
     * Example: {@code curl -X POST -H "X-Admin-Token: $TRIAGE_ADMIN_TOKEN" localhost:8080/repo-jobs/octo-org/hello}.
     * 202 when queued; 409 when the repo already has a job waiting or running.
     */
    @PostMapping("/repo-jobs/{owner}/{repo}")
    public ResponseEntity<String> add(@PathVariable String owner, @PathVariable String repo) {
        String name = owner + "/" + repo;
        return service.queueByAdmin(name)
                ? ResponseEntity.accepted().body("queued " + name + "; progress: GET /repos/" + name + "/job")
                : ResponseEntity.status(HttpStatus.CONFLICT).body(name + " already has a job waiting or running");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
