#!/usr/bin/env python3
"""Load test: sends N signed "issues opened" webhooks to a running app, the way GitHub does, and measures
(1) how fast the webhook receiver answers and (2) how long until each issue's triage report is stored (end to end).

Usage:
    LOADTEST_WEBHOOK_SECRET=<the app's github.webhook-secret> python3 -I scripts/load-test.py http://localhost:8081 500 10
    (base URL, number of webhooks, parallel senders)

- Standard library only (no new dependency). The secret is read from the environment and never printed.
- The issues are synthetic, numbered 900000+ in spring-projects/spring-boot, so each new issue is searched against the
  real ~44k-issue pool (realistic search work). Their texts are real-sounding Spring Boot problems.
- Run the app with the FAKE LLM (--triage.llm.provider=fake): the LLM steps run but cost nothing.
- End-to-end time = triage_report.created_at - the moment this script started sending that webhook (same machine clock).
- Everything the test created (issues and, by cascade, embeddings/reports; deliveries; outbox rows) is deleted at the end.
"""
import concurrent.futures
import hashlib
import hmac
import json
import os
import statistics
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

BASE, COUNT, WORKERS = sys.argv[1].rstrip("/"), int(sys.argv[2]), int(sys.argv[3])
SECRET = os.environ.get("LOADTEST_WEBHOOK_SECRET", "").encode()
if not SECRET:
    sys.exit("LOADTEST_WEBHOOK_SECRET is not set")
REPO = "spring-projects/spring-boot"
FIRST_NUMBER = 900000
RUN = f"loadtest-{int(time.time())}"

TOPICS = [
    ("DataSource URL from application.yml is ignored", "spring.datasource.url is set but Hikari connects to the default H2 database."),
    ("Actuator health endpoint returns 404 behind a proxy", "After upgrading, /actuator/health answers 404 when server.servlet.context-path is set."),
    ("Kafka listener stops consuming after a rebalance", "The @KafkaListener receives nothing after a consumer group rebalance; restart fixes it."),
    ("@ConfigurationProperties record with nested Optional is not bound", "A nested Optional<Inner> stays empty even though inner.* properties are set."),
    ("Docker Compose support does not detect my Postgres service", "spring-boot-docker-compose starts the container but no ConnectionDetails bean is created."),
    ("Native image fails at startup with a ClassNotFoundException", "The AOT-processed app works on the JVM but the native executable fails."),
    ("Logback structured logging prints JSON twice", "With logging.structured.format.console=ecs every line appears twice in the output."),
    ("Jackson 3 module for Kotlin is not registered", "Kotlin data classes fail to deserialize after moving to Spring Boot 4."),
]


def payload(i):
    number = FIRST_NUMBER + i
    title, body = TOPICS[i % len(TOPICS)]
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    return json.dumps({
        "action": "opened",
        "issue": {
            "id": 9_000_000_000 + i, "node_id": f"LOADTEST{i}", "number": number,
            "title": f"{title} (load test {i})",
            "body": f"{body}\n\nSpring Boot 4.1.2. Steps: run the attached sample. Expected it to work, but it fails.",
            "state": "open", "labels": [], "author_association": "NONE",
            "html_url": f"https://github.com/{REPO}/issues/{number}", "created_at": now, "updated_at": now,
            "repository_url": f"https://api.github.com/repos/{REPO}",   # the mapper takes the repo name from here
        },
        "repository": {"full_name": REPO, "private": False},   # private repos are ignored (piece 1b)
        "sender": {"login": "load-test"},
    }).encode()


def send(i):
    body = payload(i)
    signature = "sha256=" + hmac.new(SECRET, body, hashlib.sha256).hexdigest()
    request = urllib.request.Request(f"{BASE}/webhooks/github", data=body, method="POST", headers={
        "Content-Type": "application/json", "X-GitHub-Event": "issues",
        "X-GitHub-Delivery": f"{RUN}-{i}", "X-Hub-Signature-256": signature})
    started = time.time()
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            status = response.status
    except urllib.error.HTTPError as e:
        status = e.code
    return i, started, time.time() - started, status


