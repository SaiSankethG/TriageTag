package com.saisanketh.triage.repository;

import com.saisanketh.triage.domain.Issue;
import com.saisanketh.triage.domain.SourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
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

    /**
     * Step 1 of reading a page of issues: just the ids of one page, plus the total count.
     * Paging ids (not issues with their labels) keeps LIMIT/OFFSET in the database; see step G notes.
     */
    @Query(value = "select i.id from Issue i where lower(i.repo) = lower(:repo)",
            countQuery = "select count(i) from Issue i where lower(i.repo) = lower(:repo)")
    Page<Long> findIdsByRepo(@Param("repo") String repo, Pageable pageable);

    /** Like findIdsByRepo, but only issues whose title contains the text (any case). Used by the demo page's search. */
    @Query(value = "select i.id from Issue i where lower(i.repo) = lower(:repo)"
            + " and lower(i.title) like lower(concat('%', :text, '%'))",
            countQuery = "select count(i) from Issue i where lower(i.repo) = lower(:repo)"
                    + " and lower(i.title) like lower(concat('%', :text, '%'))")
    Page<Long> findIdsByRepoAndTitleContaining(@Param("repo") String repo, @Param("text") String text,
                                               Pageable pageable);

    /**
     * Step 2: load those issues and their labels in ONE query (a join), instead of 1 query for the
     * issues plus 1 more per issue for its labels (the N+1 problem).
     */
    @EntityGraph(attributePaths = "labels")
    List<Issue> findByIdInOrderByNumberDesc(Collection<Long> ids);

    /** Which of these issues already have a triage report (one query for a whole page, for the demo page's list). */
    @Query(value = "select issue_id from triage_report where issue_id in (:ids)", nativeQuery = true)
    List<Long> findIdsWithReport(@Param("ids") Collection<Long> ids);

    /** Sets duplicate_of on one row with a single UPDATE (no need to load the issue and its labels). */
    @Transactional
    @Modifying
    @Query("update Issue i set i.duplicateOf = :original where i.id = :id")
    int setDuplicateOf(@Param("id") Long id, @Param("original") Integer original);
}
