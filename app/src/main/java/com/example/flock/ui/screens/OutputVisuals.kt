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
                ValueChip(V(Fmt.n(v, decimals), if (v == null) ValueKind.NEUTRAL else kindOf(l.style), tagOf(l.style)), Modifier.weight(1f))
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
    OutputCard(title = "Bird curves") {
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
    OutputCard(title = "Feed curves") {
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
