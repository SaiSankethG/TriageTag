#!/usr/bin/env python3
"""Regenerates the reports of the 30 issues in docs/m4-scoring.md (now with draft-reply-v3) and writes
docs/m4-scoring-v3.md: per issue the new draft and the v2 draft from docs/m4-scoring.md, as "Draft A" and "Draft B" in an
order that differs per issue (the key is at the end), so sai can score them without knowing which prompt wrote which.

Usage: python3 -I scripts/m4-scoring-v3.py http://localhost:8081
Needs: the app running with the real LLM. docs/m4-scoring.md is only read, never changed.
"""
import hashlib
import json
import re
import sys
import urllib.request

base = sys.argv[1].rstrip("/")
v2_doc = open("docs/m4-scoring.md").read()

# The 30 issues, in the order of the v2 table, and each one's v2 draft (the "> " lines under "**Draft reply (...):**").
numbers = [int(n) for n in re.findall(r"^\| \d+ \| \[#(\d+)\]", v2_doc, re.M)]
repo = re.search(r"https://github.com/([\w.-]+/[\w.-]+)/issues/", v2_doc).group(1)
v2_drafts = {}
for section in v2_doc.split("\n## ")[1:]:
    number = int(re.match(r"\d+\. \[#(\d+)\]", section).group(1))
    status = re.search(r"\*\*Draft reply \(([a-z_]+)\):\*\*", section).group(1)
    quoted = [line[2:] for line in section.splitlines() if line.startswith("> ") or line == ">"]
    v2_drafts[number] = (status, "\n".join(quoted).strip())

reports = []
for n in numbers:
    req = urllib.request.Request(f"{base}/reports/{repo}/{n}", method="POST")
    with urllib.request.urlopen(req, timeout=300) as resp:
        reports.append(json.load(resp))
    print(f"#{n}: {reports[-1]['costTokens']} tokens, prompts {reports[-1]['promptVersions']}", file=sys.stderr)


def a_is_v3(number):
    """Deterministic but mixed order: about half the issues show v3 first."""
    return hashlib.sha256(str(number).encode()).digest()[0] % 2 == 0


def quote(text):
    return "\n".join("> " + line if line else ">" for line in (text or "(no draft)").splitlines())


total = sum(r["costTokens"] for r in reports)
lines = [
    f"# Draft replies, two prompt versions side by side ({len(reports)} recent {repo} issues)",
    "",
    "The same 30 issues as docs/m4-scoring.md. For each issue there are two drafts, **A** and **B**, written by two versions",
    "of the draft prompt; which is which changes from issue to issue, and the key is at the end. Score each draft",
    "**useful / partly / wrong** before looking at the key.",
    "",
    f"New reports made with `{reports[0]['llmModel']}` by `scripts/m4-scoring-v3.py`; {total:,} tokens in total "
    f"(${total / 1e6 * 0.10:.3f} to ${total / 1e6 * 0.50:.3f}).",
    "",
    "| # | Issue | Top match | Draft A | Draft B | Notes |",
    "|---|---|---|---|---|---|",
]
for i, r in enumerate(reports, 1):
    top = r["duplicates"][0] if r["duplicates"] else None
    top_text = f"[#{top['number']}]({top['url']}) {top['score']:.0%}" if top else "-"
    title = r["title"].replace("|", "\\|")[:60]
    lines.append(f"| {i} | [#{r['number']}]({r['url']}) {title} | {top_text} |  |  |  |")
lines.append("")

key = []
for i, r in enumerate(reports, 1):
    v3 = (r["draftReply"]["status"].lower(), r["draftReply"]["text"])
    v2 = v2_drafts[r["number"]]
    first, second = (v3, v2) if a_is_v3(r["number"]) else (v2, v3)
    key.append(f"{i}: A = {'v3' if a_is_v3(r['number']) else 'v2'}")
    lines += [f"## {i}. [#{r['number']}]({r['url']}) {r['title']}", ""]
    if r["duplicates"]:
        d = r["duplicates"][0]
        lines += [f"Top match: [#{d['number']}]({d['url']}) {d['title']} ({d['score']:.1%})", ""]
    lines += [f"**Draft A ({first[0]}):**", "", quote(first[1]), "", f"**Draft B ({second[0]}):**", "", quote(second[1]), ""]

lines += ["---", "", "<details><summary>Key (open after scoring)</summary>", "", "; ".join(key), "", "</details>", ""]
with open("docs/m4-scoring-v3.md", "w") as f:
    f.write("\n".join(lines))
print(f"wrote docs/m4-scoring-v3.md: {len(reports)} issues, {total} tokens", file=sys.stderr)
