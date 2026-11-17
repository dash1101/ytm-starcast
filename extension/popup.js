// YTM Starcast. Copyright (C) 2026 dash1101. SPDX-License-Identifier: GPL-3.0-or-later
// Additional terms under GPL section 7: see NOTICE.
// YTM Starcast's toolbar popup: is the app on this computer answering, what Discord status shows, and the three main switches.
// The popup never calls the app itself. It asks the extension's script in an open YouTube Music tab, which talks to the
// app the same way the rest of the extension does (and with the same origin).
"use strict";
const ext = globalThis.browser ?? globalThis.chrome;
const YTM = "https://music.youtube.com/";
const $ = (id) => document.getElementById(id);
const i18n = globalThis[Symbol.for("pocketfader.i18n")];
const { tr, trKey } = i18n;
document.documentElement.lang = i18n.lang;
i18n.translateDom();

// What the app says about Discord status, in plain words (the same wording as Refine's menu).
const DISCORD_TEXT = {
  showing: trKey("Showing on your Discord profile."),
  waiting: trKey("Getting ready to show…"),
  idle: trKey("Ready. Nothing is playing."),
  off: trKey("Off. Nothing is shown on Discord."),
  snoozed: trKey("Hidden for a while. It comes back by itself."),
  "paused-hidden": trKey("Not shown while the music is paused."),
  "paused-timeout": trKey("Hidden after a long pause. It comes back when you press play."),
  filtered: trKey("Not shown: this song matches one of your never-show words."),
  ad: trKey("Hidden while an advert plays."),
  "discord-down": trKey("Can't reach Discord. Open the Discord app on this computer."),
  "extension-quiet": trKey("Waiting to hear from YouTube Music."),
  "extension-missing": trKey("Waiting to hear from YouTube Music."),
  "beacon-running": trKey("The old YTM Beacon app is still running. Open YTM Starcast to move over from it."),
  unreachable: trKey("Discord status isn't answering right now. Try again in a moment."),
};
const DISCORD_DEFAULT = trKey("Show what you play on your Discord profile");
$("headline").textContent = tr("Checking…");
$("discord").textContent = tr(DISCORD_DEFAULT);

// ------------------------------------------------------------------ colours: the same as Refine's in YouTube Music
// Refine picks a hue from the album art of the song playing; the extension's script keeps the last one, and the
// popup takes on the same tones (and follows along while it is open).
const hsl = (h, s, l, a) => `hsl(${Math.round(h)} ${Math.round(s)}% ${Math.round(l)}%${a != null ? " / " + a : ""})`;
function palette(hue, vivid) {
  const c = vivid < 0.12 ? 0.3 : 1;   // a grey cover gives nearly neutral tones
  return {
    accent: hsl(hue, 78 * c, 76), onAccent: hsl(hue, 62 * c, 15), track: hsl(hue, 40 * c, 76, 0.28),
    container: hsl(hue, 42 * c, 27), onContainer: hsl(hue, 78 * c, 90),
    surface: hsl(hue, 14 * c, 7), surface1: hsl(hue, 14 * c, 10), surface2: hsl(hue, 15 * c, 13), surface3: hsl(hue, 16 * c, 17),
    outline: hsl(hue, 10 * c, 34), text: hsl(hue, 18 * c, 93), muted: hsl(hue, 10 * c, 72), glow: hsl(hue, 85 * c, 55, 0.55),
  };
}
// the logo in the same colours (logo.js): sharp on any screen
function drawLogo(p) {
  const cv = document.getElementById("logo");
  if (!cv || !globalThis.starcastLogo) return;
  const px = Math.round(26 * Math.max(1, devicePixelRatio || 1));
  if (cv.width !== px) { cv.width = px; cv.height = px; }
  try { starcastLogo.draw(cv.getContext("2d"), px, starcastLogo.colors(p)); } catch (e) {}
}
function takePalette(p) {
  drawLogo(p);
  const ok = p && typeof p === "object" && isFinite(p.h) && isFinite(p.s);
  const pal = ok ? palette(Number(p.h), Number(p.s)) : palette(352, 1);
  const st = document.documentElement.style;
  for (const [k, v] of Object.entries(pal)) st.setProperty("--pf-" + k.replace(/[A-Z]/g, (m) => "-" + m.toLowerCase()), v);
}
try {
  Promise.resolve(ext.storage.local.get("palette")).then((r) => takePalette(r && r.palette)).catch(() => {});
  ext.storage.onChanged.addListener((changes, area) => { if (area === "local" && changes.palette) takePalette(changes.palette.newValue); });
} catch (e) { /* no storage: Refine's default colours from popup.css */ }
// the language: as the extension's script in YouTube Music last heard it from YTM Starcast (the popup also keeps it, so
// it opens in it at once)
try {
  Promise.resolve(ext.storage.local.get("lang")).then((r) => {
    if (!r || typeof r.lang !== "string" || r.lang === i18n.pref) return;
    i18n.set(r.lang);
    document.documentElement.lang = i18n.lang;
    i18n.translateDom();
    clearTimeout(timer);
    refresh();
  }).catch(() => {});
} catch (e) { /* see above */ }

