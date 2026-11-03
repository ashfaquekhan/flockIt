package com.example.flock.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.flock.engine.CompanyStandard
import com.example.flock.engine.PhysiologicalEngine
import com.example.flock.ui.Fmt
import com.example.ui.theme.ValueCommercial
import com.example.ui.theme.ValueIdeal
import com.example.ui.theme.ValueMax
import com.example.ui.theme.ValuePredicted
import com.example.ui.theme.ValuePresent
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Colour for the company's commercial standard (distinct from ideal blue and projected amber). */
val CompanyColor = ValueCommercial
/** Lifting days on every chart. */
private val LiftColor = ValueMax

private enum class Style { ACTUAL, PROJECTED, IDEAL, COMPANY, LIFTED }

private class Line(val label: String, val style: Style, val valueAt: (Int) -> Double?)

private fun colorOf(s: Style) = when (s) { Style.ACTUAL -> ValuePresent; Style.PROJECTED -> ValuePredicted; Style.IDEAL -> ValueIdeal; Style.COMPANY -> CompanyColor; Style.LIFTED -> Color.White }

/** What one chart shows: its lines, days, axis, and the lifting days (day → birds lifted). */
private class ChartSpec(
    val key: String, val title: String, val unit: String, val lines: List<Line>, val fromDay: Int, val maxDay: Int,
    val yMin: Double, val yMax: Double, val yStep: Double, val decimals: Int, val companyBand: Boolean,
    val lifts: Map<Int, Int> = emptyMap()
) {
    /** a value as the chart shows it: head counts whole, the rest with their decimals */
    fun fmt(v: Double?) = if (decimals == 0) Fmt.i(v?.roundToInt()) else Fmt.n(v, decimals)
    fun tagOf(s: Style) = when (s) { Style.ACTUAL -> "Present"; Style.PROJECTED -> "Projected"; Style.IDEAL -> "Ideal"; Style.COMPANY -> "Company"; Style.LIFTED -> "Lifted" }
}

private const val PAD_L = 48f
private const val PAD_B = 26f
private const val PAD_T = 26f

/**
 * Draws [c] over the days [from]–[to]: grid, the company's ±5 % band, lifting days as rose bands with an L badge, the
 * lines (present on top), and at the day [sel] a cursor with every line's value in a box.
 */
