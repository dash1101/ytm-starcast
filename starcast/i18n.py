"""YTM Starcast's words in other languages, for what the computer shows itself (the desktop window).

Text is written in English in the code, inside tr() (trn() with the English for one and for more, for a count; trKey() marks a word
kept in a table, translated where it is shown); {name}s in it are filled from keyword arguments. The translations are
starcast/lang/<code>.json, built from the repository's i18n/ folder by tools/i18n.py. The setting ("language" in the
server's prefs) is "auto" (the computer's own language) or a language's code, and is shared with the apps.
"""
import json
import locale
import os
import re
import threading
from pathlib import Path

LANGUAGES = (("en", "English"), ("es", "Español"), ("fr", "Français"), ("de", "Deutsch"), ("pt-BR", "Português (Brasil)"),
             ("it", "Italiano"), ("ru", "Русский"), ("ja", "日本語"), ("ko", "한국어"), ("zh-CN", "简体中文"))
CODES = tuple(c for c, _ in LANGUAGES)
SETTINGS = ("auto",) + CODES
FOLDER = Path(__file__).parent / "lang"
_PLACEHOLDER = re.compile(r"\{([A-Za-z_][A-Za-z0-9_]*)\}")
_lock = threading.Lock()
_code, _table = "en", {}


def validate(value):
    """A language setting from a request -> itself, or ValueError."""
    if value not in SETTINGS:
        raise ValueError("language must be auto or one of " + ", ".join(CODES))
    return value


def system_languages():
    """The computer's languages, most wanted first (LANGUAGE, LC_ALL, LC_MESSAGES, LANG, then the locale)."""
    out = []
    for var in ("LANGUAGE", "LC_ALL", "LC_MESSAGES", "LANG"):
        for part in (os.environ.get(var) or "").split(":"):
            part = part.split(".")[0].split("@")[0]
            if part and part not in ("C", "POSIX"):
                out.append(part.replace("_", "-"))
    try:
        loc = locale.getlocale()[0]
        if loc:
            out.append(loc.replace("_", "-"))
    except Exception:
        pass
    return out


def resolve(pref, wanted=None):
    """The language for a setting: the one chosen, or for "auto" the first of the wanted languages there is."""
    if pref in CODES:
        return pref
    for w in (system_languages() if wanted is None else wanted):
        s = str(w).replace("_", "-").lower()
        for c in CODES:
            if c.lower() == s:
                return c
        for c in CODES:
            if c.split("-")[0].lower() == s.split("-")[0]:
                return c
    return "en"


def plural(code, n):
    """The plural form a whole number takes in a language (CLDR categories)."""
    n = abs(int(n))
    if code in ("ja", "ko", "zh-CN"):
        return "other"
    if code == "ru":
        a, b = n % 10, n % 100
        return "one" if a == 1 and b != 11 else "few" if 2 <= a <= 4 and not 12 <= b <= 14 else "many"
    if code in ("fr", "pt-BR"):
        return "one" if n <= 1 else "other"
    return "one" if n == 1 else "other"


def use(pref, wanted=None):
    """Use a language setting; returns the language now in use."""
    global _code, _table
    code = resolve(pref, wanted)
    table = {}
    if code != "en":
        try:
            table = json.loads((FOLDER / f"{code}.json").read_text(encoding="utf-8"))
        except (OSError, ValueError):
            table = {}
    with _lock:
        _code, _table = code, table
    return code


def current():
    return _code


def _fill(s, kw):
    return _PLACEHOLDER.sub(lambda m: str(kw[m.group(1)]) if kw.get(m.group(1)) is not None else m.group(0), s) if kw else s


def tr(text, **kw):
    """text (English) in the language in use; {name}s in it are filled from the keyword arguments."""
    x = _table.get(text)
    return _fill(x if isinstance(x, str) else text, kw)


def trn(one, other, n, **kw):
    """A count: one is the English for 1, other the English for any other number; {n} in them is n."""
    x = _table.get(other)
    s = (x.get(plural(_code, n)) or x.get("other")) if isinstance(x, dict) else None
    if not isinstance(s, str):
        s = one if n == 1 else other
    return _fill(s, {"n": n, **kw})


def trKey(text):  # noqa: N802 (the same name in every part, so one tool finds them all)
    """Marks English kept in a table as text to translate; tr() it where it is shown."""
    return text
