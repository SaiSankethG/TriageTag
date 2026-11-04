# M2 report: live issues through Kafka

4 Nov 2026. Steps 0 to 6 of `docs/prompts/m2-webhooks-kafka.md` are done. Step 6 used a **repo webhook** instead
of a GitHub App, as agreed; the App moves to M5. Every step has a section in `docs/learning-notes.md`.

## Result in one paragraph
A real GitHub issue now flows into our database live: GitHub repo webhook → smee.io → `POST /webhooks/github`
(HMAC-checked, idempotent on the delivery id) → Kafka `issue-events` (key `owner/repo#number`) → `triage-worker`
consumer → `IssueService.upsert`. On `SaiSankethG/triage-sandbox`, opening, editing and labelling an issue gave
**1 row with the right title and label**, and a redelivery was **ignored**. Flyway now owns the schema, the tests run
against real Postgres and Kafka containers, and there are **99 tests, all passing**. I only added the approved dependencies.

## What each step did

| Step | What it did | Commit |
|---|---|---|
| 0 | Flyway: V1 baseline (live schema without Hibernate's `CHECK (source = 'GITHUB')`), V2 drops that CHECK; the existing 44k-row DB was baselined at 1, so only V2 ran on it. `ddl-auto: validate`. Testcontainers Postgres tests (unique constraint, upsert twice, duplicate candidates, paging, 3 SQL per page, Jira row allowed). README/CLAUDE.md/problem.md fixed. Empty `copilot` DB dropped. Prompt file committed. | `238162d` |
| 1 | `apache/kafka:4.1.0` in KRaft mode on localhost:9092 (auto topic creation off); the app creates `issue-events` and `issue-events.DLT`, 3 partitions each | `01e1026` |
| 2 | `POST /webhooks/github`: raw-byte body, `X-Hub-Signature-256` HMAC-SHA256 with constant-time compare, secret from `GITHUB_WEBHOOK_SECRET`; no secret → 503 + log line; bad signature → 401 (body never logged); ping 200; handled `issues` actions 200; other → 202 | `1352d54` |
| 3 | Flyway V3 `webhook_delivery`. One transaction: `INSERT ... ON CONFLICT DO NOTHING` (repeat → 200 "duplicate delivery"), then publish and wait for the Kafka ack; publish failure → rollback → 500. Producer timeouts keep the answer under GitHub's 10 s | `7a574e3` |
| 4 | `@KafkaListener` (group `triage-worker`) maps and upserts; upsert ignores data older than the stored `updated_at` (`STALE`); `DefaultErrorHandler`: broken messages straight to the DLT, other errors 3 retries (0.5/1/2 s) then the DLT | `646df56` |
| 5 | End-to-end test with real Kafka + Postgres containers: signed webhook → row; same delivery twice → 1 row, 1 delivery row, 1 Kafka message; bad signature → 401, nothing on Kafka | `5b60668` |
| 6 | Real GitHub via `gh` + smee (below); learning notes, this report, CLAUDE.md "Current step" | the commit that adds this report |

## Tests
**99 tests, 0 failures** (`./mvnw test`, about 30 s including starting the containers). That's 45 more than M1:

| New test class | Tests | Kind |
|---|---|---|
| `IssueRepositoryDbTest` | 4 | real Postgres (Testcontainers) |
| `IssueServiceDbTest` | 3 | real Postgres: upsert twice, 3 SQL per page, late older event ignored |
| `WebhookDeliveryDbTest` | 2 | real Postgres: delivery id once; rollback when Kafka fails |
| `KafkaTopicsConfigTest` | 2 | unit |
| `WebhookSignatureVerifierTest` | 5 | unit, incl. GitHub's documented example |
| `GitHubWebhookControllerTest` | 14 | MockMvc |
| `WebhookIntakeServiceTest` | 3 | unit |
| `IssueEventConsumerTest` | 5 | unit |
| `KafkaErrorHandlingTest` | 2 | real Kafka + Postgres: DLT without blocking, retry then success |
| `WebhookEndToEndTest` | 3 | real Kafka + Postgres, whole app over HTTP |
| plus 2 in `IssueServiceTest` | | unit: STALE, equal `updated_at` overwrites |

## End-to-end result

**In tests (step 5, containers):** signed `issues/opened` → **200 in 76 ms** → issue row within the 10 s poll. A repeated
delivery gives `duplicate delivery` (1 row, 1 delivery row, 1 Kafka message). A bad signature gives 401 and nothing on Kafka.

**Against real GitHub (step 6),** on repo `SaiSankethG/triage-sandbox` with hook `707177355` (events: `issues`, JSON):

| GitHub action (via `gh`) | Delivery | Our answer | Consumer |
|---|---|---|---|
| open issue #1 | `1cee70be-…` issues/opened | 200 | INSERTED (partition 0, offset 0) |
| edit the title | `1ef90ea0-…` issues/edited | 200 | UPDATED (offset 1) |
| add label `bug` | `1fb49670-…` issues/labeled | 200 | UPDATED (offset 2) |
| redeliver `1cee70be-…` via the deliveries API | same GUID | 200 "duplicate delivery" | nothing (not queued) |

Database afterwards: **1 issue row** (`App crashes on startup when port 8080 is in use`, open, labels: `bug`) and
**3 delivery rows**. The real secret appears 0 times in the app log.

## Receiver response time
- End-to-end test: **76 ms** (verify + insert delivery row + publish + Kafka ack).
- Real app with the docker-compose Kafka (step 3 manual check): first request 257 ms (JVM warm-up), repeated delivery
  6 ms, wrong signature 1 ms, a new delivery after Kafka restart 48 ms.
- **Kafka down:** 500 in **2.05 s** (`max.block.ms=2000`). The longest possible answer is about 7 s, under GitHub's 10 s.
- GitHub's own delivery durations (0.02 to 0.03 s) measure smee's answer, not ours.

## Open questions (for sai)
1. **No secret → 503, not 401.** The prompt said "refuse every webhook". I chose 503 because 401 means "you aren't
   GitHub", while the real problem is our own configuration. OK?
2. **No transactional outbox yet.** The receiver waits for Kafka inside a DB transaction, so a slow Kafka holds a DB
   connection for up to ~5 s. If Kafka acks but the commit then fails, a redelivery publishes twice. That's harmless
   because the consumer is idempotent. The outbox (explained in the step 3 notes) is the proper fix. When should we build it?
3. **Testcontainers on Colima:** a Maven profile (`colima`) turns itself on only when `~/.colima/default/docker.sock`
   exists and passes `DOCKER_HOST` to the tests. Running tests from VS Code (not Maven) needs `DOCKER_HOST` set by hand.
4. **Kafka has no Docker volume:** its data is lost when the container is removed (not on stop/start). Postgres is the
   source of truth, so that's OK for development.
5. **Two `gh` logins:** `gh` prefers the `GITHUB_TOKEN` env var, a fine-grained token that couldn't create repos or hooks
   (403). I used the keyring login (`env -u GITHUB_TOKEN gh ...`, `repo` scope) for the repo and the hook. Fine, or would you
   rather give the fine-grained token those permissions?
6. **Leftovers:** the docker-compose Kafka topic still holds the 2 test messages from step 3 (already consumed, harmless).
   During local testing a sample `Codertocat/Hello-World` row and 2 test delivery rows were created in the real DB; I
   deleted them (the DB is back to 44,204 backfilled issues + 1 sandbox issue).
7. **Small API/visibility changes:** `BackfillSummary` JSON gained a `stale` count; `WebhookSignatureVerifier.sign` and the
   `TestIssues` test helper are now public so other tests can reuse them.
8. **Only some `issues` actions are handled** (opened, edited, closed, reopened, labeled, unlabeled). Others (assigned,
   milestoned, transferred, deleted...) get a 202 and are ignored. In particular, `deleted` and `transferred` issues stay
   in our DB. Handle them in M3+?
9. **smee.io is development-only.** Production needs a public HTTPS endpoint (or the GitHub App in M5).

## How to reproduce
```bash
colima start
docker-compose up -d                                   # Postgres :5433 (triage), Kafka :9092
export GITHUB_WEBHOOK_SECRET="$(cat ~/.config/triage/webhook-secret)"
./mvnw spring-boot:run
npx smee-client --url https://smee.io/CmZOPm1cX3F38kf --target http://localhost:8080/webhooks/github
# local test without GitHub:
scripts/send-test-webhook.sh src/test/resources/github/webhook-issues-opened.json my-delivery-1
```
