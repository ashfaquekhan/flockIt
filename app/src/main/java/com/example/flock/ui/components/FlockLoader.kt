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
 * The app's loading animation: a chick pecking at grain beside a feeder pan that slowly fills, drawn on
 * nothing (no box behind it), so it sits cleanly over any screen.
 */
@Composable
fun FlockLoader(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val t = rememberInfiniteTransition(label = "loader")
    val peck by t.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Restart), label = "peck")
    val fill by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2100, easing = LinearEasing), RepeatMode.Restart), label = "fill")
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val gold = Color(0xFFF2D17A); val body = Color(0xFFF6D45C); val ink = Color(0xFF1A1410)
        // feeder pan (white rim) filling with feed
        val panC = Offset(w * 0.74f, h * 0.80f); val panR = w * 0.16f
        drawOval(gold.copy(alpha = 0.25f + 0.6f * fill), Offset(panC.x - panR * 0.85f, panC.y - panR * 0.32f), Size(panR * 1.7f, panR * 0.64f))
        drawOval(Color.White.copy(alpha = 0.8f), Offset(panC.x - panR, panC.y - panR * 0.4f), Size(panR * 2, panR * 0.8f), style = Stroke(w * 0.035f))
        // grains hopping up as the chick pecks
        val hop = abs(sin(peck * PI)).toFloat()
        for (k in 0 until 3) drawCircle(gold, w * 0.025f, Offset(w * (0.30f + k * 0.06f), h * 0.88f - hop * h * (0.04f + 0.03f * k)))
        // chick: body, head bobbing down to peck, beak, eye
        val bodyC = Offset(w * 0.40f, h * 0.62f)
        drawOval(ink, Offset(bodyC.x - w * 0.19f, bodyC.y - h * 0.15f), Size(w * 0.38f, h * 0.30f))
        drawOval(body, Offset(bodyC.x - w * 0.175f, bodyC.y - h * 0.135f), Size(w * 0.35f, h * 0.27f))
        val dip = (if (peck < 0.45f) sin(peck / 0.45f * PI).toFloat() else 0f)
        val headC = Offset(w * 0.28f - dip * w * 0.04f, h * 0.40f + dip * h * 0.22f)
        drawCircle(ink, w * 0.12f, headC); drawCircle(body, w * 0.11f, headC)
        drawPath(Path().apply { moveTo(headC.x - w * 0.10f, headC.y - h * 0.02f); lineTo(headC.x - w * 0.19f, headC.y + h * 0.02f); lineTo(headC.x - w * 0.10f, headC.y + h * 0.05f); close() }, Color(0xFFF3B23C))
        drawCircle(ink, w * 0.022f, Offset(headC.x - w * 0.03f, headC.y - h * 0.025f))
        // legs
        drawLine(Color(0xFFF2A278), Offset(bodyC.x - w * 0.05f, bodyC.y + h * 0.12f), Offset(bodyC.x - w * 0.06f, h * 0.90f), w * 0.03f)
        drawLine(Color(0xFFF2A278), Offset(bodyC.x + w * 0.05f, bodyC.y + h * 0.12f), Offset(bodyC.x + w * 0.06f, h * 0.90f), w * 0.03f)
    }
}

/** Pull-to-refresh indicator: the loader slides down with the pull and pecks while refreshing — no box behind it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.FlockPullIndicator(state: PullToRefreshState, refreshing: Boolean) {
    val f = if (refreshing) 1f else state.distanceFraction.coerceIn(0f, 1f)
    if (f <= 0.01f) return
    Box(Modifier.align(Alignment.TopCenter).offset(y = (f * 56f).dp - 40.dp).alpha(f)) { FlockLoader() }
}
