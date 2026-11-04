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
import androidx.compose.ui.layout.layout
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
private fun DrawScope.drawChart(c: ChartSpec, from: Double, to: Double, sel: Int, labelArgb: Int, textPx: Float, dotLabels: Boolean = false) {
    val px = density
    val padL = PAD_L * px; val padB = PAD_B * px; val padT = PAD_T * px
    val inset = 12f * px
    val gw = size.width - padL - 2 * inset - 18f * px; val gh = size.height - padB - padT
    val span = (to - from).coerceAtLeast(1.0)
    fun x(d: Int) = padL + inset + ((d - from) / span * gw).toFloat()
    fun y(v: Double) = padT + (gh - ((v.coerceIn(c.yMin, c.yMax) - c.yMin) / (c.yMax - c.yMin)) * gh).toFloat()
    val lbl = Paint().apply { color = labelArgb; textSize = textPx; isAntiAlias = true; typeface = com.example.ui.theme.AppFonts.mono }
    var t = kotlin.math.ceil(c.yMin / c.yStep) * c.yStep
    while (t <= c.yMax + 1e-9) {
        drawLine(Color.White.copy(alpha = 0.10f), Offset(padL, y(t)), Offset(padL + gw + 2 * inset, y(t)), 1f)
        lbl.textAlign = Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText(if (c.yStep >= 1000) Fmt.n(t / 1000, 1) + "k" else Fmt.n(t, if (c.yStep < 1) 1 else 0), padL - 6f * px, y(t) + textPx / 3, lbl)
        t += c.yStep
    }
    val d0 = kotlin.math.ceil(from).toInt(); val d1 = kotlin.math.floor(to).toInt()
    val every = listOf(1, 2, 5, 7, 10, 14).firstOrNull { (d1 - d0) / it <= 6 } ?: 21
    lbl.textAlign = Paint.Align.CENTER
    for (d in d0..d1) if (d % every == 0) drawContext.canvas.nativeCanvas.drawText("d$d", x(d), size.height - 6f * px, lbl)
    val dayW = (gw / span).toFloat()
    clipRect(padL, 0f, size.width, size.height) {
        // lifting days: a rose band the width of the day and a numbered badge on top (L1, L2 … in the order lifted)
        val liftOrder = c.lifts.keys.sorted()
        c.lifts.keys.filter { it in d0..d1 }.forEach { m ->
            val bw = max(6f * px, dayW)
            drawRect(LiftColor.copy(alpha = 0.32f), Offset(x(m) - bw / 2, padT), Size(bw, gh))
            drawRoundRect(LiftColor, Offset(x(m) - 11f * px, padT - 21f * px), Size(22f * px, 16f * px), CornerRadius(8f * px))
            val b = Paint().apply { isAntiAlias = true; textSize = 10.5f * px; color = android.graphics.Color.BLACK; textAlign = Paint.Align.CENTER; typeface = com.example.ui.theme.AppFonts.mono }
            drawContext.canvas.nativeCanvas.drawText("L${liftOrder.indexOf(m) + 1}", x(m), padT - 9f * px, b)
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
        // each line's value beside its dot (where they would sit on each other, the box keeps the full list)
        if (dotLabels) {
            val dp = Paint().apply { isAntiAlias = true; textSize = textPx; typeface = com.example.ui.theme.AppFonts.mono }
            val right = x(sel) < padL + gw * 0.6f
            val used = mutableListOf<Float>()
            c.lines.mapNotNull { l -> l.valueAt(sel)?.let { l to it } }.sortedBy { y(it.second) }.forEach { (l, v) ->
                var yy = y(v) + textPx / 3
                while (used.any { kotlin.math.abs(it - yy) < textPx * 1.1f }) yy += textPx * 1.1f
                used += yy
                dp.color = colorOf(l.style).toArgb(); dp.textAlign = if (right) Paint.Align.LEFT else Paint.Align.RIGHT
                val tx = if (right) x(sel) + 9f * px else x(sel) - 9f * px
                val t = c.fmt(v); val w = dp.measureText(t)
                drawRoundRect(Color.Black.copy(alpha = 0.75f), Offset(if (right) tx - 2f * px else tx - w - 2f * px, yy - textPx), Size(w + 4f * px, textPx * 1.3f), CornerRadius(3f * px))
                drawContext.canvas.nativeCanvas.drawText(t, tx, yy, dp)
            }
        }
    }
    // the box with every line's value on the day picked, on the side away from the cursor
    val rows = buildList {
        add("Day $sel" + (c.lifts[sel]?.let { " · L${c.lifts.keys.sorted().indexOf(sel) + 1} lifted ${Fmt.i(it)}" } ?: "") to (if (c.lifts.containsKey(sel)) LiftColor else Color.White))
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
    var zoomed by rememberSaveable { mutableStateOf(false) }
    val labelArgb = Color.White.copy(alpha = 0.6f).toArgb()
    OutputCard(title = "Graphs") {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            specs.forEach { s ->
                val on = s.key == c.key
                Text(s.title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) FontWeight.Medium else FontWeight.Medium),
                    color = Color.White.copy(alpha = if (on) 1f else 0.7f),
                    modifier = Modifier.border(if (on) 2.dp else 1.dp, Color.White.copy(alpha = if (on) 1f else 0.3f), RoundedCornerShape(10.dp))
                        .clickable { pick = s.key }.padding(horizontal = 12.dp, vertical = 8.dp).testTag("chart_pick_${s.key}"))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ChartKey(c) }
            Spacer(Modifier.width(8.dp))
            Text("Zoom", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium), color = Color.White,
                modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).clickable { zoomed = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp).testTag("chart_zoom"))
        }
        var win by remember(c.key) { mutableStateOf(c.fromDay.toFloat()..c.maxDay.toFloat()) }
        val from = win.start.toDouble(); val to = win.endInclusive.toDouble()
        ChartCanvas(c, from, to, sel, { sel = it }, Modifier.fillMaxWidth().height(260.dp).testTag("chart"), 11f, labelArgb)
        ChartSliders(c, win, { win = it; sel = sel.coerceIn(it.start.roundToInt(), it.endInclusive.roundToInt()) }, sel, { sel = it })
    }
    if (zoomed) ZoomedChart(c, sel, { sel = it }) { zoomed = false }
}

