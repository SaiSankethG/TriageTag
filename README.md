# issue-triage-assistant

A maintainer-side GitHub issue triage assistant: it finds likely duplicate issues, suggests labels, flags missing details and drafts answers from the repo's docs. Spring Boot, Kafka, PostgreSQL + pgvector, Docker.

Work in progress.

## Results

Time-ordered replay on **spring-projects/spring-boot** (43,810 issues). Each maintainer-confirmed duplicate is searched as if
it had just been opened, against **earlier issues of the same repo only**. The answer key comes from GitHub's own records
("close as duplicate of", "marked as duplicate", maintainer "Duplicate of #N" comments); **833** pairs are scored
(full method and exclusions: [docs/m3-results.md](docs/m3-results.md), [docs/m3-report.md](docs/m3-report.md)).

| Method | recall@1 | recall@3 | recall@10 | MRR |
|---|---|---|---|---|
| Keyword baseline (Postgres full-text, `ts_rank`) | 19.2% | 26.5% | 37.1% | 0.244 |
| **Vector** (all-MiniLM-L6-v2 in-process + pgvector HNSW) | **31.0%** | **46.2%** | **60.9%** | **0.403** |

- **False alarms:** on 1,000 random non-duplicate issues from external reporters, a "likely duplicate" threshold of cosine
  **0.825** keeps false alarms at **10%**; at that threshold the true original is in the top 3 (and flagged) for **13.6%** of
  duplicates. Similar-but-different issues are common, so the default output is a **private top-3 for the maintainer**, not
  a public comment.
- **Labels:** on the 1,000 most recent external issues with a real label, a suggestion is made for **30.6%**, and the top
  suggested label is right **69.3%** of the time (all issues, team bookkeeping included: 80.0% coverage, 85.5% precision).

## Run locally

```bash
colima start              # the Docker runtime used here (any Docker works)
docker-compose up -d      # Postgres + pgvector on localhost:5433 (database "triage"), Kafka on localhost:9092
./mvnw spring-boot:run    # or run TriageApplication from your IDE; Flyway creates/updates the schema
curl localhost:8080/actuator/health
```

Postgres is on host port **5433**, not 5432, so it doesn't clash with a locally installed Postgres.
Set `GITHUB_TOKEN` in your shell to backfill issues (never put it in a file in this repo).

```bash
curl -X POST localhost:8080/backfill/spring-projects/spring-boot              # load issues (~12 min)
curl -X POST localhost:8080/backfill/spring-projects/spring-boot/duplicates   # answer key (~19 min)
curl -X POST localhost:8080/embeddings/spring-projects/spring-boot            # embeddings (~6 min)
curl 'localhost:8080/repos/spring-projects/spring-boot/issues/52529/similar?method=vector'
curl -X POST localhost:8080/eval/spring-projects/spring-boot                  # the replay (~2 min)
```
