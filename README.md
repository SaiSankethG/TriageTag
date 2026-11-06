# TriageTag

**TriageTag** (repository: `issue-triage-assistant`) is a maintainer-side GitHub issue triage assistant. When an issue is opened, it **privately** tells the maintainer within seconds:
likely duplicates (with a score and a second check by an LLM), suggested labels, which details the reporter left out, whether a
similar issue was already fixed in a later version, and a draft reply grounded in the project's docs. Java 21, Spring Boot 4,
Kafka, PostgreSQL + pgvector, Claude Haiku 5.5, k3s.

## The problem

Maintainers of big open-source projects spend a large part of their time on triage: is this a duplicate, which version is it,
is there a stack trace, is it already fixed, which doc page answers it? Spring Boot alone has over 43,000 issues. Search on
GitHub is keyword-based, and new reporters describe the same bug in different words.

## How it works

```mermaid
flowchart LR
    GH[GitHub repo] -- "issues webhook (HMAC-signed)" --> R[Webhook receiver]
    R -- "one transaction" --> PG[(PostgreSQL + pgvector)]
    PG -- "outbox relay" --> K[[Kafka: issue-events]]
    K --> W[Triage worker]
    W -- "embed (MiniLM, in-process)" --> PG
    W -- "nearest earlier issues, labels, docs (HNSW)" --> PG
    W -- "2 narrow questions" --> LLM[Claude Haiku 5.5]
    W -- "fixed in which version?" --> GQL[GitHub GraphQL]
    W -- "triage_report" --> PG
    PG --> UI[Demo page / REST API]
    W -. "comment mode, off by default" .-> GH
```

1. **Intake**: `POST /webhooks/github` checks the HMAC signature, then writes the delivery id and the event to an **outbox**
   table in one Postgres transaction and answers GitHub at once. A relay sends outbox rows to Kafka in order (at least once).
2. **Worker** (Kafka consumer, idempotent): stores the issue, embeds it (all-MiniLM-L6-v2, in-process), and on `opened`
   builds the report:
   - **duplicates**: the 3 nearest *earlier* issues of the repo (pgvector HNSW, cosine);
   - **LLM check** of the top match ("same root problem?", strict JSON) when it scores ≥ 90%;
   - **labels**: a similarity-weighted vote of the 10 nearest issues;
   - **missing details**: version / steps / expected vs actual / logs (LLM, strict JSON, one retry, else "unknown");
   - **already fixed?**: the match's closing PR/commit and milestone vs the reporter's Spring Boot version (no LLM);
   - **draft reply** (RAG): the nearest reference-doc sections (v4.1.2) and similar issues → a short reply that may link
     only to those sources; no relevant source → no draft.
3. **Cost guard**: a daily token budget; above it, LLM parts say "paused" and the rest of the report still works.
4. **Comment mode** (opt-in per repo, at most one comment per issue): only for open issues from outside the team, never on
   bookkeeping clones, and only when the match is ≥ 90% *and* the LLM agrees.

## Results

Time-ordered replay on **spring-projects/spring-boot** (43,810 issues). Each maintainer-confirmed duplicate is searched as if
it had just been opened, against **earlier issues of the same repo only**. The answer key comes from GitHub's own records;
**833** pairs are scored ([docs/m3-results.md](docs/m3-results.md), [docs/m3-report.md](docs/m3-report.md)).

| Method | recall@1 | recall@3 | recall@10 | MRR |
|---|---|---|---|---|
| Keyword baseline (Postgres full-text, `ts_rank`) | 19.2% | 26.5% | 37.1% | 0.244 |
| **Vector** (all-MiniLM-L6-v2 + pgvector HNSW) | **31.0%** | **46.2%** | **60.9%** | **0.403** |

**Public comments are the hard part** (external reporters; 720 duplicates, 1,000 non-duplicates;
[docs/m4-results.md](docs/m4-results.md)):

| Rule | Correct flags (of 720) | False alarms (of 1,000) |
|---|---|---|
| Score ≥ 0.825 | 55 | 101 (10.1%) |
| Score ≥ 0.825 + Claude "same problem" | 50 | 48 (4.8%) |
| Score ≥ 0.90 + Claude "same problem" | 19 | 25 (2.5%) |

