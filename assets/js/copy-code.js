/* Copy-to-clipboard buttons for the DDD + OTel workshop.
 *
 * No framework, no plugin, GitHub-Pages-safe. Adds a "Copy" button to every
 * Rouge code block (div.highlighter-rouge / div.highlight / figure.highlight),
 * including blocks that codetabs.js later moves into tab panels -- the button is
 * attached to the block element itself, so it travels with the block. Loaded
 * with `defer` AFTER codetabs.js so ordering is deterministic either way.
 */
(function () {
  "use strict";

  function codeText(block) {
    // Prefer the <code> inside .highlight pre; fall back to the block text.
    var pre = block.querySelector("pre");
    var code = pre ? (pre.querySelector("code") || pre) : block;
    // Rouge adds a trailing newline; trim it so paste is clean.
    return code.textContent.replace(/\n+$/, "");
  }

  function copy(text, btn) {
    var done = function (ok) {
      btn.classList.toggle("is-copied", ok);
      btn.textContent = ok ? "Copied" : "Copy failed";
      window.setTimeout(function () {
        btn.classList.remove("is-copied");
        btn.textContent = "Copy";
      }, 1600);
    };
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(function () { done(true); },
        function () { done(false); });
    } else {
      // Legacy fallback.
      try {
        var ta = document.createElement("textarea");
        ta.value = text;
        ta.setAttribute("readonly", "");
        ta.style.position = "absolute";
        ta.style.left = "-9999px";
        document.body.appendChild(ta);
        ta.select();
        document.execCommand("copy");
        document.body.removeChild(ta);
        done(true);
      } catch (_) { done(false); }
    }
  }

  function decorate(block) {
    if (block.getAttribute("data-copy") === "on") return; // idempotent
    block.setAttribute("data-copy", "on");
    var btn = document.createElement("button");
    btn.type = "button";
    btn.className = "copy-code-btn";
    btn.textContent = "Copy";
    btn.setAttribute("aria-label", "Copy code to clipboard");
    btn.addEventListener("click", function () { copy(codeText(block), btn); });
    block.appendChild(btn);
  }

  document.addEventListener("DOMContentLoaded", function () {
    var blocks = document.querySelectorAll(
      "div.highlighter-rouge, div.highlight, figure.highlight");
    Array.prototype.forEach.call(blocks, function (b) {
      // If a .highlighter-rouge wraps a .highlight, decorate only the outer one.
      if (b.classList.contains("highlight") &&
          b.parentNode && b.parentNode.classList &&
          b.parentNode.classList.contains("highlighter-rouge")) return;
      decorate(b);
    });
  });
})();
