# Grafana dashboard (optional)

`triage-dashboard.json`: webhook→report latency p50/p95, reports per minute, LLM tokens and estimated cost (24 h), dead letters,
the outbox backlog, Kafka consumer lag, the top-match level of new issues, and comment-mode decisions.

1. Prometheus must scrape the app's `/actuator/prometheus` (port 8080; it is deliberately not exposed through the public Ingress).
   On k3s, scrape the `triage-app` Service inside the cluster.
2. In Grafana: **Dashboards → New → Import → Upload dashboard JSON file** → choose `triage-dashboard.json` → pick your
   Prometheus data source → **Import**.
