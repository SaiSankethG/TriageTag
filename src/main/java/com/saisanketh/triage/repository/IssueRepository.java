package com.saisanketh.triage.repository;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.domain.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface IssueRepository extends JpaRepository<Issue, Long> {

    /**
     * Finds an issue by its natural key (the unique constraint on the table).
     * The backfill uses this to decide: update the existing row, or insert a new one.
     */
    Optional<Issue> findBySourceAndExternalId(SourceType source, String externalId);

    /**
     * Issues of a repo that were closed as a duplicate, or carry a label with "duplicate" in it
     * (e.g. spring-boot's "status: duplicate"). The label matters: older issues have no
     * state_reason "duplicate", only the label. Repo is compared case-insensitively.
     */
    @Query("""
            select distinct i from Issue i left join i.labels l
            where lower(i.repo) = lower(:repo)
              and (i.stateReason = 'duplicate' or lower(l) like '%duplicate%')
            order by i.number
            """)
    List<Issue> findDuplicateCandidates(@Param("repo") String repo);

    /** Sets duplicate_of on one row with a single UPDATE (no need to load the issue and its labels). */
    @Transactional
    @Modifying
    @Query("update Issue i set i.duplicateOf = :original where i.id = :id")
    int setDuplicateOf(@Param("id") Long id, @Param("original") Integer original);
}
