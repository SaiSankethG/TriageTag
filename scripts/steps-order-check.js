// Browser check for "How to use it" on the landing page: do all four steps become active in order, scrolling down and
// back up? Paste into the browser console (or run it with the preview tool) on http://localhost:8080/ at laptop width and
// at phone width. It scrolls the section in small steps, waits for the page to react, and prints the order it saw.
// Expected: down "1 2 3 4" and up "4 3 2 1", each step seen at least once, never out of order.
// In a hidden preview pane (no animation frames), first run:
//   window.requestAnimationFrame = (cb) => setTimeout(() => cb(performance.now()), 16);
(async () => {
  const how = document.getElementById('how');
  const active = () => document.querySelector('#steps [aria-current="step"] .n')?.textContent;
  const frame = () => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)));
  document.documentElement.style.scrollBehavior = 'auto';
  const top = how.offsetTop - innerHeight / 2, bottom = how.offsetTop + how.offsetHeight;
  const seen = (from, to, step) => {
    const out = [];
    return (async () => {
      for (let y = from; step > 0 ? y <= to : y >= to; y += step) {
        scrollTo(0, y); window.dispatchEvent(new Event('scroll')); await frame();
        const a = active(); if (out[out.length - 1] !== a) out.push(a);
      }
      return out.join(' ');
    })();
  };
  const down = await seen(top, bottom, 40);
  const up = await seen(bottom, top, -40);
  const ok = down === '1 2 3 4' && up === '4 3 2 1';
  console.log(`${ok ? 'PASS' : 'FAIL'}  steps down: ${down}  up: ${up}  (width ${innerWidth}px)`);
  return { ok, down, up, width: innerWidth };
})();
