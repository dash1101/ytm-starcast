# YTM Starcast. Copyright (C) 2026 dash1101. SPDX-License-Identifier: GPL-3.0-or-later
# Additional terms under GPL section 7: see NOTICE.
"""Languages: the shared setting on the server, the translation helpers in each part, and the translations themselves
(every marked text translated into every language, with the same placeholders; every part's files up to date)."""
import json
import re
import shutil
import subprocess
import sys
import threading
import time
import unittest
from pathlib import Path

from starcast import i18n

from .test_parts import YTM, Base

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import i18n as tool  # noqa: E402  (tools/i18n.py)

CODES = ["en", "es", "fr", "de", "pt-BR", "it", "ru", "ja", "ko", "zh-CN"]


class TranslationTests(unittest.TestCase):
    def test_every_text_is_translated_and_every_file_is_up_to_date(self):
        found = tool.extract()
        self.assertGreater(len(found), 100)
        bad = tool.problems(found)
        self.assertEqual(bad[:20], [], f"{len(bad)} translation problems (python3 tools/i18n.py extract lists them)")
        self.assertEqual(tool.stale(found), [], "run python3 tools/i18n.py build")

    def test_every_part_knows_the_same_languages(self):
        self.assertEqual([c for c, _ in i18n.LANGUAGES], CODES)
        self.assertEqual(["en", *tool.LANGS], CODES)
        names = [n for _, n in i18n.LANGUAGES]
        web = (ROOT / "starcast/static/index.html").read_text(encoding="utf-8")
        ext = (ROOT / "extension/i18n.js").read_text(encoding="utf-8")
        kt = (ROOT / "android/app/src/main/java/app/pocketfader/client/I18n.kt").read_text(encoding="utf-8")
        js_list = lambda src: re.findall(r"\[['\"]([\w-]+)['\"], ['\"]([^'\"]+)['\"]\]", re.search(r"LANGS = \[(.*?)\];", src, re.S).group(1))
        self.assertEqual(js_list(web), list(i18n.LANGUAGES))
        self.assertEqual(js_list(ext), list(i18n.LANGUAGES))
        self.assertEqual(re.findall(r'"([\w-]+)" to "([^"]+)"', kt), list(i18n.LANGUAGES))
        self.assertEqual(len(set(names)), len(names))

    def test_the_tool_reads_every_way_of_marking_text(self):
        src = {"js": r"""tr('It\'s {n}'); trn("{n} song", "{n} songs", x); trKey(`Back`); a.tr('no'); tr(`x ${y}`)""",
               "kt": r'''tr("Hi \"you\""); trn("{n} day", "{n} days", n, "a" to b); tr("Cost $x")''',
               "py": r'''tr("Window"); trn('{n} part', '{n} parts', 3)'''}
        got = {}
        for kind, lit, dec in (("js", tool._JS_LIT, tool.decode_c_like), ("kt", tool._KT_LIT, tool.decode_c_like),
                               ("py", tool._PY_LIT, tool.decode_py)):
            single, plural = tool._calls(lit)
            got[kind] = [dec(m.group(1)) for m in single.finditer(src[kind])] + \
                        [(dec(m.group(1)), dec(m.group(2))) for m in plural.finditer(src[kind])]
        self.assertEqual(got["js"], ["It's {n}", "Back", ("{n} song", "{n} songs")])
        self.assertEqual(got["kt"], ['Hi "you"', ("{n} day", "{n} days")])
        self.assertEqual(got["py"], ["Window", ("{n} part", "{n} parts")])
        p = tool._Markup()
        p.feed('<p data-tr>Checking&hellip;</p><b data-tr-attr="title" title="Open it">x</b><img data-tr-attr="alt" alt="Logo">')
        self.assertEqual(sorted(p.found), ["Checking…", "Logo", "Open it"])

    def test_the_tool_catches_missing_forms_and_placeholders(self):
        found = {"Hello {name}": {"one": None, "where": {"web"}}, "{n} songs": {"one": "{n} song", "where": {"web"}}}
        good = {"Hello {name}": "Hola {name}", "{n} songs": {"one": "{n} canción", "other": "{n} canciones"}}
        tables = {lang: {} for lang in tool.LANGS}
        tables["es"] = good
        bad = tool.problems(found, tables)
        self.assertFalse([b for b in bad if b.startswith("es:")])
        tables["es"] = {"Hello {name}": "Hola {nombre}", "{n} songs": "{n} canciones"}
        bad = [b for b in tool.problems(found, tables) if b.startswith("es:")]
        self.assertEqual(len(bad), 2)
        tables["ru"] = {"Hello {name}": "Привет, {name}", "{n} songs": {"one": "{n} песня", "other": "{n} песни"}}
        self.assertTrue(any("few" in b for b in tool.problems(found, tables) if b.startswith("ru:")))


