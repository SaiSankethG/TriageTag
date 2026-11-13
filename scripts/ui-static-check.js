// Static checks for the TriageTag pages, run by the ui-tester agent after each UI round: `node scripts/ui-static-check.js`.
// Plain Node, no packages. Prints one PASS/FAIL line per check and exits 1 if anything failed.
// (Layout checks such as sideways scroll and contrast need a browser; the writer measures those in the preview.)
const fs = require('fs');
const path = require('path');

const dir = path.join(__dirname, '..', 'src', 'main', 'resources', 'static');
const pages = ['index.html', 'app.html', 'guide.html', 'admin.html'];
const read = (f) => fs.readFileSync(path.join(dir, f), 'utf8');
let failed = 0;
const check = (ok, what) => { console.log(`${ok ? 'PASS' : 'FAIL'}  ${what}`); if (!ok) failed++; };

const theme = read('theme.css');
for (const page of pages) {
  const html = read(page);
  check(html.includes("family=Geist:") && html.includes("family=Geist+Mono"), `${page}: loads Geist (headings and text) and Geist Mono (terminal)`);
  // monospace only where it means code: the landing terminal, and code blocks in a report's issue text (Round 21)
  check((html.match(/var\(--mono\)/g) || []).length === (page === "index.html" || page === "app.html" ? 1 : 0), `${page}: monospace only in the landing terminal and report code blocks`);
  check(!html.includes('innerHTML'), `${page}: no innerHTML (text goes in with textContent)`);
  check(!/<script src="http/.test(html), `${page}: no external scripts`);
  check(html.includes('<link rel="stylesheet" href="/theme.css">'), `${page}: links the shared theme.css`);
  check(html.includes('<script src="/theme.js"></script>'), `${page}: loads theme.js (light/dark choice)`);
  check(!/text-transform:\s*uppercase/.test(html), `${page}: no ALL-CAPS labels (text-transform: uppercase)`);
  check(!/[→»]/.test(html), `${page}: no "→" or "»" on buttons or links`);
  check(!/\b(localStorage)\b/.test(html), `${page}: no localStorage in the page (only theme.js may use it)`);
  check(!/:root\s*\{/.test(html), `${page}: no colour tokens of its own (they live in theme.css)`);
  check(!/lets GitHub close|closes the issue as a duplicate/i.test(html), `${page}: "Duplicate of #N" marks, it doesn't close`);
  // monospace text under 14px: a rule that sets the mono font and a pixel size below 14
  const small = [...html.matchAll(/\{[^}]*var\(--mono\)[^}]*font-size:\s*(\d+(?:\.\d+)?)px[^}]*\}/g)].filter(m => +m[1] < 14);
  check(small.length === 0, `${page}: no monospace text under 14px`);
}
check(/@media \(prefers-reduced-motion: reduce\)[\s\S]*animation: none !important/.test(theme), 'theme.css: all motion off under prefers-reduced-motion');
check(/:root\[data-theme="dark"\]/.test(theme) && /:root:not\(\[data-theme="light"\]\)/.test(theme), 'theme.css: dark colours for both the system setting and the toggle');
check(!/text-transform:\s*uppercase/.test(theme), 'theme.css: no ALL-CAPS labels');
const landing = read('index.html');
check(/\.steps > li:last-child \{ min-height: 540px; \}/.test(landing), 'index.html: room after step 4 so it can reach the activation line');
check(landing.includes('if (atBottom && visible >= lastBox.height / 2) i = circles.length - 1;'), 'index.html: step 4 also activates at the page bottom when half visible');
const js = read('theme.js');
check(!/token/i.test(js), 'theme.js: stores only the light/dark choice');

console.log(failed ? `\n${failed} check(s) failed` : '\nAll static checks passed');
process.exit(failed ? 1 : 0);
