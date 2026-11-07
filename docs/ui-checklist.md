# UI checklist (the stop condition for the UI loop)

The UI is "stable" when every box below is ticked on all four pages (`/`, `/app.html`, `/guide.html`, `/admin.html`),
checked at 1470px and 375px wide, in light and dark mode. Each loop round fixes the first unticked item, re-checks,
commits locally (never pushes), and records what changed under "Log".

## Must pass (measured, not judged)
- [x] No sideways scroll at 375px and 1470px (`scrollWidth == clientWidth`).
- [x] Text contrast at least 4.5:1 everywhere, including over glows, washes and grain (computed, worst case).
- [x] Header in the same place on every page (logo and links at the same x, measured).
- [x] No console errors on any page; the page still works if `/repos` or `/settings` fails.
- [x] Every animation is off under `prefers-reduced-motion: reduce`.
- [x] Keyboard: every control reachable with Tab, visible focus ring, Enter/Space work.
- [x] `./mvnw test` green.

## Must pass (checked by eye on screenshots)
- [x] Reports list: one line per issue, priority order, "Look at this first" on the top issue, reason on select/hover.
- [x] Report: tag (word big, % small) -> buttons -> draft -> side by side -> other matches -> Details; nothing overlaps.
- [x] Landing: hero, How it helps, How to use (rail fills on scroll, picture pinned and sized to content), Who it's for,
      repositories, closing; each section visibly different.
- [x] Empty, loading and error states have a clear sentence and a next step (no blank areas).
- [x] Nothing cut off: no truncated labels except intentional one-line titles with "...".
- [x] Design rules (sai, 7 Nov): the triage tag is the one bold thing; no ALL-CAPS labels; no "→" on buttons; no small
      monospace labels; not every box the same card and shadow.
- [x] The first screen of every page says what to do next.
- [x] Wording: posting "Duplicate of #N" marks the issue as a duplicate; it does not close it.

## Out of scope for the loop
- New features, new pages, copy rewrites, or anything sai hasn't asked for.
- Pushing to GitHub (sai reviews screenshots and says "push").

## Log
Measured with an in-page audit script (overflow, contrast against the real background, ALL-CAPS, arrows on buttons,
small monospace, identical card styles, header position, console errors) on `/`, `/app.html` (welcome and a report),
`/guide.html`, `/admin.html`, at 1470px and 375px, light and dark.

- Round 1 (1470px dark): issue numbers in monospace at 12.9-13.8px (rule: no small monospace) -> numbers keep the text size,
  and in small text they use the text font. Four report sections were the same card -> only the draft stays a boxed card;
  the comparison, other matches and Details are plain sections with a rule. "Duplicate of #N" said it closes the issue
  (reports, guide, landing picture) -> "marks the issue as a duplicate (it stays open)". Guide's first screen had no next
  step -> "Read on, or open the reports and pick an issue." Phone list status -> "Pick an issue to see its report."
- Round 2 (1470px light, all pages): clean. The header "low contrast" was a false alarm: `color-mix()` computes to
  `color(srgb 0-1 ...)`, which the script read as near-black; the real ratio is about 14:1.
- Round 3 (375px): reports page scrolled sideways -> the one-row filters (638px) widened the grid column; fixed with
  `minmax(0, 1fr)` and `min-width: 0` on the card. Guide header squeezed "How to use" onto two lines -> header wraps like the
  other pages. A swinging tag's corners briefly widened the page -> `overflow-x: clip` on html/body (clip keeps sticky
  working). On phones the "How to use" picture was pinned mid-screen over the steps -> the phone rule lost to a later
  desktop rule; raised its specificity, now pinned at the top (8px).
- Round 4 (keyboard, pressed for real): Tab reaches every control in order with a visible ring, but Enter on a filter lost
  focus (the row is redrawn) -> focus goes back to the chosen filter. Then Tab to an issue, Enter opens it, focus lands on
  the tag. Reduced motion is checked in the CSS and by a test (the preview browser can't switch that setting on).
- Round 5 (375px and 1470px, light and dark): all measured checks pass, no console errors, tests green. Stop.
- Round 6 (sai: "this is too long"): steps took 93% of the screen each and the picture started mid-screen, far below step 1
  -> 50% per step (40% on phones), picture pinned 96px from the top so it lines up with the steps. Section 2457px -> 1559px.
- Round 7 (sai: section about 2 screens; the picture lines up with the active step; same on phone): kept 50% per step (the
  section is 1.9 screens on a laptop, 2.2 on a phone; 100% per step would have been 4 screens). A step now becomes active
  when it reaches the picture's top edge (desktop: step top 84px, picture top 96px) or comes out just under the picture
  (phones), so the active step and the picture always line up.
- Round 8 (sai: no light/dark toggle; "the header keeps changing" on How to use): sai's screenshots were old cached files
  (the guide had no "open the reports" line), so the server now sends `Cache-Control: no-cache` for pages, theme.css and
  theme.js: browsers re-check every load. Added a light/dark toggle (sun/moon) to the header on every page; the choice is
  remembered in the browser (theme.js, localStorage) and overrides the system setting both ways. Header measured identical on
  /, /app.html, /guide.html: logo 24px, Home 1175px, toggle 1397px.
- Round 9 (sai: "increase the width so it's usable"): the list card was 310px, so titles were cut after ~20 characters and
  filters hid off the edge -> card is 28% of the screen, between 340px and 440px (412px on a 1470px laptop); filter labels
  shortened (Likely, Possible, Needs info, Looks new; full word on hover). Five filters with counts need ~515px, so they now
  wrap onto a second line instead of scrolling (reverses the earlier "one scrollable row").
