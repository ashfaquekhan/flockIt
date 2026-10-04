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

/** How much each feeding and each tank refill is (shown beside their times). */
data class DayAmounts(val bagsEachFeed: Double? = null, val bagsPerLine: Double? = null, val litresEachRefill: Double? = null)

/**
 * The farm's day — to look at, not to set. A 24-hour dial (midnight at the top, every hour numbered): the
 * outer ring is the day in sections (sleep, light, the hot hours); inside it each job has its own lane
 * (walks, feed loads, tank refills); a bar marks the time now. Under it: the day's totals, what is next,
 * and the whole day in order with its times and amounts. The times come from [DaySchedule].
 */
@Composable
fun FarmDayClock(plan: DaySchedule.Plan, zone: java.time.ZoneId, modifier: Modifier = Modifier, amounts: DayAmounts = DayAmounts()) {
    var nowH by remember { mutableDoubleStateOf(hourNow(zone)) }
    LaunchedEffect(zone) { while (true) { nowH = hourNow(zone); kotlinx.coroutines.delay(30_000) } }
    val kinds = kindsOf(plan)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val dia = min(maxWidth.value, 320f)
            Canvas(Modifier.size(dia.dp).testTag("dayClock")) { drawDay(plan, kinds, nowH) }
        }
        Totals(plan, kinds, amounts)
        NextUp(kinds, plan, nowH)
        Agenda(plan, kinds, amounts, nowH)
    }
}

/** The day's totals in clear numbers: how often and how much. */
@Composable
private fun Totals(plan: DaySchedule.Plan, kinds: List<Kind>, a: DayAmounts) {
    data class Cell(val emoji: String, val name: String, val value: String, val sub: String, val col: Color)
    val dark = fwd(plan.darkStart, plan.darkEnd)
    val cells = listOf(
        Cell(kinds[0].emoji, "Feed", "${plan.feeds.size}×", a.bagsEachFeed?.let { Fmt.n(it, 2) + " bags" } ?: "", ValuePredicted),
        Cell(kinds[1].emoji, "Water", "${plan.refills.size}×", a.litresEachRefill?.let { Fmt.n(it, 1) + " L" } ?: "", ValueMin),
        Cell(kinds[2].emoji, "Walk", "${plan.walks.size}×", "", WalkColor),
        Cell(SUN, "Light", Fmt.n(24 - dark, 1) + " h", "${hhmmOf(plan.darkEnd)}–${hhmmOf(plan.darkStart)}", LightFill),
        Cell(SLEEP, "Sleep", Fmt.n(dark, 1) + " h", "${hhmmOf(plan.darkStart)}–${hhmmOf(plan.darkEnd)}", SleepColor),
        Cell(FIRE, "Hot", Fmt.n(fwd(plan.hotFrom, plan.hotTo), 1) + " h", "${hhmmOf(plan.hotFrom)}–${hhmmOf(plan.hotTo)}", HotColor)
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cells.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { c ->
                    Row(Modifier.weight(1f).border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(c.emoji, style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
                        Column(Modifier.padding(start = 8.dp)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(c.name + " ", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.65f), maxLines = 1, softWrap = false)
                                Text(c.value, style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = c.col, maxLines = 1, softWrap = false)
                            }
                            if (c.sub.isNotEmpty()) Text(c.sub, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = c.col.copy(alpha = 0.8f), maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
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

/**
 * The whole day in order, one line each: the time, what to do (which of how many) and how much. What has
 * gone by is dim; the next one is outlined.
 */
@Composable
private fun Agenda(plan: DaySchedule.Plan, kinds: List<Kind>, a: DayAmounts, nowH: Double) {
    data class Item(val h: Double, val emoji: String, val what: String, val amount: String, val col: Color)
    val items = buildList {
        plan.refills.sorted().forEachIndexed { i, t -> add(Item(t, kinds[1].emoji, "Refill tank ${i + 1}/${plan.refills.size}", a.litresEachRefill?.let { Fmt.n(it, 1) + " L" } ?: "", ValueMin)) }
        plan.feeds.sorted().forEachIndexed { i, t -> add(Item(t, kinds[0].emoji, "Load feeders ${i + 1}/${plan.feeds.size}", a.bagsEachFeed?.let { Fmt.n(it, 2) + " bags" } ?: "", ValuePredicted)) }
        plan.walks.sorted().forEachIndexed { i, t -> add(Item(t, kinds[2].emoji, "Walk house ${i + 1}/${plan.walks.size}", "", WalkColor)) }
        add(Item(plan.darkEnd, SUN, "Lights on", Fmt.n(24 - fwd(plan.darkStart, plan.darkEnd), 1) + " h", LightFill))
        add(Item(plan.hotFrom, FIRE, "Hot hours start", "to " + hhmmOf(plan.hotTo), HotColor))
        add(Item(plan.darkStart, SLEEP, "Lights off", Fmt.n(fwd(plan.darkStart, plan.darkEnd), 1) + " h", SleepColor))
    }.sortedBy { fwd(plan.darkEnd - 1.0, it.h) }          // the farm's day starts an hour before the lights come on
    val next = items.minByOrNull { fwd(nowH, it.h) }
    // how far through the farm's day it is now, to dim what has gone by
    val nowPos = fwd(plan.darkEnd - 1.0, nowH)
    val fs = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceIn(1f, 1.5f)
    Column(Modifier.fillMaxWidth().testTag("clockAgenda"), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items.forEach { it ->
            val isNext = it === next
            val gone = !isNext && fwd(plan.darkEnd - 1.0, it.h) < nowPos
            val alpha = if (isNext) 1f else if (gone) 0.42f else 0.82f
            Row(Modifier.fillMaxWidth().then(if (isNext) Modifier.border(1.dp, it.col.copy(alpha = 0.7f), RoundedCornerShape(6.dp)) else Modifier).padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(hhmmOf(it.h), style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                    color = it.col.copy(alpha = alpha), modifier = Modifier.width(52.dp * fs), maxLines = 1, softWrap = false)
                Text(it.emoji, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(26.dp * fs), maxLines = 1, softWrap = false)
                Text(it.what, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = alpha), modifier = Modifier.weight(1f), maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (it.amount.isNotEmpty()) Text(it.amount, style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                    color = it.col.copy(alpha = alpha), maxLines = 1, softWrap = false)
            }
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
        // every hour numbered; the quarters of the day stand out
        text(String.format("%02d", h), at(h.toDouble(), rOut - 8f * px), if (h % 6 == 0) 11.5f else 9.5f,
            Color.White.copy(alpha = if (h % 6 == 0) 0.95f else if (major) 0.7f else 0.5f), bold = h % 6 == 0)
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
