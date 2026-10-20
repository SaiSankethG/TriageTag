# incident-copilot

AI incident copilot for microservices. Spring Boot, Kafka, PostgreSQL, Docker.

## Run locally

```bash
docker compose up -d      # starts Postgres
./mvnw spring-boot:run    # or run IncidentCopilotApplication from IntelliJ
curl localhost:8080/actuator/health
```
