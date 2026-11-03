package com.example.flock.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.StatusWarn

/**
 * Under the top bar: a moving bar and a line of text while the farm is loading from (or saving to) its sheet, and
 * a quiet note when the last load did not get through. A load is never silent.
 */
@Composable
fun LoadingStrip(loading: Boolean, syncStatus: String, loadedAtMs: Long, timeZone: String) {
    val offline = !loading && syncStatus == "offline"
    AnimatedVisibility(visible = loading || offline) {
        Column(Modifier.fillMaxWidth().testTag("loading_strip")) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = Color.White, trackColor = Color.White.copy(alpha = 0.12f))
            val since = if (loadedAtMs > 0) " · last loaded " + hhmm(loadedAtMs, timeZone) else ""
            Text(
                if (loading) "Loading the latest from the Google Sheet…" else "Could not reach the Google Sheet — showing what is on the phone$since",
                style = MaterialTheme.typography.labelMedium, color = if (offline) StatusWarn else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }
    }
}

private fun hhmm(ms: Long, timeZone: String): String = try {
    java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.of(timeZone)).toLocalTime().let { String.format("%02d:%02d", it.hour, it.minute) }
} catch (e: Exception) { "" }
