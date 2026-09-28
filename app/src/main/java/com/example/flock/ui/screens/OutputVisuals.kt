package com.example.flock.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendSwatch(ValuePresent, "Present", dashed = false, thick = true)
        LegendSwatch(ValueIdeal, "Ideal · Ross", dashed = false, thick = false)
        LegendSwatch(CompanyColor, "Commercial", dashed = true, thick = false)
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
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = color))
    }
}

// =================================== KPI bars ===================================

enum class Better { HIGHER, LOWER, CLOSER }

data class Kpi(
    val label: String, val unit: String, val actual: Double?, val actualKind: ValueKind,
    val company: Double?, val ideal: Double?, val better: Better, val decimals: Int,
    val settling: Boolean = false, val idealLabel: String = "Ideal", val points: Boolean = false
)

private fun status(k: Kpi): Pair<String, Color> {
    val grey = Color(0xFF9AA0A6)
    val a = k.actual ?: return "not logged" to grey
    val c = k.company ?: return "—" to grey
    if (k.settling) return "settling" to grey
    val pct = if (c != 0.0) (a - c) / c * 100 else 0.0
    // percentages (mortality) are compared in points, not "% of a %"
    val txt = if (k.points) Fmt.signed(a - c, 2) + " pt" else Fmt.signed(pct, 1) + "%"
    val col = when (k.better) {
        Better.HIGHER -> if (pct >= -5) OkColor else if (pct >= -10) StatusWarn else StatusCrit
        Better.LOWER -> if (pct <= 5) OkColor else if (pct <= 10) StatusWarn else StatusCrit
        Better.CLOSER -> if (kotlin.math.abs(pct) <= 10) OkColor else if (kotlin.math.abs(pct) <= 20) StatusWarn else StatusCrit
    }
    return txt to col
}

