package com.example.flock.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.engine.CompanyStandard
import com.example.flock.engine.PhysiologicalEngine
import com.example.flock.network.HourPoint
import com.example.flock.ui.Fmt
import com.example.ui.theme.StatusCrit
import com.example.ui.theme.StatusWarn
import com.example.ui.theme.ValueCommercial
import com.example.ui.theme.ValueIdeal
import com.example.ui.theme.ValuePredicted
import com.example.ui.theme.ValuePresent
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Colour for the company's commercial standard (distinct from ideal blue and projected amber). */
val CompanyColor = ValueCommercial
private val OkColor = Color(0xFF46B98C)

// =================================== chart legend ===================================

@Composable
fun StandardsLegend() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        LegendSwatch(ValuePresent, "Present", dashed = false, thick = true)
        LegendSwatch(ValuePredicted, "Projected", dashed = true, thick = false)
        LegendSwatch(ValueIdeal, "Ideal", dashed = false, thick = false)
        LegendSwatch(CompanyColor, "Com", dashed = true, thick = false)
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String, dashed: Boolean, thick: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 22.dp, height = 10.dp)) {
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                strokeWidth = if (thick) 6f else 3.5f, cap = StrokeCap.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null)
        }
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, softWrap = false)
    }
}

// =================================== charts ===================================

private enum class Style { ACTUAL, PROJECTED, IDEAL, COMPANY, LIFTED }

private class Line(val label: String, val style: Style, val valueAt: (Int) -> Double?)

private fun colorOf(s: Style) = when (s) { Style.ACTUAL -> ValuePresent; Style.PROJECTED -> ValuePredicted; Style.IDEAL -> ValueIdeal; Style.COMPANY -> CompanyColor; Style.LIFTED -> Color.White }
private fun kindOf(s: Style) = when (s) { Style.ACTUAL -> ValueKind.PRESENT; Style.PROJECTED -> ValueKind.PREDICTED; Style.IDEAL -> ValueKind.IDEAL; Style.COMPANY -> ValueKind.COMMERCIAL; Style.LIFTED -> ValueKind.NEUTRAL }

/** What one chart shows: its lines, days, axis and the lifting days to mark. */
private class ChartSpec(
    val title: String, val unit: String, val lines: List<Line>, val fromDay: Int, val maxDay: Int,
    val yMin: Double, val yMax: Double, val yStep: Double, val decimals: Int, val companyBand: Boolean,
    val idealTag: String = "Ideal", val marks: List<Int> = emptyList()
) {
    /** a value as the boxes show it: head counts whole, the rest with their decimals */
    fun fmt(v: Double?) = if (decimals == 0) Fmt.i(v?.roundToInt()) else Fmt.n(v, decimals)
    fun tagOf(s: Style) = when (s) { Style.ACTUAL -> "Present"; Style.PROJECTED -> "Projected"; Style.IDEAL -> idealTag; Style.COMPANY -> "Commercial"; Style.LIFTED -> "Lifted" }
}

