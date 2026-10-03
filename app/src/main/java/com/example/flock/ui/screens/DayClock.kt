package com.example.flock.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.flock.domain.DaySchedule
import com.example.ui.theme.ValueMin
import com.example.ui.theme.ValuePredicted
import com.example.ui.theme.ValuePresent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** "06:00" → 6.0 */
fun hoursOf(hhmm: String): Double = hhmm.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 0) + (it.getOrNull(1)?.toIntOrNull() ?: 0) / 60.0 }
/** 6.5 → "06:30" (wraps round the day) */
fun hhmmOf(h: Double): String { val m = (((h % 24) + 24) % 24 * 60).roundToInt() % 1440; return String.format("%02d:%02d", m / 60, m % 60) }

private val WalkColor = Color.White
private val HotColor = Color(0xFFE5734B)

/**
 * The farm's day on one 24-hour dial (midnight at the top) — to look at, not to set: the dark band, the hot
 * hours, feed loads (gold), tank refills (cyan), walks (white) and a pointer at the time now. The middle
 * says what is next and in how long. The times come from [DaySchedule].
 */
@Composable
fun FarmDayClock(plan: DaySchedule.Plan, zone: java.time.ZoneId, modifier: Modifier = Modifier) {
    var nowH by remember { mutableDoubleStateOf(hourNow(zone)) }
    LaunchedEffect(zone) { while (true) { nowH = hourNow(zone); kotlinx.coroutines.delay(30_000) } }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val dia = min(maxWidth.value, 300f)
            Canvas(Modifier.size(dia.dp).testTag("dayClock")) { drawDay(plan, nowH) }
        }
        // legend: what each mark is, with its count / span
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Legend(ValuePredicted, "Feed", "${plan.feeds.size}×")
            Legend(ValueMin, "Water", "${plan.refills.size}×")
            Legend(WalkColor, "Walk", "${plan.walks.size}×")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Legend(Color.White.copy(alpha = 0.35f), "Dark", "${hhmmOf(plan.darkStart)}–${hhmmOf(plan.darkEnd)}")
            Legend(HotColor, "Hot", "${hhmmOf(plan.hotFrom)}–${hhmmOf(plan.hotTo)}")
        }
    }
}

@Composable
private fun Legend(c: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(9.dp)) { drawCircle(c) }
        Spacer(Modifier.width(5.dp))
        Text("$label ", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = if (c == WalkColor) Color.White else c)
    }
}

