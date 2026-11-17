// YTM Starcast. Copyright (C) 2026 dash1101. SPDX-License-Identifier: GPL-3.0-or-later
// Additional terms under GPL section 7: see NOTICE.
package app.pocketfader.client

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Seconds left on the sleep timer, counting down between polls. 0 when none is running. */
@Composable
fun rememberSleepLeft(s: RemoteState): Int {
    var now by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(s.sleepLeft, s.sleepAt) {
        while (s.sleepLeft > 0) {
            now = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    if (s.sleepLeft <= 0) return 0
    return (s.sleepLeft - ((maxOf(now, s.sleepAt) - s.sleepAt) / 1000).toInt()).coerceAtLeast(0)
}

/** The moon button next to the song title: shows the time left, and opens the timer choices. */
@Composable
fun SleepButton(s: RemoteState) {
    var open by remember { mutableStateOf(false) }
    val left = rememberSleepLeft(s)
    val active = s.sleepLeft > 0 && left > 0
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        if (active) Text(sleepText(left), style = MaterialTheme.typography.labelMedium, color = tint)
        PressIconButton(
            if (active) Icons.Filled.Bedtime else Icons.Outlined.Bedtime,
            if (active) tr("Sleep timer: {time} left", "time" to sleepText(left)) else tr("Sleep timer"), 48, 24, tint, active, true,
        ) { open = true }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(tr("Sleep timer")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (active) tr("Playback will pause in {time}.", "time" to sleepText(left))
                        else if (s.source == "phone") tr("Pause playback on this phone after…") else tr("Pause playback on the computer after…"),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    for (row in listOf(listOf(15, 30, 45), listOf(60, 90, 120))) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (m in row) FilterChip(colors = refineChipColors(onDialog = true), border = refineChipBorder(false), selected = false, onClick = { Remote.setSleep(m); open = false }, label = { Text(trn("{n} min", "{n} min", m)) })
                        }
                    }
                    FilterChip(colors = refineChipColors(onDialog = true), border = refineChipBorder(false), selected = false, onClick = { Remote.setSleep("track"); open = false }, label = { Text(tr("When this song ends")) })
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(tr("Close")) } },
            dismissButton = { if (active) TextButton(onClick = { Remote.setSleep(0); open = false }) { Text(tr("Turn off")) } },
        )
    }
}

private fun sleepText(sec: Int): String = when {
    sec >= 3600 -> tr("{h} h {m} min", "h" to sec / 3600, "m" to (sec % 3600) / 60)
    sec >= 60 -> trn("{n} min", "{n} min", (sec + 59) / 60)
    else -> tr("{n} s", "n" to sec)
}
