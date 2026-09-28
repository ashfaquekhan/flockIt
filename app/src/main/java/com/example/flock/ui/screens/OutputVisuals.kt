package com.example.flock.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FarmEntity
import com.example.flock.data.FeedTypeEntity
import com.example.flock.engine.CompanyStandard
import com.example.flock.engine.IbController
import com.example.flock.engine.PhysiologicalEngine
import com.example.flock.network.HourPoint
import com.example.flock.network.WeatherResult
import com.example.flock.ui.Fmt
import com.example.ui.theme.StatusCrit
import com.example.ui.theme.StatusWarn
import com.example.ui.theme.ValueIdeal
import com.example.ui.theme.ValuePredicted
import com.example.ui.theme.ValuePresent
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Colour for the company's commercial standard (distinct from ideal blue and predicted amber). */
val CompanyColor = Color(0xFFB08CF0)
private val OkColor = Color(0xFF46B98C)

// =================================== legend ===================================

@Composable
fun StandardsLegend() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendSwatch(ValuePresent, "Actual", dashed = false, thick = true)
        LegendSwatch(ValueIdeal, "Ideal · Ross", dashed = false, thick = false)
        LegendSwatch(CompanyColor, "Company", dashed = true, thick = false)
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String, dashed: Boolean, thick: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 20.dp, height = 10.dp)) {
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                strokeWidth = if (thick) 6f else 3.5f, cap = StrokeCap.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null)
        }
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = color))
    }
}

// =================================== KPI scorecard ===================================

private enum class Better { HIGHER, LOWER, CLOSER }

private data class Kpi(
    val label: String, val unit: String, val actual: Double?, val projected: Boolean,
    val company: Double?, val ideal: Double?, val better: Better, val decimals: Int, val settling: Boolean = false,
    val idealLabel: String = "ross", val points: Boolean = false
)

private fun status(k: Kpi): Pair<String, Color> {
    val a = k.actual ?: return "—" to Color(0xFF9AA0A6)
    val c = k.company ?: return "—" to Color(0xFF9AA0A6)
    if (k.settling) return "settling" to Color(0xFF9AA0A6)
    val pct = if (c != 0.0) (a - c) / c * 100 else 0.0
    // percentages (mortality) are compared in points, not "% of a %"
    val txt = if (k.points) (if (a >= c) "+" else "−") + Fmt.n(kotlin.math.abs(a - c), 2) + " pt"
        else (if (pct >= 0) "+" else "−") + Fmt.n(kotlin.math.abs(pct), 1) + "%"
    val col = when (k.better) {
        Better.HIGHER -> if (pct >= -5) OkColor else if (pct >= -10) StatusWarn else StatusCrit
        Better.LOWER -> if (pct <= 5) OkColor else if (pct <= 10) StatusWarn else StatusCrit
        Better.CLOSER -> if (kotlin.math.abs(pct) <= 10) OkColor else if (kotlin.math.abs(pct) <= 20) StatusWarn else StatusCrit
    }
    return txt to col
}

