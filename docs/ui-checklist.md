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
- Round 10 (sai: list column ~460px on laptops, at least 3 filters visible, scrollbar only on hover, full title on hover):
  column `clamp(340px, 34vw, 460px)`: 460px at 1470 (report 851px), 435px at 1280 (report 686px); filters back to one row,
  4 of 5 visible, scrollbar hidden until hover or keyboard focus; full title as a tooltip. Measured: Spring Boot titles
  average 88 characters and the median needs 574px, so only 9% fit one line at 460px; "most fit" would need a ~660px column.
- Round 11 (sai): Reports list titles on 2 lines, cut with "…" after 2; the full-title tooltip stays.
- Round 12 (sai: bigger step text, bigger picture box, bigger "Who it's for" text): step titles 28px in the display font (22px on phones), descriptions 18px, numbers 40px; the picture box takes more width (1.25fr), 36px padding, larger rows, issue card, reply and tag; persona text 18px, headings 26px. Phone sizes kept smaller so the pinned picture doesn't cover the steps.
- Round 13 (sai: logo further right, links further left, nicer spacing): the header sits in the landing page's centred 1160px box (theme.css, so every page matches); 28px between links. Measured on /, /app.html, /guide.html at 1470px: logo 168px (same as the landing headline), links from 997px, toggle ends at 1288px.
- Round 12, update (sai's exact sizes): step titles 32px (24px on phones), step text 18px, circles 40px; the picture box 560 x 480px with its content scaled up (rows 20px, issue title 26px, tag word 40px, reply 20px); Who it's for titles 24px, text and bullets 18px (phones too). Measured at 1470px and 375px.
- Round 13, update (sai): header content in a centred 1200px column with 40px sides on wide screens (phones keep 20px); 32px between links. Measured at 1470px on /, /app.html, /guide.html: logo 168px = landing headline, links from 985px, toggle ends 1288px = where the landing content ends. Phone unchanged (logo 20px, toggle ends 355px).
- Round 14 (Dosu plan, row 1): the landing hero has a live triage window that types what the assistant did for one real report (issue opened, compared with 43,810 earlier issues, best match and score, what's missing, draft ready), then the tag swings in; reduced motion shows it all at once. Reviewer agent found: the console grew while typing (wrapping lines pushed the hero down) -> its final height is measured and reserved first, title cut to 40 characters; the cursor blinked forever -> removed when typing ends. Also fixed (sai): step 4 of How to use never became active on laptops -> it activates once the end of the steps reaches the bottom of the picture. Tester agent: 267 tests and static checks pass.
- Round 15 (Dosu plan, row 2): animated comparison bars at the end of How it helps with the replay's real numbers (original in the top 3: TriageTag 46.2% vs keyword 26.5%; top 10: 60.9% vs 37.1%). Bars rest at their real width and grow from zero when scrolled into view (reduced motion: no animation). Reviewer agent: the copy said 'for every issue ... closed as a duplicate', but 203 pairs were excluded -> now 'for 833 issues ... closed as duplicates of an earlier issue'; phone labels got their own column width; a class clash (.who) moved the labels -> renamed .lbl. Also (sai): step 4 of How to use: 540px of room after it, the section-bottom fallback, and a page-bottom + half-visible fallback. Step order checked with scripts/steps-order-check.js: 1470px and 375px both 'down 1 2 3 4, up 4 3 2 1'. The tester now requires that check. Tests 267/267, static checks pass.
- Round 16 (Dosu plan, row 3): a marker rides the end of the How to use line (behind the step numbers), and each step's picture plays a short entrance when it becomes active (repo rows slide in one by one, the issue drops in, the tag swings, the reply and buttons slide in); all off under reduced motion. Reviewer: PASS; took its optional note: the marker is ink, not amber (amber means "possible duplicate"). Tester: PASS (267 tests, static checks, step order 1 2 3 4 / 4 3 2 1 at 1470px and 375px; no sideways scroll at both widths).
- Round 17 (Dosu plan, row 4): four number tiles in the closing section that rise in one after another and count up: 833 duplicates tested; 46.2% found the original in the top 3 (keyword 26.5%); $0.001 or less AI cost per report; 13 ms to answer GitHub (p95, 500 test webhooks). Equal tiles, not bars of different heights (different units). Reviewer: "under $0.001" overstated the README's $0.0002-0.001 -> "$0.001 or less" (and the Who-it's-for line), "500 test webhooks", count-up always formats in en-US. Tester failed the first run because step order wasn't re-checked on the new page -> re-run: 1470px and 375px both down 1 2 3 4 / up 4 3 2 1; then PASS. Dosu plan complete.
- Round 18 (sai: wording): "private note(s)" replaced with "not posted on GitHub" wherever the pages implied only the maintainer can see a report (landing hero and Who it's for, guide lead/heading/bullets/trial, user guide, README). Reviewer: the Who-it's-for line read as always true though comment mode exists -> "Not posted on GitHub by default: ... unless you switch comments on"; the guide now says anyone with the link can read the reports. Tester: PASS (267 tests, static checks, no "private" left in the pages); step order re-run on the final page: 1470px and 375px down 1 2 3 4 / up 4 3 2 1.
- Round 19 (sai's spec, sent as "Round 18"): landing hero is centred, with the headline and description up to 720px wide, and the terminal on the left and the "Possible duplicate" tag on the right below them (stacked on phones, terminal first). The terminal dots use the Mac colours #FF5F57, #FEBC2E and #28C840. Instrument Sans (Google Fonts) is used for all headings and text on every page; IBM Plex Mono is used only in the terminal, and issue numbers now use the text font with tabular figures. The light-mode glow is a third of the pushed strength (dark is unchanged). The hard edge between the hero and "Less time sorting issues" is now a 160px fade from the page background, in both modes. Measured at 1470px: no sideways scroll; the header on /, /app.html and /guide.html has the logo at 168px, links from 981px (the narrower font moves the right-aligned links 4px) and the toggle ending at 1288px. At 375px: header 20 to 355px, sample in one column, no sideways scroll. Step order at 1470px and 375px: down 1 2 3 4, up 4 3 2 1. Reviewer: PASS, with one dead margin rule removed. Tester: the first run failed because DemoPageTest still expected the old fonts; the test was updated and static checks were added (Instrument Sans on every page, monospace only in the terminal).