private fun DrawScope.drawChart(c: ChartSpec, from: Double, to: Double, sel: Int, labelArgb: Int, textPx: Float) {
    val px = density
    val padL = PAD_L * px; val padB = PAD_B * px; val padT = PAD_T * px
    val gw = size.width - padL - 14f * px; val gh = size.height - padB - padT
    val span = (to - from).coerceAtLeast(1.0)
    fun x(d: Int) = padL + ((d - from) / span * gw).toFloat()
    fun y(v: Double) = padT + (gh - ((v.coerceIn(c.yMin, c.yMax) - c.yMin) / (c.yMax - c.yMin)) * gh).toFloat()
    val lbl = Paint().apply { color = labelArgb; textSize = textPx; isAntiAlias = true; typeface = com.example.ui.theme.AppFonts.mono }
    var t = kotlin.math.ceil(c.yMin / c.yStep) * c.yStep
    while (t <= c.yMax + 1e-9) {
        drawLine(Color.White.copy(alpha = 0.10f), Offset(padL, y(t)), Offset(padL + gw, y(t)), 1f)
        lbl.textAlign = Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText(if (c.yStep >= 1000) Fmt.n(t / 1000, 1) + "k" else Fmt.n(t, if (c.yStep < 1) 1 else 0), padL - 6f * px, y(t) + textPx / 3, lbl)
        t += c.yStep
    }
    val d0 = kotlin.math.ceil(from).toInt(); val d1 = kotlin.math.floor(to).toInt()
    val every = listOf(1, 2, 5, 7, 10, 14).firstOrNull { (d1 - d0) / it <= 6 } ?: 21
    lbl.textAlign = Paint.Align.CENTER
    for (d in d0..d1) if (d % every == 0) drawContext.canvas.nativeCanvas.drawText("d$d", x(d), size.height - 6f * px, lbl)
    val dayW = (gw / span).toFloat()
    clipRect(padL, 0f, padL + gw + 10f * px, size.height) {
        // lifting days: a rose band the width of the day and an L badge on top
        c.lifts.keys.filter { it in d0..d1 }.forEach { m ->
            val bw = max(6f * px, dayW)
            drawRect(LiftColor.copy(alpha = 0.32f), Offset(x(m) - bw / 2, padT), Size(bw, gh))
            drawCircle(LiftColor, 9f * px, Offset(x(m), padT - 12f * px))
            val b = Paint().apply { isAntiAlias = true; textSize = 11f * px; isFakeBoldText = true; color = android.graphics.Color.BLACK; textAlign = Paint.Align.CENTER }
            drawContext.canvas.nativeCanvas.drawText("L", x(m), padT - 8f * px, b)
        }
        if (c.companyBand) c.lines.firstOrNull { it.style == Style.COMPANY }?.let { comp ->
            val ds = (max(CompanyStandard.TOLERANCE_FROM_DAY, c.fromDay)..c.maxDay).filter { comp.valueAt(it) != null }
            if (ds.size > 1) drawPath(Path().apply {
                moveTo(x(ds.first()), y(comp.valueAt(ds.first())!! * 1.05))
                ds.forEach { lineTo(x(it), y(comp.valueAt(it)!! * 1.05)) }
                ds.reversed().forEach { lineTo(x(it), y(comp.valueAt(it)!! * 0.95)) }
                close()
            }, CompanyColor.copy(alpha = 0.12f))
        }
        c.lines.sortedBy { when (it.style) { Style.ACTUAL -> 3; Style.LIFTED -> 2; Style.PROJECTED -> 1; else -> 0 } }.forEach { l ->
            val pts = (c.fromDay..c.maxDay).mapNotNull { d -> l.valueAt(d)?.let { Offset(x(d), y(it)) } }
            if (pts.size < 2) { pts.forEach { drawCircle(colorOf(l.style), 4f * px, it) }; return@forEach }
            val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
            when (l.style) {
                Style.ACTUAL -> {
                    drawPath(Path().apply { addPath(path); lineTo(pts.last().x, padT + gh); lineTo(pts.first().x, padT + gh); close() },
                        Brush.verticalGradient(listOf(ValuePresent.copy(alpha = 0.22f), ValuePresent.copy(alpha = 0f)), startY = padT, endY = padT + gh))
                    drawPath(path, ValuePresent, style = Stroke(3f * px, cap = StrokeCap.Round))
                }
                Style.PROJECTED -> drawPath(path, ValuePredicted, style = Stroke(2f * px, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * px, 4f * px))))
                Style.IDEAL -> drawPath(path, ValueIdeal.copy(alpha = 0.9f), style = Stroke(1.5f * px))
                Style.COMPANY -> drawPath(path, CompanyColor, style = Stroke(2f * px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f * px, 5f * px))))
                Style.LIFTED -> drawPath(path, Color.White.copy(alpha = 0.85f), style = Stroke(2f * px))
            }
        }
        // the cursor and the dots on it
        drawLine(Color.White.copy(alpha = 0.6f), Offset(x(sel), padT), Offset(x(sel), padT + gh), 1.2f * px)
        c.lines.forEach { l -> l.valueAt(sel)?.let { v -> drawCircle(colorOf(l.style), 5f * px, Offset(x(sel), y(v))); drawCircle(Color.Black, 2f * px, Offset(x(sel), y(v))) } }
    }
    // the box with every line's value on the day picked, on the side away from the cursor
    val rows = buildList {
        add("Day $sel" + (c.lifts[sel]?.let { " · lifted ${Fmt.i(it)}" } ?: "") to (if (c.lifts.containsKey(sel)) LiftColor else Color.White))
        c.lines.forEach { l -> l.valueAt(sel)?.let { add("${c.tagOf(l.style)} ${c.fmt(it)}" to colorOf(l.style)) } }
    }
    val tp = Paint().apply { isAntiAlias = true; textSize = textPx * 1.05f; typeface = com.example.ui.theme.AppFonts.mono }
    val bw = rows.maxOf { tp.measureText(it.first) } + 14f * px
    val lh = tp.textSize * 1.3f
    val bh = lh * rows.size + 8f * px
    val bx = if (x(sel) > padL + gw / 2) padL + 6f * px else padL + gw - bw - 6f * px
    val by = padT + 4f * px
    drawRoundRect(Color.Black.copy(alpha = 0.85f), Offset(bx, by), Size(bw, bh), CornerRadius(8f * px))
    drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(bx, by), Size(bw, bh), CornerRadius(8f * px), style = Stroke(1f))
    rows.forEachIndexed { i, (txt, col) -> tp.color = col.toArgb(); drawContext.canvas.nativeCanvas.drawText(txt, bx + 7f * px, by + 4f * px + lh * (i + 0.8f), tp) }
}