/** Draws [c] over the days [from]–[to] with the day [sel] picked: grid, company band, lines (present on top), lifting days. */
private fun DrawScope.drawChart(c: ChartSpec, from: Double, to: Double, sel: Int, gridC: Color, labelArgb: Int, selC: Color, textPx: Float) {
    val padL = 44f; val padB = 24f
    val gw = size.width - padL; val gh = size.height - padB
    val span = (to - from).coerceAtLeast(1.0)
    fun px(d: Int) = padL + ((d - from) / span * gw).toFloat()
    fun py(v: Double) = (gh - ((v.coerceIn(c.yMin, c.yMax) - c.yMin) / (c.yMax - c.yMin)) * gh).toFloat()
    val lbl = Paint().apply { color = labelArgb; textSize = textPx; isAntiAlias = true }
    var t = kotlin.math.ceil(c.yMin / c.yStep) * c.yStep
    while (t <= c.yMax + 1e-9) {
        val y = py(t)
        drawLine(gridC, Offset(padL, y), Offset(padL + gw, y), strokeWidth = 1f)
        drawContext.canvas.nativeCanvas.drawText(if (c.yStep >= 1000) Fmt.n(t / 1000, 1) + "k" else Fmt.n(t, 1), 0f, y + 9f, lbl)
        t += c.yStep
    }
    val d0 = kotlin.math.ceil(from).toInt(); val d1 = kotlin.math.floor(to).toInt()
    val every = listOf(1, 2, 5, 7, 10, 14).firstOrNull { (d1 - d0) / it <= 6 } ?: 21
    for (d in d0..d1) if (d % every == 0) drawContext.canvas.nativeCanvas.drawText("d$d", px(d) - 14f, size.height, lbl)
    clipRect(padL, 0f, size.width, size.height) {
        // the lifting days: a dashed line and an L at the top
        c.marks.filter { it in d0..d1 }.forEach { m ->
            drawLine(Color.White.copy(alpha = 0.55f), Offset(px(m), 0f), Offset(px(m), gh), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)))
            lbl.color = Color.White.toArgb(); drawContext.canvas.nativeCanvas.drawText("L", px(m) + 3f, 12f, lbl); lbl.color = labelArgb
        }
        if (c.companyBand) {
            val comp = c.lines.firstOrNull { it.style == Style.COMPANY }
            if (comp != null) {
                val ds = (max(CompanyStandard.TOLERANCE_FROM_DAY, c.fromDay)..c.maxDay).filter { comp.valueAt(it) != null }
                if (ds.size > 1) {
                    val band = Path().apply {
                        moveTo(px(ds.first()), py(comp.valueAt(ds.first())!! * 1.05))
                        ds.forEach { lineTo(px(it), py(comp.valueAt(it)!! * 1.05)) }
                        ds.reversed().forEach { lineTo(px(it), py(comp.valueAt(it)!! * 0.95)) }
                        close()
                    }
                    drawPath(band, CompanyColor.copy(alpha = 0.16f))
                }
            }
        }
        // reference lines first, present on top
        c.lines.sortedBy { when (it.style) { Style.ACTUAL -> 3; Style.LIFTED -> 2; Style.PROJECTED -> 1; else -> 0 } }.forEach { l ->
            val pts = (c.fromDay..c.maxDay).mapNotNull { d -> l.valueAt(d)?.let { Offset(px(d), py(it)) } }
            if (pts.size < 2) { pts.forEach { drawCircle(colorOf(l.style), 5f, it) }; return@forEach }
            val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
            when (l.style) {
                Style.ACTUAL -> {
                    val fill = Path().apply { addPath(path); lineTo(pts.last().x, gh); lineTo(pts.first().x, gh); close() }
                    drawPath(fill, Brush.verticalGradient(listOf(ValuePresent.copy(alpha = 0.25f), ValuePresent.copy(alpha = 0f)), startY = 0f, endY = gh))
                    drawPath(path, ValuePresent, style = Stroke(width = 5f, cap = StrokeCap.Round))
                }
                Style.PROJECTED -> drawPath(path, ValuePredicted, style = Stroke(width = 3.5f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 7f))))
                Style.IDEAL -> drawPath(path, ValueIdeal.copy(alpha = 0.95f), style = Stroke(width = 3.5f, cap = StrokeCap.Round))
                Style.COMPANY -> drawPath(path, CompanyColor, style = Stroke(width = 3.5f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))))
                Style.LIFTED -> drawPath(path, Color.White.copy(alpha = 0.85f), style = Stroke(width = 3f, cap = StrokeCap.Square))
            }
        }
        drawLine(selC.copy(alpha = 0.35f), Offset(px(sel), 0f), Offset(px(sel), gh), strokeWidth = 2f)
        c.lines.forEach { l -> l.valueAt(sel)?.let { v -> drawCircle(colorOf(l.style), 7f, Offset(px(sel), py(v))); drawCircle(Color.White, 2.8f, Offset(px(sel), py(v))) } }
    }
}

/**
 * Tap-to-inspect line chart over flock days: Present (thick, filled), Projected (dotted), Ideal · Ross (blue),
 * Commercial (violet dashed, with its ±5 % band from day 28 when [ChartSpec.companyBand]); lifting days dashed with an
 * L. The boxes above show every line's value for the day picked; the corner button opens it full screen, to zoom
 * (pinch) and move (drag) along the days.
 */