def psql(sql):
    return subprocess.run(["docker-compose", "exec", "-T", "postgres", "psql", "-U", "copilot", "-d", "triage", "-At",
                           "-F", "|", "-c", sql], check=True, capture_output=True, text=True).stdout


def percentile(values, p):
    values = sorted(values)
    return values[min(len(values) - 1, int(round(p / 100 * (len(values) - 1))))]


print(f"Sending {COUNT} webhooks with {WORKERS} parallel senders...", file=sys.stderr)
t0 = time.time()
with concurrent.futures.ThreadPoolExecutor(WORKERS) as pool:
    results = list(pool.map(send, range(COUNT)))
send_seconds = time.time() - t0
failed = [r for r in results if r[3] != 200]

numbers = f"{FIRST_NUMBER} AND {FIRST_NUMBER + COUNT - 1}"
deadline = time.time() + 900
reports = {}
last_progress, last_count = time.time(), -1
while time.time() < deadline and time.time() - last_progress < 60:   # give up after 60 s without a new report
    rows = psql(f"SELECT i.number, extract(epoch FROM r.created_at) FROM triage_report r JOIN issue i ON i.id = r.issue_id "
                f"WHERE i.repo = '{REPO}' AND i.number BETWEEN {numbers}")
    reports = {int(n): float(t) for n, t in (line.split("|") for line in rows.split())}
    if len(reports) >= COUNT - len(failed):
        break
    if len(reports) != last_count:
        last_progress, last_count = time.time(), len(reports)
    time.sleep(2)
total_seconds = time.time() - t0

receiver_ms = [r[2] * 1000 for r in results if r[3] == 200]
if not reports:
    psql(f"DELETE FROM webhook_delivery WHERE delivery_id LIKE '{RUN}-%'")
    psql(f"DELETE FROM outbox_event WHERE message_key LIKE '{REPO}#9_____'")
    sys.exit("No report was stored: check the app log and triage_kafka_dead_letters_total. Cleaned up deliveries and outbox rows.")
e2e_s = [reports[FIRST_NUMBER + i] - started for i, started, _, status in results
         if status == 200 and FIRST_NUMBER + i in reports]

print(f"""
| Load test ({COUNT} signed webhooks, {WORKERS} parallel senders, fake LLM) | |
|---|---|
| Webhooks accepted (HTTP 200) | {len(receiver_ms)} of {COUNT} |
| Receiver response time p50 / p95 | {percentile(receiver_ms, 50):.0f} ms / {percentile(receiver_ms, 95):.0f} ms |
| Sending all webhooks took | {send_seconds:.1f} s ({COUNT / send_seconds:.0f} webhooks/s) |
| Reports stored | {len(e2e_s)} of {COUNT} |
| End to end (webhook sent → report stored) p50 / p95 | {percentile(e2e_s, 50):.1f} s / {percentile(e2e_s, 95):.1f} s |
| All {COUNT} reports done after | {total_seconds:.0f} s ({len(e2e_s) / total_seconds:.1f} reports/s) |
""")
if failed:
    print(f"Not accepted: {len(failed)} (statuses {sorted({r[3] for r in failed})})", file=sys.stderr)
print(f"Fastest end to end (no queue in front of it): {min(e2e_s):.2f} s", file=sys.stderr)

# Clean up everything this run created (issues cascade to labels, embeddings, reports and comment rows).
psql(f"DELETE FROM issue WHERE repo = '{REPO}' AND number BETWEEN {numbers}")
psql(f"DELETE FROM webhook_delivery WHERE delivery_id LIKE '{RUN}-%'")
psql(f"DELETE FROM outbox_event WHERE message_key LIKE '{REPO}#9_____'")   # 900000-999999 only, sent or not
print("Cleaned up the test issues, deliveries and outbox rows.", file=sys.stderr)
