package com.example.flock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GlassLine
import kotlinx.coroutines.delay

/** The function running now (null: none) — buttons read it to wait their turn. */
val LocalBusy = androidx.compose.runtime.staticCompositionLocalOf<String?> { null }

/**
 * While a function runs (saving a day, repairing the sheet …) every tap on the page waits: the screen takes no
 * touches, and after a moment a card says what is running and for how long, so nothing is pressed twice.
 */
@Composable
fun BusyOverlay(label: String?) {
    if (label == null) return
    var shown by remember(label) { mutableStateOf(false) }
    var secs by remember(label) { mutableIntStateOf(0) }
    LaunchedEffect(label) { delay(250); shown = true; while (true) { delay(1000); secs++ } }
    Box(
        Modifier.fillMaxSize()
            .background(if (shown) Color.Black.copy(alpha = 0.55f) else Color.Transparent)
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
            .testTag("busy_overlay"),
        contentAlignment = Alignment.Center
    ) {
        if (shown) Column(
            Modifier.padding(32.dp).background(Color.Black, RoundedCornerShape(16.dp)).border(1.dp, GlassLine, RoundedCornerShape(16.dp))
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
            Text("$label…", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal), color = Color.White, textAlign = TextAlign.Center)
            Text("Other buttons wait until it is done · $secs s", style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        }
    }
}