@Composable
private fun StandardChart(c: ChartSpec, markerDay: Int) {
    var sel by remember(markerDay) { mutableStateOf(markerDay.coerceIn(c.fromDay, c.maxDay)) }
    var zoomed by remember { mutableStateOf(false) }
    val gridC = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val selC = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(c.title + if (c.unit.isNotEmpty()) " (${c.unit})" else "", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            Text("Day $sel", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(8.dp))
            Text("⤢ Zoom", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White,
                modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).clickable { zoomed = true }
                    .padding(horizontal = 10.dp, vertical = 5.dp).testTag("zoom_${c.title.lowercase().replace(' ', '_')}"))
        }
        ValueRow(c.lines.map { l -> val v = l.valueAt(sel); V(c.fmt(v), if (v == null) ValueKind.NEUTRAL else kindOf(l.style), c.tagOf(l.style)) })
        Canvas(
            Modifier.fillMaxWidth().height(180.dp)
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                .pointerInput(c.maxDay, c.fromDay) {
                    detectTapGestures { o ->
                        val padL = 48f
                        val frac = ((o.x - padL) / (size.width - padL - 16f)).coerceIn(0f, 1f)
                        sel = (c.fromDay + frac * (c.maxDay - c.fromDay)).roundToInt()
                    }
                }
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) { drawChart(c, c.fromDay.toDouble(), c.maxDay.toDouble(), sel, gridC, labelArgb, selC, 10.sp.toPx()) }
    }
    if (zoomed) ZoomedChart(c, sel, { sel = it }) { zoomed = false }
}

