# CLAUDE.md: issue-triage-assistant

## What this project is
TriageTag (display name since 7 Nov 2026; repo and package names unchanged): a maintainer-side GitHub issue triage assistant. When an issue is opened, it
privately tells the maintainer: likely duplicates (with a score), suggested
labels, missing details, and a draft answer from the repo's docs (RAG).
Proof: a time-ordered replay over past issues, scored against duplicates and
labels that maintainers marked, compared with baselines (keyword search first).
It is sai's interview portfolio project. Clarity beats cleverness.

## Stack (do not change without asking)
- Java 21, Spring Boot 4.1.1, Maven wrapper (`./mvnw`)
- PostgreSQL 16 (+ pgvector later) via `docker-compose.yml`; Colima is the Docker runtime
- Kafka (Spring for Apache Kafka), Spring Data JPA, Actuator, Lombok
- LLM layer (M4): Spring AI or LangChain4j, decided then
- Do NOT upgrade Java or Spring Boot. Do NOT add dependencies without asking.

## Commands
- Start DB:      `colima start` then `docker-compose up -d`  (Postgres on localhost:5433, DB `triage`; Kafka on localhost:9092)
- Run app:       `./mvnw spring-boot:run`
- Build:         `./mvnw -q -DskipTests package`
- Test:          `./mvnw test`
- Health check:  `curl localhost:8080/actuator/health`  (expect UP)
- Note: on sai's Mac use `docker-compose` (not `docker compose`); Docker Postgres is on host port 5433, DB `triage` (5432 is a local Homebrew Postgres).

## Package layout (com.saisanketh.triage)
- `github`     GitHub REST client, webhook receiver, signature check
- `domain`     JPA entities (repo, issue, webhook_delivery, triage_result, doc_chunk)
- `repository` Spring Data repositories
- `kafka`      producers, consumers, topic config
- `triage`     duplicate search, label suggestion, missing-details check
- `ai`         LLM client and prompts (M4)
- `eval`       replay test and metrics (M3)

## Rules for Claude
1. sai decided (3 Nov 2026) that Claude writes the code. sai must still be able to explain
   every line in an interview. So after EVERY piece: explain it in plain, simple English
   (what it does, why this way, one alternative you rejected), then list 3 likely
   interview questions about it with short answers. Add these to docs/learning-notes.md.
   Assume sai is new to Lombok, JPA/Hibernate annotations and Spring internals: explain
   each annotation or library from the basics (what it is, a tiny before/after example).
2. Build one piece at a time, in the order of the build plan. Keep each change small.
   No clever tricks: clarity beats cleverness.
3. Every service class gets unit tests. Run `./mvnw test` before saying a piece is done.
4. Commit after each working piece, with a clear message. Never force-push.
5. Never commit secrets. Tokens and API keys go in env vars (e.g. GITHUB_TOKEN).
6. Only public data. No HCLTech code, names or data, ever.
7. Only one Claude session edits code at a time.
8. Do not upgrade Java/Spring Boot or add dependencies without asking sai.

## Current step (sai updates this line)
Phase 0, M0-M6 done (4 Nov 2026; reports: docs/m1-report.md ... m6-report.md). STOP C (outreach + first OSS PR: docs/outreach.md)
and STOP B (Oracle VM + k3s, GitHub App, HTTPS choice, demo video: docs/m5-report.md) are sai's.
UI STABLE (sai, 8 Nov 2026, after UI Round 19): the hold is lifted. Next: STOP B deploy (Oracle VM + k3s, docs/m5-report.md), then the backend gaps (install backfill, private-repo report access, per-repo budget and cleanup). Hand scores still open:
docs/m4-scoring.md, docs/m4-scoring-v3.md (blind A/B), docs/m4-results.md section 4 (48 false alarms).
Flyway V1-V11. Outbox (V10). Comment mode off by default (0.90 + LLM yes). Admin POSTs need X-Admin-Token (TRIAGE_ADMIN_TOKEN).
Metrics: /actuator/prometheus; dashboard deploy/grafana/. Load test: receiver p50 7 ms / p95 13 ms (500 webhooks).
Replay (833 pairs): vector recall@3 46.2% vs keyword 26.5%. Draft prompt v3. Image: ./mvnw spring-boot:build-image (arm64).