/** The lines of a chart as small marks with their names. */
@Composable
private fun ChartKey(c: ChartSpec) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        c.lines.forEach { l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(width = 16.dp, height = 8.dp)) {
                    drawLine(colorOf(l.style), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), if (l.style == Style.ACTUAL) 3.dp.toPx() else 2.dp.toPx(),
                        pathEffect = when (l.style) { Style.PROJECTED -> PathEffect.dashPathEffect(floatArrayOf(4f, 6f)); Style.COMPANY -> PathEffect.dashPathEffect(floatArrayOf(10f, 6f)); else -> null })
                }
                Spacer(Modifier.width(4.dp))
                Text(c.tagOf(l.style), style = MaterialTheme.typography.labelSmall, color = colorOf(l.style), maxLines = 1, softWrap = false)
            }
        }
        if (c.lifts.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(LiftColor, RoundedCornerShape(5.dp)))
            Spacer(Modifier.width(4.dp))
            Text("Lifting", style = MaterialTheme.typography.labelSmall, color = LiftColor, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * The flock's graphs in one card: pick what to see; drag along the chart to read any day (every line's value shows in
 * the box); lifting days are rose bands with an L. Full screen to zoom.
 */
@Composable
fun GraphsCard(d: OutputData) {
    val specs = remember(d) { chartSpecs(d) }
    var pick by rememberSaveable { mutableStateOf("weight") }
    val c = specs.firstOrNull { it.key == pick } ?: specs.first()
    var sel by remember(d.day, c.key) { mutableStateOf(d.day.coerceIn(c.fromDay, c.maxDay)) }
    var zoomed by remember { mutableStateOf(false) }
    val labelArgb = Color.White.copy(alpha = 0.6f).toArgb()
    OutputCard(title = "Graphs") {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            specs.forEach { s ->
                val on = s.key == c.key
                Text(s.title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
                    color = Color.White.copy(alpha = if (on) 1f else 0.7f),
                    modifier = Modifier.border(if (on) 2.dp else 1.dp, Color.White.copy(alpha = if (on) 1f else 0.3f), RoundedCornerShape(10.dp))
                        .clickable { pick = s.key }.padding(horizontal = 12.dp, vertical = 8.dp).testTag("chart_pick_${s.key}"))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ChartKey(c) }
            Spacer(Modifier.width(8.dp))
            Text("Zoom", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White,
                modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).clickable { zoomed = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp).testTag("chart_zoom"))
        }
        Canvas(
            Modifier.fillMaxWidth().height(260.dp).testTag("chart")
                .pointerInput(c.key) {
                    fun dayAt(x: Float): Int {
                        val padL = PAD_L * density
                        return (c.fromDay + ((x - padL) / (size.width - padL)).coerceIn(0f, 1f) * (c.maxDay - c.fromDay)).roundToInt()
                    }
                    detectTapGestures { o -> sel = dayAt(o.x) }
                }
                .pointerInput(c.key) {
                    fun dayAt(x: Float): Int {
                        val padL = PAD_L * density
                        return (c.fromDay + ((x - padL) / (size.width - padL)).coerceIn(0f, 1f) * (c.maxDay - c.fromDay)).roundToInt()
                    }
                    detectHorizontalDragGestures { ch, _ -> ch.consume(); sel = dayAt(ch.position.x) }
                }
        ) { drawChart(c, c.fromDay.toDouble(), c.maxDay.toDouble(), sel, labelArgb, 11f * density) }
    }
    if (zoomed) ZoomedChart(c, sel, { sel = it }) { zoomed = false }
}

