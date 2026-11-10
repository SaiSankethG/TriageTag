#!/usr/bin/env python3
"""
Smoke test for a running TriageTag (local rehearsal, or the VM after a deploy). Python standard library only.

Checks every path once, the way GitHub and a visitor use it:
  pages and health, public JSON, admin token required, webhook signature, a new issue -> report, a redelivery -> no
  duplicate, a private repository -> ignored, and (optional) installing the App on a small public repo -> its
  "Add repository" job finishes.

Usage:
  BASE_URL=http://localhost:8090 GITHUB_WEBHOOK_SECRET=... TRIAGE_ADMIN_TOKEN=... scripts/smoke-test.py
  INSTALL_REPO=spring-projects/spring-petclinic ... scripts/smoke-test.py     # also run the install check (needs GITHUB_TOKEN on the app)
  PUBLIC=1 BASE_URL=https://<your-host> ...                                   # through the public Ingress (see below)

PUBLIC=1: the Ingress routes only public paths, so the probe URLs (/actuator/health/liveness, /readiness) and the admin
paths (/repo-jobs) answer 404 there on purpose. Those checks are skipped. For the full set on the VM, run it through
`sudo k3s kubectl -n triage port-forward svc/triage-app 8080:80` with BASE_URL=http://localhost:8080.

Leaves test data behind: each run adds one issue (and its report) numbered 800000-899999 in Codertocat/Hello-World (the
GitHub example repo of the fixture, so the real repos' searches are untouched; it does show up in /repos). To remove it:
  DELETE FROM issue WHERE repo = 'Codertocat/Hello-World' AND number BETWEEN 800000 AND 899999;
  DELETE FROM outbox_event WHERE message_key LIKE 'Codertocat/Hello-World#8_____';

Secrets are read from the environment and never printed. Exit code 0 = all checks passed.
"""
import hashlib
import hmac
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid

BASE = os.environ.get("BASE_URL", "http://localhost:8080").rstrip("/")
SECRET = os.environ.get("GITHUB_WEBHOOK_SECRET", "")
ADMIN = os.environ.get("TRIAGE_ADMIN_TOKEN", "")
INSTALL_REPO = os.environ.get("INSTALL_REPO", "")
PUBLIC = os.environ.get("PUBLIC", "") == "1"
REPORT_WAIT_S = int(os.environ.get("REPORT_WAIT_S", "90"))
JOB_WAIT_S = int(os.environ.get("JOB_WAIT_S", "1800"))
FIXTURE = os.path.join(os.path.dirname(__file__), "..", "src", "test", "resources", "github", "webhook-issues-opened.json")

failures = []


def check(ok, what, detail=""):
    print(("PASS  " if ok else "FAIL  ") + what + (f"  ({detail})" if detail and not ok else ""))
    if not ok:
        failures.append(what)


