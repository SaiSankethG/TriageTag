# M1 report: load past issues

Overnight run, 3-4 Nov 2026. All of M1 (steps A to H of `docs/prompts/m1-finish.md`) is done, tested and pushed.
Every step has a section in `docs/learning-notes.md`: what it does, the basics, why, a rejected alternative,
and 3 interview questions.

## Result in one paragraph
The database now holds every issue of **spring-petclinic (394)** and **spring-boot (43,810)**. Both counts are
exactly what GitHub reports, and pull requests are skipped (2,284 and 8,035). Re-running a backfill inserts nothing.
The **answer key** is built: **1,036 of spring-boot's 2,041 duplicates** have `duplicate_of` filled from GitHub's own
records, and **945** of those point to an issue we store, so M3 can score them. **54 tests**, all passing.
No step failed, and no new dependencies were added.

## What each step did

| Step | What it did | Commit |
|---|---|---|
| A | `IssuePage` gets `int skipped`; `source/` types have nothing GitHub-specific left | `eccff82` |
| (setup) | Docker Postgres on host port **5433**, DB renamed **`triage`**, note in CLAUDE.md | `5afaa57` |
| B | First page sorted oldest first; rate limit logged every 10 pages; on a rate-limit 403/429, wait (`Retry-After`, else until `X-RateLimit-Reset`, else 1 min) and retry up to 3 times; other 403s fail with a clear message | `57fb769` |
| C | Mapper already covered every column; added a realistic open-issue fixture and a test of every field | `266b720` |
| D | `IssueService.upsert` (match on source + external_id) and `BackfillService.backfill` (loop to `nextCursor == null`, summary) | `b193355` |
| E | `POST /backfill/{owner}/{repo}`, synchronous, returns the summary JSON; 404/400 as ProblemDetail | `29d530e` |
| F | Looked at real data first, then `POST /backfill/{owner}/{repo}/duplicates` fills `duplicate_of` via GitHub GraphQL | `a8bcc27` |
| G | `GET /repos/{owner}/{repo}/issues?page&size`: DTOs, 3 SQL statements per page (no N+1), `open-in-view: false` | `f9bfc82` |
| H | Real runs (below), learning notes, CLAUDE.md "Current step" | `ab8a272` |

Earlier M1 commits (before the overnight run): `907330d` piece 1, `687aa42` piece 2, `6e2bca7` pieces 3+4.

## Tests
**54 tests, 0 failures** (`./mvnw test`):

