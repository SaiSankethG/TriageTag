package com.saisanketh.triage.repository;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.domain.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IssueRepository extends JpaRepository<Issue, Long> {

    /**
     * Finds an issue by its natural key (the unique constraint on the table).
     * The backfill uses this to decide: update the existing row, or insert a new one.
     */
    Optional<Issue> findBySourceAndExternalId(SourceType source, String externalId);
}
