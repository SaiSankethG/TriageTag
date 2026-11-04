package com.saisanketh.triage.web;

import com.saisanketh.triage.report.TriageReportDto;
import com.saisanketh.triage.report.TriageReportRepository;
import com.saisanketh.triage.report.TriageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Triage reports: read one; or (re)create one for an issue that is already stored (e.g. an older issue, for the demo). */
@RestController
@RequiredArgsConstructor
public class ReportController {

    private final TriageReportRepository reports;
    private final TriageService triageService;

    /** Example: {@code curl localhost:8080/reports/spring-projects/spring-boot/52582}; 404 if there is no report yet. */
    @GetMapping("/reports/{owner}/{repo}/{number}")
    public ResponseEntity<TriageReportDto> get(@PathVariable String owner, @PathVariable String repo,
                                               @PathVariable int number) {
        return ResponseEntity.of(reports.find(owner + "/" + repo, number));
    }

    /** Creates (or replaces) the report; uses the LLM. 404 if the issue isn't stored. */
    @PostMapping("/reports/{owner}/{repo}/{number}")
    public ResponseEntity<TriageReportDto> create(@PathVariable String owner, @PathVariable String repo,
                                                  @PathVariable int number) {
        String fullName = owner + "/" + repo;
        if (triageService.create(fullName, number).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.of(reports.find(fullName, number));
    }
}