/** A chart full screen: pinch to zoom into the days, drag to move along them, tap a day to read every line on it. */
@Composable
private fun ZoomedChart(c: ChartSpec, sel0: Int, onSel: (Int) -> Unit, onClose: () -> Unit) {
    var sel by remember { mutableStateOf(sel0) }
    var from by remember { mutableStateOf(c.fromDay.toDouble()) }
    var to by remember { mutableStateOf(c.maxDay.toDouble()) }
    val gridC = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val selC = MaterialTheme.colorScheme.onSurface
    androidx.compose.ui.window.Dialog(onDismissRequest = { onSel(sel); onClose() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).padding(12.dp).testTag("chart_zoomed"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.title + if (c.unit.isNotEmpty()) " (${c.unit})" else "", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                Text("Day $sel", style = MaterialTheme.typography.titleMedium.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Bold))
                Spacer(Modifier.width(10.dp))
                Text("Close", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White,
                    modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).clickable { onSel(sel); onClose() }
                        .padding(horizontal = 12.dp, vertical = 6.dp))
            }
            StandardsLegend()
            ValueRow(c.lines.map { l -> val v = l.valueAt(sel); V(c.fmt(v), if (v == null) ValueKind.NEUTRAL else kindOf(l.style), c.tagOf(l.style)) })
            Canvas(
                Modifier.fillMaxWidth().weight(1f)
                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val span = to - from
                            val padL = 52f
                            val atFrac = ((centroid.x - padL) / (size.width - padL)).coerceIn(0f, 1f)
                            val at = from + atFrac * span
                            val newSpan = (span / zoom).coerceIn(4.0, (c.maxDay - c.fromDay).toDouble().coerceAtLeast(4.0))
                            var nf = at - atFrac * newSpan - pan.x / (size.width - padL) * newSpan
                            nf = nf.coerceIn(c.fromDay.toDouble(), c.maxDay - newSpan)
                            from = nf; to = nf + newSpan
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { o ->
                            val padL = 52f
                            val frac = ((o.x - padL) / (size.width - padL - 16f)).coerceIn(0f, 1f)
                            sel = (from + frac * (to - from)).roundToInt().coerceIn(c.fromDay, c.maxDay)
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            ) { drawChart(c, from, to, sel, gridC, labelArgb, selC, 12.sp.toPx()) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All days" to (c.fromDay.toDouble() to c.maxDay.toDouble()), "Last 14" to (max(c.fromDay, sel - 10).toDouble() to min(c.maxDay, max(c.fromDay, sel - 10) + 14).toDouble()),
                    "Last 7" to (max(c.fromDay, sel - 5).toDouble() to min(c.maxDay, max(c.fromDay, sel - 5) + 7).toDouble())).forEach { (t, r) ->
                    Text(t, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(8.dp)).clickable { from = r.first; to = r.second }.padding(vertical = 8.dp))
                }
            }
            Text("Pinch to zoom into the days, drag to move along them, tap a day to read it. L = a lifting day.",
                style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

private fun chartMaxDay(d: OutputData) = max(d.harvestAge, d.day).coerceIn(14, CompanyStandard.MAX_DAY)
/** The top of an axis: the next whole [step] above [v] (with a little headroom). */
private fun niceTop(v: Double, step: Double): Double = kotlin.math.ceil(v * 1.04 / step) * step

/** The lifting days of the flock (marked on every chart). */
private fun liftDays(d: OutputData) = d.rows.filter { it.birdsLifted > 0 }.map { it.dayNumber }

/** Weight, FCR, cFCR, mortality and the birds in the house (live and lifted): Present and Projected vs Ideal (Ross) vs Commercial. */
@Composable
fun BirdCharts(d: OutputData) {
    val maxDay = chartMaxDay(d)
    val by = d.byDay
    val marks = liftDays(d)
    val mass = d.massByDay
    OutputCard(title = "Bird curves") {
        StandardsLegend()
        StandardChart(ChartSpec(
            "Body weight", "g",
            listOf(
                Line("Present", Style.ACTUAL) { day -> by[day]?.avgWeight?.takeIf { day <= d.day } },
                Line("Projected", Style.PROJECTED) { day -> d.proj[day]?.weightG },
                Line("Ideal", Style.IDEAL) { day -> PhysiologicalEngine.bwFromDay(day.toDouble(), d.breed) },
                Line("Commercial", Style.COMPANY) { day -> CompanyStandard.bw(day) }
            ),
            0, maxDay, 0.0, niceTop(maxOf(CompanyStandard.bw(maxDay) ?: 0.0, PhysiologicalEngine.bwFromDay(maxDay.toDouble(), d.breed)), 1000.0), 1000.0, 1, companyBand = true, marks = marks
        ), d.day)
        StandardChart(ChartSpec(
            "Birds in the house", "",
            listOf(
                Line("Present", Style.ACTUAL) { day -> by[day]?.takeIf { day <= minOf(d.day, d.lastEnteredDay) }?.liveBirds?.toDouble() },
                Line("Projected", Style.PROJECTED) { day -> d.proj[day]?.live },
                Line("Lifted", Style.LIFTED) { day -> if (day <= d.day) d.rows.filter { it.dayNumber <= day }.sumOf { it.birdsLifted }.toDouble().takeIf { marks.isNotEmpty() } else null },
                Line("Commercial", Style.COMPANY) { day -> d.entryBirds * (1 - CompanyStandard.cumMortPct(day) / 100.0) }
            ),
            0, maxDay, 0.0, niceTop(d.placed.toDouble().coerceAtLeast(1000.0), 2000.0), if (d.placed > 12000) 4000.0 else 2000.0, 0, companyBand = false, marks = marks
        ), d.day)
        StandardChart(ChartSpec(
            "FCR", "",
            listOf(
                Line("Present", Style.ACTUAL) { day -> by[day]?.takeIf { it.avgWeight != null && day in 5..d.day }?.let { mass[day]?.fcr ?: it.fcr } },
                Line("Projected", Style.PROJECTED) { day -> d.proj[day]?.fcr?.takeIf { day >= 5 } },
                Line("Ideal", Style.IDEAL) { day -> PhysiologicalEngine.stdFcrFromDay(day.toDouble(), d.breed) },
                Line("Commercial", Style.COMPANY) { day -> CompanyStandard.fcr(day) }
            ),
            5, maxDay, 0.6, 2.0, 0.2, 3, companyBand = true, marks = marks
        ), d.day)
        StandardChart(ChartSpec(
            "cFCR to 2 kg", "",
            listOf(
                Line("Present", Style.ACTUAL) { day -> by[day]?.takeIf { it.avgWeight != null && day in 5..d.day }?.let { mass[day]?.cFcrCompany ?: it.cFcr } },
                Line("Projected", Style.PROJECTED) { day -> val pj = d.proj[day]; val w = pj?.weightG; val f = pj?.fcr
                    if (w != null && f != null && day >= 5) PhysiologicalEngine.computeCorrectedFcr(w / 1000, f) else null },
                Line("Ideal", Style.IDEAL) { day -> val bw = PhysiologicalEngine.bwFromDay(day.toDouble(), d.breed); PhysiologicalEngine.computeCorrectedFcr(bw / 1000, PhysiologicalEngine.stdFcrFromDay(day.toDouble(), d.breed)) },
                Line("Commercial", Style.COMPANY) { day -> CompanyStandard.cfcr(day) }
            ),
            5, maxDay, 1.0, 1.8, 0.2, 3, companyBand = true, marks = marks
        ), d.day)
        StandardChart(ChartSpec(
            "Cumulative mortality", "%",
            listOf(
                Line("Present", Style.ACTUAL) { day -> by[day]?.cumMortPct?.takeIf { day <= minOf(d.day, d.lastEnteredDay) } },
                Line("Projected", Style.PROJECTED) { day -> d.proj[day]?.cumMortPct },
                Line("Ideal", Style.IDEAL) { day -> PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_MAXMORT_BY_AGE, day.toDouble()) },
                Line("Commercial", Style.COMPANY) { day -> CompanyStandard.cumMortPct(day) }
            ),
            0, maxDay, 0.0, niceTop(maxOf(CompanyStandard.cumMortPct(maxDay), d.mortTDPct ?: 0.0), 1.0), if (maxDay > 49) 2.0 else 1.0, 2, companyBand = false, marks = marks
        ), d.day)
    }
}