let lastTab = null;          // the tab that answered last time is asked first
let parts = null;            // the switches as the app last said (null: not known, or an older app = all on)
let ok = false;              // the switches can be changed right now
let busy = false;            // a change is on its way
let timer = 0;

// ------------------------------------------------------------------ talking to a YouTube Music tab
async function ytmTabs() {
  let tabs = [];
  try { tabs = await ext.tabs.query({ url: YTM + "*" }); } catch (e) {}
  // a tab's address is only visible with access to YouTube Music, so a tab without one is not ours to ask
  tabs = (tabs || []).filter((t) => typeof t.url === "string" && t.url.startsWith(YTM));
  const score = (t) => (t.id === lastTab ? 8 : 0) + (t.audible ? 4 : 0) + (t.active ? 2 : 0);
  return tabs.sort((a, b) => score(b) - score(a) || (b.lastAccessed || 0) - (a.lastAccessed || 0));
}
function limit(p, ms) {
  return Promise.race([p, new Promise((_, no) => setTimeout(() => no(new Error("timeout")), ms))]);
}
async function allowed() {
  try { return await ext.permissions.contains({ origins: [YTM + "*"] }); } catch (e) { return true; }
}
// Ask the first YouTube Music tab where YTM Starcast's script answers.
async function ask(msg) {
  const tabs = await ytmTabs();
  if (!tabs.length) return (await allowed()) ? { noTab: true } : { noAccess: true };
  for (const t of tabs) {
    try {
      const r = await limit(ext.tabs.sendMessage(t.id, msg), 9000);
      if (r && typeof r === "object") { lastTab = t.id; return r; }
    } catch (e) {
      // no YTM Starcast script in that tab: it was open before YTM Starcast was added or updated
    }
  }
  return { noScript: true, tab: tabs[0].id };
}

// ------------------------------------------------------------------ drawing
function setState(tone, head, detail) {
  $("dot").className = "dot " + tone;
  $("headline").textContent = head;
  $("detail").textContent = detail || "";
  $("detail").hidden = !detail;
}
function setAction(label, fn) {
  const b = $("act");
  b.hidden = !label;
  b.disabled = false;
  b.textContent = label || "";
  b.onclick = fn || null;
}
function paintParts() {
  for (const input of document.querySelectorAll("input[data-part]")) {
    input.disabled = busy || !ok;
    input.checked = parts ? parts[input.dataset.part] !== false : true;
  }
}
function show(r) {
  const s = r.status && typeof r.status === "object" ? r.status : null;
  const version = s && typeof s.version === "string" ? s.version.slice(0, 24) : "";
  $("version").textContent = version;
  $("version").hidden = !version;
  setAction(null);
  let showParts = false;
  if (s) {
    parts = s.parts && typeof s.parts === "object" ? s.parts : null;
    ok = !!parts;
    showParts = true;
    setState("ok", tr("YTM Starcast is running"), ok ? null : tr("This version can't switch parts from here."));
    if (r.twin) {
      setState("warn", tr("Two copies of the extension are loaded"), tr("Keep only one: remove the older one (Pocketfader or YTM Starcast) on the browser's extensions page, then reload YouTube Music."));
    }
    const dc = s.discord && typeof s.discord === "object" ? s.discord : null;
    $("discord").textContent = tr((dc && DISCORD_TEXT[dc.headline]) || DISCORD_DEFAULT);
  } else {
    ok = false;
    $("discord").textContent = tr(DISCORD_DEFAULT);
    if (r.noTab) {
      setState("idle", tr("Open YouTube Music"), tr("YTM Starcast reaches the app on this computer through a YouTube Music tab in this browser."));
      setAction(tr("Open YouTube Music"), () => { Promise.resolve(ext.tabs.create({ url: YTM })).catch(() => {}); });
    } else if (r.noAccess) {
      setState("warn", tr("Allow YTM Starcast on YouTube Music"), tr("The browser hasn't let YTM Starcast into YouTube Music yet, so it can't connect."));
      setAction(tr("Allow"), () => {
        // asked straight from the click: browsers only show this question in answer to one
        let asked;
        try { asked = ext.permissions.request({ origins: [YTM + "*"] }); } catch (e) {}
        Promise.resolve(asked).catch(() => {}).finally(() => refresh());
      });
    } else if (r.noScript) {
      setState("warn", tr("Reload YouTube Music"), tr("Your YouTube Music tab was open before YTM Starcast was added or updated. Reload it to connect it (the song starts again)."));
      setAction(tr("Reload the tab"), () => {
        $("act").disabled = true;
        Promise.resolve(ext.tabs.reload(r.tab)).catch(() => {});
        clearTimeout(timer);
        timer = setTimeout(refresh, 3000);
      });
    } else if (r.error === "old_app") {
      parts = null;   // an app from before these switches has everything on
      showParts = true;
      const v = r.app && typeof r.app.version === "string" && r.app.version ? r.app.version : "";
      setState("warn", tr("An older app is running"), v
        ? tr("The app on this computer is an older version ({version}). Update it to YTM Starcast to switch parts on and off here. Until then everything stays on.", { version: v })
        : tr("The app on this computer is an older version. Update it to YTM Starcast to switch parts on and off here. Until then everything stays on."));
    } else if (r.error === "offline") {
      setState("bad", tr("YTM Starcast isn't answering"), tr("Start YTM Starcast on this computer, then open this again."));
    } else {
      setState("bad", tr("YTM Starcast didn't answer properly"), tr("Try again in a moment."));
    }
  }
  $("parts").hidden = !showParts;
  $("dc").hidden = !(s && ok);
  paintParts();
}

