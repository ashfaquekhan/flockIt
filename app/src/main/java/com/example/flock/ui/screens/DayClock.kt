package com.example.flock.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.flock.ui.Fmt
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
private val SleepColor = Color(0xFF8FA8FF)
private val SleepFill = Color(0xFF2B3A67)
private val LightFill = Color(0xFFE9D9A8)

/** One kind of thing on the clock: its picture, name, colour and times. */
private data class Kind(val emoji: String, val name: String, val color: Color, val times: List<Double>)

private fun kindsOf(plan: DaySchedule.Plan) = listOf(
    Kind("🌾", "Feed", ValuePredicted, plan.feeds),      // 🌾
    Kind("💧", "Water", ValueMin, plan.refills),         // 💧
    Kind("🚶", "Walk", WalkColor, plan.walks)            // 🚶
)
private const val SLEEP = "😴"   // 😴
private const val SUN = "☀️"     // ☀️
private const val FIRE = "🔥"    // 🔥

private fun fwd(a: Double, b: Double) = ((b - a) % 24 + 24) % 24

/**
 * The farm's day on one 24-hour dial (midnight at the top) — to look at, not to set. The outer ring is the
 * day in sections: sleep, light, and the hot hours. Inside it each job has its own lane: walks, feed loads,
 * tank refills. A hand points at the time now. What is next, and every time, are listed under the dial.
 * The times come from [DaySchedule].
 */
@Composable
fun FarmDayClock(plan: DaySchedule.Plan, zone: java.time.ZoneId, modifier: Modifier = Modifier) {
    var nowH by remember { mutableDoubleStateOf(hourNow(zone)) }
    LaunchedEffect(zone) { while (true) { nowH = hourNow(zone); kotlinx.coroutines.delay(30_000) } }
    val kinds = kindsOf(plan)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val dia = min(maxWidth.value, 310f)
            Canvas(Modifier.size(dia.dp).testTag("dayClock")) { drawDay(plan, kinds, nowH) }
        }
        NextUp(kinds, plan, nowH)
        kinds.forEach { TimesRow(it, nowH) }
        SpanRow(SLEEP, "Sleep", SleepColor, plan.darkStart, plan.darkEnd)
        SpanRow(FIRE, "Hot", HotColor, plan.hotFrom, plan.hotTo)
    }
}

