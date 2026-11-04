package com.saisanketh.triage.service;

import java.time.Instant;
import java.util.List;

/**
 * What the read API shows for one issue. A plain record (a DTO), not the JPA entity: it holds only
 * these fields, has no lazy collections, and changing the entity can't silently change the API.
 */
public record IssueSummary(int number, String title, String state, String stateReason,
                           List<String> labels, Instant createdAt, Integer duplicateOf,
                           String authorAssociation, boolean hasReport) {
}
