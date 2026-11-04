# issue-triage-assistant

A maintainer-side GitHub issue triage assistant: it finds likely duplicate issues, suggests labels, flags missing details and drafts answers from the repo's docs. Spring Boot, Kafka, PostgreSQL + pgvector, Docker.

Work in progress.

## Run locally

```bash
colima start              # the Docker runtime used here (any Docker works)
docker-compose up -d      # starts Postgres on localhost:5433 (database "triage")
./mvnw spring-boot:run    # or run TriageApplication from your IDE; Flyway creates/updates the schema
curl localhost:8080/actuator/health
```

Postgres is on host port **5433**, not 5432, so it doesn't clash with a locally installed Postgres.
Set `GITHUB_TOKEN` in your shell to backfill issues (never put it in a file in this repo).