/** Actual vs Company vs Ross for the day, each with a small bullet bar (±5 % band around Company). */
@Composable
fun KpiScorecard(entry: DailyDataEntity, dailyRows: List<DailyDataEntity>, breed: String, farm: FarmEntity, feedTypes: List<FeedTypeEntity>) {
    val day = entry.dayNumber
    val bwNow = entry.avgWeight ?: PhysiologicalEngine.bwFromDay(entry.weightAge, breed)
    val prev = dailyRows.filter { it.dayNumber < day && it.avgWeight != null }.maxByOrNull { it.dayNumber }
    val gain = if (entry.avgWeight != null && prev?.avgWeight != null) (entry.avgWeight - prev.avgWeight) / (day - prev.dayNumber) else null
    val rossBw = PhysiologicalEngine.bwFromDay(day.toDouble(), breed)
    val rossFcr = PhysiologicalEngine.stdFcrFromDay(day.toDouble(), breed)
    // Feed logged today is yesterday's use.
    val usedKg = usedByType(entry).entries.sumOf { (code, bags) -> bags * (feedTypes.firstOrNull { it.code == code }?.bagKg ?: farm.feedBagKg) }
    val yLive = dailyRows.firstOrNull { it.dayNumber == day - 1 }?.liveBirds?.takeIf { it > 0 } ?: entry.liveBirds.coerceAtLeast(1)
    val feedYesterday = if (usedKg > 0 && day >= 1) usedKg * 1000.0 / yLive else null
    val kpis = listOf(
        Kpi("Body weight", "g", bwNow, entry.avgWeight == null, CompanyStandard.bw(day), rossBw, Better.HIGHER, 0),
        Kpi("Daily gain", "g/day", gain, false, CompanyStandard.gain(day), rossBw - PhysiologicalEngine.bwFromDay(max(0, day - 1).toDouble(), breed), Better.HIGHER, 0),
        Kpi("FCR", "", entry.fcr, false, CompanyStandard.fcr(day), rossFcr, Better.LOWER, 3, settling = day < 7),
        Kpi("cFCR", "→ 2 kg", entry.cFcr, false, CompanyStandard.cfcr(day), PhysiologicalEngine.computeCorrectedFcr(rossBw / 1000, rossFcr), Better.LOWER, 3, settling = day < 7),
        Kpi("Mortality", "% cumulative", entry.cumMortPct, false, CompanyStandard.cumMortPct(day), entry.maxMortPct, Better.LOWER, 2, idealLabel = "ceiling", points = true),
        Kpi("Feed / bird", "g · yesterday", feedYesterday, false, CompanyStandard.feedPerDay(day - 1), PhysiologicalEngine.dailyFeedFromDay(max(1, day - 1).toDouble(), breed), Better.CLOSER, 0)
    )
    OutputCard(title = "How the flock compares · Day $day") {
        StandardsLegend()
        kpis.forEach { KpiRow(it) }
        Text(
            if (CompanyStandard.toleranceApplies(day)) "Company tolerance ±5 % applies from day 28 (shaded)." else "Shaded band = ±5 % of Company; the company applies it from day 28.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun KpiRow(k: Kpi) {
    val (stTxt, stCol) = status(k)
    val actualColor = if (k.actual == null) Color(0xFF9AA0A6) else if (k.projected) ValuePredicted else ValuePresent
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1.3f)) {
                Text(k.label, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                Text(k.unit + if (k.projected) " · projected" else "", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(Fmt.n(k.actual, k.decimals), modifier = Modifier.weight(1f), textAlign = TextAlign.End,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black), color = actualColor)
            Column(Modifier.weight(1.1f), horizontalAlignment = Alignment.End) {
                Text("co " + Fmt.n(k.company, k.decimals), style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, color = CompanyColor))
                Text(k.idealLabel + " " + Fmt.n(k.ideal, k.decimals), style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, color = ValueIdeal))
            }
            Surface(color = stCol.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(start = 8.dp)) {
                Text(stTxt, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = stCol, fontFamily = FontFamily.Monospace))
            }
        }
        BulletBar(k, actualColor)
    }
}

/** Company ±15 % scale: shaded ±5 % tolerance, Company tick, Ross tick, actual dot. */
@Composable
private fun BulletBar(k: Kpi, actualColor: Color) {
    val c = k.company ?: return
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().height(14.dp).padding(top = 4.dp)) {
        val span = max(kotlin.math.abs(c) * 0.15, 0.05)
        val lo = c - span; val hi = c + span
        fun x(v: Double) = ((v.coerceIn(lo, hi) - lo) / (hi - lo) * size.width).toFloat()
        val mid = size.height / 2
        drawRoundRect(track, size = Size(size.width, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f))
        val t0 = x(c * 0.95); val t1 = x(c * 1.05)
        drawRect(CompanyColor.copy(alpha = 0.22f), topLeft = Offset(min(t0, t1), 0f), size = Size(kotlin.math.abs(t1 - t0), size.height))
        drawLine(CompanyColor, Offset(x(c), 0f), Offset(x(c), size.height), strokeWidth = 4f)
        k.ideal?.let { drawLine(ValueIdeal, Offset(x(it), 2f), Offset(x(it), size.height - 2f), strokeWidth = 3f) }
        k.actual?.let {
            val clipped = it < lo || it > hi
            drawCircle(actualColor, radius = size.height / 2.2f, center = Offset(x(it), mid))
            if (clipped) drawCircle(Color.White, radius = size.height / 5f, center = Offset(x(it), mid))
        }
    }
}

