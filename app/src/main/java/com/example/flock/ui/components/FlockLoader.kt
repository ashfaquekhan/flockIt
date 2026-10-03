package com.example.flock.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The app's loading animation, drawn on nothing (no box behind it) so it sits cleanly over any screen:
 * a thin white arc going round. A plain placeholder until the app's own mark is chosen.
 */
@Composable
fun FlockLoader(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val t = rememberInfiniteTransition(label = "loader")
    val turn by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "turn")
    Canvas(modifier.size(size)) {
        val w = this.size.width * 0.11f
        val inset = w / 2
        drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, Offset(inset, inset), Size(this.size.width - w, this.size.height - w), style = Stroke(w))
        drawArc(Color.White, turn - 90f, 100f, false, Offset(inset, inset), Size(this.size.width - w, this.size.height - w),
            style = Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}

/** Pull-to-refresh indicator: the loader slides down with the pull and pecks while refreshing — no box behind it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.FlockPullIndicator(state: PullToRefreshState, refreshing: Boolean) {
    val f = if (refreshing) 1f else state.distanceFraction.coerceIn(0f, 1f)
    if (f <= 0.01f) return
    Box(Modifier.align(Alignment.TopCenter).offset(y = (f * 52f).dp - 34.dp).alpha(f)) { FlockLoader() }
}
