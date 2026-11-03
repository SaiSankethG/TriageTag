package com.saisanketh.triage.github;

import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * One page of results from the tracker.
 *
 * @param items      the issues on this page, as the raw JSON the tracker sent
 *                   (on GitHub this also includes pull requests; piece 4 skips them)
 * @param linkHeader the raw HTTP Link header, used for paging in piece 3;
 *                   null when there is only one page
 */
public record IssuePage(List<JsonNode> items, String linkHeader) {
}