// =================================== charts ===================================

private enum class Style { ACTUAL, IDEAL, COMPANY }

private class Line(val label: String, val style: Style, val valueAt: (Int) -> Double?)

/**
 * Tap-to-inspect line chart over flock days: Actual (thick, filled), Ideal · Ross (blue), Company
 * (violet dashed, with its ±5 % band from day 28 when [companyBand]). The strip above shows every
 * series' value for the chosen day.
 */
@Composable
private fun StandardChart(
    title: String, unit: String, lines: List<Line>, maxDay: Int, markerDay: Int,
    yMin: Double, yMax: Double, yStep: Double, decimals: Int, companyBand: Boolean, fromDay: Int = 0
) {
    var sel by remember(markerDay) { mutableStateOf(markerDay.coerceIn(fromDay, maxDay)) }
    val gridC = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val bg = MaterialTheme.colorScheme.surfaceVariant
    val selC = MaterialTheme.colorScheme.onSurface
    fun colorOf(s: Style) = when (s) { Style.ACTUAL -> ValuePresent; Style.IDEAL -> ValueIdeal; Style.COMPANY -> CompanyColor }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            Text("Day $sel", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            lines.forEach { l ->
                Text(Fmt.n(l.valueAt(sel), decimals) + if (unit.isNotEmpty() && l.valueAt(sel) != null) " $unit" else "",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = colorOf(l.style)))
            }
        }
        Canvas(
            Modifier.fillMaxWidth().height(170.dp)
                .background(bg, RoundedCornerShape(12.dp))
                .pointerInput(maxDay, fromDay) {
                    detectTapGestures { o ->
                        val padL = 40f
                        val frac = ((o.x - padL) / (size.width - padL - 16f)).coerceIn(0f, 1f)
                        sel = (fromDay + frac * (maxDay - fromDay)).roundToInt()
                    }
                }
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            val padL = 32f; val padB = 18f
            val gw = size.width - padL; val gh = size.height - padB
            fun px(d: Int) = padL + (d - fromDay).toFloat() / (maxDay - fromDay).coerceAtLeast(1) * gw
            fun py(v: Double) = (gh - ((v.coerceIn(yMin, yMax) - yMin) / (yMax - yMin)) * gh).toFloat()
            val lbl = Paint().apply { color = labelArgb; textSize = 22f; isAntiAlias = true }
            var t = kotlin.math.ceil(yMin / yStep) * yStep
            while (t <= yMax + 1e-9) {
                val y = py(t)
                drawLine(gridC, Offset(padL, y), Offset(padL + gw, y), strokeWidth = 1f)
                drawContext.canvas.nativeCanvas.drawText(if (yStep >= 1000) "${(t / 1000).toInt()}k" else Fmt.n(t, if (yStep < 1) 1 else 0), 0f, y + 7f, lbl)
                t += yStep
            }
            for (d in listOf(fromDay, (fromDay + maxDay) / 2, maxDay)) drawContext.canvas.nativeCanvas.drawText("$d", px(d) - 6f, size.height, lbl)

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
            // reference lines first, actual on top
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
                    Style.IDEAL -> drawPath(path, ValueIdeal.copy(alpha = 0.9f), style = Stroke(width = 3f, cap = StrokeCap.Round))
                    Style.COMPANY -> drawPath(path, CompanyColor, style = Stroke(width = 3f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))))
                }
            }
            // selected day
            drawLine(selC.copy(alpha = 0.35f), Offset(px(sel), 0f), Offset(px(sel), gh), strokeWidth = 2f)
            lines.forEach { l -> l.valueAt(sel)?.let { v -> drawCircle(colorOf(l.style), 6f, Offset(px(sel), py(v))); drawCircle(Color.White, 2.4f, Offset(px(sel), py(v))) } }
        }
    }
}

