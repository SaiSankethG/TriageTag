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

## Package layout (com.saisanketh.triage)
- `github`     GitHub REST client, webhook receiver, signature check
- `domain`     JPA entities (repo, issue, webhook_delivery, triage_result, doc_chunk)
- `repository` Spring Data repositories
- `kafka`      producers, consumers, topic config
- `triage`     duplicate search, label suggestion, missing-details check
- `ai`         LLM client and prompts (M4)
- `eval`       replay test and metrics (M3)

## Rules for Claude
1. sai must be able to explain every line in an interview.
   sai WRITES the core logic: the issue table/entity, backfill paging, webhook
   signature check + duplicate-delivery guard, Kafka producer/consumer,
   similarity search, and the replay test. For these, Claude explains, hints
   and reviews; it does not write the code.
2. Claude may write boilerplate: config, docker-compose, DTOs/JSON mapping,
   test scaffolding, README drafts. Keep changes small and explain why.
3. Every service class gets unit tests.
4. Commit after each working step, with a clear message.
5. Never commit secrets. Tokens and API keys go in env vars (e.g. GITHUB_TOKEN).
6. Only public data. No HCLTech code, names or data, ever.
7. Only one Claude session edits code at a time.

## Current step (sai updates this line)
M0 done (rename). M1: backfill one public repo's issues into Postgres via the
GitHub REST API. First: sai designs the `issue` table from a real issue's JSON.
