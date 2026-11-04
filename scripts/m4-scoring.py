#!/usr/bin/env python3
"""Creates triage reports for the N most recent issues by external reporters of a repo (POST /reports/...) and writes
them to docs/m4-scoring.md as a table + one section per issue, for sai to score by hand (useful / partly / wrong).

Usage: python3 -I scripts/m4-scoring.py spring-projects/spring-boot 30
Needs: the app running on localhost:8080 (with the real LLM), the Docker Postgres on 5433 (read via docker-compose).
"""
import json
import os
import subprocess
import sys
import urllib.request

repo, count = sys.argv[1], int(sys.argv[2])
base = "http://localhost:8080"

sql = ("SELECT number FROM issue WHERE repo = '%s' AND author_association NOT IN ('OWNER','MEMBER','COLLABORATOR') "
       "ORDER BY created_at DESC LIMIT %d" % (repo.replace("'", ""), count))
out = subprocess.run(["docker-compose", "exec", "-T", "postgres", "psql", "-U", "copilot", "-d", "triage", "-At", "-c", sql],
                     check=True, capture_output=True, text=True).stdout
numbers = [int(n) for n in out.split()]

reports = []
for n in numbers:
    req = urllib.request.Request(f"{base}/reports/{repo}/{n}", method="POST",
                                 headers={"X-Admin-Token": os.environ.get("TRIAGE_ADMIN_TOKEN", "")})
    with urllib.request.urlopen(req, timeout=300) as resp:
        reports.append(json.load(resp))
    print(f"report for #{n}: {reports[-1]['costTokens']} tokens", file=sys.stderr)


def cell(text):
    return (text or "").replace("|", "\\|").replace("\n", " ")


def top(r):
    if not r["duplicates"]:
        return "-"
    d = r["duplicates"][0]
    check = r.get("duplicateCheck")
    verdict = f", LLM: {check['verdict']}" if check else ""
    return f"[#{d['number']}]({d['url']}) {d['score']:.2f}{verdict}"


def missing(r):
    m = r["missing"]
    if m["status"] != "OK":
        return "unknown"
    return ", ".join(x.lower().replace("_", " ") for x in m["missing"]) or "nothing"


total_tokens = sum(r["costTokens"] for r in reports)
lines = [
    f"# M4 STOP A: {len(reports)} recent {repo} issues, scored by hand",
    "",
    f"Reports created with `{reports[0]['llmModel']}` (prompts: {reports[0]['promptVersions']}) by "
    f"`scripts/m4-scoring.py`. Issues: the {len(reports)} most recent by external reporters. "
    f"Total LLM tokens: {total_tokens:,} (${total_tokens / 1e6 * 0.10:.3f} to ${total_tokens / 1e6 * 0.50:.3f} at "
    f"Haiku 5.5's $0.10 input / $0.50 output per million; mostly input).",
    "",
    "**How to score:** for each issue, open the link, read the report section below, and put **useful**, **partly** "
    "or **wrong** in the Score column (and a few words in Notes if not useful). Judge it as a maintainer: would this "
    "report have saved you time?",
    "",
    "| # | Issue | Top match (score, LLM check) | Labels | Missing | Draft | Score | Notes |",
    "|---|---|---|---|---|---|---|---|",
]
for i, r in enumerate(reports, 1):
    labels = ", ".join(l["label"] for l in r["labels"]) or "-"
    lines.append(f"| {i} | [#{r['number']}]({r['url']}) {cell(r['title'])[:60]} | {top(r)} | {cell(labels)} | "
                 f"{missing(r)} | {r['draftReply']['status'].lower()} |  |  |")
lines.append("")
for i, r in enumerate(reports, 1):
    lines += [f"## {i}. [#{r['number']}]({r['url']}) {r['title']}", ""]
    lines.append("**Possible duplicates:** " + ("; ".join(
        f"[#{d['number']}]({d['url']}) {d['title']} ({d['score']:.3f})" for d in r["duplicates"]) or "none"))
    if r.get("duplicateCheck"):
        c = r["duplicateCheck"]
        lines.append(f"  - LLM check of the top match: **{c['verdict']}**: {c['reason']}")
    lines.append("")
    lines.append("**Labels:** " + (", ".join(f"{l['label']} ({l['share']:.0%})" for l in r["labels"]) or "none"))
    lines.append("")
    lines.append("**Missing details:** " + missing(r))
    lines.append("")
    if r.get("fixedHint"):
        lines += ["**Already fixed?** " + r["fixedHint"]["text"], ""]
    d = r["draftReply"]
    lines.append(f"**Draft reply ({d['status'].lower()}):**")
    lines.append("")
    lines += ["> " + line for line in (d["text"] or "").splitlines()]
    if d.get("unknownLinks"):
        lines += ["", "Links not among the sources: " + ", ".join(d["unknownLinks"])]
    lines += ["", f"Tokens: {r['costTokens']}", ""]

with open("docs/m4-scoring.md", "w") as f:
    f.write("\n".join(lines))
print(f"wrote docs/m4-scoring.md: {len(reports)} reports, {total_tokens} tokens", file=sys.stderr)