The LLM check halves the false alarms. But only ~4% of new external issues are duplicates, so even then most public comments
would point at the wrong issue (many of the rest are the team's own forward-port clones, which comment mode now skips). That is
why the default is **shadow mode**: a private top 3 for the maintainer.

- **Labels:** suggested for 30.6% of recent external issues; the top label is right 69.3% of the time.
- **Cost:** a full report is ~1,000–5,800 tokens on Claude Haiku 5.5 (≈ $0.0002–0.001).
- **Hand-scoring:** 30 recent issues with full reports in [docs/m4-scoring.md](docs/m4-scoring.md) (and two draft versions
  side by side, blind, in [docs/m4-scoring-v3.md](docs/m4-scoring-v3.md)).

### Load test (local, M6)
500 signed `issues opened` webhooks sent by `scripts/load-test.py` (10 parallel senders) to the app on a MacBook (Apple Silicon),
with Postgres and Kafka in Docker (Colima, 2 GB VM) and the **fake LLM** (so it measures the pipeline, not Claude's latency).
Each test issue is searched against the real ~44k spring-boot issues; everything the test created is deleted afterwards.

| | Result |
|---|---|
| Webhooks accepted | 500 of 500 |
| **Receiver response time** p50 / p95 | **7 ms / 13 ms** (GitHub allows 10 s) |
| Reports stored | 500 of 500, no dead letters |
| End to end (webhook sent → report stored) p50 / p95 | 23.1 s / 43.3 s: all 500 arrived within 0.4 s, and one worker thread made ~10.4 reports/s, so this is mostly time in the queue |
| One report with no queue in front of it | 0.23 s |

Real traffic is far lower (spring-boot: ~16 new issues a day over the last 30 days, ~2 from outside the team), so a new issue's report is ready in well under a second plus the
real LLM calls (~2-5 s on Haiku 5.5). For bursts, the topic has 3 partitions, so 3 listener threads (`concurrency: 3`) would
roughly triple throughput.

## Run locally

```bash
colima start              # the Docker runtime used here (any Docker works)
docker-compose up -d      # Postgres + pgvector on localhost:5433 (database "triage"), Kafka on localhost:9092
./mvnw spring-boot:run    # Flyway creates/updates the schema; open http://localhost:8080/ (landing) or /app.html (reports)
```

Postgres is on host port **5433**, so it doesn't clash with a locally installed Postgres. Secrets only ever come from env vars:
`GITHUB_TOKEN` (backfill), `GITHUB_WEBHOOK_SECRET` (webhooks), `ANTHROPIC_API_KEY` (without it, a rule-based stand-in LLM is
used and the page says so), and `TRIAGE_ADMIN_TOKEN`.

**Admin calls** (everything that changes data or spends tokens: backfill, embeddings, docs, eval, `POST /reports`) need the
header `X-Admin-Token` with the value of `TRIAGE_ADMIN_TOKEN`; without the env var they answer 503, with a wrong token 401.
Reading (the demo page, `GET /reports`, search) and the GitHub webhook (own HMAC check) don't need it. Set it before starting the app:

```bash
export TRIAGE_ADMIN_TOKEN=$(openssl rand -hex 32)
```
```bash
curl -X POST -H "X-Admin-Token: $TRIAGE_ADMIN_TOKEN" localhost:8080/backfill/spring-projects/spring-boot              # issues (~12 min)
curl -X POST -H "X-Admin-Token: $TRIAGE_ADMIN_TOKEN" localhost:8080/backfill/spring-projects/spring-boot/duplicates   # answer key (~19 min)
curl -X POST -H "X-Admin-Token: $TRIAGE_ADMIN_TOKEN" localhost:8080/embeddings/spring-projects/spring-boot            # embeddings (~6 min)
curl -X POST -H "X-Admin-Token: $TRIAGE_ADMIN_TOKEN" localhost:8080/docs/spring-projects/spring-boot                  # docs (~15 s)
curl -X POST -H "X-Admin-Token: $TRIAGE_ADMIN_TOKEN" localhost:8080/reports/spring-projects/spring-boot/52529         # one report
curl -X POST -H "X-Admin-Token: $TRIAGE_ADMIN_TOKEN" localhost:8080/eval/spring-projects/spring-boot                  # replay (~3 min)
./mvnw test                                                                   # 251 tests; Testcontainers for Postgres and Kafka
```

On the demo page, **Create report** appears only after pasting the admin token into the "Admin" box (kept in that browser tab
only); without it, only existing reports can be viewed.

Container image and deployment: `./mvnw spring-boot:build-image` (linux/arm64, buildpacks) and `deploy/k3s/`
(Postgres and Kafka StatefulSets, the app with startup/liveness/readiness probes, an Ingress that routes only the public paths,
and a secret *template*).

## Design decisions

- **PostgreSQL + pgvector, not Cassandra or MongoDB.** The data is relational (issues, labels, reports, deliveries) and small
  (tens of thousands of rows per repo). One database gives transactions across the issue, its embedding and the outbox, plus
  full-text search for the keyword baseline and HNSW for vectors. Cassandra shines at huge write volumes with simple key
  access, which this isn't; a separate vector store would add a system to keep in sync.
- **Kafka between intake and work.** GitHub waits at most 10 s for a webhook answer; embedding and LLM calls take seconds and
  can fail. Kafka decouples them, keeps per-issue order (key = issue), gives retries and a dead-letter topic, and lets the
  worker scale separately.
- **Transactional outbox.** "Write to Postgres" and "write to Kafka" can't be one atomic step. The webhook writes the event to
  an outbox table in the same transaction; a relay publishes it. Events are never lost, and webhooks are accepted while Kafka
  is down.
- **Shadow mode first.** Measured: at a 4% duplicate base rate, automatic public comments are mostly wrong even with an LLM
  check. So the default is a private report; public comments are opt-in, strict, and never repeated.
- **Our own interfaces around the AI.** `EmbeddingService` and `TriageLlm` are ours; tests use a fake LLM; prompts are
  versioned files; LLM output is parsed as strict JSON with one retry, else "unknown"; every report stores the model,
  prompt versions and tokens.
- **Measure before trusting.** Every threshold came from the replay (false alarms on non-duplicates, not just recall on
  duplicates), and ideas that didn't help (excluding forward-port clones from the candidates: recall went down) were dropped.

## Limits

- The duplicate check and the false-alarm numbers rely on what maintainers *marked*; some "false alarms" are real but unmarked
  duplicates.
- The "already fixed?" version parsing is Spring Boot-specific; the reference docs are one version (4.1.2).
- One replica of each component on one small VM: no high availability.
- MiniLM reads only ~256 word pieces per text; long issues are judged by their start.
