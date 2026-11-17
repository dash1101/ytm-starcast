#!/usr/bin/env python3
# YTM Starcast. Copyright (C) 2026 dash1101. SPDX-License-Identifier: GPL-3.0-or-later
# Additional terms under GPL section 7: see NOTICE.
"""YTM Starcast's translations: find the text to translate in the source and build each part's language files.

Every piece of text a person reads is written in English in the source, inside tr("...") (or trn("one", "other", n) for
a count, or trKey("...") to mark a word kept in a table and translated where it is shown). That English is the key: the
translations live in i18n/<language>.json, one file per language, mapping each English text to its translation. For a
count the key is the "other" form and the translation is an object of plural forms ({"one": ..., "other": ...}; Russian
also has "few" and "many", Japanese, Korean and Chinese only "other").

    python3 tools/i18n.py extract    # write i18n/source.json: every text, where it is used, and what is missing
    python3 tools/i18n.py build      # write each part's files from i18n/*.json (run after changing either)
    python3 tools/i18n.py check      # exit 1 when a file is out of date or a translation is missing (the tests run this)
    python3 tools/i18n.py list FILE  # the texts marked in some files (writes nothing)

See docs/TRANSLATING.md.
"""
import ast
import glob
import html.parser
import json
import os
import re
import sys
from pathlib import Path
from xml.sax.saxutils import escape as xml_escape

ROOT = Path(__file__).resolve().parent.parent
I18N = ROOT / "i18n"

# The languages besides English, with the plural forms each needs (CLDR categories, whole numbers only)
LANGS = {"es": ("one", "other"), "fr": ("one", "other"), "de": ("one", "other"), "pt-BR": ("one", "other"),
         "it": ("one", "other"), "ru": ("one", "few", "many", "other"), "ja": ("other",), "ko": ("other",),
         "zh-CN": ("other",)}
# Chrome's _locales folder names, and Android's res/values-<x> qualifiers
CHROME = {"es": "es", "fr": "fr", "de": "de", "pt-BR": "pt_BR", "it": "it", "ru": "ru", "ja": "ja", "ko": "ko", "zh-CN": "zh_CN"}
ANDROID = {"es": "es", "fr": "fr", "de": "de", "pt-BR": "pt", "it": "it", "ru": "ru", "ja": "ja", "ko": "ko", "zh-CN": "zh"}

PLACEHOLDER = re.compile(r"\{([A-Za-z_][A-Za-z0-9_]*)\}")

# ---------------------------------------------------------------- finding the text
_JS_LIT = r"""'(?:[^'\\\n]|\\.)*'|"(?:[^"\\\n]|\\.)*"|`(?:[^`\\$]|\\.|\$(?!\{))*`"""
_KT_LIT = r'"(?:[^"\\\n$]|\\.|\$(?![{A-Za-z_]))*"'
_PY_LIT = r"""'(?:[^'\\\n]|\\.)*'|"(?:[^"\\\n]|\\.)*\""""
_SEP = r"\s*,\s*"


def _calls(lit):
    pre = r"(?<![\w.$])"
    return (re.compile(pre + r"(?:tr|trKey)\(\s*(" + lit + r")\s*[,)]"),
            re.compile(pre + r"trn\(\s*(" + lit + ")" + _SEP + "(" + lit + ")" + _SEP))


_ESC = re.compile(r"\\(u\{[0-9a-fA-F]+\}|u[0-9a-fA-F]{4}|x[0-9a-fA-F]{2}|\n|.)", re.S)
_SIMPLE = {"n": "\n", "t": "\t", "r": "\r", "b": "\b", "f": "\f", "v": "\v", "0": "\0", "\n": ""}


def decode_c_like(lit):
    """A JavaScript or Kotlin string literal (quotes included) -> its text."""
    body = lit[1:-1]

    def one(m):
        e = m.group(1)
        if e.startswith("u{"):
            return chr(int(e[2:-1], 16))
        if e[0] in "ux" and len(e) > 1:
            return chr(int(e[1:], 16))
        return _SIMPLE.get(e, e)
    return _ESC.sub(one, body)


def decode_py(lit):
    return ast.literal_eval(lit)


class _Markup(html.parser.HTMLParser):
    """Static markup: an element with data-tr has its text translated; data-tr-attr="title aria-label" names the
    attributes translated."""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.found, self._open = [], []

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        for name in (a.get("data-tr-attr") or "").split():
            if a.get(name):
                self.found.append(a[name])
        if "data-tr" in a:
            self._open.append([tag, ""])
        elif self._open:
            self._open.append([tag, None])

    def handle_startendtag(self, tag, attrs):
        a = dict(attrs)
        for name in (a.get("data-tr-attr") or "").split():
            if a.get(name):
                self.found.append(a[name])

    def handle_endtag(self, tag):
        if self._open:
            t, text = self._open.pop()
            if text is not None and text.strip():
                self.found.append(text)

    def handle_data(self, data):
        if self._open and self._open[-1][1] is not None:
            self._open[-1][1] += data