/** What comes next and in how long — under the dial, not inside it. Wraps to a second line at large text sizes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NextUp(kinds: List<Kind>, plan: DaySchedule.Plan, nowH: Double) {
    data class Ev(val h: Double, val emoji: String, val what: String, val col: Color)
    val evs = kinds.flatMap { k -> k.times.map { Ev(it, k.emoji, k.name, k.color) } } +
        Ev(plan.darkStart, SLEEP, "Lights off", SleepColor) + Ev(plan.darkEnd, SUN, "Lights on", LightFill)
    val e = evs.minByOrNull { fwd(nowH, it.h) } ?: return
    FlowRow(Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp).testTag("clockNext"),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Next", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f), maxLines = 1, softWrap = false)
        Text(e.emoji, Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
        Text(e.what, Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1, softWrap = false)
        Text(hhmmOf(e.h), Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = e.col, maxLines = 1, softWrap = false)
        Text("in " + Fmt.n(fwd(nowH, e.h), 1) + " h", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace), color = Color.White.copy(alpha = 0.65f), maxLines = 1, softWrap = false)
    }
}

/** One job: its picture, name, how many times, and every time (the next one bright, the ones gone by dim). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimesRow(k: Kind, nowH: Double) {
    val next = k.times.minByOrNull { fwd(nowH, it) }
    val fs = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceIn(1f, 1.5f)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(k.emoji, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(28.dp * fs))
        Text(k.name, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White.copy(alpha = 0.85f), modifier = Modifier.width(52.dp * fs), maxLines = 1, softWrap = false)
        Text("${k.times.size}×", style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = k.color, modifier = Modifier.width(30.dp * fs), maxLines = 1, softWrap = false)
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            k.times.sorted().forEach { t ->
                val isNext = t == next
                Text(hhmmOf(t), style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium),
                    color = if (isNext) k.color else k.color.copy(alpha = if (t < nowH) 0.4f else 0.75f), maxLines = 1, softWrap = false)
            }
        }
    }
}

/** A stretch of the day (sleep, the hot hours): from – to and how long, lined up with the times above. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpanRow(emoji: String, name: String, color: Color, from: Double, to: Double) {
    val fs = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceIn(1f, 1.5f)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(emoji, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(28.dp * fs))
        Text(name, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White.copy(alpha = 0.85f), modifier = Modifier.width((52.dp + 30.dp) * fs), maxLines = 1, softWrap = false)
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${hhmmOf(from)} – ${hhmmOf(to)}", style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = color, maxLines = 1, softWrap = false)
            Text(Fmt.n(fwd(from, to), 1) + " h", style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium), color = color.copy(alpha = 0.75f), maxLines = 1, softWrap = false)
        }
    }
}

private fun DrawScope.drawDay(plan: DaySchedule.Plan, kinds: List<Kind>, nowH: Double) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val px = density
    val rOut = min(size.width, size.height) / 2f
    val ringW = 20f * px
    val rDay = rOut - 30f * px                       // middle of the day ring
    // one lane per job, biggest circle for the job with the most times
    val lanes = listOf(rDay - 28f * px, rDay - 55f * px, rDay - 82f * px)   // walk, feed, water
    fun at(h: Double, rad: Float): Offset { val a = h / 24 * 2 * PI; return Offset(c.x + rad * sin(a).toFloat(), c.y - rad * cos(a).toFloat()) }
    fun arc(fromH: Double, toH: Double, rad: Float, w: Float, col: Color) {
        val start = (fromH / 24 * 360 - 90).toFloat(); var sweep = (fwd(fromH, toH) / 24 * 360).toFloat(); if (sweep == 0f) sweep = 360f
        drawArc(col, start, sweep, false, Offset(c.x - rad, c.y - rad), Size(rad * 2, rad * 2), style = Stroke(w, cap = StrokeCap.Butt))
    }
    val mono = Paint().apply { isAntiAlias = true; typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER }
    val emoji = Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER }
    fun text(t: String, p: Offset, sp: Float, col: Color, bold: Boolean = false) {
        mono.textSize = sp * px; mono.color = col.toArgb(); mono.isFakeBoldText = bold
        drawContext.canvas.nativeCanvas.drawText(t, p.x, p.y + mono.textSize * 0.35f, mono)
    }
    fun pic(e: String, p: Offset, sp: Float) {
        emoji.textSize = sp * px
        drawContext.canvas.nativeCanvas.drawText(e, p.x, p.y + emoji.textSize * 0.36f, emoji)
    }

    // the day in sections: sleep, light, hot — cut apart at their edges
    arc(plan.darkEnd, plan.darkStart, rDay, ringW, LightFill.copy(alpha = 0.30f))
    arc(plan.darkStart, plan.darkEnd, rDay, ringW, SleepFill)
    arc(plan.hotFrom, plan.hotTo, rDay, ringW, HotColor.copy(alpha = 0.75f))
    listOf(plan.darkStart, plan.darkEnd, plan.hotFrom, plan.hotTo).forEach { h ->
        drawLine(Color.Black, at(h, rDay - ringW / 2 - 1f * px), at(h, rDay + ringW / 2 + 1f * px), 3.5f * px)
    }
    drawCircle(Color.White.copy(alpha = 0.4f), rDay + ringW / 2, c, style = Stroke(1f * px))
    drawCircle(Color.White.copy(alpha = 0.4f), rDay - ringW / 2, c, style = Stroke(1f * px))
    pic(SLEEP, at(plan.darkStart + fwd(plan.darkStart, plan.darkEnd) / 2, rDay), 13f)
    pic(FIRE, at(plan.hotFrom + fwd(plan.hotFrom, plan.hotTo) / 2, rDay), 13f)
    // the sun in the longer light stretch (before or after the hot hours)
    val before = fwd(plan.darkEnd, plan.hotFrom); val after = fwd(plan.hotTo, plan.darkStart)
    pic(SUN, at(if (before >= after) plan.darkEnd + before / 2 else plan.hotTo + after / 2, rDay), 13f)

    // hours round the outside
    for (h in 0 until 24) {
        val major = h % 3 == 0
        drawLine(Color.White.copy(alpha = if (major) 0.75f else 0.35f), at(h.toDouble(), rDay + ringW / 2), at(h.toDouble(), rDay + ringW / 2 + (if (major) 6f else 3.5f) * px), (if (major) 1.6f else 1f) * px)
        if (major) text(String.format("%02d", h), at(h.toDouble(), rOut - 8f * px), 10.5f, Color.White.copy(alpha = if (h % 6 == 0) 0.8f else 0.5f))
    }

    // lanes: walk, feed, water — a faint circle each, the job's picture at every time
    val order = listOf(kinds[2], kinds[0], kinds[1])
    order.forEachIndexed { i, k ->
        val r = lanes[i]
        drawCircle(k.color.copy(alpha = 0.22f), r, c, style = Stroke(1f * px))
        k.times.forEach { t ->
            val p = at(t, r)
            drawCircle(Color.Black, 9.5f * px, p)
            drawCircle(k.color.copy(alpha = 0.9f), 9.5f * px, p, style = Stroke(1.3f * px))
            pic(k.emoji, p, 11.5f)
        }
    }

    // the time now: a hand from the middle to the ring, and a bright bar across the ring
    drawLine(ValuePresent.copy(alpha = 0.5f), at(nowH, 20f * px), at(nowH, rDay - ringW / 2), 1.4f * px, StrokeCap.Round)
    drawLine(Color.Black, at(nowH, rDay - ringW / 2 - 3f * px), at(nowH, rDay + ringW / 2 + 3f * px), 6.5f * px, StrokeCap.Round)
    drawLine(ValuePresent, at(nowH, rDay - ringW / 2 - 3f * px), at(nowH, rDay + ringW / 2 + 3f * px), 3.2f * px, StrokeCap.Round)
    drawCircle(Color.Black, 19f * px, c)
    drawCircle(ValuePresent.copy(alpha = 0.7f), 19f * px, c, style = Stroke(1.2f * px))
    text(hhmmOf(nowH), c, 10.5f, ValuePresent, bold = true)
}

private fun hourNow(zone: java.time.ZoneId): Double { val z = java.time.ZonedDateTime.now(zone); return z.hour + z.minute / 60.0 }