def request(method, path, body=None, headers=None):
    req = urllib.request.Request(BASE + path, data=body, method=method, headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()
    except urllib.error.URLError as e:
        return 0, str(e.reason)


def webhook(event, payload, delivery=None, secret=None):
    body = json.dumps(payload).encode()
    sig = "sha256=" + hmac.new((SECRET if secret is None else secret).encode(), body, hashlib.sha256).hexdigest()
    return request("POST", "/webhooks/github", body, {
        "Content-Type": "application/json", "X-GitHub-Event": event,
        "X-GitHub-Delivery": delivery or str(uuid.uuid4()), "X-Hub-Signature-256": sig})


def wait_for(what, fn, seconds):
    end = time.time() + seconds
    while time.time() < end:
        result = fn()
        if result:
            return result
        time.sleep(2)
    return None


def main():
    if not SECRET or not ADMIN:
        sys.exit("Set GITHUB_WEBHOOK_SECRET and TRIAGE_ADMIN_TOKEN (the same values as the app's).")
    print(f"Smoke test against {BASE}")

    # 1. Health and pages
    status, body = request("GET", "/actuator/health")
    check(status == 200 and '"UP"' in body, "health is UP", body[:120])
    if not PUBLIC:   # not routed by the public Ingress, on purpose
        for probe in ("liveness", "readiness"):
            status, _ = request("GET", f"/actuator/health/{probe}")
            check(status == 200, f"{probe} probe answers 200", str(status))
    for page in ("/", "/app.html", "/guide.html", "/theme.css", "/theme.js"):
        status, _ = request("GET", page)
        check(status == 200, f"GET {page} is 200", str(status))
    status, body = request("GET", "/repos")
    check(status == 200 and body.startswith("["), "GET /repos lists repositories", body[:120])
    status, body = request("GET", "/settings")
    check(status == 200 and "llmModel" in body, "GET /settings answers", body[:120])

    # 2. Admin calls need the token
    status, _ = request("POST", "/repo-jobs/octo-org/hello")
    if PUBLIC:
        check(status == 404, "admin paths are not reachable from outside (404)", str(status))
    else:
        check(status == 401, "an admin call without the token is 401", str(status))

    # 3. Webhook: wrong signature, new issue -> report, redelivery, private repo
    with open(FIXTURE) as f:
        fixture = json.load(f)
    status, _ = webhook("issues", fixture, secret="wrong-secret")
    check(status == 401, "a webhook with a wrong signature is 401", str(status))

    number = 800000 + int(time.time()) % 99999          # a fresh issue every run (load-test.py uses 900000+)
    payload = json.loads(json.dumps(fixture))
    payload["issue"]["id"] = 7_000_000_000 + number
    payload["issue"]["node_id"] = f"SMOKE_{number}"
    payload["issue"]["number"] = number
    payload["issue"]["title"] = f"Smoke test {number}: application fails to start on port 8080"
    repo = payload["repository"]["full_name"]
    delivery = str(uuid.uuid4())
    started = time.time()
    status, body = webhook("issues", payload, delivery)
    answered_ms = int((time.time() - started) * 1000)
    check(status == 200 and "accepted" in body, f"a new issue webhook is accepted ({answered_ms} ms)", f"{status} {body}")
    check(answered_ms < 2000, "the webhook is answered in under 2 s", f"{answered_ms} ms")
    report = wait_for("report", lambda: (lambda s: s[0] == 200 and s)(request("GET", f"/reports/{repo}/{number}")),
                      REPORT_WAIT_S)
    took = int(time.time() - started)
    check(bool(report), f"its report appears (after {took} s)", f"none after {REPORT_WAIT_S} s")
    status, body = webhook("issues", payload, delivery)
    check(status == 200 and "duplicate" in body, "the same delivery again is a duplicate", f"{status} {body}")

    private = json.loads(json.dumps(payload))
    private["repository"]["private"] = True
    private["issue"]["number"] = number + 1
    status, body = webhook("issues", private)
    check(status == 202 and "private" in body, "an issue in a private repo is ignored (202)", f"{status} {body}")
    time.sleep(5)
    status, _ = request("GET", f"/reports/{repo}/{number + 1}")
    check(status == 404, "the private issue has no report", str(status))

    # 4. Optional: installing the App on a small public repo runs its "Add repository" job to the end
    if INSTALL_REPO:
        status, body = webhook("installation", {"action": "created", "installation": {"id": 1},
                                                "repositories": [{"full_name": INSTALL_REPO, "private": False}]})
        check(status == 200 and "queued 1" in body, f"installing on {INSTALL_REPO} queues a job", f"{status} {body}")
        last = {}

        def job_finished():
            s, b = request("GET", f"/repos/{INSTALL_REPO}/job")
            if s == 200:
                job = json.loads(b)
                if (job["status"], job["step"], job["done"]) != (last.get("status"), last.get("step"), last.get("done")):
                    print(f"      job: {job['status']} {job['step'] or ''} {job['done'] or 0}/{job['total'] or '?'}")
                    last.update(job)
                return job if job["status"] in ("DONE", "FAILED") else None
            return None

        job = wait_for("job", job_finished, JOB_WAIT_S)
        check(bool(job) and job["status"] == "DONE", f"the job for {INSTALL_REPO} is DONE",
              json.dumps(job)[:300] if job else f"not finished after {JOB_WAIT_S} s")

    print()
    print("All checks passed" if not failures else f"{len(failures)} check(s) failed: " + "; ".join(failures))
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
