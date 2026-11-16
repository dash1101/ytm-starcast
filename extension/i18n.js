/* YTM Starcast extension: its words in the language chosen in Starcast's settings (or the browser's own).
 *
 * Text is written in English in the scripts, inside tr() (trn() with the English for one and for more, for a count; trKey() marks
 * a word kept in a table, translated where it is shown); i18n_strings.js holds the translations, built from i18n/ by
 * tools/i18n.py. The language setting is "auto" (the browser's language) or a language's code; the extension hears it
 * from YTM Starcast and keeps it, so the next page starts in it at once.
 */
(function (scope) {
  "use strict";
  const KEY = Symbol.for("pocketfader.i18n");
  if (scope[KEY]) return;
  const LANGS = [["en", "English"], ["es", "Español"], ["fr", "Français"], ["de", "Deutsch"], ["pt-BR", "Português (Brasil)"],
    ["it", "Italiano"], ["ru", "Русский"], ["ja", "日本語"], ["ko", "한국어"], ["zh-CN", "简体中文"]];
  const CODES = LANGS.map((l) => l[0]);
  const STORE = "pocketfader.lang";
  let pref = "auto", lang = "en", table = {};
  const listeners = new Set();

  /** The language to use for a setting: the one chosen, or for "auto" the first of the browser's languages there is. */
  function resolve(p, wanted) {
    if (CODES.includes(p)) return p;
    const list = wanted || (scope.navigator && (navigator.languages || [navigator.language])) || [];
    for (const w of list) {
      const s = String(w || "").toLowerCase();
      const hit = CODES.find((c) => c.toLowerCase() === s) || CODES.find((c) => c.split("-")[0].toLowerCase() === s.split("-")[0]);
      if (hit) return hit;
    }
    return "en";
  }

  /** The plural form a whole number takes in a language (CLDR categories). */
  function plural(code, n) {
    n = Math.abs(Math.trunc(Number(n) || 0));
    if (code === "ja" || code === "ko" || code === "zh-CN") return "other";
    if (code === "ru") {
      const a = n % 10, b = n % 100;
      if (a === 1 && b !== 11) return "one";
      if (a >= 2 && a <= 4 && (b < 12 || b > 14)) return "few";
      return "many";
    }
    if (code === "fr" || code === "pt-BR") return n <= 1 ? "one" : "other";
    return n === 1 ? "one" : "other";
  }

  const fill = (s, v) => v ? s.replace(/\{([A-Za-z_]\w*)\}/g, (m, k) => (k in v && v[k] != null ? String(v[k]) : m)) : s;

  /** The text in the language in use; {name}s in it are filled from vars. */
  function tr(text, vars) {
    const x = table[text];
    return fill(typeof x === "string" ? x : String(text), vars);
  }

  /** A count: "one" is the English for 1, "other" the English for any other number; {n} is the number. */
  function trn(one, other, n, vars) {
    const x = table[other];
    let s = x && typeof x === "object" ? (x[plural(lang, n)] ?? x.other) : null;
    if (typeof s !== "string") s = Number(n) === 1 ? one : other;
    return fill(s, { n, ...vars });
  }

  const trKey = (text) => text;

  function strings() {
    return scope[Symbol.for("pocketfader.i18n.strings")] || {};
  }

  /** Use a language setting ("auto" or a code). Calls the listeners when the language in use changes. */
  function set(p) {
    p = CODES.includes(p) ? p : "auto";
    const before = lang;
    pref = p;
    lang = resolve(p);
    table = lang === "en" ? {} : strings()[lang] || {};
    try { scope.localStorage && localStorage.setItem(STORE, p); } catch (e) { /* storage blocked: start from "auto" next time */ }
    if (lang !== before) for (const fn of [...listeners]) { try { fn(lang); } catch (e) { /* one listener's trouble stays its own */ } }
    return lang;
  }

  /** Translates static markup: elements with data-tr have their text translated, and data-tr-attr="title aria-label"
   *  names attributes to translate. The English is kept, so this can run again after the language changes. */
  function translateDom(root) {
    root = root || scope.document;
    if (!root || !root.querySelectorAll) return;
    for (const el of root.querySelectorAll("[data-tr]")) {
      if (el.dataset.trSrc == null) el.dataset.trSrc = el.textContent;
      el.textContent = tr(el.dataset.trSrc);
    }
    for (const el of root.querySelectorAll("[data-tr-attr]")) {
      for (const a of el.dataset.trAttr.split(/\s+/).filter(Boolean)) {
        const k = "trSrc" + a.replace(/(^|-)(\w)/g, (m, d, c) => c.toUpperCase());
        if (el.dataset[k] == null) el.dataset[k] = el.getAttribute(a) || "";
        if (el.dataset[k]) el.setAttribute(a, tr(el.dataset[k]));
      }
    }
  }

  let start = "auto";
  try { start = (scope.localStorage && localStorage.getItem(STORE)) || "auto"; } catch (e) { /* see set() */ }
  pref = CODES.includes(start) ? start : "auto";
  lang = resolve(pref);
  table = lang === "en" ? {} : strings()[lang] || {};

  // Inside YouTube Music the extension's isolated script passes the setting on as YTM Starcast says it ({__vmr: "lang"}).
  if (scope.addEventListener && scope.document) {
    scope.addEventListener("message", (ev) => {
      const d = ev.source === scope ? ev.data : null;
      if (d && d.__vmr === "lang" && typeof d.lang === "string") set(d.lang);
    });
  }

  scope[KEY] = Object.freeze({
    LANGS, tr, trn, trKey, set, resolve, plural, translateDom,
    get lang() { return lang; },
    get pref() { return pref; },
    onChange(fn) { listeners.add(fn); return () => listeners.delete(fn); },
  });
})(typeof globalThis !== "undefined" ? globalThis : self);