class PythonHelperTests(unittest.TestCase):
    def tearDown(self):
        i18n.use("en")

    def test_resolve(self):
        self.assertEqual(i18n.resolve("de", ["fr"]), "de")
        self.assertEqual(i18n.resolve("auto", ["pt_PT", "en"]), "pt-BR")
        self.assertEqual(i18n.resolve("auto", ["zh-TW"]), "zh-CN")
        self.assertEqual(i18n.resolve("auto", ["sv", "ko-KR"]), "ko")
        self.assertEqual(i18n.resolve("auto", ["sv"]), "en")
        self.assertEqual(i18n.resolve("nonsense", ["ja"]), "ja")

    def test_plural_forms(self):
        self.assertEqual([i18n.plural("ru", n) for n in (1, 2, 5, 11, 21, 22, 25, 112)],
                         ["one", "few", "many", "many", "one", "few", "many", "many"])
        self.assertEqual([i18n.plural("fr", n) for n in (0, 1, 2)], ["one", "one", "other"])
        self.assertEqual([i18n.plural("en", n) for n in (0, 1, 2)], ["other", "one", "other"])
        self.assertEqual(i18n.plural("ja", 1), "other")

    def test_english_and_placeholders(self):
        i18n.use("en")
        self.assertEqual(i18n.tr("Playing on {device}", device="TV"), "Playing on TV")
        self.assertEqual(i18n.trn("{n} song", "{n} songs", 1), "1 song")
        self.assertEqual(i18n.trn("{n} song", "{n} songs", 0), "0 songs")
        self.assertEqual(i18n.tr("Left {x}"), "Left {x}")

    def test_a_language_from_its_file(self):
        self.assertEqual(i18n.use("de"), "de")
        table = json.loads((i18n.FOLDER / "de.json").read_text(encoding="utf-8"))
        for text, v in table.items():
            if isinstance(v, str) and "{" not in text:
                self.assertEqual(i18n.tr(text), v)
                break
        self.assertEqual(i18n.tr("not a text anywhere"), "not a text anywhere")

    def test_validate(self):
        for v in ("auto", *CODES):
            self.assertEqual(i18n.validate(v), v)
        for v in ("", "EN", "pt", None, 3, ["de"]):
            with self.assertRaises(ValueError):
                i18n.validate(v)