/** Present vs Commercial vs Ideal, each KPI with a bullet bar (±5 % band around Commercial). */
@Composable
fun KpiCard(title: String, kpis: List<Kpi>, day: Int) {
    OutputCard(title = title) {
        kpis.forEach { KpiRow(it) }
        Text(
            if (CompanyStandard.toleranceApplies(day)) "Shaded band = commercial ±5.0% (applies from day 28)."
            else "Shaded band = commercial ±5.0%; the company applies it from day 28.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun KpiRow(k: Kpi) {
    val (stTxt, stCol) = status(k)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(k.label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            if (k.unit.isNotEmpty()) Text("  " + k.unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Surface(color = stCol.copy(alpha = 0.18f), shape = RoundedCornerShape(8.dp)) {
                Text(stTxt, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = stCol, fontFamily = FontFamily.Monospace))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TaggedValue(Fmt.n(k.actual, k.decimals), if (k.actual == null) ValueKind.NEUTRAL else k.actualKind, kindTag(k.actualKind), Modifier.weight(1f), big = true)
            TaggedValue(Fmt.n(k.company, k.decimals), ValueKind.COMMERCIAL, "Commercial", Modifier.weight(1f))
            TaggedValue(Fmt.n(k.ideal, k.decimals), ValueKind.IDEAL, k.idealLabel, Modifier.weight(1f))
        }
        BulletBar(k)
    }
}

/** A value with its coloured tag above it. */
@Composable
fun TaggedValue(value: String, kind: ValueKind, tag: String, modifier: Modifier = Modifier, big: Boolean = false) {
    Column(modifier) {
        Text(tag, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = kindColor(kind).copy(alpha = 0.85f), maxLines = 1)
        Text(value, maxLines = 1, color = kindColor(kind),
            style = (if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
                .copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = if (big) 19.sp else 15.sp))
    }
}

/** Commercial ±15 % scale: shaded ±5 % tolerance, Commercial tick, Ideal tick, present dot. */
@Composable
private fun BulletBar(k: Kpi) {
    val c = k.company ?: return
    val track = MaterialTheme.colorScheme.surfaceVariant
    val dotC = kindColor(k.actualKind)
    Canvas(Modifier.fillMaxWidth().height(16.dp)) {
        val span = max(kotlin.math.abs(c) * 0.15, 0.05)
        val lo = c - span; val hi = c + span
        fun x(v: Double) = ((v.coerceIn(lo, hi) - lo) / (hi - lo) * size.width).toFloat()
        val mid = size.height / 2
        drawRoundRect(track, size = Size(size.width, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f))
        val t0 = x(c * 0.95); val t1 = x(c * 1.05)
        drawRect(CompanyColor.copy(alpha = 0.25f), topLeft = Offset(min(t0, t1), 0f), size = Size(kotlin.math.abs(t1 - t0), size.height))
        drawLine(CompanyColor, Offset(x(c), 0f), Offset(x(c), size.height), strokeWidth = 5f)
        k.ideal?.let { drawLine(ValueIdeal, Offset(x(it), 2f), Offset(x(it), size.height - 2f), strokeWidth = 4f) }
        k.actual?.let {
            val clipped = it < lo || it > hi
            drawCircle(dotC, radius = size.height / 2.1f, center = Offset(x(it), mid))
            if (clipped) drawCircle(Color.White, radius = size.height / 5f, center = Offset(x(it), mid))
        }
    }
}

// =================================== charts ===================================

private enum class Style { ACTUAL, IDEAL, COMPANY }

private class Line(val label: String, val style: Style, val valueAt: (Int) -> Double?)

/**
 * Tap-to-inspect line chart over flock days: Present (thick, filled), Ideal · Ross (blue), Commercial
 * (violet dashed, with its ±5 % band from day 28 when [companyBand]). The strip above shows every
 * series' value for the chosen day.
 */
@Composable
private fun StandardChart(
    title: String, unit: String, lines: List<Line>, maxDay: Int, markerDay: Int,
    yMin: Double, yMax: Double, yStep: Double, decimals: Int, companyBand: Boolean, fromDay: Int = 0,
    idealTag: String = "Ideal", presentKind: ValueKind = ValueKind.PRESENT
) {
    var sel by remember(markerDay) { mutableStateOf(markerDay.coerceIn(fromDay, maxDay)) }
    val gridC = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val bg = MaterialTheme.colorScheme.surfaceVariant
    val selC = MaterialTheme.colorScheme.onSurface
    fun colorOf(s: Style) = when (s) { Style.ACTUAL -> ValuePresent; Style.IDEAL -> ValueIdeal; Style.COMPANY -> CompanyColor }
    fun kindOf(s: Style) = when (s) { Style.ACTUAL -> presentKind; Style.IDEAL -> ValueKind.IDEAL; Style.COMPANY -> ValueKind.COMMERCIAL }
    fun tagOf(s: Style) = when (s) { Style.ACTUAL -> kindTag(presentKind); Style.IDEAL -> idealTag; Style.COMPANY -> "Commercial" }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title + if (unit.isNotEmpty()) " ($unit)" else "", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            Text("Day $sel", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            lines.forEach { l ->
                val v = l.valueAt(sel)
                TaggedValue(Fmt.n(v, decimals), if (v == null) ValueKind.NEUTRAL else kindOf(l.style), tagOf(l.style), Modifier.weight(1f))
            }
        }
        Canvas(
            Modifier.fillMaxWidth().height(180.dp)
                .background(bg, RoundedCornerShape(12.dp))
                .pointerInput(maxDay, fromDay) {
                    detectTapGestures { o ->
                        val padL = 48f
                        val frac = ((o.x - padL) / (size.width - padL - 16f)).coerceIn(0f, 1f)
                        sel = (fromDay + frac * (maxDay - fromDay)).roundToInt()
                    }
                }
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            val padL = 44f; val padB = 24f
            val gw = size.width - padL; val gh = size.height - padB
            fun px(d: Int) = padL + (d - fromDay).toFloat() / (maxDay - fromDay).coerceAtLeast(1) * gw
            fun py(v: Double) = (gh - ((v.coerceIn(yMin, yMax) - yMin) / (yMax - yMin)) * gh).toFloat()
            val lbl = Paint().apply { color = labelArgb; textSize = 27f; isAntiAlias = true }
            var t = kotlin.math.ceil(yMin / yStep) * yStep
            while (t <= yMax + 1e-9) {
                val y = py(t)
                drawLine(gridC, Offset(padL, y), Offset(padL + gw, y), strokeWidth = 1f)
                drawContext.canvas.nativeCanvas.drawText(if (yStep >= 1000) Fmt.n(t / 1000, 1) + "k" else Fmt.n(t, 1), 0f, y + 9f, lbl)
                t += yStep
            }
            for (d in listOf(fromDay, (fromDay + maxDay) / 2, maxDay)) drawContext.canvas.nativeCanvas.drawText("d$d", px(d) - 14f, size.height, lbl)

            if (companyBand) {
                val comp = lines.firstOrNull { it.style == Style.COMPANY }
                if (comp != null) {
                    val ds = (max(CompanyStandard.TOLERANCE_FROM_DAY, fromDay)..maxDay).filter { comp.valueAt(it) != null }
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
            lines.sortedBy { if (it.style == Style.ACTUAL) 1 else 0 }.forEach { l ->
                val pts = (fromDay..maxDay).mapNotNull { d -> l.valueAt(d)?.let { Offset(px(d), py(it)) } }
                if (pts.size < 2) { pts.forEach { drawCircle(colorOf(l.style), 5f, it) }; return@forEach }
                val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
                when (l.style) {
                    Style.ACTUAL -> {
                        val fill = Path().apply { addPath(path); lineTo(pts.last().x, gh); lineTo(pts.first().x, gh); close() }
                        drawPath(fill, Brush.verticalGradient(listOf(ValuePresent.copy(alpha = 0.25f), ValuePresent.copy(alpha = 0f)), startY = 0f, endY = gh))
                        drawPath(path, ValuePresent, style = Stroke(width = 5f, cap = StrokeCap.Round))
                    }
                    Style.IDEAL -> drawPath(path, ValueIdeal.copy(alpha = 0.95f), style = Stroke(width = 3.5f, cap = StrokeCap.Round))
                    Style.COMPANY -> drawPath(path, CompanyColor, style = Stroke(width = 3.5f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))))
                }
            }
            drawLine(selC.copy(alpha = 0.35f), Offset(px(sel), 0f), Offset(px(sel), gh), strokeWidth = 2f)
            lines.forEach { l -> l.valueAt(sel)?.let { v -> drawCircle(colorOf(l.style), 7f, Offset(px(sel), py(v))); drawCircle(Color.White, 2.8f, Offset(px(sel), py(v))) } }
        }
    }
}

private fun chartMaxDay(d: OutputData) = max(d.harvestAge, d.day).coerceIn(14, CompanyStandard.MAX_DAY)

/** Weight, FCR, cFCR and mortality: Present vs Ideal (Ross) vs Commercial. */
@Composable
fun BirdCharts(d: OutputData) {
    val maxDay = chartMaxDay(d)
    val by = d.byDay
    OutputCard(title = "📈 Bird curves") {
        StandardsLegend()
        Text("Tap a chart to read any day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StandardChart(
            "Body weight", "g",
            listOf(
                Line("Present", Style.ACTUAL) { day -> by[day]?.avgWeight ?: by[day]?.takeIf { it.projected && day <= d.day }?.let { PhysiologicalEngine.bwFromDay(it.weightAge, d.breed) } },
                Line("Ideal", Style.IDEAL) { day -> PhysiologicalEngine.bwFromDay(day.toDouble(), d.breed) },
                Line("Commercial", Style.COMPANY) { day -> CompanyStandard.bw(day) }
            ),
            maxDay, d.day, 0.0, 3600.0, 1000.0, 1, companyBand = true, presentKind = d.vk
        )
        StandardChart(
            "FCR", "",
            listOf(
                Line("Present", Style.ACTUAL) { day -> by[day]?.fcr?.takeIf { day in 5..d.day } },
                Line("Ideal", Style.IDEAL) { day -> PhysiologicalEngine.stdFcrFromDay(day.toDouble(), d.breed) },
                Line("Commercial", Style.COMPANY) { day -> CompanyStandard.fcr(day) }
            ),
            maxDay, d.day, 0.6, 2.0, 0.2, 3, companyBand = true, fromDay = 5
        )
        StandardChart(
            "cFCR to 2 kg", "",
            listOf(
                Line("Present", Style.ACTUAL) { day -> by[day]?.cFcr?.takeIf { day in 5..d.day } },
                Line("Ideal", Style.IDEAL) { day -> val bw = PhysiologicalEngine.bwFromDay(day.toDouble(), d.breed); PhysiologicalEngine.computeCorrectedFcr(bw / 1000, PhysiologicalEngine.stdFcrFromDay(day.toDouble(), d.breed)) },
                Line("Commercial", Style.COMPANY) { day -> CompanyStandard.cfcr(day) }
            ),
            maxDay, d.day, 1.0, 1.8, 0.2, 3, companyBand = true, fromDay = 5
        )
        StandardChart(
            "Cumulative mortality", "%",
            listOf(
                Line("Present", Style.ACTUAL) { day -> by[day]?.cumMortPct?.takeIf { day <= d.day } },
                Line("Ideal", Style.IDEAL) { day -> PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_MAXMORT_BY_AGE, day.toDouble()) },
                Line("Commercial", Style.COMPANY) { day -> CompanyStandard.cumMortPct(day) }
            ),
            maxDay, d.day, 0.0, 7.0, 1.0, 2, companyBand = false
        )
        Text("Mortality ideal = industry benchmark; commercial = company daily allowance added up.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Feed per bird per day and cumulative feed per bird: Present vs Ideal (Ross) vs Commercial. */
@Composable
fun FeedCharts(d: OutputData) {
    val maxDay = chartMaxDay(d)
    val by = d.byDay
    // feed logged on day d+1 is what the birds ate on day d
    val perBird = { day: Int ->
        val next = by[day + 1]
        val live = by[day]?.liveBirds?.takeIf { it > 0 }
        if (next == null || live == null || day + 1 > d.day) null else d.usedKg(next).takeIf { it > 0 }?.let { it * 1000 / live }
    }
    val cumPerBird = { day: Int ->
        val live = by[day]?.liveBirds?.takeIf { it > 0 }
        if (live == null || day + 1 > d.day) null
        else d.rows.filter { it.dayNumber <= day + 1 }.sumOf { d.usedKg(it) }.takeIf { it > 0 }?.let { it * 1000 / live }
    }
    OutputCard(title = "📈 Feed curves") {
        StandardsLegend()
        StandardChart(
            "Feed per bird per day", "g",
            listOf(
                Line("Present", Style.ACTUAL) { day -> perBird(day) },
                Line("Ideal", Style.IDEAL) { day -> PhysiologicalEngine.dailyFeedFromDay(max(1, day).toDouble(), d.breed) },
                Line("Commercial", Style.COMPANY) { day -> CompanyStandard.feedPerDay(day) }
            ),
            maxDay, max(1, d.day - 1), 0.0, 240.0, 60.0, 1, companyBand = false, fromDay = 1
        )
        StandardChart(
            "Cumulative feed per bird", "g",
            listOf(
                Line("Present", Style.ACTUAL) { day -> cumPerBird(day) },
                Line("Ideal", Style.IDEAL) { day -> PhysiologicalEngine.cumFeedFromDay(day.toDouble(), d.breed) },
                Line("Commercial", Style.COMPANY) { day -> CompanyStandard.cumFeed(day) }
            ),
            maxDay, max(1, d.day - 1), 0.0, 7000.0, 1000.0, 1, companyBand = true, fromDay = 1
        )
        Text("Feed entered on a day is what the birds ate the day before, so the present line runs one day behind.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// =================================== ventilation helpers ===================================

@Composable
fun MiniStat(label: String, value: String, kind: ValueKind = ValueKind.NEUTRAL) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
            color = if (kind == ValueKind.NEUTRAL) MaterialTheme.colorScheme.onSurface else kindColor(kind))
    }
}

/** Horizontal scale from comfort − 6 to comfort + 8 °C: cold / comfortable (±2) / warm / hot. */
@Composable
fun FeltGauge(comfort: Double, felt: Double, air: Double) {
    val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxWidth().height(50.dp)) {
        val lo = comfort - 6; val hi = comfort + 8
        val barTop = 6f; val barH = 20f
        fun x(v: Double) = ((v.coerceIn(lo, hi) - lo) / (hi - lo) * size.width).toFloat()
        val zones = listOf(lo to comfort - 2 to ValueIdeal.copy(alpha = 0.55f), comfort - 2 to comfort + 2 to OkColor.copy(alpha = 0.7f),
            comfort + 2 to comfort + 4 to StatusWarn.copy(alpha = 0.7f), comfort + 4 to hi to StatusCrit.copy(alpha = 0.7f))
        zones.forEach { (range, col) -> drawRect(col, Offset(x(range.first), barTop), Size(x(range.second) - x(range.first), barH)) }
        drawLine(ink.copy(alpha = 0.6f), Offset(x(air), barTop - 4), Offset(x(air), barTop + barH + 4), strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
        drawCircle(ink, 10f, Offset(x(felt), barTop + barH / 2))
        drawCircle(Color.White, 4.5f, Offset(x(felt), barTop + barH / 2))
        val lbl = Paint().apply { color = labelArgb; textSize = 26f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        drawContext.canvas.nativeCanvas.drawText("cold", x(comfort - 4), size.height - 2f, lbl)
        drawContext.canvas.nativeCanvas.drawText("comfort ${Fmt.n(comfort, 1)}°", x(comfort), size.height - 2f, lbl)
        drawContext.canvas.nativeCanvas.drawText("hot", x(comfort + 6), size.height - 2f, lbl)
    }
}

/** Felt temperature (line) against the comfort band, with fans running as bars underneath. */
@Composable
fun Next24Chart(pts: List<HourPoint>, felt: List<Double>, fans: List<Double>, comfort: Double, fanCount: Int) {
    val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val bg = MaterialTheme.colorScheme.surfaceVariant
    Text("Next 24 hours", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
    Canvas(Modifier.fillMaxWidth().height(160.dp).background(bg, RoundedCornerShape(12.dp)).padding(10.dp)) {
        val padL = 44f; val padB = 24f
        val gw = size.width - padL
        val topH = (size.height - padB) * 0.68f
        val barTop = topH + 8f; val barH = size.height - padB - barTop
        val lo = min(comfort - 4, felt.minOrNull() ?: comfort); val hi = max(comfort + 6, felt.maxOrNull() ?: comfort)
        fun px(i: Int) = padL + i.toFloat() / (pts.size - 1).coerceAtLeast(1) * gw
        fun py(v: Double) = (topH - ((v - lo) / (hi - lo)) * topH).toFloat()
        drawRect(OkColor.copy(alpha = 0.18f), Offset(padL, py(comfort + 2)), Size(gw, py(comfort - 2) - py(comfort + 2)))
        val path = Path().apply { felt.forEachIndexed { i, v -> if (i == 0) moveTo(px(i), py(v)) else lineTo(px(i), py(v)) } }
        drawPath(path, ValuePredicted, style = Stroke(width = 4f, cap = StrokeCap.Round))
        val bw = gw / pts.size * 0.7f
        fans.forEachIndexed { i, f ->
            val h = (f / fanCount).toFloat() * barH
            drawRect(ValueIdeal.copy(alpha = 0.8f), Offset(px(i) - bw / 2, barTop + barH - h), Size(bw, h))
        }
        val lbl = Paint().apply { color = labelArgb; textSize = 25f; isAntiAlias = true }
        drawContext.canvas.nativeCanvas.drawText("${Fmt.n(hi, 1)}°", 0f, py(hi) + 10f, lbl)
        drawContext.canvas.nativeCanvas.drawText("${Fmt.n(lo, 1)}°", 0f, py(lo), lbl)
        drawContext.canvas.nativeCanvas.drawText("fans", 0f, barTop + barH, lbl)
        for (i in pts.indices step 6) drawContext.canvas.nativeCanvas.drawText(String.format("%02d:00", pts[i].hour), px(i) - 20f, size.height, lbl)
    }
    Text("Amber line: what birds feel (projected) · green band: comfortable · blue bars: fans running.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
