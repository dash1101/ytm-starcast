// YTM Starcast. Copyright (C) 2026 dash1101. SPDX-License-Identifier: GPL-3.0-or-later
// Additional terms under GPL section 7: see NOTICE.
package app.pocketfader.client

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Extension
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(cfg: ServerConfig, s: RemoteState, onEditConnection: () -> Unit, modifier: Modifier = Modifier) {
    val p by Settings.prefs.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val version = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty() } catch (e: Exception) { "" }
    }

    // The settings are split into pages: an overview of categories, and one page per category.
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    val link = SettingsNav.deepLink?.let { if (it == "library") "downloads" else it }    // "library" is part of downloads now
    LaunchedEffect(link) {
        if (link != null) {
            page = link
            SettingsNav.deepLink = null
        }
    }
    androidx.activity.compose.BackHandler(enabled = page != null) { page = null }
    val current = page
    if (current == null) {
        SettingsOverview(p, s, cfg, version, modifier) { page = it }
        return
    }
    Column(modifier.fillMaxSize()) {
    PageHeader(allCats.firstOrNull { it.key == current }?.title ?: trKey("Settings")) { page = null }
    if (current == "sound" && Remote.local) {
        // music plays on this phone: its own volume and outputs
        PhoneSoundScreen(Modifier.weight(1f).fillMaxWidth())
    } else if (current == "sound" && !s.audioOn) {
        // computer audio is switched off on the computer: say so, with a way to turn it back on
        Column(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) { AudioOffNote(s.online) }
    } else if (current == "sound") {
        MixerScreen(s, Modifier.weight(1f).fillMaxWidth())
    } else {
    // the page's rows glide in one after another as it opens (not when screen transitions are switched off)
    val arrival = rememberArrival(current)
    CompositionLocalProvider(
        LocalPageTitle provides (allCats.firstOrNull { it.key == current }?.title ?: ""),
        LocalSettingsArrival provides (if (p.transitions) arrival else null),
    ) {
    val pageScroll = rememberScrollState()
    Column(
        Modifier.weight(1f).edgeFade(pageScroll).verticalScroll(pageScroll).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (page == "connection") Section(trKey("Connection")) {
            val status = when {
                !s.configured -> tr("Not set up")
                s.online -> if (s.volume.device.isNotBlank()) tr("Connected · {name}", "name" to s.volume.device) else tr("Connected")
                else -> tr("Can't reach the computer right now")
            }
            Info(tr("Computer"), cfg.url.ifBlank { tr("Not set up") })
            Info(tr("Status"), status)
            Info(
                tr("YouTube Music extension"),
                if (!s.remoteOn) tr("Not used: the YouTube Music remote is switched off") else if (s.ytm) tr("Connected") else tr("Not connected"),
            )
            FilledTonalButton(onClick = onEditConnection, modifier = Modifier.pageGlide().padding(top = 4.dp)) { Text(tr("Edit connection")) }
            if (s.online && !s.ytm) OutlinedButton(onClick = { page = "extension" }, modifier = Modifier.pageGlide()) { Text(tr("Set up the browser extension")) }
        }

        if (page == "extension") Section(trKey("Browser extension")) { ExtensionSetup(s, LocalSettingsArrival.current) }

        if (page == "parts") Section(trKey("Parts")) {
            // fresh from the computer each time the page opens (another phone or the browser may have changed them)
            LaunchedEffect(Unit) { Remote.refreshParts() }
            val parts = s.parts
            if (parts == null) {
                Text(
                    tr("This computer's YTM Starcast has no parts to switch on or off yet. Update YTM Starcast on the computer."),
                    modifier = Modifier.pageGlide(),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val scope = rememberCoroutineScope()
                Text(
                    tr("Switch off the parts you don't use. They are switched on the computer, so the change counts for every phone and browser that uses it."),
                    modifier = Modifier.pageGlide(),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Toggle(
                    tr("YouTube Music remote"), tr("See and control what plays in YouTube Music on the computer: the queue, search and your library."),
                    parts.remote, enabled = s.online,
                ) { v -> scope.launch { Remote.setPart("remote", v) } }
                Toggle(
                    tr("Discord status"), tr("Show the song you're playing on your Discord profile."),
                    parts.discord, enabled = s.online,
                ) { v -> scope.launch { Remote.setPart("discord", v); if (s.canBeacon) BeaconModel.refresh() } }
                Toggle(
                    tr("Refine in YouTube Music"), tr("Improvements inside YouTube Music in the computer's browser. Its own options are in its ✦ panel there."),
                    parts.refine, enabled = s.online,
                ) { v -> scope.launch { Remote.setPart("refine", v) } }
                Toggle(
                    tr("Computer audio"), tr("The computer's volume, output devices and equalizer."),
                    parts.audio, enabled = s.online,
                ) { v -> scope.launch { Remote.setPart("audio", v) } }
            }
        }

        if (page == "appearance") Section(trKey("Appearance")) {
            Choice(
                tr("Theme"), tr("Black turns pixels fully off on OLED screens."),
                listOf("system" to tr("System"), "light" to tr("Light"), "dark" to tr("Dark"), "black" to tr("Black")), p.theme,
            ) { v -> Settings.update { it.copy(theme = v) } }
            Toggle(
                tr("Album colours"),
                tr("The whole app takes the colours of the cover that plays, as Refine does in YouTube Music.") +
                    (if (p.accent != 0) " " + tr("The accent colour chosen below replaces them while it's set.") else ""),
                p.albumColors,
            ) { v -> Settings.update { it.copy(albumColors = v) } }
            if (Build.VERSION.SDK_INT >= 31) {
                // the theme takes Album colours first, so the wallpaper's colours would change nothing while it is on
                val albumOn = p.albumColors && p.accent == 0
                Toggle(
                    tr("Material You colours"),
                    if (albumOn) tr("Not used while Album colours is on.") else tr("Use colours from your wallpaper (when the accent colour below is on Auto)."),
                    p.dynamicColor, enabled = !albumOn,
                ) { v -> Settings.update { it.copy(dynamicColor = v) } }
            }
            AccentPicker(p, Modifier.pageGlide())
            Toggle(tr("Cover colours in the background"), tr("Tint the Now playing screen with the cover's colour."), p.coverWash) { v -> Settings.update { it.copy(coverWash = v) } }
            Toggle(tr("Lyric highlight follows the cover"), tr("Off: the line being sung uses your accent colour."), p.coverHighlight) { v -> Settings.update { it.copy(coverHighlight = v) } }
            Choice(
                tr("Language"), tr("For YTM Starcast on every device. Automatic uses each device's own language."),
                listOf("auto" to tr("Automatic")) + I18n.LANGUAGES, p.language,
            ) { v -> Remote.setLanguage(v) }
            Choice(
                tr("Navigation bar"), tr("Classic keeps the bar along the bottom; Pill floats above the content."),
                listOf("bar" to tr("Classic bar"), "pill" to tr("Floating pill")), p.navStyle,
            ) { v -> Settings.update { it.copy(navStyle = v) } }
            Choice(
                tr("Open on"), tr("The screen shown when the app starts."),
                listOf("playing" to tr("Playing"), "search" to tr("Search"), "settings" to tr("Settings")), p.startTab,
            ) { v -> Settings.update { it.copy(startTab = v) } }
        }

        if (page == "effects") Section(trKey("Effects")) {
            Choice(
                tr("Cover glow"), tr("How bright the cover's colours shine around the album art. Extreme is the brightest."),
                listOf("off" to tr("Off"), "soft" to tr("Soft"), "strong" to tr("Strong"), "extreme" to tr("Extreme")), p.coverGlow,
            ) { v -> Settings.update { it.copy(coverGlow = v) } }
            Choice(
                tr("Cover visualizer"),
                tr("Glow: the cover's colours shine softly around it. Halo: the blurred cover turns slowly around it. Orbit: a ring of the album's colours runs round its edge.") +
                    (if (p.coverGlow == "off") " " + tr("Turn on Cover glow to use one.") else ""),
                listOf("glow" to tr("Glow"), "halo" to tr("Halo"), "orbit" to tr("Orbit")), p.coverViz, enabled = p.coverGlow != "off",
            ) { v -> Settings.update { it.copy(coverViz = v) } }
            Choice(
                tr("Cover shape"), tr("Circle spins slowly like a record while music plays."),
                listOf("rounded" to tr("Rounded"), "soft" to tr("Soft"), "circle" to tr("Circle"), "cut" to tr("Cut corners")), p.coverShape,
            ) { v -> Settings.update { it.copy(coverShape = v) } }
            Toggle(tr("Glow on text, icons and buttons"), tr("A soft glow around the song title, the lyric being sung and the buttons that are on."), p.glowUi) { v ->
                Settings.update { it.copy(glowUi = v) }
            }
            Toggle(tr("Wavy seek bar"), tr("The played part of the bar ripples while music plays."), p.wavyBar) { v -> Settings.update { it.copy(wavyBar = v) } }
            Toggle(
                tr("Screen transitions"), tr("A short fade and slide between screens, and the rows of lists, pages and settings gliding in as they open."),
                p.transitions,
            ) { v -> Settings.update { it.copy(transitions = v) } }
            Text(
                tr("These are light drawing effects. “Reduce animations” (under Performance) stops all motion and keeps the rest."),
                modifier = Modifier.pageGlide(),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (page == "screen") Section(trKey("Screen & always-on")) {
            Choice(
                tr("Keep screen on"), tr("Handy when the phone or tablet is a dedicated remote or now-playing display."),
                listOf("off" to tr("Off"), "playing" to tr("While playing"), "always" to tr("Always")), p.keepAwake,
            ) { v -> Settings.update { it.copy(keepAwake = v) } }
            Choice(
                tr("Dim when untouched"), tr("Lowers the brightness after a while; touch to wake. Needs Keep screen on."),
                listOf("0" to tr("Never")) + listOf(1, 2, 5, 10).map { "$it" to minutes(it) }, p.dimAfterMin.toString(),
                enabled = p.keepAwake != "off",
            ) { v -> Settings.update { it.copy(dimAfterMin = v.toInt()) } }
            Toggle(tr("Full screen"), tr("Hide the status and navigation bars. Swipe from the edge to bring them back."), p.immersive) { v ->
                Settings.update { it.copy(immersive = v) }
            }
            Toggle(tr("Show the time"), tr("In full screen, the time in the top corner."), p.barClock, enabled = p.immersive) { v ->
                Settings.update { it.copy(barClock = v) }
            }
            Toggle(tr("Show the battery"), tr("In full screen, the battery level beside it."), p.barBattery, enabled = p.immersive) { v ->
                Settings.update { it.copy(barBattery = v) }
            }
            Toggle(
                tr("Pixel shift"),
                tr("Moves the whole screen a few pixels every so often so static parts don't burn in on OLED displays. Works best with Always on and the Black theme."),
                p.pixelShift,
            ) { v -> Settings.update { it.copy(pixelShift = v) } }
            Choice(
                tr("Shift every"), null,
                listOf(1, 2, 5, 10).map { "$it" to minutes(it) }, p.pixelShiftMin.toString(), enabled = p.pixelShift,
            ) { v -> Settings.update { it.copy(pixelShiftMin = v.toInt()) } }
            Choice(
                tr("Shift distance"), null,
                listOf("2" to tr("Small"), "4" to tr("Medium"), "8" to tr("Large")), p.pixelShiftDp.toString(), enabled = p.pixelShift,
            ) { v -> Settings.update { it.copy(pixelShiftDp = v.toInt()) } }
        }

        if (page == "controls") Section(trKey("Controls")) {
            Toggle(
                tr("Media notification"), tr("Playback controls on the lock screen and in the notification shade. Also keeps the connection alive in the background."),
                p.notification,
            ) { v -> Settings.update { it.copy(notification = v) } }
            // the computer's volume is part of "Computer audio"; with that switched off there is nothing for the buttons to change
            if (s.audioOn) {
                Toggle(tr("Volume buttons control the computer"), tr("While the app is open, or the notification is active and something is playing."), p.volumeKeys) { v ->
                    Settings.update { it.copy(volumeKeys = v) }
                }
                Choice(
                    tr("Volume step"), tr("Percent per button press."),
                    listOf("1" to "1%", "2" to "2%", "5" to "5%", "10" to "10%"), p.volumeStep.toString(), enabled = p.volumeKeys,
                ) { v -> Settings.update { it.copy(volumeStep = v.toInt()) } }
            }
            Toggle(tr("Haptic feedback"), tr("A small buzz on the main buttons."), p.haptics) { v -> Settings.update { it.copy(haptics = v) } }
            // other players on the computer (Spotify, a video in another tab, VLC...)
            var pauseOthers by remember { mutableStateOf<Boolean?>(null) }
            androidx.compose.runtime.LaunchedEffect(s.online) {
                if (s.online) pauseOthers = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { Remote.serverPrefs().optBoolean("pauseOthers", false) }.getOrNull()
                }
            }
            pauseOthers?.let { on ->
                Toggle(
                    tr("Pause other players when YouTube Music starts"),
                    tr("Other music or video playing on the computer is paused when a YouTube Music song starts, so they don't play over each other."),
                    on,
                ) { v -> pauseOthers = v; Remote.setServerPref("pauseOthers", v) }
            }
            OutlinedButton(onClick = { Remote.pauseOthers() }, enabled = s.online, modifier = Modifier.pageGlide()) { Text(tr("Pause other players now")) }
        }

        // what only matters when the phone itself plays the music: how a changed speed sounds, and when it stops
        if (page == "controls") Section(trKey("Music on this phone")) {
            Toggle(
                tr("Keep the pitch when the speed changes"),
                tr("Off: a slower song also sounds lower and a faster one higher, as on a record."),
                p.keepPitch,
            ) { v -> Settings.update { it.copy(keepPitch = v) } }
            Text(
                if (p.phoneSpeed != 100) tr("The speed is set from the player while music plays on this phone (now {speed}).", "speed" to speedShort(p.phoneSpeed))
                else tr("The speed is set from the player while music plays on this phone."),
                modifier = Modifier.pageGlide(),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // the phone's player follows this setting as it changes, so this works whether or not music is playing
            if (p.phoneSpeed != 100) {
                OutlinedButton(onClick = { Settings.update { it.copy(phoneSpeed = 100) } }, modifier = Modifier.pageGlide()) {
                    Text(tr("Back to normal speed"))
                }
            }
            Toggle(
                tr("Keep playing when the app is swiped away"),
                tr("When off, swiping the app away stops the music; the queue and your place in the song are kept."),
                p.keepPlayingClosed,
            ) { v -> Settings.update { it.copy(keepPlayingClosed = v) } }
            Toggle(
                tr("Pause when headphones disconnect"),
                tr("Pauses when the sound would move to the phone's speaker, as when headphones or an AUX cable are unplugged, or a Bluetooth or USB speaker disconnects."),
                p.pauseOnUnplug,
            ) { v -> Settings.update { it.copy(pauseOnUnplug = v) }; LocalPlayer.setPauseOnUnplug(v) }
        }

        if (page == "lyrics") Section(trKey("Lyrics")) {
            Toggle(tr("Romanize"), tr("Show lines in Latin letters (Korean, Japanese kana, Cyrillic, Greek, and anything a source provides)."), p.lyricsRoman) { v ->
                Settings.update { it.copy(lyricsRoman = v) }
            }
            Toggle(tr("Translations"), tr("Show a translation under each line when a source has one."), p.lyricsTranslation) { v ->
                Settings.update { it.copy(lyricsTranslation = v) }
            }
            Choice(
                tr("Sync"), tr("Shift the lyrics if they run early or late on this device. You can also nudge them while viewing lyrics."),
                listOf(
                    "-500" to tr("{n} s", "n" to "−0.5"), "-200" to tr("{n} s", "n" to "−0.2"), "0" to tr("On time"),
                    "200" to tr("{n} s", "n" to "+0.2"), "500" to tr("{n} s", "n" to "+0.5"),
                ), p.lyricsOffsetMs.toString(),
            ) { v -> Settings.update { it.copy(lyricsOffsetMs = v.toInt()) } }
            // one block, so its titles stay close to their chips and it glides in as one row
            Column(Modifier.pageGlide(), verticalArrangement = Arrangement.spacedBy(6.dp)) { LyricsLayoutControls() }
            Choice(
                tr("Preferred source"), tr("Used first when it has the song. You can also switch while viewing lyrics."),
                listOf("auto" to tr("Automatic"), "kugou" to "KuGou", "lrclib" to "LRCLIB", "netease" to "NetEase", "ytm" to "YouTube Music"), p.lyricsSource,
            ) { v -> Settings.update { it.copy(lyricsSource = v) } }
        }

        if (page == "performance") Section(trKey("Performance")) {
            Choice(
                tr("Update speed"), tr("How often the app asks the computer what's playing. Battery saver is gentler on old phones."),
                listOf("fast" to tr("Fast"), "normal" to tr("Normal"), "battery" to tr("Battery saver")), p.pollSpeed,
            ) { v -> Settings.update { it.copy(pollSpeed = v) } }
            Toggle(tr("Reduce animations"), tr("Fewer moving parts; smoother on older devices."), p.reduceMotion) { v -> Settings.update { it.copy(reduceMotion = v) } }
            Toggle(tr("Pictures in the queue"), tr("Little cover pictures next to each queued song. Turn off if scrolling the queue is slow on an old device."), p.queueArt) { v ->
                Settings.update { it.copy(queueArt = v) }
            }
        }

        if (page == "discord") {
            if (s.canBeacon) {
                BeaconCard(s.online, Modifier.pageGlide())
            } else {
                Text(
                    tr("This computer's YTM Starcast doesn't support Discord status yet. Update YTM Starcast on the computer."),
                    modifier = Modifier.pageGlide(),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                tr("Discord status shows the song playing in YouTube Music on your Discord profile. Here you can see whether it's working, turn it on or off, hide it for a while and change what it shows. Discord has to be open on the computer."),
                modifier = Modifier.pageGlide().padding(horizontal = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // only the song playing goes to the computer this app is connected to; with no computer there is nothing to show it
            if (cfg.isSet) Section(trKey("Music on this phone")) {
                Toggle(
                    tr("Show what this phone plays"),
                    tr("When music plays on this phone, your computer shows it as your Discord status. Needs YTM Starcast and the Discord app running on your computer.") +
                        if (s.online && !s.canPhoneDiscord) " " + tr("This computer's YTM Starcast can't do this yet: update YTM Starcast on the computer.") else "",
                    p.discordFromPhone,
                ) { v -> Settings.update { it.copy(discordFromPhone = v) }; PhonePresence.refresh() }
            }
        }

        if (page == "downloads") Section(trKey("Downloads")) {
            val saved by Downloads.items.collectAsStateWithLifecycle()
            var confirm by remember { mutableStateOf(false) }
            Choice(
                tr("Play music on"), tr("On this phone, songs from YouTube Music play straight from the internet (or from your downloads); nothing has to be running on a computer."),
                listOf("computer" to tr("The computer"), "phone" to tr("This phone")), p.playOn,
            ) { v -> Remote.setPlayOn(v, carryOver = false) }
            Choice(
                tr("Download quality"),
                tr("For songs saved from now on: about 1.2 MB a minute at Best, 1 MB at Standard, 0.4 MB at Data saver. Best is usually Opus, Standard AAC (plays everywhere)."),
                listOf("best" to tr("Best") + " · 160 kbps", "normal" to tr("Standard") + " · 128 kbps", "saver" to tr("Data saver") + " · 50 kbps"), p.dlQuality,
            ) { v -> Settings.update { it.copy(dlQuality = v) }; AutoDownload.soon() }
            Info(tr("Saved on this phone"), trn("{n} song · {size}", "{n} songs · {size}", saved.size, "size" to formatBytes(saved.sumOf { it.size })))
            Text(
                tr("Downloads keep the song, its cover and its lyrics, and play with no connection. Download from a song's ⋮ menu, a playlist, the queue or the player. They're stored in the app's own folder and removed if you uninstall it."),
                modifier = Modifier.pageGlide(),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { confirm = true }, enabled = saved.isNotEmpty(), modifier = Modifier.pageGlide()) { Text(tr("Delete all downloads")) }
            if (confirm) ConfirmDialog(
                tr("Delete all downloads?"), trn("{n} song will be removed from this phone.", "{n} songs will be removed from this phone.", saved.size), tr("Delete"),
                onDismiss = { confirm = false },
            ) { confirm = false; Downloads.deleteAll() }
        }

        // the library and what's kept offline are on the downloads page too: one place for music on this phone
        if (page == "downloads") {
            val lists by Library.playlists.collectAsStateWithLifecycle()
            val liked by Library.liked.collectAsStateWithLifecycle()
            val at by Library.syncedAt.collectAsStateWithLifecycle()
            val status by Library.status.collectAsStateWithLifecycle()
            val problem by Library.problem.collectAsStateWithLifecycle()
            val keep by Library.keepOffline.collectAsStateWithLifecycle()
            val history by Library.history.collectAsStateWithLifecycle()
            val saved by Downloads.items.collectAsStateWithLifecycle()
            var confirmHistory by remember { mutableStateOf(false) }
            Section(trKey("Your library")) {
                Info(
                    tr("Synced from your account"),
                    if (at == 0L) tr("Not yet") else ago(at).replaceFirstChar { it.uppercase() } + " · " +
                        trn("{n} playlist", "{n} playlists", lists.size) + " · " + trn("{n} liked song", "{n} liked songs", liked.size),
                )
                val waiting = Library.pendingCount
                if (waiting > 0) Info(
                    tr("Waiting to be sent"),
                    trn(
                        "{n} change made without the account (likes, songs added to playlists)",
                        "{n} changes made without the account (likes, songs added to playlists)", waiting,
                    ),
                )
                status?.let { Text(it, Modifier.pageGlide(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                if (status == null) problem?.let { Info(tr("The last automatic sync stopped"), it) }
                FilledTonalButton(onClick = { Library.sync() }, enabled = s.ytm && status == null, modifier = Modifier.pageGlide()) {
                    Text(if (status != null) tr("Syncing…") else tr("Sync now"))
                }
                Text(
                    tr("Your playlists, liked songs and YouTube Music home page are copied to this phone while YouTube Music is open on the computer, so they're here when it isn't: on the go, with no computer at all, or with no connection (for downloaded songs). Likes and playlist additions made in the meantime are sent at the next sync.") +
                        (if (!s.ytm) " " + tr("Open YouTube Music on the computer to sync.") else ""),
                    modifier = Modifier.pageGlide(),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Toggle(tr("Sync automatically"), tr("Each time this phone pairs with or connects to a computer with YouTube Music open, and every few hours while it stays connected."), p.autoSync) { v ->
                    Settings.update { it.copy(autoSync = v) }
                }
            }
            Section(trKey("Automatic downloads")) {
                Toggle(tr("Liked songs"), tr("Keep the songs you like downloaded, new likes included."), p.autoDlLiked) { v ->
                    Settings.update { it.copy(autoDlLiked = v) }; AutoDownload.soon()
                }
                val song = remember(p.dlQuality, saved) { AutoDownload.typicalSong() }
                val limitBytes = p.autoDlLimitMb * 1_048_576L
                val songsNeed = p.smartDownloads.coerceAtLeast(0) * song
                // room for automatic downloads: what's free, plus what they take now (they make way for each other)
                val freeNow = remember(saved) { Downloads.freeBytes() }
                val room = if (freeNow < 0) Long.MAX_VALUE else freeNow + saved.filter { it.auto }.sumOf { it.size }
                NumberChoice(
                    tr("Smart downloads"),
                    when {
                        p.smartDownloads < 0 ->
                            tr("As many as fit in the space below: your most played songs and quick picks first, then liked songs, what you play most and your playlists.") + " " +
                                if (p.autoDlLimitMb > 0) trn("About {n} song at this quality.", "About {n} songs at this quality.", limitBytes / song)
                                else tr("With no limit, it stops 1 GB short of filling the phone.")
                        else ->
                            tr("Keep up to this many of your most played songs and today's quick picks on the phone, refreshed as your taste changes (fewer while your listening history is short).") +
                                if (p.autoDlLimitMb > 0 && songsNeed > limitBytes)
                                    " " + tr("That many need about {size}: about {n} fit in the space below.", "size" to gb(songsNeed), "n" to limitBytes / song) else ""
                    },
                    presets = listOf(0 to tr("Off"), 25 to "25", 50 to "50", 100 to "100", 250 to "250", 500 to "500", 1000 to "1000", -1 to tr("Fill the space")),
                    value = p.smartDownloads, range = 1..1000, unitLabel = tr("Songs"), custom = { trn("{n} song", "{n} songs", it) },
                    warning = if (p.smartDownloads > 0 && songsNeed > room)
                        tr("That many need about {size}, more than the phone has room for ({free}).", "size" to gb(songsNeed), "free" to gb(room)) else null,
                    warnAbove = if (room == Long.MAX_VALUE) null else (room / song).toInt().coerceAtLeast(1),
                ) { v -> Settings.update { it.copy(smartDownloads = v) }; Recommend.refreshIfStale(); AutoDownload.soon() }
                Info(tr("Playlists kept on this phone"), if (keep.isEmpty()) tr("None. Open a playlist and turn on “Keep on this phone”.") else "${keep.size}: " +
                    keep.mapNotNull { k -> if (k == "LM") tr("Liked music") else lists.firstOrNull { it.id == k }?.title }.joinToString(", "))
                val free = remember { Downloads.freeBytes() }
                NumberChoice(
                    tr("Space for automatic downloads"),
                    tr("Songs you download yourself don't count, and are never removed automatically.") +
                        (if (p.autoDlLimitMb > 0) " " + trn("Room for about {n} song at this quality.", "Room for about {n} songs at this quality.", limitBytes / song) else "") +
                        if (free >= 0) " " + tr("{size} free on this phone.", "size" to gb(free)) else "",
                    presets = listOf(1, 2, 5, 10, 25, 50, 100).map { it to gbCount(it) } + (0 to tr("No limit")),
                    value = if (p.autoDlLimitMb <= 0) 0 else (p.autoDlLimitMb + 1023) / 1024,
                    range = 1..2000, unitLabel = "GB", custom = { gbCount(it) },
                    warning = when {
                        p.autoDlLimitMb > 0 && limitBytes > room ->
                            tr("More than the phone has room for ({free}): downloads stop a little before it's full.", "free" to gb(room))
                        p.autoDlLimitMb <= 0 && room != Long.MAX_VALUE ->
                            tr("Downloads stop a little before the phone is full ({free} free for them now).", "free" to gb(room))
                        else -> null
                    },
                    warnAbove = if (room == Long.MAX_VALUE) null else (room / 1_073_741_824L).toInt(),
                ) { v -> Settings.update { it.copy(autoDlLimitMb = v * 1024) }; AutoDownload.soon() }
                Toggle(tr("Only on Wi-Fi"), tr("Automatic downloads wait for Wi-Fi (or another connection that isn't metered)."), p.dlWifiOnly) { v ->
                    Settings.update { it.copy(dlWifiOnly = v) }; AutoDownload.soon()
                }
                val plan = remember(p, keep, liked, lists, saved) { AutoDownload.plan() }
                val autoNow = saved.filter { it.auto }
                Info(
                    tr("Downloaded automatically"),
                    trn("{n} song · {size}", "{n} songs · {size}", autoNow.size, "size" to formatBytes(autoNow.sumOf { it.size })) +
                        if (plan.first > 0) " " + tr("(the rules want {n}, about {size})", "n" to plan.first, "size" to formatBytes(plan.second)) else "",
                )
                OutlinedButton(onClick = { AutoDownload.soon(); Remote.notify(tr("Checking for songs to download…")) }, modifier = Modifier.pageGlide()) { Text(tr("Download now")) }
            }
            Section(trKey("Recommendations")) {
                Info(tr("Listening history"), trn("{n} play remembered on this phone", "{n} plays remembered on this phone", history.size))
                Text(
                    tr("When YouTube Music on the computer isn't connected, “For you”, autoplay and “I'm feeling lucky” are worked out on this phone from what you play (here and on the computer), skip and like, using YouTube Music's own song radios. Your listening history is kept only on this phone. Songs from it are used to ask YouTube for similar music."),
                    modifier = Modifier.pageGlide(),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { confirmHistory = true }, enabled = history.isNotEmpty(), modifier = Modifier.pageGlide()) { Text(tr("Clear listening history")) }
            }
            if (confirmHistory) ConfirmDialog(
                tr("Clear listening history?"), tr("The recommendations start over from your likes and playlists."), tr("Clear"),
                onDismiss = { confirmHistory = false },
            ) { confirmHistory = false; Library.clearHistory() }
        }

        if (page == "updates") Section(trKey("Updates")) {
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val info = UpdateManager.info
            val newer = UpdateManager.available(ctx)
            Info(tr("Installed"), version.ifBlank { tr("unknown") })
            Info(tr("Latest release"), info?.latest?.ifBlank { null } ?: tr("Not checked yet"))
            UpdateManager.message?.let { Text(it, Modifier.pageGlide(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (UpdateManager.needsReinstall) {
                OutlinedButton(onClick = { UpdateManager.openReleasePage(ctx) }, modifier = Modifier.pageGlide()) { Text(tr("Open the download page")) }
            }
            Row(Modifier.pageGlide(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = { scope.launch { UpdateManager.check(ctx, refresh = true) } }, enabled = !UpdateManager.checking) {
                    Text(if (UpdateManager.checking) tr("Checking…") else tr("Check now"))
                }
                if (newer) {
                    androidx.compose.material3.Button(onClick = { scope.launch { UpdateManager.install(ctx) } }, enabled = !UpdateManager.downloading) {
                        Text(if (UpdateManager.downloading) tr("Downloading…") else tr("Download & install"))
                    }
                }
            }
            Toggle(tr("Check automatically"), tr("Looks for a new version on GitHub when the app opens (or asks the computer, when GitHub can't be reached from the phone)."), p.autoUpdate) { v ->
                Settings.update { it.copy(autoUpdate = v) }
            }
            val notes = remember(info?.notes) { info?.notes?.let { parseReleaseNotes(it) } }
            if (notes != null) {
                // just the "What's new" part of the release, as a list, not the whole release page
                Column(Modifier.pageGlide(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        notes.heading.let { h ->
                            if (h.startsWith("What's new in ")) tr("What's new in {version}", "version" to h.removePrefix("What's new in ").trim())
                            else h.ifBlank { tr("What's new") }
                        },
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    )
                    for (item in notes.items.take(12)) {
                        Row {
                            Text("•", Modifier.padding(end = 8.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(inlineMarkdown(item), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (page == "about") Section(trKey("About")) {
            Row(Modifier.pageGlide(), verticalAlignment = Alignment.CenterVertically) {
                StarcastTile(52.dp)
                Column(Modifier.padding(start = 14.dp)) {
                    Text("YTM Starcast", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        tr("Android app · {version}", "version" to version.ifBlank { tr("version unknown") }),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Info("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            Text(
                tr("Made by {author}. Free software under the GNU GPL, version 3 or later, with no warranty.", "author" to "dash1101"),
                modifier = Modifier.pageGlide(),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val links = androidx.compose.ui.platform.LocalUriHandler.current
            OutlinedButton(onClick = { runCatching { links.openUri("https://github.com/dash1101/ytm-starcast") } }, modifier = Modifier.pageGlide()) { Text(tr("Source code")) }
            OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.pageGlide()) { Text(tr("Reset all settings")) }
        }
        if (page == "about") Section(trKey("If the app closed by itself")) {
            // what Android says about the last times the app stopped (Android 11 and later), and errors the app wrote down
            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            val exits = remember { CrashLog.exits(ctx) }
            var errors by remember { mutableStateOf(CrashLog.read()) }
            if (exits.isEmpty() && errors == null) {
                Text(
                    if (Build.VERSION.SDK_INT >= 30) tr("Nothing has gone wrong that the app knows of.")
                    else tr("No errors written down. (Android only tells apps why they were closed from Android 11 on.)"),
                    modifier = Modifier.pageGlide(),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (exits.isNotEmpty()) {
                Column(Modifier.pageGlide(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("The last times it stopped"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (e in exits.take(4)) Text(e, style = MaterialTheme.typography.bodySmall)
                }
            }
            errors?.let { text ->
                Column(Modifier.pageGlide(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("Errors written down"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text.lineSequence().filter { it.isNotBlank() }.take(5).joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall, maxLines = 8,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                tr("When Android closes the app to free memory, that isn't an error in the app, but it is why the music's place can be lost; switching off battery optimisation for YTM Starcast in Android's settings helps on devices that do it often. This report stays on this device unless you copy it somewhere."),
                modifier = Modifier.pageGlide(),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.pageGlide(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(CrashLog.report(ctx)))
                    Remote.notify(tr("Report copied"))
                }) { Text(tr("Copy report")) }
                if (errors != null) OutlinedButton(onClick = { CrashLog.clear(); errors = null }) { Text(tr("Clear")) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    }
    }
    }

    if (confirmReset) {
        ConfirmDialog(
            tr("Reset settings?"), tr("This restores every setting on this page to its default. Your connection is kept."), tr("Reset"),
            onDismiss = { confirmReset = false },
        ) {
            confirmReset = false
            Settings.reset()
            PhonePresence.refresh()      // "Show what this phone plays" may have been switched back on
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    // the page's header already names its first card; the cards after it get their own small heading
    val pageTitle = LocalPageTitle.current
    Column {
        if (pageTitle == null) {
            Row(Modifier.pageGlide().padding(start = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(allCats.firstOrNull { it.title == title }?.icon ?: Icons.Filled.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(tr(title), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        } else if (title in SUBHEADS) {
            // small and in the accent colour, as the overview's group names
            Text(
                tr(title), Modifier.pageGlide().padding(start = 8.dp, top = 4.dp, bottom = 6.dp), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
            )
        }
        Card(
            Modifier.fillMaxWidth(),
            shape = PfShape.Lg,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        }
    }
}

@Composable
private fun Info(label: String, value: String) {
    Column(Modifier.pageGlide()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Toggle(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    // the whole row flips the switch (its words too, not only the switch), and reads as one switch to TalkBack
    Row(
        Modifier.pageGlide().fillMaxWidth().heightIn(min = 48.dp).clip(PfShape.Sm)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                title, style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = null)
    }
}

@Composable
private fun Choice(
    title: String,
    subtitle: String?,
    options: List<Pair<String, String>>,
    selected: String,
    enabled: Boolean = true,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.pageGlide(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title, style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                FilterChip(colors = refineChipColors(), border = refineChipBorder(selected == value, enabled), selected = selected == value, shape = chipShape(selected == value), onClick = { onSelect(value) }, enabled = enabled, label = { Text(label) })
            }
        }
    }
}

/** "1.4 GB" / "350 MB" */
private fun gb(bytes: Long): String =
    if (bytes >= 1_073_741_824L) tr("{n} GB", "n" to "%.1f".format(bytes / 1_073_741_824.0).removeSuffix(".0").removeSuffix(",0"))
    else tr("{n} MB", "n" to bytes / 1_048_576L)

/** "5 GB" */
private fun gbCount(n: Int): String = tr("{n} GB", "n" to n)

/** "5 min" */
private fun minutes(n: Int): String = trn("{n} min", "{n} min", n)

/** A number picked from [presets] (0 means off / no limit when it is one of them), or typed in with "Custom…" (within
 *  [range]; [unitLabel] names the typed number's unit, [custom] writes a number with it). */
@Composable
private fun NumberChoice(
    title: String,
    subtitle: String?,
    presets: List<Pair<Int, String>>,
    value: Int,
    range: IntRange,
    unitLabel: String,
    custom: (Int) -> String,
    warning: String? = null,
    warnAbove: Int? = null,
    onSet: (Int) -> Unit,
) {
    var asking by remember { mutableStateOf(false) }
    val isPreset = presets.any { it.first == value }
    Column(Modifier.pageGlide(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (warning != null) Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { (v, label) ->
                FilterChip(colors = refineChipColors(), border = refineChipBorder(value == v), selected = value == v, shape = chipShape(value == v), onClick = { onSet(v) }, label = { Text(label) })
            }
            FilterChip(colors = refineChipColors(), border = refineChipBorder(!isPreset), 
                selected = !isPreset, shape = chipShape(!isPreset), onClick = { asking = true },
                label = { Text(if (isPreset) tr("Custom…") else custom(value)) },
            )
        }
    }
    if (asking) {
        var text by remember { mutableStateOf(if (isPreset || value == 0) "" else value.toString()) }
        val n = text.trim().toIntOrNull()
        val ok = n != null && n in range
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(title) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { t -> text = t.filter { it.isDigit() }.take(6) },
                    singleLine = true,
                    label = { Text(unitLabel) },
                    supportingText = {
                        if (n != null && warnAbove != null && n > warnAbove) {
                            Text(tr("More than fits on the phone now (about {amount}).", "amount" to custom(warnAbove)), color = MaterialTheme.colorScheme.error)
                        } else {
                            Text(tr("From {min} to {max}.", "min" to custom(range.first), "max" to custom(range.last)))
                        }
                    },
                    isError = text.isNotEmpty() && !ok,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ),
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { if (ok) { onSet(n!!); asking = false } }, enabled = ok) { Text(tr("Set")) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { asking = false }) { Text(tr("Cancel")) } },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// The overview: categories in groups. Tapping one opens its page.

/** Lets other screens (the "new version" banner) open a particular settings page. */
object SettingsNav {
    var deepLink by mutableStateOf<String?>(null)
}

private val LocalPageTitle = androidx.compose.runtime.compositionLocalOf<String?> { null }

/** The open page's arrival: its rows glide in one after another (null: they just appear). */
private val LocalSettingsArrival = androidx.compose.runtime.compositionLocalOf<Arrival?> { null }

/** This row takes its turn in the open page's arrival, after the rows above it. */
@Composable
private fun Modifier.pageGlide(): Modifier = arrive(LocalSettingsArrival.current)

/** Cards that come after a page's first one, and so need a heading of their own. */
private val SUBHEADS = setOf("Your library", "Automatic downloads", "Recommendations", "If the app closed by itself", "Music on this phone")

private class Cat(val key: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

// names in English (trKey); tr() where they are drawn
private val groups = listOf(
    trKey("Listening") to listOf(
        Cat("sound", trKey("Sound & outputs"), Icons.Filled.Tune), Cat("downloads", trKey("Downloads & offline"), Icons.Filled.DownloadForOffline),
        Cat("lyrics", trKey("Lyrics"), Icons.Filled.MusicNote), Cat("controls", trKey("Controls"), Icons.Filled.TouchApp),
    ),
    trKey("Look & feel") to listOf(
        Cat("appearance", trKey("Appearance"), Icons.Filled.Palette), Cat("effects", trKey("Effects"), Icons.Filled.AutoAwesome),
        Cat("screen", trKey("Screen & always-on"), Icons.Filled.Smartphone),
    ),
    trKey("Connections") to listOf(
        Cat("connection", trKey("Connection"), Icons.Filled.Link), Cat("parts", trKey("Parts"), Icons.Filled.PowerSettingsNew),
        Cat("extension", trKey("Browser extension"), Icons.Filled.Extension),
        Cat("discord", trKey("Discord status"), Icons.Filled.Chat), Cat("updates", trKey("Updates"), Icons.Filled.SystemUpdate),
    ),
    trKey("More") to listOf(Cat("performance", trKey("Performance"), Icons.Filled.Speed), Cat("about", trKey("About"), Icons.Filled.Info)),
)
private val allCats = groups.flatMap { it.second }

private fun themeSummary(t: String) = when (t) {
    "light" -> tr("Light theme"); "dark" -> tr("Dark theme"); "black" -> tr("Black theme"); else -> tr("System theme")
}

private fun vizName(v: String) = when (v) { "halo" -> tr("Halo"); "orbit" -> tr("Orbit"); else -> tr("Glow") }

private fun glowName(g: String) = when (g) { "strong" -> tr("Strong"); "extreme" -> tr("Extreme"); else -> tr("Soft") }

private fun partsSummary(p: Parts): String {
    val off = listOfNotNull(
        tr("Remote").takeIf { !p.remote }, tr("Discord status").takeIf { !p.discord },
        "Refine".takeIf { !p.refine }, tr("Computer audio").takeIf { !p.audio },
    )
    return if (off.isEmpty()) tr("Everything is on") else tr("Off: {parts}", "parts" to off.joinToString(", "))
}

/** Pages left out of the overview: Parts on a computer without part switches, the computer's sound while that part is off. */
private fun catShown(key: String, s: RemoteState): Boolean = when (key) {
    "parts" -> s.parts != null
    "sound" -> s.audioOn || Remote.local
    else -> true
}

private fun summaryOf(key: String, p: Prefs, s: RemoteState, cfg: ServerConfig, version: String): String = when (key) {
    "sound" -> if (s.volume.available) {
        (if (s.volume.muted) tr("{n}% (muted)", "n" to s.volume.percent) else "${s.volume.percent}%") +
            s.volume.device.let { if (it.isBlank()) "" else " · $it" }
    } else tr("No audio output found")
    "downloads" -> (if (p.playOn == "phone") tr("Playing on this phone") else tr("Playing on the computer")) + " · " +
        trn("{n} downloaded", "{n} downloaded", Downloads.items.value.size) +
        Library.syncedAt.value.let { at -> if (at == 0L) "" else " · " + tr("library synced {ago}", "ago" to ago(at)) } +
        (if (p.autoDlLiked || p.smartDownloads != 0 || Library.keepOffline.value.isNotEmpty()) " · " + tr("automatic downloads on") else "")
    "lyrics" -> (if (p.lyricsRoman) tr("Romanized") else tr("Original text")) + " · " + (if (p.lyricsSource == "auto") tr("automatic source") else p.lyricsSource)
    "controls" -> (if (p.notification) tr("Notification on") else tr("Notification off")) +
        (if (s.audioOn) " · " + (if (p.volumeKeys) tr("volume keys on") else tr("volume keys off")) else "")
    "appearance" -> themeSummary(p.theme) + (if (p.accent != 0) " · " + tr("custom accent") else if (p.albumColors) " · " + tr("album colours") else "") +
        " · " + (if (p.navStyle == "pill") tr("pill navigation") else tr("classic navigation"))
    "effects" -> (if (p.coverGlow == "off") tr("No cover glow") else vizName(p.coverViz) + " · " + glowName(p.coverGlow)) + " · " +
        (if (p.wavyBar) tr("wavy bar") else tr("plain bar"))
    "screen" -> when (p.keepAwake) { "always" -> tr("Always on"); "playing" -> tr("On while playing"); else -> tr("Screen sleeps normally") } +
        (if (p.pixelShift) " · " + tr("pixel shift") else "")
    "connection" -> if (!s.configured) tr("Not set up")
        else if (s.online) tr("Connected · {name}", "name" to cfg.url.removePrefix("http://").removePrefix("https://").trimEnd('/'))
        else tr("Can't reach the computer")
    "parts" -> s.parts?.let { partsSummary(it) } ?: tr("Switch parts of YTM Starcast on or off")
    "extension" -> if (s.ytm) tr("Connected") else if (s.extNeverSet) tr("Not added to a browser yet") else tr("How to add it to the computer's browser")
    "discord" -> BeaconModel.status?.let { beaconSummary(it) } ?: tr("Show your music on Discord")
    "updates" -> tr("Installed {version}", "version" to version.ifBlank { tr("unknown") })
    "performance" -> (when (p.pollSpeed) { "fast" -> tr("Fast updates"); "battery" -> tr("Battery saver"); else -> tr("Normal updates") }) +
        (if (p.reduceMotion) " · " + tr("reduced animations") else "")
    else -> tr("Version {version}", "version" to version.ifBlank { tr("unknown") })
}

@Composable
private fun SettingsOverview(p: Prefs, s: RemoteState, cfg: ServerConfig, version: String, modifier: Modifier, open: (String) -> Unit) {
    val scroll = rememberScrollState()
    // the categories glide in one after another each time the overview appears
    val arrival = rememberArrival("overview").takeIf { p.transitions }
    Column(
        modifier.edgeFade(scroll).verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (s.online && s.remoteOn && s.extNeverSet) ExtensionPrompt(Modifier.arrive(arrival)) { open("extension") }
        for ((label, all) in groups) {
            val cats = all.filter { catShown(it.key, s) }
            if (cats.isNotEmpty()) {
                // group names: small, in the accent colour, as in the web app and Refine
                Text(
                    tr(label), Modifier.arrive(arrival).padding(start = 8.dp, top = 10.dp), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                )
                Card(
                    Modifier.fillMaxWidth(), shape = PfShape.Lg,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column {
                        cats.forEachIndexed { i, c ->
                            CategoryRow(c, summaryText(c.key, p, s, cfg, version), divider = i > 0, arrival = arrival) { open(c.key) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun summaryText(key: String, p: Prefs, s: RemoteState, cfg: ServerConfig, version: String): String {
    if (key == "updates") {
        return if (UpdateManager.available(LocalContext.current)) tr("A new version is ready")
        else tr("Installed {version}", "version" to version.ifBlank { tr("unknown") })
    }
    // music on this phone: the page is about the phone's own sound
    if (key == "sound" && Remote.local) return tr("This phone") + " · ${LocalPlayer.phoneVolume()}% · ${phoneOutputName(LocalContext.current)}"
    return summaryOf(key, p, s, cfg, version)
}

/** One category: its icon on a tonal circle, its name and a line saying how it is set. [divider]: a hairline above it. */
@Composable
private fun CategoryRow(c: Cat, summary: String, divider: Boolean = false, arrival: Arrival? = null, onClick: () -> Unit) {
    val line = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    Row(
        Modifier.arrive(arrival).fillMaxWidth()
            .drawBehind {
                // from where the words start to the card's edge; drawn in the row so it glides in with it
                if (divider) {
                    val inset = 70.dp.toPx()
                    val rtl = layoutDirection == LayoutDirection.Rtl
                    drawLine(
                        line, Offset(if (rtl) 0f else inset, 0f), Offset(if (rtl) size.width - inset else size.width, 0f),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(c.icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(tr(c.title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        ShapeIconButton(Icons.Filled.ArrowBack, tr("Back to settings"), onBack)
        Text(tr(title), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}