/** Feed per bird per day and cumulative feed per bird: Present vs Ideal (Ross) vs Commercial. */
@Composable
fun FeedCharts(d: OutputData) {
    val maxDay = chartMaxDay(d)
    val by = d.byDay
    val marks = liftDays(d)
    // The x-axis is the standard's day. Feed entered on flock day x is what the birds ate the day before —
    // the standard's day x (it starts at day 1) — so it is plotted at x against the standard's row x.
    val perBird = { day: Int ->
        val row = by[day]
        val live = by[day - 1]?.liveBirds?.takeIf { it > 0 }
        if (row == null || live == null || day > d.day) null else d.usedKg(row).takeIf { it > 0 }?.let { it * 1000 / live }
    }
    val cumPerBird = { day: Int ->
        val live = by[day]?.liveBirds?.takeIf { it > 0 }
        if (live == null || day > d.day) null
        else d.rows.filter { it.dayNumber <= day }.sumOf { d.usedKg(it) }.takeIf { it > 0 }?.let { it * 1000 / live }
    }
    val cumTop = niceTop(maxOf(CompanyStandard.cumFeed(maxDay) ?: 0.0, PhysiologicalEngine.cumFeedFromDay(maxDay.toDouble(), d.breed)), 1000.0)
    val dayTop = niceTop(maxOf(CompanyStandard.feedPerDay(maxDay) ?: 0.0, PhysiologicalEngine.dailyFeedFromDay(maxDay.toDouble(), d.breed)), 60.0)
    OutputCard(title = "Feed curves") {
        StandardsLegend()
        StandardChart(ChartSpec(
            "Feed per bird per day", "g",
            listOf(
                Line("Present", Style.ACTUAL) { day -> perBird(day) },
                Line("Projected", Style.PROJECTED) { day -> d.proj[day]?.feedPerBirdG },
                Line("Ideal", Style.IDEAL) { day -> PhysiologicalEngine.dailyFeedFromDay(max(1, day).toDouble(), d.breed) },
                Line("Commercial", Style.COMPANY) { day -> CompanyStandard.feedPerDay(day) }
            ),
            1, maxDay, 0.0, dayTop, 60.0, 1, companyBand = false, marks = marks
        ), max(1, d.day))
        StandardChart(ChartSpec(
            "Cumulative feed per bird", "g",
            listOf(
                Line("Present", Style.ACTUAL) { day -> cumPerBird(day) },
                Line("Projected", Style.PROJECTED) { day -> d.proj[day]?.cumFeedPerBirdG },
                Line("Ideal", Style.IDEAL) { day -> PhysiologicalEngine.cumFeedFromDay(day.toDouble(), d.breed) },
                Line("Commercial", Style.COMPANY) { day -> CompanyStandard.cumFeed(day) }
            ),
            1, maxDay, 0.0, cumTop, if (cumTop > 8000) 2000.0 else 1000.0, 1, companyBand = true, marks = marks
        ), max(1, d.day))
    }
}
