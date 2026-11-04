# M2 prompt: live issues through Kafka (plus step 0: Flyway + real-DB tests)

Read CLAUDE.md, docs/schema.md, docs/problem.md, docs/m1-report.md and docs/learning-notes.md first.
Same rules as M1: one step at a time; after each step `./mvnw test` must pass, add a learning-notes
section (plain English, basics with a before/after example, why, one rejected alternative, 3 interview
Q&As), then commit. Never print, log or commit a token or secret. Keep bash commands simple (one command
per call, no for loops, `cd &&` chains or shell variables; multi-step logic goes in a script under scripts/).
Use `docker-compose`, not `docker compose`. Don't touch Homebrew Postgres on 5432.

**Dependencies sai approved for this milestone (and only these):** Flyway (Boot 4 starter/module +
the Postgres Flyway module), Testcontainers (Postgres and Kafka modules + `spring-boot-testcontainers`),
and the Boot 4 test starters for JPA and WebMvc (`spring-boot-starter-data-jpa-test`,
`spring-boot-webmvc-test` or whatever the exact Boot 4.1 names are; check the Boot 4.1 docs).
Let the Spring Boot parent manage the versions. Anything else: don't add it; write it under "Open questions".

If a step fails 3 times: commit what works, write the problem under "Open questions" in
docs/m2-report.md, and move on. If anything here conflicts with the code, stop and ask.

## Step 0: Flyway + real-database tests (before any M2 code)
1. Add `.claude/settings.local.json` to .gitignore.
2. Flyway: `src/main/resources/db/migration/V1__baseline.sql` = today's schema exactly as the running DB
   has it (get it with `docker-compose exec postgres pg_dump -U copilot -d triage --schema-only`), but
   WITHOUT Hibernate's `CHECK (source = 'GITHUB')` constraint, so Jira rows will work later.
   The existing DB already has 44k rows: use `baseline-on-migrate: true` with baseline version 1, so Flyway
   adopts it without re-running V1, then `V2__drop_source_check.sql` drops the CHECK constraint there.
   Make sure V1 + V2 also build a correct schema on an EMPTY database (that's what the tests use).
   Switch `ddl-auto` to `validate`. Explain in the notes why ddl-auto: update is not used in production.
3. Real-DB tests with Testcontainers Postgres 16 (`@DataJpaTest` + `@ServiceConnection`, or a shared base
   class so the container starts once): the unique constraint rejects a second (source, external_id);
   `IssueService.upsert` twice = 1 row, labels replaced; `findDuplicateCandidates` finds label-only and
   state_reason-only duplicates; `findIdsByRepo` paging + count; `listIssues` runs 3 SQL statements.
4. Docs from the M1 report: fix README + CLAUDE.md "Start DB" (`docker-compose`, port 5433);
   docs/problem.md: old closed duplicates show state_reason `completed`, not NULL.
5. The old empty `copilot` database: drop it only if that can't touch `triage`. Leave the DB user as is.
6. Run the app once against the real DB: Flyway must report baseline + V2 applied and Hibernate validate
   must pass. Then the commit.

## Step 1: Kafka in docker-compose
Add a single-node Kafka (official `apache/kafka` image, KRaft mode, no ZooKeeper) on localhost:9092.
Topic `issue-events`, 3 partitions, created by the app (a `NewTopic` bean), plus `issue-events.DLT`.
Explain partitions, keys and consumer groups with a small example in the notes.

## Step 2: Webhook receiver `POST /webhooks/github`
- Read the body as raw bytes (the signature is over the exact bytes; never re-serialize first).
- Check `X-Hub-Signature-256` = `sha256=` + HMAC-SHA256(body, secret) with a constant-time compare
  (`MessageDigest.isEqual`). Secret from env var `GITHUB_WEBHOOK_SECRET` (application.yml
  `github.webhook-secret: ${GITHUB_WEBHOOK_SECRET:}`). If the secret isn't set, refuse every webhook
  with a clear log line. Wrong or missing signature = 401, body never logged.
- Unit-test the signature check with GitHub's documented example: secret `It's a Secret to Everybody`,
  payload `Hello, World!`, expected `sha256=757107ea0eb2509fc211221cce984b8a37570b6d7586c22c46f4379c8b043e17`
  (https://docs.github.com/en/webhooks/using-webhooks/validating-webhook-deliveries).
- `X-GitHub-Event`: `ping` = 200; `issues` with action opened, edited, closed, reopened, labeled,
  unlabeled = handle; anything else = 202 and ignore.
- Must answer well within GitHub's 10-second limit: do no GitHub calls and no triage work here.

## Step 3: Idempotency on `X-GitHub-Delivery`
- Flyway V3: table `webhook_delivery(delivery_id varchar(64) PRIMARY KEY, event varchar(50) NOT NULL,
  action varchar(50), repo varchar(200), received_at timestamptz NOT NULL)`. Update docs/schema.md.
- In ONE transaction: insert the delivery row (`INSERT ... ON CONFLICT DO NOTHING`; 0 rows = repeat →
  answer 200 "duplicate delivery" and stop), then publish to Kafka and wait for the broker ack
  (max ~5 s). If the publish fails, the transaction rolls back and we answer 500, so GitHub's Redeliver
  works later. Explain this trade-off in the notes, and name the transactional outbox pattern as the
  proper fix we're not building yet.
- Message: key = `owner/repo#number` (same issue → same partition → kept in order), value = JSON with
  deliveryId, action, repo and the full `issue` object as GitHub sent it.

## Step 4: Consumer (triage worker)
- `@KafkaListener` on `issue-events`, group `triage-worker`: map `issue` with the existing
  `GitHubIssueMapper` and call `IssueService.upsert`. Labels come with the issue, so labeled/unlabeled
  just upsert too.
- Out-of-order safety: only overwrite a row if the incoming `updated_at` is >= the stored one (a late,
  older event must not undo a newer one). Real-DB test for this.
- Errors: `DefaultErrorHandler` with 3 retries and backoff, then to `issue-events.DLT`. A bad message
  must not block the partition.
- Upsert is already idempotent, so Kafka's at-least-once delivery is safe: explain why in the notes.

## Step 5: End-to-end test without GitHub
Testcontainers Postgres + Kafka, `@SpringBootTest`: post a correctly signed `issues/opened` payload
(a realistic fixture copied from GitHub's docs, public data only) to `/webhooks/github` → poll the DB
(simple loop, max 10 s; no new dependency) → the issue row exists. Post the same delivery id again →
still 1 row and 1 delivery row. Bad signature → 401 and nothing in Kafka.

## Step 6: Real GitHub (needs sai, so stop here and ask)
Check `node -v`. Then tell sai, in plain steps, exactly what to do:
1. Create a public test repo `triage-sandbox` on SaiSankethG.
2. Get a webhook proxy URL from https://smee.io (New Channel).
3. Create a GitHub App on the personal account: webhook URL = the smee URL, a generated secret,
   permissions Issues: Read-only, event: Issues; install it only on `triage-sandbox`.
4. `export GITHUB_WEBHOOK_SECRET=...` in the terminal that starts the app (never in a file in the repo).
Then forward with `npx smee-client --url <smee url> --target http://localhost:8080/webhooks/github`
(if node is missing, ask sai before installing anything), open an issue, edit it, add a label,
and press "Redeliver" in the App's Advanced tab. Check: 1 issue row, labels right, the redelivery ignored.

## At the end
Update CLAUDE.md "Current step", push to origin main, and write docs/m2-report.md: what each step did,
commit hashes, test count, the end-to-end result, the receiver's response time, and open questions.
