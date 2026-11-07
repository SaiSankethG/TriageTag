# TriageTag user guide (for maintainers)

The guide lives in the app, so it's always next to the reports: **`/guide.html`** (on a local run:
http://localhost:8080/guide.html; linked as "How to use" in the page header).

In short:
- **Landing page** (`/`): a live sample report, what it does, the four steps, and who it's for. **Check issues** opens the reports.
- **Reports page** (`/app.html`): pick the repository at the top, then an issue on the left. The *Triaged* list puts open issues
  first; each line has one of four words and a one-line why, and you can filter by word or tick *Open issues only*.
  The words: **Likely duplicate** (≥ 90% and the AI check agrees), **Possible duplicate** (≥ 75%, or ≥ 90% without the AI's
  agreement), **Needs info** (not a duplicate, but details are missing), **Looks new**.
- A report, top to bottom: the tag (word, why, the % in small type, only the missing details), the buttons (**Copy reply**,
  **Copy "Duplicate of #N"**, **Open on GitHub**), the draft reply, the issue and its best match side by side, other similar
  issues (folded), and **Details** (AI check, model, tokens). On a phone the report comes first.
- Every report has its own link (`?repo=…&number=…`).
- Not posted on GitHub by default: nothing appears on GitHub unless a maintainer switches comment mode on for the repository. Reports are on this site, readable by anyone with the link.
- The public pages only read. Creating a report needs the admin token, on `/admin.html`, which the Ingress does not route
  (local run or port-forward only).