/** All KPI charts: body weight, FCR, cFCR, cumulative mortality, feed per bird per day. */
@Composable
fun PerformanceCharts(dailyRows: List<DailyDataEntity>, breed: String, markerDay: Int, harvestAge: Int, farm: FarmEntity, feedTypes: List<FeedTypeEntity>) {
    val byDay = remember(dailyRows) { dailyRows.associateBy { it.dayNumber } }
    val maxDay = max(harvestAge, markerDay).coerceIn(14, CompanyStandard.MAX_DAY)
    val measured = { d: Int -> byDay[d]?.avgWeight }
    // actual feed per bird on day d = feed logged on day d+1 ÷ live birds on day d
    val feedActual = { d: Int ->
        val next = byDay[d + 1]
        val live = byDay[d]?.liveBirds?.takeIf { it > 0 }
        if (next == null || live == null) null else {
            val kg = usedByType(next).entries.sumOf { (code, bags) -> bags * (feedTypes.firstOrNull { it.code == code }?.bagKg ?: farm.feedBagKg) }
            if (kg > 0) kg * 1000 / live else null
        }
    }
    OutputCard(title = "Performance charts") {
        StandardsLegend()
        Text("Tap a chart to see any day.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StandardChart(
            "Body weight", "g",
            listOf(
                Line("Actual", Style.ACTUAL) { d -> measured(d) ?: byDay[d]?.takeIf { it.projected && d <= markerDay }?.let { PhysiologicalEngine.bwFromDay(it.weightAge, breed) } },
                Line("Ross", Style.IDEAL) { d -> PhysiologicalEngine.bwFromDay(d.toDouble(), breed) },
                Line("Company", Style.COMPANY) { d -> CompanyStandard.bw(d) }
            ),
            maxDay, markerDay, 0.0, 3600.0, 1000.0, 0, companyBand = true
        )
        StandardChart(
            "FCR", "",
            listOf(
                Line("Actual", Style.ACTUAL) { d -> byDay[d]?.fcr?.takeIf { d >= 5 } },
                Line("Ross", Style.IDEAL) { d -> PhysiologicalEngine.stdFcrFromDay(d.toDouble(), breed) },
                Line("Company", Style.COMPANY) { d -> CompanyStandard.fcr(d) }
            ),
            maxDay, markerDay, 0.6, 2.0, 0.2, 3, companyBand = true, fromDay = 5
        )
        StandardChart(
            "cFCR (to 2 kg)", "",
            listOf(
                Line("Actual", Style.ACTUAL) { d -> byDay[d]?.cFcr?.takeIf { d >= 5 } },
                Line("Ross", Style.IDEAL) { d -> val bw = PhysiologicalEngine.bwFromDay(d.toDouble(), breed); PhysiologicalEngine.computeCorrectedFcr(bw / 1000, PhysiologicalEngine.stdFcrFromDay(d.toDouble(), breed)) },
                Line("Company", Style.COMPANY) { d -> CompanyStandard.cfcr(d) }
            ),
            maxDay, markerDay, 1.0, 1.8, 0.2, 3, companyBand = true, fromDay = 5
        )
        StandardChart(
            "Cumulative mortality", "%",
            listOf(
                Line("Actual", Style.ACTUAL) { d -> byDay[d]?.cumMortPct?.takeIf { d <= markerDay } },
                Line("Ceiling", Style.IDEAL) { d -> PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_MAXMORT_BY_AGE, d.toDouble()) },
                Line("Company", Style.COMPANY) { d -> CompanyStandard.cumMortPct(d) }
            ),
            maxDay, markerDay, 0.0, 7.0, 1.0, 2, companyBand = false
        )
        StandardChart(
            "Feed per bird per day", "g",
            listOf(
                Line("Actual", Style.ACTUAL) { d -> feedActual(d) },
                Line("Ross", Style.IDEAL) { d -> PhysiologicalEngine.dailyFeedFromDay(max(1, d).toDouble(), breed) },
                Line("Company", Style.COMPANY) { d -> CompanyStandard.feedPerDay(d) }
            ),
            maxDay, markerDay, 0.0, 240.0, 60.0, 0, companyBand = false, fromDay = 1
        )
        Text("Blue on mortality is the industry ceiling (worst acceptable), not a target.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// =================================== ventilation ===================================

/** One simple ventilation picture: fans running, felt temperature vs comfort, next 24 hours. */
@Composable
fun VentSimpleCard(entry: DailyDataEntity, farm: FarmEntity, weather: WeatherResult?, hourly: List<HourPoint>, isToday: Boolean) {
    val day = entry.dayNumber
    val bw = entry.avgWeight ?: PhysiologicalEngine.bwFromDay(entry.weightAge, "Ross308")
    val plan = IbController.dayPlan(day, bw, entry.liveBirds, farm)
    val hour = currentHour(farm)
    val now = if (isToday && weather != null) IbController.simulate(plan, weather.tempC, weather.rhPercent, hour, entry.liveBirds, bw, farm) else null
    val minLv = plan.minLv
    val fansNow = now?.fans ?: minLv.avgFans
    val allowed = plan.maxFans
    OutputCard(title = if (now != null) "Ventilation now · ${String.format("%02d:00", hour)}" else "Ventilation · Day $day") {
        // fan bank
        val lit = if (now != null) fansNow.roundToInt().coerceAtLeast(1) else minLv.fansOn
        // fans switch on in the ladder order (e.g. 5, 3, 7, 1 …), not left to right
        val seq = PhysiologicalEngine.fanSequence(max(1, farm.fanCount))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (i in 1..farm.fanCount) {
                val rank = seq.indexOf(i).let { if (it < 0) farm.fanCount else it }
                val on = rank < lit
                val capped = rank >= allowed
                Box(
                    Modifier.weight(1f).height(26.dp).background(
                        when { on -> ValueIdeal; capped -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f); else -> MaterialTheme.colorScheme.surfaceVariant },
                        RoundedCornerShape(6.dp)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$i", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (capped) 0.4f else 0.8f)))
                }
            }
        }
        Text(
            (if (now != null) "About ${Fmt.n(fansNow, 1)} fans running · " else "") +
                "minimum: " + (if (minLv.isTimer) "1 fan ${minLv.on}s on / ${minLv.off}s off" else "${minLv.fansOn} fan non-stop") +
                " · up to $allowed at this age",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (now != null) {
            FeltGauge(comfort = plan.comfort, felt = now.feltC, air = now.houseC)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniStat("Outside", "${Fmt.n(weather!!.tempC, 1)}° · ${Fmt.n(weather.rhPercent, 0)}%")
                MiniStat("House", "${Fmt.n(now.houseC, 1)}° · ${Fmt.n(now.houseRh, 0)}%")
                MiniStat("Birds feel", "${Fmt.n(now.feltC, 1)}°")
            }
            val pts = hourly.let { hs ->
                val start = hs.indexOfFirst { it.hour == hour }.takeIf { it >= 0 } ?: 0
                hs.drop(start).take(24)
            }
            if (pts.size >= 6) {
                val states = pts.map { IbController.simulate(plan, it.tempC, it.rhPct, it.hour, entry.liveBirds, bw, farm) }
                Next24Chart(pts, states.map { it.feltC }, states.map { it.fans }, plan.comfort, farm.fanCount)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniStat("Comfort", "${Fmt.n(plan.comfort, 1)}°")
                MiniStat("Air need", "${Fmt.n(plan.needCfm, 0)} cfm")
                MiniStat("Heaters below", "${Fmt.n(plan.heat, 1)}°")
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold))
    }
}

/** Horizontal scale from comfort − 6 to comfort + 8 °C: cold / comfortable (±2) / warm / hot. */
@Composable
private fun FeltGauge(comfort: Double, felt: Double, air: Double) {
    val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxWidth().height(46.dp)) {
        val lo = comfort - 6; val hi = comfort + 8
        val barTop = 6f; val barH = 18f
        fun x(v: Double) = ((v.coerceIn(lo, hi) - lo) / (hi - lo) * size.width).toFloat()
        val zones = listOf(lo to comfort - 2 to ValueIdeal.copy(alpha = 0.55f), comfort - 2 to comfort + 2 to OkColor.copy(alpha = 0.7f),
            comfort + 2 to comfort + 4 to StatusWarn.copy(alpha = 0.7f), comfort + 4 to hi to StatusCrit.copy(alpha = 0.7f))
        zones.forEach { (range, col) -> drawRect(col, Offset(x(range.first), barTop), Size(x(range.second) - x(range.first), barH)) }
        drawLine(ink.copy(alpha = 0.6f), Offset(x(air), barTop - 4), Offset(x(air), barTop + barH + 4), strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
        drawCircle(ink, 9f, Offset(x(felt), barTop + barH / 2))
        drawCircle(Color.White, 4f, Offset(x(felt), barTop + barH / 2))
        val lbl = Paint().apply { color = labelArgb; textSize = 22f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        drawContext.canvas.nativeCanvas.drawText("cold", x(comfort - 4), size.height - 2f, lbl)
        drawContext.canvas.nativeCanvas.drawText("comfort ${Fmt.n(comfort, 1)}°", x(comfort), size.height - 2f, lbl)
        drawContext.canvas.nativeCanvas.drawText("hot", x(comfort + 6), size.height - 2f, lbl)
    }
}

/** Felt temperature (line) against the comfort band, with fans running as bars underneath. */
@Composable
private fun Next24Chart(pts: List<HourPoint>, felt: List<Double>, fans: List<Double>, comfort: Double, fanCount: Int) {
    val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val bg = MaterialTheme.colorScheme.surfaceVariant
    Text("Next 24 hours", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
    Canvas(Modifier.fillMaxWidth().height(150.dp).background(bg, RoundedCornerShape(12.dp)).padding(10.dp)) {
        val padL = 30f; val padB = 18f
        val gw = size.width - padL
        val topH = (size.height - padB) * 0.68f
        val barTop = topH + 8f; val barH = size.height - padB - barTop
        val lo = min(comfort - 4, felt.minOrNull() ?: comfort) ; val hi = max(comfort + 6, felt.maxOrNull() ?: comfort)
        fun px(i: Int) = padL + i.toFloat() / (pts.size - 1).coerceAtLeast(1) * gw
        fun py(v: Double) = (topH - ((v - lo) / (hi - lo)) * topH).toFloat()
        drawRect(OkColor.copy(alpha = 0.18f), Offset(padL, py(comfort + 2)), Size(gw, py(comfort - 2) - py(comfort + 2)))
        val path = Path().apply { felt.forEachIndexed { i, v -> if (i == 0) moveTo(px(i), py(v)) else lineTo(px(i), py(v)) } }
        drawPath(path, ValuePresent, style = Stroke(width = 4f, cap = StrokeCap.Round))
        val bw = gw / pts.size * 0.7f
        fans.forEachIndexed { i, f ->
            val h = (f / fanCount).toFloat() * barH
            drawRect(ValueIdeal.copy(alpha = 0.8f), Offset(px(i) - bw / 2, barTop + barH - h), Size(bw, h))
        }
        val lbl = Paint().apply { color = labelArgb; textSize = 20f; isAntiAlias = true }
        drawContext.canvas.nativeCanvas.drawText("${Fmt.n(hi, 0)}°", 0f, py(hi) + 8f, lbl)
        drawContext.canvas.nativeCanvas.drawText("${Fmt.n(lo, 0)}°", 0f, py(lo), lbl)
        drawContext.canvas.nativeCanvas.drawText("fans", 0f, barTop + barH, lbl)
        for (i in pts.indices step 6) drawContext.canvas.nativeCanvas.drawText(String.format("%02d", pts[i].hour), px(i) - 10f, size.height, lbl)
    }
    Text("Green line: what birds feel · green band: comfortable · blue bars: fans running.",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// =================================== collapsible details ===================================

@Composable
fun DetailsSection(title: String, content: @Composable () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = if (open) "Collapse" else "Expand")
            }
            if (open) Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    }
}

/** Three quick "today" tiles: feed to give, water, live birds. */
@Composable
fun TodayTiles(entry: DailyDataEntity, farm: FarmEntity, placed: Int) {
    val vk = if (entry.projected) ValueKind.PREDICTED else ValueKind.PRESENT
    val bag = if (farm.feedBagKg > 0) farm.feedBagKg else 50.0
    val tank = if (farm.drinkTankL > 0) farm.drinkTankL else 2000.0
    GistRow(
        GistItem("Feed to give", Fmt.n(entry.totalFeedKg / bag, 2) + " bags", "${Fmt.n(entry.totalFeedKg, 1)} kg · ${CompanyStandard.feedPhase(entry.dayNumber)}", vk),
        GistItem("Water", "${Fmt.n(entry.totalWaterL, 0)} L", "${Fmt.n(entry.totalWaterL / tank * farm.waterRefillFactor, 1)} tank fills", vk),
        GistItem("Live birds", Fmt.i(entry.liveBirds), "of ${Fmt.i(placed)}", ValueKind.PRESENT)
    )
}