SURFACES = {
    # the web app (one file: its script and its markup)
    "web": {"js": ["starcast/static/index.html"], "html": ["starcast/static/index.html"]},
    # the browser extension: its scripts (Refine, the toolbar popup, ...), the popup's markup and its manifest's words
    "ext": {"js": ["extension/*.js"], "html": ["extension/*.html"], "skip": ["extension/i18n_strings.js"],
            "chrome": "extension/_locales/en/messages.json"},
    # the Android app, and the names and descriptions of its home screen widgets
    "android": {"kt": ["android/app/src/main/java/**/*.kt"], "xml": "android/app/src/main/res/values/strings.xml"},
    # the desktop window and anything the server says that people read
    "py": {"py": ["starcast/**/*.py"]},
}


def _files(patterns, skip=()):
    out = []
    for p in patterns:
        out += sorted(glob.glob(str(ROOT / p), recursive=True))
    skip = {str(ROOT / s) for s in skip}
    return [Path(f) for f in out if f not in skip]


def extract():
    """-> {text: {"one": English singular or None, "where": set of parts, "files": set of file names}}"""
    found = {}

    def add(text, part, f, one=None):
        e = found.setdefault(text, {"one": None, "where": set(), "files": set(), "plain": set()})
        e["where"].add(part)
        e["files"].add(Path(f).name)
        if one is None:
            e["plain"].add(Path(f).name)
        else:
            if e["one"] not in (None, one):
                raise SystemExit(f"{text!r}: two different singular forms ({e['one']!r}, {one!r})")
            e["one"] = one

    for part, s in SURFACES.items():
        for kind, lit, dec in (("js", _JS_LIT, decode_c_like), ("kt", _KT_LIT, decode_c_like), ("py", _PY_LIT, decode_py)):
            single, plural = _calls(lit)
            for f in _files(s.get(kind, []), s.get("skip", ())):
                src = f.read_text(encoding="utf-8")
                for m in single.finditer(src):
                    add(dec(m.group(1)), part, f)
                for m in plural.finditer(src):
                    add(dec(m.group(2)), part, f, one=dec(m.group(1)))
        for f in _files(s.get("html", [])):
            p = _Markup()
            p.feed(f.read_text(encoding="utf-8"))
            for text in p.found:
                add(text, part, f)
        if "chrome" in s:
            for v in json.loads((ROOT / s["chrome"]).read_text(encoding="utf-8")).values():
                add(v["message"], part, s["chrome"])
        if "xml" in s:
            for v in _android_strings(ROOT / s["xml"]).values():
                add(v, part, s["xml"])
    for text, e in found.items():
        if not text.strip():
            raise SystemExit("an empty text is marked for translation")
        if e["one"] is not None and e["plain"]:
            raise SystemExit(f"{text!r} is a count (trn) in some places and a plain text (tr) in {sorted(e['plain'])}: "
                             "use trn everywhere, or a different text")
    return found


def _android_strings(path):
    import xml.etree.ElementTree as ET
    out = {}
    for el in ET.parse(path).getroot().findall("string"):
        if el.get("translatable") == "false":
            continue
        out[el.get("name")] = (el.text or "").replace("\\'", "'").replace('\\"', '"')
    return out


# ---------------------------------------------------------------- the translations
def load(lang):
    f = I18N / f"{lang}.json"
    return json.loads(f.read_text(encoding="utf-8")) if f.exists() else {}


def problems(found, tables=None):
    """Every missing or malformed translation, as text lines."""
    out = []
    for lang, forms in LANGS.items():
        table = tables[lang] if tables is not None else load(lang)
        for text, e in sorted(found.items()):
            want = set(PLACEHOLDER.findall(text)) | ({"n"} if e["one"] is not None else set())
            v = table.get(text)
            if v is None:
                out.append(f"{lang}: missing {text!r}")
                continue
            if e["one"] is None:
                if not isinstance(v, str):
                    out.append(f"{lang}: {text!r} should be a plain text")
                    continue
                vals = [v]
            else:
                if not isinstance(v, dict) or set(v) != set(forms) or not all(isinstance(x, str) for x in v.values()):
                    out.append(f"{lang}: {text!r} counts something: give the forms {', '.join(forms)}")
                    continue
                vals = list(v.values())
            for x in vals:
                have = set(PLACEHOLDER.findall(x))
                extra = have - want
                lost = (want - {"n"}) - have if e["one"] is not None else want - have
                if extra or lost:
                    out.append(f"{lang}: {text!r} -> {x!r}: placeholders {sorted(have)} should be {sorted(want)}")
    return out