@unittest.skipUnless(shutil.which("node"), "needs Node.js")
class ExtensionHelperTests(unittest.TestCase):
    def run_js(self, body):
        script = """
// newer Node has navigator (and localStorage) of its own: replace them
Object.defineProperty(globalThis, "localStorage", { configurable: true, writable: true,
  value: { s: {}, getItem(k) { return this.s[k] ?? null; }, setItem(k, v) { this.s[k] = String(v); } } });
Object.defineProperty(globalThis, "navigator", { configurable: true, writable: true, value: { languages: ["ru-RU", "en"] } });
require(%s); require(%s);
const I = globalThis[Symbol.for("pocketfader.i18n")];
const out = [];
%s
console.log(JSON.stringify(out));
""" % (json.dumps(str(ROOT / "extension/i18n_strings.js")), json.dumps(str(ROOT / "extension/i18n.js")), body)
        r = subprocess.run(["node", "-e", script], capture_output=True, text=True, timeout=30)
        self.assertEqual(r.returncode, 0, r.stderr)
        return json.loads(r.stdout)

    def test_auto_follows_the_browser_and_a_choice_wins(self):
        out = self.run_js("""
out.push(I.pref, I.lang);
const seen = []; I.onChange((l) => seen.push(l));
out.push(I.set("ja"), I.set("ja"), I.set("bogus"), localStorage.getItem("pocketfader.lang"));
out.push(seen);
I.set("en");
out.push(I.trn("{n} song", "{n} songs", 1), I.trn("{n} song", "{n} songs", 2), I.tr("Hello {who}", { who: "A" }));
""")
        self.assertEqual(out, ["auto", "ru", "ja", "ja", "ru", "auto", ["ja", "ru", "en"], "1 song", "2 songs", "Hello A"])

    def test_translations_are_used(self):
        strings = json.loads((ROOT / "extension/i18n_strings.js").read_text(encoding="utf-8").split(" = ", 1)[1].rstrip(";\n"))
        de = strings["de"]
        text = next(k for k, v in de.items() if isinstance(v, str) and "{" not in k)
        plural = next((k for k, v in de.items() if isinstance(v, dict)), None)
        out = self.run_js("I.set('de'); out.push(I.tr(%s)); %s" % (json.dumps(text), (
            "out.push(I.trn('x', %s, 1), I.trn('x', %s, 7));" % (json.dumps(plural), json.dumps(plural)) if plural else "")))
        self.assertEqual(out[0], de[text])
        if plural:
            self.assertEqual(out[1:], [de[plural]["one"].replace("{n}", "1"), de[plural]["other"].replace("{n}", "7")])


class LanguageSettingTests(Base):
    def test_shared_setting(self):
        self.assertEqual(self.req("GET", "/api/prefs")[1]["language"], "auto")
        code, r, _ = self.req("POST", "/api/prefs", {"language": "ja"})
        self.assertEqual((code, r["language"]), (200, "ja"))
        self.assertEqual(self.req("GET", "/api/prefs")[1]["language"], "ja")
        self.assertEqual(self.req("GET", "/ext/status")[1]["language"], "ja")
        self.assertEqual(self.req("POST", "/ext/state", {"instance": "A", "now": {}}, {"Origin": YTM})[1]["cfg"]["lang"], "ja")
        for bad in ("klingon", "", None, 5, ["de"]):
            code, r, _ = self.req("POST", "/api/prefs", {"language": bad, "pauseOthers": True})
            self.assertEqual(code, 400, bad)
        self.assertEqual(self.req("GET", "/api/prefs")[1].get("pauseOthers"), None)   # a bad language refuses it all
        self.assertEqual(self.req("POST", "/ext/language", {"language": "auto"})[1], {"language": "auto"})
        self.assertEqual(self.req("POST", "/ext/language", {"language": "xx"})[0], 400)
        self.assertEqual(self.req("GET", "/api/prefs")[1]["language"], "auto")

    def test_a_stored_unknown_language_reads_as_auto(self):
        self.app.prefs.merge({"language": "tlh"})
        self.assertEqual(self.req("GET", "/api/prefs")[1]["language"], "auto")

    def test_the_extension_route_is_not_for_websites(self):
        code, _, _ = self.req("POST", "/ext/language", {"language": "de"}, {"Origin": "https://example.com"})
        self.assertEqual(code, 403)
        self.assertEqual(self.req("GET", "/api/prefs")[1]["language"], "auto")

    def test_a_change_reaches_a_waiting_poll_at_once(self):
        self.app.bridge.push_state({"instance": "A", "now": {}})
        got = {}

        def poll():
            got["r"] = self.req("GET", "/ext/poll?i=A", timeout=30)[1]
            got["at"] = time.time()
        t = threading.Thread(target=poll)
        t.start()
        time.sleep(0.2)
        start = time.time()
        self.assertEqual(self.req("POST", "/api/prefs", {"language": "fr"})[0], 200)
        t.join(5)
        self.assertFalse(t.is_alive())
        self.assertLess(got["at"] - start, 2.0)
        self.assertEqual(got["r"]["cfg"]["lang"], "fr")


if __name__ == "__main__":
    unittest.main()
