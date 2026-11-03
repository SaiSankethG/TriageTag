# CLAUDE.md: issue-triage-assistant

## What this project is
A maintainer-side GitHub issue triage assistant. When an issue is opened, it
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
- Start DB:      `colima start && docker compose up -d`
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
Phase 0 and M0 done. Schema decided: docs/schema.md. Problem: docs/problem.md.
M1 next, piece by piece: 1) Issue entity + SourceType enum + IssueRepository,
2) IssueSource interface + GitHubIssueSource (RestClient, token from GITHUB_TOKEN),
3) Link-header paging, 4) skip pull requests + map JSON to Issue,
5) IssueService.upsert by (source, external_id), 6) POST /backfill/{owner}/{repo},
7) GET /repos/{owner}/{repo}/issues, 8) tests. Test on a small repo before spring-boot.
