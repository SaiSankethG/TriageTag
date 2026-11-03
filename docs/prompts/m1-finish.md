# Prompt: finish M1 (load past issues)

Read CLAUDE.md, docs/problem.md, docs/schema.md and docs/learning-notes.md first.
Follow every rule in CLAUDE.md. Work through the steps below IN ORDER. After EACH step:
run `./mvnw test`, add that step's section to docs/learning-notes.md (plain English,
basics explained, why this way, one rejected alternative, 3 interview questions with
answers), and make one commit. Do not upgrade Java/Spring Boot. Do not add any
dependency without asking me first (stop and ask). Never log or commit the token.

## Step A: refactor from review
- Move `IssueSource` and `IssuePage` to the neutral package `com.saisanketh.triage.source`.
- `IssueSource`: `IssuePage fetchPage(String repo, String cursor)`; `repo` is "owner/name",
  `cursor` is null for the first page.
- `IssuePage`: `List<Issue> issues`, `String nextCursor` (null on the last page),
  plus `int skipped` (items skipped on this page, e.g. pull requests).
- Nothing GitHub-specific (Link header, JsonNode, pull requests) may appear in these two types.

## Step B: paging (piece 3), inside GitHubIssueSource
- First page: `/repos/{owner}/{repo}/issues?state=all&per_page=100&sort=created&direction=asc`.
- Parse the `Link` header; the `rel="next"` URL becomes `nextCursor`. Next call uses that URL as-is.
- Rate limits: read `X-RateLimit-Remaining` / `X-RateLimit-Reset`. Log the remaining count
  every 10 pages. If GitHub answers 403/429 because of the rate limit, wait until reset
  (or `Retry-After`) and retry that page, at most 3 times; otherwise fail with a clear message.
- Tests with MockRestServiceServer: 2 pages then stop; a rate-limit response then success.

## Step C: skip PRs and map (piece 4)
- Skip any item that has a `pull_request` field (count it in `skipped`).
- New class `GitHubIssueMapper` (JSON -> `Issue`), every column in docs/schema.md:
  source=GITHUB, externalId = `id` as text, repo, number, title, body, state, stateReason,
  issueType = `type.name` (null if no type), authorAssociation, milestone = `milestone.title`,
  htmlUrl, createdAt/updatedAt/closedAt as `Instant`, labels = label names,
  raw = the full JSON of that item as a string. duplicateOf stays null here.
- Unit test: map a realistic issue JSON (open and closed-as-duplicate) and check every field.

## Step D: upsert (piece 5)
- `IssueService.upsert(Issue)` with `@Transactional`: find by (source, externalId) using
  `IssueRepository.findBySourceAndExternalId`; if found copy all fields onto the existing row
  (replace labels), else insert. Returns whether it inserted or updated.
- `IssueService.backfill(String repo)`: loop pages until nextCursor is null, upsert each issue,
  return a summary record: pages, inserted, updated, skipped, durationMs.
- Test with a mocked repository: second run of the same data = 0 inserts.

## Step E: backfill endpoint (piece 6)
- `POST /backfill/{owner}/{repo}` runs `IssueService.backfill` and returns the summary as JSON.
  Synchronous is fine for now (explain the trade-off vs running it in the background).

## Step F: answer key (duplicate_of)
- First LOOK at real data with curl for spring-projects/spring-boot #52529 (closed as duplicate):
  its timeline (`/issues/52529/timeline`) and comments. Find the most reliable way to get the
  ORIGINAL issue number (e.g. a "marked_as_duplicate" timeline event, or a comment like
  "Duplicate of #N"). Write what you found in learning-notes.
- `POST /backfill/{owner}/{repo}/duplicates`: for issues with state_reason = duplicate OR a
  label containing "duplicate", fetch that signal and fill `duplicate_of`. Only fill it when
  sure; count how many were found vs not found and return that summary.

## Step G: read endpoint (piece 7)
- `GET /repos/{owner}/{repo}/issues?page=0&size=20` returns a DTO page (number, title, state,
  stateReason, labels, createdAt, duplicateOf) and the total count. Never return the entity
  (labels are lazy). Use a fetch join or `@EntityGraph` for labels; explain the N+1 problem.

## Step H: run it for real (piece 8)
1. `colima start && docker compose up -d`, then `./mvnw spring-boot:run`.
2. Backfill a SMALL repo first: `curl -X POST localhost:8080/backfill/spring-projects/spring-petclinic`.
   Compare our issue count with GitHub's (search API `total_count` for `repo:... is:issue`).
3. Then `spring-projects/spring-boot` (takes a while; watch the rate-limit log), then the
   duplicates step. Report: issues stored, PRs skipped, duplicates with duplicate_of filled.
4. Update CLAUDE.md "Current step", tick-ready summary for me, push all commits.

Stop and ask me if anything here conflicts with the code or the schema.