/** The chart drawn over [from]–[to] with the cursor at [sel]; one finger along it moves the cursor. */
@Composable
private fun ChartCanvas(c: ChartSpec, from: Double, to: Double, sel: Int, onSel: (Int) -> Unit, modifier: Modifier, textSp: Float, labelArgb: Int, dotLabels: Boolean = false) {
    val setSel by androidx.compose.runtime.rememberUpdatedState(onSel)
    val f by androidx.compose.runtime.rememberUpdatedState(from)
    val t by androidx.compose.runtime.rememberUpdatedState(to)
    Canvas(modifier.pointerInput(c.key) {
        fun dayAt(x: Float): Int {
            val padL = PAD_L * density + 12f * density
            val gw = size.width - padL - 30f * density
            return (f + ((x - padL) / gw).coerceIn(0f, 1f) * (t - f)).roundToInt().coerceIn(c.fromDay, c.maxDay)
        }
        awaitEachGesture {
            val down = awaitFirstDown(); setSel(dayAt(down.position.x))
            do {
                val ev = awaitPointerEvent()
                ev.changes.firstOrNull { it.pressed }?.let { ch -> if (kotlin.math.abs(ch.position.x - ch.previousPosition.x) > 0f) { setSel(dayAt(ch.position.x)); ch.consume() } }
            } while (ev.changes.any { it.pressed })
        }
    }) { drawChart(c, from, to, sel, labelArgb, textSp * density, dotLabels) }
}

