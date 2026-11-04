# M6 report: metrics and polish (STOP C)

4 Nov 2026. M6 steps 1 to 3 of `docs/prompts/m3-m6-build.md` are done, tested and pushed, plus the admin-endpoint fix from the
M5 review and a budget fix. **This is STOP C**: the outreach and the first open-source PR are sai's work; the message template and
candidate repos are in **docs/outreach.md**. Nobody has been contacted. STOP B (VM, GitHub App, video) is still open too.

## What each step did

| Step | What it did | Commit |
|---|---|---|
| fix (M5 review) | **Admin endpoints protected**: every non-GET endpoint except the webhook needs `X-Admin-Token` = `TRIAGE_ADMIN_TOKEN` (unset → 503, wrong → 401, constant-time compare); the Ingress routes only public paths; the demo page sends the token only if pasted | `e6c2c78` |
| 1 | **Prometheus metrics** (`micrometer-registry-prometheus`): webhook→report latency histogram, LLM tokens and estimated cost, dead letters, duplicate levels, comment decisions, outbox backlog; Kafka consumer lag from Boot's Kafka client metrics. `/actuator/prometheus` on the app port only | `06d2905` |
| 2 | **Grafana dashboard** `deploy/grafana/triage-dashboard.json` (9 panels) + import steps; a test checks every query's metric names | `36a510b` |
| fix | The **fake LLM no longer uses up the daily budget** (only real spend does); closes M5 open question 4 | `25b4fd3` |
| STOP C prep | **docs/outreach.md**: an honest message template + 11 candidate Java repos with counts of unanswered issues | `1f0bb0a` |
| 3 | **Load test** `scripts/load-test.py`: 500 signed webhooks; receiver p50 **7 ms** / p95 **13 ms**; end to end p50 23 s / p95 43 s under a 0.4 s burst (queueing: ~10.4 reports/s on one worker thread); one report alone 0.23 s. Found that dead letters were silent in the log: now logged | `a42e40a` |

## Tests
**258 tests, 0 failures** (`./mvnw test`), 13 more than at the M5 report (245). New: the admin filter (and the real app refusing
admin POSTs), metrics (unit + the real `/actuator/prometheus` endpoint end to end), the dashboard file, the fake-budget rule,
the dead-letter log message.

## Dependencies
Only `io.micrometer:micrometer-registry-prometheus` (approved in the plan; version 1.17.1 from Spring Boot).

## LLM spend in M6
None: the load test used the fake LLM.

## Notes
- The load test ran against the shared local database and Kafka, with sai's app stopped (it shares the consumer group). Its
  issues, embeddings, reports, deliveries and outbox rows were deleted afterwards (checked: 0 left). The first smoke run left
  **5 messages in the local `issue-events.DLT` topic** (synthetic issues without `repository_url`); they're harmless and go away
  when the Kafka container is recreated.
- `/actuator/prometheus` is open on the app port (no token): it's for a Prometheus inside the cluster and isn't routed by the Ingress.

## STOP C: what sai needs to do
1. **Outreach** (docs/outreach.md): adapt the template, start with spring-ai, langchain4j and spring-framework, one message each.
2. **First open-source PR** to LangChain4j or Spring AI (separate from this project).
3. Still from STOP B (docs/m5-report.md): the Oracle VM + k3s, the HTTPS choice, the GitHub App, the demo video, and the hand
   scores (docs/m4-scoring.md, docs/m4-scoring-v3.md, docs/m4-results.md section 4).

## Open questions
1. **Listener concurrency**: one thread handles ~10 reports/s. Real traffic is ~16 issues/day, so it's fine; for bursts,
   `spring.kafka.listener.concurrency: 3` (one per partition) is a one-line change. Not done, to keep ordering reasoning simple.
2. **Prometheus/Grafana on the VM**: optional; ~1 GB more on a 12-GB VM. Decide after the app runs there.