| Test class | Tests | What it covers |
|---|---|---|
| `GitHubIssueSourceTest` | 14 | request + headers, 2-page cursor paging, PR skip/count, rate-limit wait + retry, give-up after 3, plain 403, token never sent to other hosts |
| `GitHubIssueMapperTest` | 5 | every field of a closed duplicate and a realistic open issue, nulls, `raw` = full JSON |
| `GitHubDuplicateSourceTest` | 18 | each rule, built from real spring-boot cases (#52529, #52124, #16344, #39044, #50073, #52567), plus the GraphQL HTTP call |
| `IssueServiceTest` | 2 | insert vs update, labels replaced, `duplicate_of` kept |
| `IssueServiceListTest` | 3 | paging, newest first, DTO mapping, empty page skips the 2nd query |
| `BackfillServiceTest` | 2 | 2 pages, counts; second run of the same data = 0 inserts |
| `DuplicateServiceTest` | 2 | only clear answers written, every outcome counted |
| `BackfillControllerTest` | 4 | JSON summary, 404, POST only, duplicates endpoint |
| `IssueControllerTest` | 4 | JSON page, query params, size/page validation |

## Backfill numbers

| Run | Pages | Inserted | Updated | Skipped (PRs) | Time | Checked against GitHub |
|---|---|---|---|---|---|---|
| spring-petclinic | 27 | 394 | 0 | 2,284 | 30.1 s | 394 issues and 2,284 PRs: exact match |
| spring-petclinic, 2nd run | 27 | 0 | 394 | 2,284 | 31.8 s | idempotent; 74 label rows before and after |
| spring-boot | 519 | 43,810 | 0 | 8,035 | 713 s (11.9 min) | 43,810 issues: exact match; PRs 8,034 in search (±1, see open questions) |

- No rate-limit waits: about 570 REST calls in total, out of 5,000 per hour.
- No errors in the app log, and no Kafka warnings (Kafka isn't used yet).
- Read API with real data: one page of 20 petclinic issues with labels takes **3 SQL statements**
  (page of ids, count, issues + labels).

## Duplicates (answer key), spring-boot

| | Count |
|---|---|
| Issues closed **or labelled** as duplicate (checked) | **2,041** (2,011 with the label, plus 30 closed as duplicate without it) |
| **Found**, `duplicate_of` filled | **1,036** (51%) |
| ... from "close as duplicate of #N" | 117 |
| ... from a "marked as duplicate" link | 137 |
| ... from a maintainer comment ("Duplicate of #N", "Duplicates <url>") | 782 |
| Not found (no reliable link, e.g. only "see #N" or nothing) | 909 |
| Original is in another repo (e.g. spring-framework), so not stored | 88 |
| Ambiguous (signals point at more than one issue) | 8 |
| Time | 1,121 s (18.7 min), one GraphQL call per issue |

Hand-checked in the DB: #52529 -> #52519, #52124 -> #49279, #16344 -> #16381, #47366 -> #46944,
#48342 -> #48300, #50073 -> #49910. #39044 (other repo) and #52567 ("see #52367") were correctly left empty.

Findings that matter for M3:
- **945** of the 1,036 originals are issues we store. The other **91** point to numbers we don't have, most likely pull requests.
- **109** pairs have an "original" that is **newer** than the duplicate (maintainers sometimes close the older issue in
  favour of a newer, better one). A time-ordered replay can't find these.
- Only **178** spring-boot issues have `state_reason = duplicate`. Older duplicates show `completed`, and 2023-24 ones
  often `not_planned`, so the label is the main signal.

## Open questions (for sai)
Decisions I made that differ from the prompt, or that need your call:
1. **`backfill` lives in `BackfillService`, not `IssueService`** (the prompt said `IssueService.backfill`). Reason: `@Transactional`
   on `upsert` is silently skipped when called from inside the same class (Spring proxy self-invocation). OK to keep?
2. **`upsert` doesn't copy a null `duplicate_of`** (the prompt said "copy all fields"). Otherwise every re-run of the backfill
   would erase the answer key. OK to keep?
3. **M3 replay scope:** score only the 945 pairs whose original is a stored issue? And skip, or report separately, the
   109 pairs where the original is newer than the duplicate?
4. **Answer key strictness:** maintainer comments count even when hedged ("I think this may be a duplicate of #N"), as long as
   the issue is closed/labelled as a duplicate and nothing contradicts it. Tighten this, or accept it?
5. **Synchronous endpoints:** the spring-boot backfill held one HTTP request open for 12 minutes, and the duplicates step for 19.
   Fine for a developer with curl; a background job (Kafka is already in the stack) would be better later.
6. **No database-backed tests.** `@DataJpaTest` / `@WebMvcTest` need the Boot 4 modules `spring-boot-starter-data-jpa-test` /
   `spring-boot-webmvc-test`, which are new dependencies, so I didn't add them. The JPQL queries are checked at startup
   and by the real runs, not by tests. Add them?
7. **Schema is still `ddl-auto: update`.** Hibernate added `CHECK (source = 'GITHUB')`, which will block Jira rows later.
   Flyway (a dependency) would fix both. Add before Jira?
8. **The duplicates step doesn't wait out a GraphQL rate limit**; it stops with a clear message. 2,041 calls fit easily in
   5,000/hour, but a much bigger repo would need the same wait-and-retry as step B.
9. **Docs to update?** `README.md` and the "Start DB" line in CLAUDE.md still say `docker compose` and don't mention port 5433
   (the new note under Commands corrects it). `docs/problem.md` says old closed issues may have no `state_reason`;
   real data shows `completed`.
10. **Leftovers in the Docker volume:** the old empty `copilot` database still exists, and the DB user/password are still
    `copilot` (you asked to rename the DB only). Drop or rename?
11. **PR count off by one** for spring-boot (8,035 skipped vs 8,034 in GitHub search). The issue count is exact; search
    counts can lag, so I didn't chase it.
12. Small things: "updated" counts every issue we already had, even if nothing changed (we could compare `updated_at` and
    report "unchanged"). `GitHubIssueSource` and `GitHubDuplicateSource` each build their own RestClient with the same
    headers (small duplication).

## How to reproduce
```bash
colima start && docker-compose up -d          # Postgres on localhost:5433, DB triage
export GITHUB_TOKEN=...                        # never commit it
./mvnw spring-boot:run
curl -X POST localhost:8080/backfill/spring-projects/spring-petclinic
curl -X POST localhost:8080/backfill/spring-projects/spring-boot
curl -X POST localhost:8080/backfill/spring-projects/spring-boot/duplicates
curl 'localhost:8080/repos/spring-projects/spring-boot/issues?page=0&size=20'
```
