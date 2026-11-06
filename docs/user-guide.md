# User guide (for maintainers)

The guide lives in the app, so it's always next to the reports: **`/guide.html`** (on a local run:
http://localhost:8080/guide.html; linked as "How to use" in the page header).

In short:
- **Reports page** (`/`): choose an issue on the left (tab *Triaged*, tab *All issues* with title search, or type a number).
  The triage tag answers "is this a duplicate?" in words: **Likely duplicate** (≥ 90% and the AI check agrees),
  **Possible duplicate** (≥ 75%, or ≥ 90% without the AI's agreement), **Probably new**.
- Below it: similar earlier issues (bars, open/closed state, an "Already fixed?" box when a match was fixed), the details the
  reporter gave (✓ given, ✗ missing, – not needed), suggested labels, and a draft reply with **Copy reply**.
- Every report has its own link (`?repo=…&number=…`).
- Private by default: nothing is posted on GitHub unless a maintainer switches comment mode on for the repository.
- Creating new reports from the page needs the admin token (the *Admin* box at the bottom left).
