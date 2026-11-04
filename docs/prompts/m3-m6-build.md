# M3 to M6 prompt: duplicates, LLM layer, ship, metrics

Start only after M2 is done and pushed. Read CLAUDE.md, docs/schema.md, docs/problem.md,
docs/m1-report.md, docs/m2-report.md and docs/learning-notes.md first.

Same rules as M1/M2: one step at a time; after each step `./mvnw test` must pass, add a learning-notes
section (plain English, basics with a before/after example, why, one rejected alternative, 3 interview
Q&As), then commit and push. Never print, log or commit a token, key or secret. Keep bash commands simple
(one command per call; multi-step logic in scripts/). `docker-compose`, not `docker compose`. Postgres is on
5433. Public data only. Schema changes only through new Flyway migrations, plus docs/schema.md.

**Approved dependencies (only these, versions from a BOM or the Boot parent where possible):**
LangChain4j core + `langchain4j-embeddings-all-minilm-l6-v2` (in-process embeddings, no API key; do
NOT use the LangChain4j Spring Boot starter, wire beans by hand so Boot 4 compatibility doesn't matter),
and `micrometer-registry-prometheus` (M6). The LLM provider module is added only at STOP A.
Anything else: don't add it; write it under "Open questions" in the milestone report.

If a step fails 3 times: commit what works, note it under "Open questions", move on.
At each **STOP**: commit, push, write the milestone report, and tell sai exactly what's needed.

---------------------------------------------------------------------------------------------------
## M3: Duplicates, labels and the first real numbers   → report: docs/m3-report.md
0. **(added by sai after M2) deleted / transferred issues:** handle `issues` actions `deleted` (remove the
   row; labels cascade) and `transferred` (remove it from the old repo), with tests.
1. **pgvector:** switch the compose image to `pgvector/pgvector:pg16` (same major version, same volume,
   data stays; verify the row count is still 43,810 + 394 after). Flyway: `CREATE EXTENSION vector`,
   `issue_embedding(issue_id PK/FK, model varchar, embedding vector(384), created_at)`, HNSW index with
   cosine ops. Testcontainers uses the same image.
2. **Keyword baseline (#1):** Postgres full-text on title + body (`tsvector` generated column + GIN index,
   English config), top 3 by `ts_rank`, only issues of the same repo created BEFORE the query issue.
3. **Embeddings:** `EmbeddingService` behind our own interface (so the model can change). Text = title +
   first ~2,000 chars of body, cleaned (strip HTML comments/issue-template boilerplate). Batch job
   `POST /embeddings/{owner}/{repo}` that embeds only missing rows, logs progress, and reports time.
   Store the vector with plain SQL (`?::vector`), no extra library. New issues from the Kafka worker
   get embedded too.
4. **Vector top 3:** cosine similarity via pgvector, same "same repo, created before" rule. Return
   number, title, score.
5. **Label suggestion:** from the 10 nearest earlier issues, vote on labels (weighted by similarity);
   suggest a label only above a vote threshold.
6. **Replay evaluation (the resume number):** `EvalService` + `POST /eval/{owner}/{repo}`, results also
   written to `docs/m3-results.md`. Scored set = answer-key pairs whose original is a stored issue AND
   older than the duplicate (expect ~836 = 945 minus newer-original ones; report the exact count and
   what was excluded and why). For each: does the true original appear in the top 1 / top 3 / top 10, for
   keyword vs vector (recall@k) + MRR. False alarms: on 1,000 random non-duplicate issues, how often the
   top-1 score is above the threshold; pick the threshold that keeps false alarms <= 10% and report
   recall at it. Labels: on the last 1,000 issues, precision of the top suggested label vs real labels.
   Also spot-check 20 hedged-comment answer-key pairs and report how many look right.
7. README: a "Results" section with the table (keyword vs vector, recall@1/3/10, false alarms, labels).
   **STOP M3**: show sai the numbers before going on.

---------------------------------------------------------------------------------------------------
## M4: LLM layer   → report: docs/m4-report.md
1. `TriageLlm` interface (our own) + a `FakeTriageLlm` used in tests and when no key is set. All prompts
   in `src/main/resources/prompts/`, versioned by file name.
2. **Missing-details check:** for a new issue, ask the LLM which of version, steps to reproduce,
   expected vs actual, logs/stack trace are missing; strict JSON output, parsed and validated; on bad
   JSON retry once, then mark "unknown". Unit-test the parsing with fake answers.
3. **"Already fixed?" hint (no LLM):** for the top duplicate match that is closed, find from GitHub's
   records the closing PR/commit and its milestone (version). Parse the reporter's Spring Boot version
   from the body (regex). If fixed in a later version than theirs: hint "fixed in X, try upgrading".
   Store what's needed (Flyway migration), fetched via the existing GitHub client.
4. **RAG draft reply:** load spring-boot's reference docs (public asciidoc from the repo at a fixed tag)
   into `doc_chunk(id, source_url, heading, text, embedding vector(384))`, chunked by section
   (~500 tokens, overlap). For a new issue: top 5 chunks + top 3 similar issues → the LLM drafts a
   short, polite reply that cites the links it used; if nothing relevant scores above a threshold, say
   so instead of guessing.
5. **Triage report:** `triage_report(issue_id, created_at, duplicates jsonb, labels jsonb,
   missing jsonb, fixed_hint, draft_reply, llm_model, prompt_version, cost_tokens)`. The Kafka worker
   creates it for `opened` events. `GET /reports/{owner}/{repo}/{number}` returns it as a DTO.
6. **Demo page:** one static HTML page (`src/main/resources/static/index.html`, plain JS fetch, no new
   dependency): pick a repo + issue number, show the report.
7. Cost guard: count tokens per call, a daily token budget in config; above it, skip the LLM steps and
   say so in the report.
   **STOP A (needs sai):** ask sai which LLM provider/model to use and to create an API key with a
   monthly spend cap, set as an env var. Then add only that provider's LangChain4j module, wire the
   real `TriageLlm`, run it on 30 recent spring-boot issues, and write them to
   `docs/m4-scoring.md` as a table for sai to score by hand (useful / partly / wrong) with links.

---------------------------------------------------------------------------------------------------
## M5: Ship it   → report: docs/m5-report.md
0. **(added by sai after M2) transactional outbox, before deploying:** the webhook receiver writes the
   delivery row and an outbox row in one DB transaction; a relay publishes outbox rows to Kafka.
1. Container image with `./mvnw spring-boot:build-image` (no Dockerfile needed; if that doesn't work on
   Colima, a multi-stage Dockerfile on eclipse-temurin:21).
2. `deploy/k3s/`: manifests for the app (Deployment, Service, readiness/liveness on actuator health),
   Postgres with pgvector (StatefulSet + PVC), Kafka (single node), and a `Secret` TEMPLATE only
   (`secret.example.yaml`, placeholders; real secrets never in git). Resource requests that fit a
   2 OCPU / 12 GB ARM VM (Oracle Always Free since June 2026; verify current limits); the image must be built for arm64.
3. **GitHub App auth for posting (comment mode):** create the App JWT (RS256 with the App's private key,
   using java.security, no new dependency), exchange it for an installation token, cache it until
   ~5 min before expiry. Private key path from env var, never in the repo.
4. **Comment mode, off by default:** per-repo setting (Flyway table `repo_setting(repo, mode)` with
   `SHADOW` default, `SUGGEST` opt-in). In SUGGEST, post ONE comment on a new issue only if the top
   duplicate is above the M3 threshold: the link + score + "a bot suggested this; maintainers decide".
   Never post twice on the same issue (idempotent on issue + report id).
5. README: problem, architecture diagram (Mermaid), how to run locally, results table, limits, and the
   design decisions (Postgres+pgvector over Cassandra/Mongo, Kafka, shadow-first).
   **STOP B (needs sai):** sai creates the Oracle Cloud VM and installs k3s (give exact click/command
   steps), makes the App public, and records the 2-minute demo video (write a script for it in
   `docs/demo-script.md`).

---------------------------------------------------------------------------------------------------
## M6: Metrics and polish   → report: docs/m6-report.md
1. Prometheus via Micrometer: webhook→report latency (timer), LLM tokens and cost per day (counter),
   Kafka consumer lag, DLT count, duplicate suggestions made. Expose `/actuator/prometheus`.
2. A Grafana dashboard JSON in `deploy/grafana/` (optional to install).
3. Load test with a script in scripts/ (no new dependency): 500 signed webhooks; report p50/p95
   receiver response time and end-to-end time. Write the numbers into the README.
   **STOP C (needs sai):** outreach to ~10 maintainers and the first PR to LangChain4j/Spring AI are
   sai's work; write `docs/outreach.md` with a short, honest message template and the list of
   candidate repos (public Java repos with many open, unanswered issues), and stop.
