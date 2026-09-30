package com.example.flock.ui.screens

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ValuePresent
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** "06:00" → 6.0 */
fun hoursOf(hhmm: String): Double = hhmm.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 0) + (it.getOrNull(1)?.toIntOrNull() ?: 0) / 60.0 }
/** 6.5 → "06:30" (wraps round the day) */
fun hhmmOf(h: Double): String { val m = (((h % 24) + 24) % 24 * 60).roundToInt() % 1440; return String.format("%02d:%02d", m / 60, m % 60) }

/** A clock shift kept on the phone (per farm and clock), in hours. */
@Composable
fun rememberClockShift(key: String): MutableState<Double> {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("flockit_ui", Context.MODE_PRIVATE) }
    val state = remember(key) { mutableStateOf(prefs.getFloat("clock_$key", 0f).toDouble()) }
    return object : MutableState<Double> by state {
        override var value: Double
            get() = state.value
            set(v) { state.value = v; prefs.edit().putFloat("clock_$key", v.toFloat()).apply() }
    }
}

/**
 * 24-hour dial: midnight at the top, noon at the bottom. The coloured marks are the day's times; drag
 * the ring round and they all move together (the gaps stay the same), in 15-minute steps. The dark
 * band is the lights-off period, the small green pointer on the rim is the time now, and the middle
 * shows the next time and how long until it.
 */
@Composable
fun DayClock(
    baseTimes: List<Double>,
    shiftH: Double,
    onShift: (Double) -> Unit,
    color: Color,
    dark: Pair<Double, Double>?,
    zone: java.time.ZoneId,
    modifier: Modifier = Modifier,
    tag: String = "clock"
) {
    var drag by remember { mutableDoubleStateOf(0.0) }
    var nowH by remember { mutableDoubleStateOf(hourNow(zone)) }
    LaunchedEffect(zone) { while (true) { nowH = hourNow(zone); kotlinx.coroutines.delay(30_000) } }
    val times = baseTimes.map { ((it + shiftH + drag) % 24 + 24) % 24 }.sorted()
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        val dia = min(maxWidth.value, 280f)
        Canvas(
            Modifier.size(dia.dp).testTag(tag)
                .pointerInput(shiftH) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val r = min(size.width, size.height) * 0.31f
                        val d0 = (down.position - c).getDistance()
                        if (d0 < r * 0.55f || d0 > r * 1.45f) return@awaitEachGesture   // only the ring turns; elsewhere the page scrolls
                        down.consume()
                        fun ang(p: Offset) = atan2((p.x - c.x).toDouble(), (c.y - p.y).toDouble())
                        var prev = ang(down.position)
                        var total = 0.0
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            val a = ang(ch.position)
                            var da = a - prev
                            if (da > PI) da -= 2 * PI; if (da < -PI) da += 2 * PI
                            prev = a; total += da
                            drag = total / (2 * PI) * 24
                            ch.consume()
                        }
                        val snapped = (((shiftH + drag) * 4).roundToInt() / 4.0 % 24 + 24) % 24
                        drag = 0.0
                        onShift(if (snapped > 12) snapped - 24 else snapped)
                    }
                }
        ) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = min(size.width, size.height) * 0.31f
            fun at(h: Double, rad: Float): Offset { val a = h / 24 * 2 * PI; return Offset(c.x + rad * sin(a).toFloat(), c.y - rad * cos(a).toFloat()) }
            val px = density
            val paint = Paint().apply { isAntiAlias = true; typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER }
            fun text(t: String, p: Offset, sizeSp: Float, col: Color, bold: Boolean = false, align: Paint.Align = Paint.Align.CENTER) {
                paint.textSize = sizeSp * px; paint.color = col.toArgb(); paint.isFakeBoldText = bold; paint.textAlign = align
                drawContext.canvas.nativeCanvas.drawText(t, p.x, p.y + paint.textSize * 0.35f, paint)
            }
            // lights-off band inside the ring
            dark?.let { (a, b) ->
                val start = (a / 24 * 360 - 90).toFloat(); var sweep = ((b - a + 24) % 24 / 24 * 360).toFloat(); if (sweep == 0f) sweep = 0.01f
                val rr = r * 0.80f
                drawArc(Color.White.copy(alpha = 0.13f), start, sweep, false, Offset(c.x - rr, c.y - rr), Size(rr * 2, rr * 2), style = Stroke(9f * px, cap = StrokeCap.Butt))
            }
            // dial: ring, hour ticks, 00 / 06 / 12 / 18
            drawCircle(Color.White.copy(alpha = 0.35f), r, c, style = Stroke(1.4f * px))
            for (h in 0 until 24) {
                val major = h % 6 == 0
                drawLine(Color.White.copy(alpha = if (major) 0.7f else 0.3f), at(h.toDouble(), r - (if (major) 7f else 4f) * px), at(h.toDouble(), r), if (major) 1.6f * px else 1f * px)
            }
            listOf(0, 6, 12, 18).forEach { h -> text(String.format("%02d", h), at(h.toDouble(), r * 0.66f), 11f, Color.White.copy(alpha = 0.55f)) }
            // the ring of times: arcs between the marks, then the marks with their times outside
            if (times.size > 1) {
                val ringPath = Path()
                for (i in times.indices) {
                    val a0 = times[i]; val a1 = if (i + 1 < times.size) times[i + 1] else times[0] + 24
                    val steps = ((a1 - a0) * 4).toInt().coerceAtLeast(2)
                    for (k in 0..steps) { val p = at(a0 + (a1 - a0) * k / steps, r); if (k == 0) ringPath.moveTo(p.x, p.y) else ringPath.lineTo(p.x, p.y) }
                }
                drawPath(ringPath, color.copy(alpha = 0.35f), style = Stroke(2.5f * px))
            }
            times.forEach { t ->
                drawCircle(Color.Black, 8f * px, at(t, r)); drawCircle(color, 6.5f * px, at(t, r))
                // the time sits outside its mark: to the right on the right half, to the left on the left half
                val sa = sin(t / 24 * 2 * PI)
                val align = when { sa > 0.35 -> Paint.Align.LEFT; sa < -0.35 -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
                text(hhmmOf(t), at(t, r + (if (align == Paint.Align.CENTER) 20f else 13f) * px), 12f, color, align = align)
            }
            // now: a pointer on the rim
            val np = at(nowH, r + 3f * px); val a = nowH / 24 * 2 * PI
            val tip = at(nowH, r - 6f * px)
            val side = Offset(cos(a).toFloat(), sin(a).toFloat()) * (5f * px)
            val base = at(nowH, r + 9f * px)
            drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(base.x + side.x, base.y + side.y); lineTo(base.x - side.x, base.y - side.y); close() }, ValuePresent)
            drawLine(ValuePresent.copy(alpha = 0.5f), c, np, 1f * px)
            // middle: next time and hours to it
            val next = times.minByOrNull { ((it - nowH) % 24 + 24) % 24 }
            if (next != null) {
                val wait = ((next - nowH) % 24 + 24) % 24
                text("next", Offset(c.x, c.y - 17f * px), 10.5f, Color.White.copy(alpha = 0.55f))
                text(hhmmOf(next), c, 17f, color, bold = true)
                text(com.example.flock.ui.Fmt.n(wait, 1) + " h", Offset(c.x, c.y + 18f * px), 11f, Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

private fun hourNow(zone: java.time.ZoneId): Double { val z = java.time.ZonedDateTime.now(zone); return z.hour + z.minute / 60.0 }
