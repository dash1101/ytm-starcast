# Languages

YTM Starcast is in English, Español, Français, Deutsch, Português (Brasil), Italiano, Русский, 日本語, 한국어 and 简体中文.
The **Language** setting (in the web app, the Android app, the desktop window and Refine's All settings) is
shared through the computer: *Automatic* lets each device use its own language; choosing one uses it everywhere.

## How it works

Every piece of text a person reads is written in English in the source and marked for translation. That English text is
the key. `i18n/<code>.json` holds one language's translations: `{"English text": "translation"}`. `tools/i18n.py` finds the
marked text and builds each part's own files from those:

| Part | Marked in | Built files |
| --- | --- | --- |
| Web app | `starcast/static/index.html` | `starcast/static/i18n/<code>.json` |
| Browser extension (Refine, popup) | `extension/*.js`, `extension/popup.html`, `extension/_locales/en/messages.json` | `extension/i18n_strings.js`, `extension/_locales/*/messages.json` |
| Android app | `android/.../*.kt`, `res/values/strings.xml` | `assets/i18n/<code>.json`, `res/values-<x>/strings.xml` |
| Desktop window | `starcast/**/*.py` | `starcast/lang/<code>.json` |

```bash
python3 tools/i18n.py extract   # i18n/source.json: every text and where it is used; lists what is missing
python3 tools/i18n.py build     # rebuild every part's files after changing the source or i18n/*.json
python3 tools/i18n.py check     # what the tests run: nothing missing, nothing out of date
```

## Marking text in the source

The same three functions exist in every part (JavaScript, Kotlin, Python):

| Call | Use |
| --- | --- |
| `tr("Text")` | a piece of text |
| `tr("Playing on {device}", {device: name})` | text with something filled in: `{name}` placeholders. Kotlin: `tr("Playing on {device}", "device" to name)`; Python: `tr("Playing on {device}", device=name)` |
| `trn("{n} song", "{n} songs", count)` | a count: the English for 1, then the English for any other number; `{n}` is the number. Other placeholders as with `tr` |
| `trKey("Text")` | marks English kept in a table or constant (computed once, when the language may still change) without translating it; call `tr(value)` where it is shown |

Rules:

- **The first argument must be one plain string literal**: no `+`, no `${...}` / `$name` interpolation, no variables, so the
  tool can find it. Put changing parts in `{placeholders}`.
- **Keep whole sentences together.** Never build a sentence from several `tr` pieces or add words around a translated
  piece; other languages order words differently. Use placeholders instead.
- **Never make plurals by hand** (`n === 1 ? "song" : "songs"`, `"s"` suffixes): use `trn`.
- **Translate when shown, not when loaded.** Text in top-level constants, enums and tables is evaluated once; mark it
  with `trKey` and `tr()` it where it is drawn. In Compose, call `tr` while composing (not inside `remember {}`), so the
  screen is redrawn in a new language.
- **Never compare displayed text** (`if (button.textContent === "Play")`): compare the state behind it.
- **Static HTML**: put `data-tr` on an element whose whole content is plain text (`<span data-tr>Settings</span>`), and
  `data-tr-attr="title aria-label placeholder"` on an element to translate those attributes. In markup built by script,
  use `${tr("Text")}`.
- **Not translated**: product and service names on their own (YTM Starcast, YouTube Music, Discord, Refine, Tailscale,
  Android, Windows, Linux), names of languages, songs, artists, albums and anything else that comes from YouTube
  Music, things only developers read (logs, console messages, error codes, ids, URLs, CSS), and keyboard keys.
- Text that comes from the server and is shown as is stays English unless the client maps a code to its own `tr` text.

## Translating

Each `i18n/<code>.json` maps the English text to the translation. `i18n/source.json` lists every text with where it is
used (`where`: web, ext, android, py; `files`), which helps with context.

- Keep every `{placeholder}` exactly as it is (same name, in braces); move it where the sentence needs it.
- A text with a `one` form in `source.json` is a count: give an object of plural forms instead of a string. Spanish,
  French, German, Portuguese, Italian: `{"one": ..., "other": ...}`. Russian: `{"one": ..., "few": ..., "many": ...,
  "other": ...}` (1 / 2-4 / 5-20 / fractions). Japanese, Korean, Chinese: `{"other": ...}`.
- Keep product names (YTM Starcast, YouTube Music, Discord, Refine, Tailscale) as they are.
- Write what the app's own language would say: short, plain, the usual words of phone and music apps in that language,
  and that language's capitalization (sentence case in French, Spanish, Italian, Portuguese; nouns capitalized in German).
- Keep the same tone as the English: friendly and plain, addressing the person the way apps in that language usually do
  (German "du" is fine in a music app; French "vous").
