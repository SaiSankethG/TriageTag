# issue-triage-assistant

A maintainer-side GitHub issue triage assistant: it finds likely duplicate issues, suggests labels, flags missing details and drafts answers from the repo's docs. Spring Boot, Kafka, PostgreSQL + pgvector, Docker.

Work in progress.

## Run locally

```bash
docker compose up -d      # starts Postgres
./mvnw spring-boot:run    # or run TriageApplication from your IDE
curl localhost:8080/actuator/health
```
