# Stability rehearsal before the deploy (10 Nov 2026)

sai asked to make the application stable before creating the Oracle VM. So the whole app was run on the Mac the way it will
run on the VM, then broken on purpose, and whatever failed was fixed.

## Set-up
- **The real container image** (`./mvnw spring-boot:build-image`, the image k3s will run), limited like the k3s Deployment:
  2.5 GB memory, 2 CPUs.
- **Its own Postgres 16 + pgvector and Kafka 4.1** on a separate Docker network, port 8090. sai's dev database and Kafka
  were not touched (only read once, for the dump below).
- **Data**: a `pg_dump --format=custom` of the dev database (113 MB: 44,205 issues, embeddings, docs, reports), restored with
  `pg_restore`. This is the same method as the nightly backup, so the restore path is tested too.
- Fake LLM (no API cost), a real `GITHUB_TOKEN` for the install test.

## Results (after the fixes below)

| Test | Result |
|---|---|
| Smoke test (`scripts/smoke-test.py`): pages, health and probes, `/repos`, `/settings`, admin token required, bad signature 401, new issue → report, redelivery → "duplicate", private repo → 202 ignored and no report | **All passed**. Webhook answered in 14 ms, report after 2 s |
| Install the App on `spring-projects/spring-petclinic` (installation webhook) | Job QUEUED → BACKFILL (392 issues) → EMBEDDINGS (392) → DOCS → **DONE** in 35 s, with the "no reference docs" note |
| Kill the app in the middle of a repo job (`docker kill`), start it again | At startup: "1 interrupted job(s) queued again". The job ran again from the start and **finished DONE** |
| Kafka stopped, 20 new issues sent | **All 20 accepted** (p95 3 ms), 20 rows waiting in the outbox; Kafka started → **20/20 reports**, outbox empty |
| Postgres stopped, 3 webhooks sent | **500 after 3.0 s** (was 30 s, see fix 1), so GitHub can redeliver. Postgres started → next webhooks 200, reports made, **no app restart needed**. Liveness stayed UP (so k3s doesn't restart the app for a database problem) |
| Postgres stopped for 45 s while 10 issue events were already in Kafka (after fix 6) | Workers waited and retried; Postgres back → **10/10 reports, 0 dead letters**, no restart |
| A broken (non-JSON) message put straight into Kafka | Sent to `issue-events.DLT`, logged, counted in `triage_kafka_dead_letters_total`; the next issue was triaged normally |
| Load test (`scripts/load-test.py`): 500 webhooks, 10 parallel senders, 3 workers | **500/500 accepted**, receiver p50 9 ms / p95 44 ms; **500/500 reports in 24 s**; end to end p50 11.1 s / p95 20.3 s (M6, 1 worker: 23 s / 43 s) |
| Memory during the load test | **Peak 631 MB of 2.5 GB**; no restarts, no OOM kill |
| Soak: 10 minutes, 2 webhooks/s (1,200 issues) | **1,200/1,200 reports**, outbox empty, memory flat (see below), no errors |

## What the rehearsal found, and the fixes

| # | Found | Why it matters | Fix | Commit |
|---|---|---|---|---|
| 1 | With Postgres down, every webhook waited **30 s** before answering 500 | GitHub gives up after 10 s, so it would record a timeout instead of an error, and every waiting request held one of Tomcat's web threads | `spring.datasource.hikari.connection-timeout: 3000`: now 500 in 3 s | `db1a41b` |
| 2 | The image ran **Java 25**, not 21 | The buildpack takes the Java version of the machine that built the jar (25 on the Mac). The project is Java 21 (CLAUDE.md) | `BP_JVM_VERSION=21` in the image settings (pom.xml); checked: "using Java 21.0.13" | this batch |
| 3 | **0% memory headroom**: heap max 1.97 GB of 2.5 GB | The embedding model (ONNX Runtime) uses memory outside the heap; a full heap plus that could exceed the limit and get the pod killed | `BPL_JVM_HEAD_ROOM=20` in `deploy/k3s/30-app.yaml`: heap max 1.45 GB | this batch |
| 4 | `load-test.py` read its results from the docker-compose database, not from the app under test | Against any other stack it reports "no report was stored", and its clean-up runs in the wrong database (it only deletes synthetic issues 900000+, so no real data was at risk) | `LOADTEST_DB_CONTAINER=<container>` chooses the database | this batch |
| 5 | A finished repo job without docs said "its reports have no draft reply" | Wrong since piece 4: drafts use earlier issues | Same wording as the reports page | `e4d4a02` |
| 6 | (Found in review of fix 1) A message already in Kafka failed its 3 quick retries in ~15 s when the database was down, and went to the dead-letter topic | A Postgres restart of 20 s would lose the reports of every message in flight | Database-unreachable errors get their own back-off: 1, 2, 4 ... up to 30 s between tries, for up to 10 min; other errors keep the 3 quick retries. Tested: 45 s outage, 10/10 reports, 0 dead letters | this batch |

## Soak test
10 minutes of steady traffic, 2 new issues per second (1,200 in all), on the Java 21 image with 20% headroom. Container memory every
30 s: 634, 636, 642, 643, 645, 645, 646, 646, 646, 647, 647, 647, 647, 647, 647, 648, 648, 649, 649, 649 MB. It rises a little in the
first 2 minutes (the JIT compiler and caches warming up), then stays flat: no sign of a leak. All 1,200 reports were stored, the outbox
was empty at the end, there were no restarts, and no ERROR lines (the only ones in the log came from stopping Postgres on purpose
just before).

## Not covered here (on purpose)
- **The real Claude API.** The fake LLM ran every step. The AI path was measured in M4/M5; with the API, each report adds 1 to 3
  calls (about 2 to 5 s each), so end-to-end times will be longer, not less stable.
- **HTTPS, the Ingress and the GitHub App itself.** These only exist on the VM. Run `scripts/smoke-test.py` against the public URL
  right after the deploy (with `INSTALL_REPO` once the App exists).
- **Losing the whole VM.** Backups are on the same disk; copy one off the VM now and then (docs/m5-report.md).

## How to run the same checks on the VM
Through the public URL (probe and admin paths are not routed there, so `PUBLIC=1` skips or inverts those checks):
```bash
PUBLIC=1 BASE_URL=https://<your-host> GITHUB_WEBHOOK_SECRET=... TRIAGE_ADMIN_TOKEN=... python3 -I scripts/smoke-test.py
```
The full set, through a port-forward on the VM (`sudo k3s kubectl -n triage port-forward svc/triage-app 8080:80`):
```bash
BASE_URL=http://localhost:8080 GITHUB_WEBHOOK_SECRET=... TRIAGE_ADMIN_TOKEN=... python3 -I scripts/smoke-test.py
```
Each run leaves one test issue in `Codertocat/Hello-World` (numbers 800000-899999); the script's header has the SQL to remove it.