/** A chart full screen: one finger reads the days, two fingers zoom into them and move along them. */
@Composable
private fun ZoomedChart(c: ChartSpec, sel0: Int, onSel: (Int) -> Unit, onClose: () -> Unit) {
    var sel by remember { mutableStateOf(sel0) }
    var from by remember { mutableStateOf(c.fromDay.toDouble()) }
    var to by remember { mutableStateOf(c.maxDay.toDouble()) }
    val labelArgb = Color.White.copy(alpha = 0.65f).toArgb()
    androidx.compose.ui.window.Dialog(onDismissRequest = { onSel(sel); onClose() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).padding(12.dp).testTag("chart_zoomed"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.title + if (c.unit.isNotEmpty()) " · ${c.unit}" else "", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                Text("Close", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White,
                    modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).clickable { onSel(sel); onClose() }
                        .padding(horizontal = 12.dp, vertical = 6.dp))
            }
            ChartKey(c)
            Canvas(
                Modifier.fillMaxWidth().weight(1f)
                    .pointerInput(Unit) {
                        val padL = PAD_L * density
                        fun dayAt(x: Float) = (from + ((x - padL) / (size.width - padL)).coerceIn(0f, 1f) * (to - from)).roundToInt().coerceIn(c.fromDay, c.maxDay)
                        awaitEachGesture {
                            val down = awaitFirstDown(); sel = dayAt(down.position.x)
                            do {
                                val ev = awaitPointerEvent()
                                val pressed = ev.changes.filter { it.pressed }
                                if (pressed.size >= 2) {
                                    val span = to - from
                                    val newSpan = (span / ev.calculateZoom()).coerceIn(4.0, (c.maxDay - c.fromDay).toDouble().coerceAtLeast(4.0))
                                    val mid = (from + to) / 2 - ev.calculatePan().x / (size.width - padL) * span
                                    from = (mid - newSpan / 2).coerceIn(c.fromDay.toDouble(), c.maxDay - newSpan); to = from + newSpan
                                } else if (pressed.size == 1) sel = dayAt(pressed[0].position.x)
                                ev.changes.forEach { it.consume() }
                            } while (ev.changes.any { it.pressed })
                        }
                    }
            ) { drawChart(c, from, to, sel, labelArgb, 12f * density) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All days" to (c.fromDay.toDouble() to c.maxDay.toDouble()),
                    "14 days" to (max(c.fromDay, sel - 10).toDouble() to min(c.maxDay, max(c.fromDay, sel - 10) + 14).toDouble()),
                    "7 days" to (max(c.fromDay, sel - 5).toDouble() to min(c.maxDay, max(c.fromDay, sel - 5) + 7).toDouble())).forEach { (t, r) ->
                    Text(t, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(8.dp)).clickable { from = r.first; to = r.second }.padding(vertical = 10.dp))
                }
            }
        }
    }
}

private fun chartMaxDay(d: OutputData) = max(d.harvestAge, d.day).coerceIn(14, CompanyStandard.MAX_DAY)
/** The top of an axis: the next whole [step] above [v] (with a little headroom). */
private fun niceTop(v: Double, step: Double): Double = kotlin.math.ceil(v * 1.04 / step) * step