// ------------------------------------------------------------------ live
let gen = 0;                 // an answer that arrives after a newer question (or a change) is dropped
async function refresh() {
  clearTimeout(timer);
  const mine = ++gen;
  const r = await ask({ starcast: "status" });
  if (mine !== gen || busy) return;
  show(r);
  clearTimeout(timer);
  timer = setTimeout(refresh, 4000);
}
for (const input of document.querySelectorAll("input[data-part]")) {
  input.addEventListener("change", async () => {
    busy = true;
    gen++;
    clearTimeout(timer);
    for (const other of document.querySelectorAll("input[data-part]")) other.disabled = true;
    const r = await ask({ starcast: "parts", parts: { [input.dataset.part]: input.checked } });
    busy = false;
    if (r.parts && typeof r.parts === "object") {
      parts = r.parts;
      ok = true;
      paintParts();
      timer = setTimeout(refresh, 900);   // the Discord line catches up a moment later
    } else if (r.error === "failed" || r.error === "bad_request") {
      setState("bad", tr("That change didn't go through"), tr("Try again in a moment."));
      paintParts();                       // the switch flips back to what the app last said
      timer = setTimeout(refresh, 4000);
    } else {
      show(r);                            // the app or the tab went away: say so
      timer = setTimeout(refresh, 4000);
    }
  });
}
// Discord status options: built when first opened, through the same YouTube Music tab as the switches
const dc = $("dc");
dc.addEventListener("toggle", () => {
  if (!dc.open || $("dcPanel").firstChild || !globalThis.__starcastDiscordPanel) return;
  $("dcPanel").append(globalThis.__starcastDiscordPanel({
    ask: (op, extra) => ask({ starcast: op, ...(extra || {}) }).then((r) =>
      r && (r.noTab || r.noAccess || r.noScript) ? { error: "offline" } : r),
  }));
});
refresh();

// ------------------------------------------------------------------ a newer version in YTM Starcast's folder
// (the app copies it there when it is updated; the background script reloads into it)
async function paintUpdate() {
  let r = null;
  try { r = await limit(ext.runtime.sendMessage({ starcast: "updateState" }), 8000); } catch (e) {}
  const v = r && typeof r.ready === "string" ? r.ready.slice(0, 16) : "";
  $("upd").hidden = !v;
  if (!v) return;
  $("updHead").textContent = tr("Version {version} is ready", { version: v });
  $("updDetail").textContent = r.tried
    ? tr("It didn't take: this browser seems to load YTM Starcast from another folder than the app's. Load it from the app's folder instead (Settings, Browser extension in the app shows it), then updates arrive by themselves.")
    : tr("Updating restarts YTM Starcast in this browser and reloads your YouTube Music tabs (the song starts again).");
  $("updAct").textContent = r.tried ? tr("Try again") : tr("Update now");
  $("updAct").disabled = false;
  $("updAct").onclick = () => {
    $("updAct").disabled = true;
    Promise.resolve(ext.runtime.sendMessage({ starcast: "updateNow" })).catch(() => {});
  };
}
paintUpdate();
