// Light/dark choice for every page. The page follows the system setting until the visitor presses the header toggle;
// the choice is then remembered in this browser (localStorage) and set as data-theme on <html>, which theme.css reads.
// Loaded in <head> so the saved theme applies before the page is drawn (no flash of the wrong colours).
(function () {
  var KEY = 'triagetag-theme';
  var root = document.documentElement;
  var saved = null;
  try { saved = localStorage.getItem(KEY); } catch (e) { /* storage blocked: follow the system setting */ }
  if (saved === 'light' || saved === 'dark') root.setAttribute('data-theme', saved);

  function isDark() {
    var chosen = root.getAttribute('data-theme');
    return chosen ? chosen === 'dark' : window.matchMedia('(prefers-color-scheme: dark)').matches;
  }
  function describe(button) {
    var label = isDark() ? 'Switch to light mode' : 'Switch to dark mode';
    button.setAttribute('aria-label', label);
    button.title = label;
  }
  document.addEventListener('DOMContentLoaded', function () {
    var button = document.querySelector('.theme-toggle');
    if (!button) return;
    describe(button);
    button.addEventListener('click', function () {
      var next = isDark() ? 'light' : 'dark';
      root.setAttribute('data-theme', next);
      try { localStorage.setItem(KEY, next); } catch (e) { /* not remembered, still switched */ }
      describe(button);
    });
  });
})();