/** Every chart of the flock: weight, birds in the house, FCR, cFCR, mortality, feed a day and in total. */
private fun chartSpecs(d: OutputData): List<ChartSpec> {
    val maxDay = chartMaxDay(d)
    val by = d.byDay
    val lifts = d.rows.filter { it.birdsLifted > 0 }.associate { it.dayNumber to it.birdsLifted }
    val mass = d.massByDay
    // feed entered on flock day x is what the birds ate the day before — the standard's day x — so it is plotted at x
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
    return listOf(
        ChartSpec("weight", "Weight", "g", listOf(
            Line("Present", Style.ACTUAL) { day -> by[day]?.avgWeight?.takeIf { day <= d.day } },
            Line("Projected", Style.PROJECTED) { day -> d.proj[day]?.weightG },
            Line("Ideal", Style.IDEAL) { day -> PhysiologicalEngine.bwFromDay(day.toDouble(), d.breed) },
            Line("Company", Style.COMPANY) { day -> CompanyStandard.bw(day) }
        ), 0, maxDay, 0.0, niceTop(maxOf(CompanyStandard.bw(maxDay) ?: 0.0, PhysiologicalEngine.bwFromDay(maxDay.toDouble(), d.breed)), 1000.0), 1000.0, 1, true, lifts),
        ChartSpec("birds", "Birds", "", listOf(
            Line("Present", Style.ACTUAL) { day -> by[day]?.takeIf { day <= minOf(d.day, d.lastEnteredDay) }?.liveBirds?.toDouble() },
            Line("Projected", Style.PROJECTED) { day -> d.proj[day]?.live },
            Line("Lifted", Style.LIFTED) { day -> if (day <= d.day && lifts.isNotEmpty()) d.rows.filter { it.dayNumber <= day }.sumOf { it.birdsLifted }.toDouble() else null },
            Line("Company", Style.COMPANY) { day -> d.entryBirds * (1 - CompanyStandard.cumMortPct(day) / 100.0) }
        ), 0, maxDay, 0.0, niceTop(d.placed.toDouble().coerceAtLeast(1000.0), 2000.0), if (d.placed > 12000) 4000.0 else 2000.0, 0, false, lifts),
        ChartSpec("fcr", "FCR", "", listOf(
            Line("Present", Style.ACTUAL) { day -> by[day]?.takeIf { it.avgWeight != null && day in 5..d.day }?.let { mass[day]?.fcr ?: it.fcr } },
            Line("Projected", Style.PROJECTED) { day -> d.proj[day]?.fcr?.takeIf { day >= 5 } },
            Line("Ideal", Style.IDEAL) { day -> PhysiologicalEngine.stdFcrFromDay(day.toDouble(), d.breed) },
            Line("Company", Style.COMPANY) { day -> CompanyStandard.fcr(day) }
        ), 5, maxDay, 0.6, 2.0, 0.2, 3, true, lifts),
        ChartSpec("cfcr", "cFCR", "2 kg", listOf(
            Line("Present", Style.ACTUAL) { day -> by[day]?.takeIf { it.avgWeight != null && day in 5..d.day }?.let { mass[day]?.cFcrCompany ?: it.cFcr } },
            Line("Projected", Style.PROJECTED) { day -> val pj = d.proj[day]; val w = pj?.weightG; val f = pj?.fcr
                if (w != null && f != null && day >= 5) PhysiologicalEngine.computeCorrectedFcr(w / 1000, f) else null },
            Line("Ideal", Style.IDEAL) { day -> val bw = PhysiologicalEngine.bwFromDay(day.toDouble(), d.breed); PhysiologicalEngine.computeCorrectedFcr(bw / 1000, PhysiologicalEngine.stdFcrFromDay(day.toDouble(), d.breed)) },
            Line("Company", Style.COMPANY) { day -> CompanyStandard.cfcr(day) }
        ), 5, maxDay, 1.0, 1.8, 0.2, 3, true, lifts),
        ChartSpec("mort", "Mortality", "%", listOf(
            Line("Present", Style.ACTUAL) { day -> by[day]?.cumMortPct?.takeIf { day <= minOf(d.day, d.lastEnteredDay) } },
            Line("Projected", Style.PROJECTED) { day -> d.proj[day]?.cumMortPct },
            Line("Ideal", Style.IDEAL) { day -> PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_MAXMORT_BY_AGE, day.toDouble()) },
            Line("Company", Style.COMPANY) { day -> CompanyStandard.cumMortPct(day) }
        ), 0, maxDay, 0.0, niceTop(maxOf(CompanyStandard.cumMortPct(maxDay), d.mortTDPct ?: 0.0), 1.0), if (maxDay > 49) 2.0 else 1.0, 2, false, lifts),
        ChartSpec("feed", "Feed a day", "g a bird", listOf(
            Line("Present", Style.ACTUAL) { day -> perBird(day) },
            Line("Projected", Style.PROJECTED) { day -> d.proj[day]?.feedPerBirdG },
            Line("Ideal", Style.IDEAL) { day -> PhysiologicalEngine.dailyFeedFromDay(max(1, day).toDouble(), d.breed) },
            Line("Company", Style.COMPANY) { day -> CompanyStandard.feedPerDay(day) }
        ), 1, maxDay, 0.0, dayTop, 60.0, 1, false, lifts),
        ChartSpec("cumfeed", "Feed total", "g a bird", listOf(
            Line("Present", Style.ACTUAL) { day -> cumPerBird(day) },
            Line("Projected", Style.PROJECTED) { day -> d.proj[day]?.cumFeedPerBirdG },
            Line("Ideal", Style.IDEAL) { day -> PhysiologicalEngine.cumFeedFromDay(day.toDouble(), d.breed) },
            Line("Company", Style.COMPANY) { day -> CompanyStandard.cumFeed(day) }
        ), 1, maxDay, 0.0, cumTop, if (cumTop > 8000) 2000.0 else 1000.0, 1, true, lifts)
    )
}