private fun DrawScope.drawDay(plan: DaySchedule.Plan, nowH: Double) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = min(size.width, size.height) * 0.33f
    val px = density
    fun at(h: Double, rad: Float): Offset { val a = h / 24 * 2 * PI; return Offset(c.x + rad * sin(a).toFloat(), c.y - rad * cos(a).toFloat()) }
    fun arc(fromH: Double, toH: Double, rad: Float, w: Float, col: Color) {
        val start = (fromH / 24 * 360 - 90).toFloat(); var sweep = (((toH - fromH) % 24 + 24) % 24 / 24 * 360).toFloat(); if (sweep == 0f) sweep = 360f
        drawArc(col, start, sweep, false, Offset(c.x - rad, c.y - rad), Size(rad * 2, rad * 2), style = Stroke(w, cap = StrokeCap.Butt))
    }
    val paint = Paint().apply { isAntiAlias = true; typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER }
    fun text(t: String, p: Offset, sp: Float, col: Color, bold: Boolean = false, align: Paint.Align = Paint.Align.CENTER) {
        paint.textSize = sp * px; paint.color = col.toArgb(); paint.isFakeBoldText = bold; paint.textAlign = align
        drawContext.canvas.nativeCanvas.drawText(t, p.x, p.y + paint.textSize * 0.35f, paint)
    }
    // the day: light ring with the dark band and the hot hours outside it
    arc(plan.darkEnd, plan.darkStart, r, 12f * px, Color(0xFFE9D9A8).copy(alpha = 0.30f))
    arc(plan.darkStart, plan.darkEnd, r, 12f * px, Color(0xFF3A4A6A).copy(alpha = 0.55f))
    arc(plan.hotFrom, plan.hotTo, r + 12f * px, 5f * px, HotColor.copy(alpha = 0.85f))
    drawCircle(Color.White.copy(alpha = 0.35f), r - 6f * px, c, style = Stroke(1f * px))
    drawCircle(Color.White.copy(alpha = 0.35f), r + 6f * px, c, style = Stroke(1f * px))
    for (h in 0 until 24) {
        val major = h % 6 == 0
        drawLine(Color.White.copy(alpha = if (major) 0.7f else 0.3f), at(h.toDouble(), r - 6f * px - (if (major) 6f else 3f) * px), at(h.toDouble(), r - 6f * px), if (major) 1.6f * px else 1f * px)
    }
    listOf(0, 6, 12, 18).forEach { h -> text(String.format("%02d", h), at(h.toDouble(), r * 0.68f), 11f, Color.White.copy(alpha = 0.55f)) }
    // a crescent in the dark, a sun in the light
    val darkMid = plan.darkStart + (((plan.darkEnd - plan.darkStart) % 24 + 24) % 24) / 2
    val moon = at(darkMid, r * 0.45f)
    drawCircle(Color.White.copy(alpha = 0.7f), 6f * px, moon); drawCircle(Color.Black, 5f * px, Offset(moon.x + 3f * px, moon.y - 2f * px))
    val lightMid = plan.darkEnd + (((plan.darkStart - plan.darkEnd) % 24 + 24) % 24) / 2
    val sun = at(lightMid, r * 0.45f)
    drawCircle(Color(0xFFF2D17A).copy(alpha = 0.8f), 4.5f * px, sun)
    for (k in 0 until 8) { val a = k * PI / 4; drawLine(Color(0xFFF2D17A).copy(alpha = 0.6f), Offset(sun.x + (7 * px * cos(a)).toFloat(), sun.y + (7 * px * sin(a)).toFloat()), Offset(sun.x + (10 * px * cos(a)).toFloat(), sun.y + (10 * px * sin(a)).toFloat()), 1.2f * px) }
    // walks: small white ticks inside the ring
    plan.walks.forEach { w -> drawLine(WalkColor, at(w, r - 18f * px), at(w, r - 11f * px), 2.2f * px, StrokeCap.Round) }
    // refills: cyan drops just inside the ring
    plan.refills.forEach { t ->
        val p = at(t, r - 2f * px)
        drawCircle(ValueMin, 4.2f * px, Offset(p.x, p.y + 1.5f * px))
        drawPath(Path().apply { moveTo(p.x, p.y - 5f * px); lineTo(p.x + 3.5f * px, p.y); lineTo(p.x - 3.5f * px, p.y); close() }, ValueMin)
    }
    // feeds: gold dots on the ring with their times outside
    plan.feeds.forEach { t ->
        drawCircle(Color.Black, 7.5f * px, at(t, r)); drawCircle(ValuePredicted, 6f * px, at(t, r))
        val sa = sin(t / 24 * 2 * PI)
        val align = when { sa > 0.35 -> Paint.Align.LEFT; sa < -0.35 -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
        text(hhmmOf(t), at(t, r + (if (align == Paint.Align.CENTER) 30f else 22f) * px), 11.5f, ValuePredicted, align = align)
    }
    // now
    val tip = at(nowH, r + 6f * px); val base = at(nowH, r + 19f * px); val a = nowH / 24 * 2 * PI
    val side = Offset(cos(a).toFloat(), sin(a).toFloat()) * (5f * px)
    drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(base.x + side.x, base.y + side.y); lineTo(base.x - side.x, base.y - side.y); close() }, ValuePresent)
    // middle: the next thing to do and how long until it
    data class Ev(val h: Double, val what: String, val col: Color)
    val evs = plan.feeds.map { Ev(it, "Feed", ValuePredicted) } + plan.refills.map { Ev(it, "Water", ValueMin) } + plan.walks.map { Ev(it, "Walk", WalkColor) }
    evs.minByOrNull { ((it.h - nowH) % 24 + 24) % 24 }?.let { e ->
        val wait = ((e.h - nowH) % 24 + 24) % 24
        text(e.what, Offset(c.x, c.y - 17f * px), 11f, e.col)
        text(hhmmOf(e.h), c, 17f, e.col, bold = true)
        text(com.example.flock.ui.Fmt.n(wait, 1) + " h", Offset(c.x, c.y + 18f * px), 11f, Color.White.copy(alpha = 0.7f))
    }
}

private fun hourNow(zone: java.time.ZoneId): Double { val z = java.time.ZonedDateTime.now(zone); return z.hour + z.minute / 60.0 }