def _subset(table, keys):
    return {k: table[k] for k in sorted(keys) if k in table}


def _dump(obj):
    return json.dumps(obj, ensure_ascii=False, indent=0, sort_keys=True, separators=(",", ":")) + "\n"


def outputs(found):
    """-> {path relative to ROOT: file content} for every built file."""
    tables = {lang: load(lang) for lang in LANGS}
    keys = {part: {t for t, e in found.items() if part in e["where"]} for part in SURFACES}
    out = {}
    for lang, table in tables.items():
        out[f"starcast/static/i18n/{lang}.json"] = _dump(_subset(table, keys["web"]))
        out[f"android/app/src/main/assets/i18n/{lang}.json"] = _dump(_subset(table, keys["android"]))
        out[f"starcast/lang/{lang}.json"] = _dump(_subset(table, keys["py"]))
    ext = {lang: _subset(t, keys["ext"]) for lang, t in tables.items()}
    out["extension/i18n_strings.js"] = (
        "// YTM Starcast. Copyright (C) 2026 dash1101. SPDX-License-Identifier: GPL-3.0-or-later\n"
        "// Additional terms under GPL section 7: see NOTICE.\n"
        "/* YTM Starcast extension: its words in each language. Built by tools/i18n.py from i18n/*.json; don't edit it */\n"
        'globalThis[Symbol.for("pocketfader.i18n.strings")] = '
        + json.dumps(ext, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + ";\n")
    en = json.loads((ROOT / SURFACES["ext"]["chrome"]).read_text(encoding="utf-8"))
    for lang, table in tables.items():
        msgs = {k: {"message": table.get(v["message"], v["message"]), **({"description": v["description"]} if "description" in v else {})}
                for k, v in en.items()}
        out[f"extension/_locales/{CHROME[lang]}/messages.json"] = json.dumps(msgs, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    strings = _android_strings(ROOT / SURFACES["android"]["xml"])
    for lang, table in tables.items():
        rows = []
        for name, en_text in strings.items():
            v = table.get(en_text)
            if isinstance(v, str) and v != en_text:
                v = xml_escape(v).replace("'", "\\'").replace('"', '\\"')
                rows.append(f'    <string name="{name}">{v}</string>')
        out[f"android/app/src/main/res/values-{ANDROID[lang]}/strings.xml"] = (
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!-- Built by tools/i18n.py from i18n/*.json; don't edit it -->\n"
            "<resources>\n" + "\n".join(rows) + ("\n" if rows else "") + "</resources>\n")
    out["i18n/source.json"] = json.dumps(
        [{"text": t, **({"one": e["one"]} if e["one"] is not None else {}), "where": sorted(e["where"]),
          "files": sorted(e["files"])} for t, e in sorted(found.items())],
        ensure_ascii=False, indent=1) + "\n"
    return out


def stale(found):
    return [p for p, content in outputs(found).items()
            if not (ROOT / p).exists() or (ROOT / p).read_text(encoding="utf-8") != content]


def main(argv):
    cmd = argv[1] if len(argv) > 1 else "check"
    if cmd == "list":
        names = {Path(a).name for a in argv[2:]}
        found = extract()
        rows = sorted((t, e) for t, e in found.items() if e["files"] & names)
        for t, e in rows:
            print(("trn " + json.dumps(e["one"], ensure_ascii=False) + " / " if e["one"] is not None else "") + json.dumps(t, ensure_ascii=False))
        print(f"-- {len(rows)} texts", file=sys.stderr)
        return 0
    found = extract()
    if cmd == "extract":
        (I18N / "source.json").write_text(outputs(found)["i18n/source.json"], encoding="utf-8")
        miss = problems(found)
        print(f"{len(found)} texts; {len(miss)} problems")
        for line in miss[:40]:
            print("  " + line)
        return 0
    if cmd == "build":
        for p, content in outputs(found).items():
            f = ROOT / p
            f.parent.mkdir(parents=True, exist_ok=True)
            if not f.exists() or f.read_text(encoding="utf-8") != content:
                f.write_text(content, encoding="utf-8")
                print("wrote " + p)
        return 0
    if cmd == "check":
        bad = problems(found) + [f"out of date: {p} (run tools/i18n.py build)" for p in stale(found)]
        for line in bad:
            print(line)
        return 1 if bad else 0
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