/** Under a chart: the days shown (drag either end) and the cursor (drag it to read every day, no tapping). */
@Composable
private fun ChartSliders(c: ChartSpec, win: ClosedFloatingPointRange<Float>, onWin: (ClosedFloatingPointRange<Float>) -> Unit, sel: Int, onSel: (Int) -> Unit) {
    val colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White.copy(alpha = 0.8f),
        inactiveTrackColor = Color.White.copy(alpha = 0.2f), activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent)
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("day $sel", Modifier.width(64.dp), style = MaterialTheme.typography.labelLarge, color = Color.White)
            androidx.compose.material3.Slider(value = sel.toFloat(), onValueChange = { onSel(it.roundToInt()) },
                valueRange = win.start..max(win.start + 1f, win.endInclusive), colors = colors, modifier = Modifier.weight(1f).testTag("chart_cursor"))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("d${win.start.roundToInt()}–${win.endInclusive.roundToInt()}", Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium, color = Color.White)
            androidx.compose.material3.RangeSlider(value = win, onValueChange = { r -> if (r.endInclusive - r.start >= 3f) onWin(r) },
                valueRange = c.fromDay.toFloat()..c.maxDay.toFloat(), colors = colors, modifier = Modifier.weight(1f).testTag("chart_window"))
        }
    }
}

/** Lays [content] out sideways (turned a quarter) when the space is taller than wide: a chart full screen in landscape. */
private fun Modifier.landscape(): Modifier = this.layout { m, cs ->
    val w = cs.maxWidth; val h = cs.maxHeight
    if (h <= w) { val p = m.measure(cs); return@layout layout(p.width, p.height) { p.place(0, 0) } }
    val p = m.measure(androidx.compose.ui.unit.Constraints.fixed(h, w))
    layout(w, h) { p.placeWithLayer((w - p.width) / 2, (h - p.height) / 2) { rotationZ = 90f } }
}

/** A chart full screen, in landscape: one finger reads the days, the sliders move the cursor and choose the days. */
@Composable
private fun ZoomedChart(c: ChartSpec, sel0: Int, onSel: (Int) -> Unit, onClose: () -> Unit) {
    var sel by remember { mutableStateOf(sel0) }
    var win by remember { mutableStateOf(c.fromDay.toFloat()..c.maxDay.toFloat()) }
    val labelArgb = Color.White.copy(alpha = 0.7f).toArgb()
    androidx.compose.ui.window.Dialog(onDismissRequest = { onSel(sel); onClose() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Column(Modifier.fillMaxSize().landscape().padding(12.dp).testTag("chart_zoomed"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.title + if (c.unit.isNotEmpty()) " · ${c.unit}" else "", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
                    Box(Modifier.weight(2f)) { ChartKey(c) }
                    Text("Close", style = MaterialTheme.typography.labelLarge, color = Color.White,
                        modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).clickable { onSel(sel); onClose() }
                            .padding(horizontal = 12.dp, vertical = 6.dp))
                }
                ChartCanvas(c, win.start.toDouble(), win.endInclusive.toDouble(), sel, { sel = it }, Modifier.fillMaxWidth().weight(1f), 12f, labelArgb, dotLabels = true)
                ChartSliders(c, win, { win = it; sel = sel.coerceIn(it.start.roundToInt(), it.endInclusive.roundToInt()) }, sel, { sel = it })
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
    val liftedBy = { day: Int -> lifts.filterKeys { it <= day }.values.sum().toDouble() }
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
            // the company's and the breed's deaths, with the birds lifted till then taken off both (as in the house)
            Line("Ideal", Style.IDEAL) { day -> d.entryBirds * (1 - PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_MAXMORT_BY_AGE, day.toDouble()) / 100.0) - liftedBy(day) },
            Line("Company", Style.COMPANY) { day -> d.entryBirds * (1 - CompanyStandard.cumMortPct(day) / 100.0) - liftedBy(day) }
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
