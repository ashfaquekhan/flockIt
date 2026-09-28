package com.example.flock.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FarmEntity
import com.example.flock.data.FeedStockSummary
import com.example.flock.data.FlockEntity
import com.example.flock.data.FeedTypeEntity
import com.example.flock.network.HourPoint
import com.example.flock.network.WeatherResult
import com.example.flock.ui.Fmt
import com.example.flock.engine.PhysiologicalEngine
import com.example.flock.ui.components.FanVisualizer
import com.example.flock.ui.components.HouseFloorPlan
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.DomainFeed
import com.example.ui.theme.DomainInsight
import com.example.ui.theme.DomainMed
import com.example.ui.theme.DomainVent
import com.example.ui.theme.DomainWater
import com.example.ui.theme.StatusCrit
import com.example.ui.theme.StatusCritWash
import com.example.ui.theme.StatusProjected
import com.example.ui.theme.StatusWarn
import com.example.ui.theme.StatusWarnWash
import com.example.ui.theme.ValueIdeal
import com.example.ui.theme.ValueIdealWash
import com.example.ui.theme.ValuePredicted
import com.example.ui.theme.ValuePredictedWash
import com.example.ui.theme.ValuePresent
import com.example.ui.theme.ValuePresentWash
import kotlin.math.abs
import kotlin.math.max

/**
 * How a displayed number was produced — kept visually distinct so present/predicted/ideal
 * are never confused. PRESENT = you measured/logged it; PREDICTED = estimated from the growth
 * curve because no sample was entered; IDEAL = the breed-standard target; NEUTRAL = n/a ("—").
 */
enum class ValueKind { PRESENT, PREDICTED, IDEAL, NEUTRAL }

fun kindColor(k: ValueKind): Color = when (k) {
    ValueKind.PRESENT -> ValuePresent
    ValueKind.PREDICTED -> ValuePredicted
    ValueKind.IDEAL -> ValueIdeal
    ValueKind.NEUTRAL -> Color(0xFF9AA0A6)
}

fun kindWash(k: ValueKind): Color = when (k) {
    ValueKind.PRESENT -> ValuePresentWash
    ValueKind.PREDICTED -> ValuePredictedWash
    ValueKind.IDEAL -> ValueIdealWash
    ValueKind.NEUTRAL -> Color(0xFF26292E)
}

fun kindTag(k: ValueKind): String = when (k) {
    ValueKind.PRESENT -> "measured"
    ValueKind.PREDICTED -> "predicted"
    ValueKind.IDEAL -> "ideal"
    ValueKind.NEUTRAL -> ""
}

/** Density is stored kg/m² but displayed in kg/ft² (1 m² = 10.7639 ft²). */
fun kgPerFt2(kgPerM2: Double): Double = kgPerM2 * 0.092903

@Composable
fun OutputScreen(
    flock: FlockEntity?,
    farm: FarmEntity,
    entry: DailyDataEntity?,
    dailyRows: List<DailyDataEntity>,
    feedStockSummary: FeedStockSummary,
    feedTypes: List<FeedTypeEntity> = emptyList(),
    weather: WeatherResult? = null,
    hourly: List<HourPoint> = emptyList(),
    isToday: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    if (entry == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No data for this day. Enter data in the ENTRY tab.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val breed = flock?.breed ?: "Ross308"
    val day = entry.dayNumber
    val weightAge = entry.weightAge ?: day.toDouble()
    val isProjected = entry.projected
    // Weight-derived numbers are PRESENT when a sample was entered today, else PREDICTED.
    val vk = if (isProjected) ValueKind.PREDICTED else ValueKind.PRESENT

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Visual first: today's jobs, how the flock compares (Actual · Ross · Company), charts,
        // then the pictures (ventilation, feed store, bird sizes, floor). Number tables last, folded.
        TodayTiles(entry, farm, flock?.birdsPlaced ?: 0)
        KpiScorecard(entry, dailyRows, breed, farm, feedTypes)
        PerformanceCharts(dailyRows, breed, day, flock?.harvestAge ?: 42, farm, feedTypes)
        VentSimpleCard(entry, farm, weather, hourly, isToday)
        FeedStockCard(entry, farm, dailyRows, feedTypes)
        PopulationDistributionCard(entry = entry)
        HouseFloorPlan(entry = entry, farm = farm)

        DetailsSection("Detailed tables") {
            BirdsLedgerCard(entry, flock, dailyRows)
            WeightLedgerCard(entry, dailyRows, breed, vk)
            FeedLedgerCard(entry, farm, dailyRows, feedTypes, flock?.harvestAge ?: 42, breed)
            WaterLedgerCard(entry, farm, dailyRows)
            DieselLedgerCard(entry, farm, dailyRows)
            OutputCard(title = "Birds — Space & Density") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        val density = entry.densityKgM2
                        BigMetric(
                            label = "Stocking Density",
                            value = density?.let { Fmt.n(kgPerFt2(it), 3) } ?: "—",
                            unit = "kg/ft²",
                            toleranceText = "Safe cap: ≤ ${Fmt.n(kgPerFt2(farm.densityCapDefault), 3)} kg/ft²",
                            statusTag = if (density != null && density > farm.densityCapDefault) "▲ over cap" else "ok",
                            kind = vk
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        BigMetric(
                            label = "Floor Space",
                            value = Fmt.n(entry.ftPerBird, 3),
                            unit = "ft²/bird",
                            toleranceText = "Minimum recommended: ≥ ${Fmt.n(entry.minFtPerBird, 3)} ft²",
                            statusTag = if (entry.ftPerBird < entry.minFtPerBird) "▼ crowded" else "ok",
                            kind = vk
                        )
                    }
                }
                if (entry.barricadeFt > 0) {
                    BigMetric(
                        label = "Brooding Barricade",
                        value = "${entry.barricadeFt}",
                        unit = "ft",
                        toleranceText = "Occupied floor area: ${Fmt.n(entry.occupiedFt2, 1)} ft²",
                        statusTag = "brooding",
                        kind = vk
                    )
                }
                BigMetric(
                    label = "Litter moisture",
                    value = "20–25",
                    unit = "%",
                    toleranceText = "Over 30 % = wet litter → ammonia, foot-pad lesions · rake every 2 days",
                    statusTag = "ideal",
                    kind = ValueKind.IDEAL
                )
            }
            VentThermoCard(entry = entry, farm = farm, vk = vk)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

data class GistItem(val label: String, val value: String, val sub: String, val kind: ValueKind = ValueKind.NEUTRAL)

@Composable
fun GistRow(a: GistItem, b: GistItem, c: GistItem) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GistTile(a, Modifier.weight(1f))
        GistTile(b, Modifier.weight(1f))
        GistTile(c, Modifier.weight(1f))
    }
}

@Composable
fun GistTile(item: GistItem, modifier: Modifier = Modifier) {
    val valColor = if (item.value == "—") kindColor(ValueKind.NEUTRAL) else kindColor(item.kind)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
            Text(
                item.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                item.value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace),
                color = valColor,
                maxLines = 1
            )
            Text(
                item.sub,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/** Compact colour key — one word per kind, no prose. */
@Composable
fun ValueLegend(isProjected: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            LegendChip("Present", ValuePresent)
            LegendChip("Predicted", ValuePredicted)
            LegendChip("Ideal", ValueIdeal)
        }
    }
}

@Composable
private fun LegendChip(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(11.dp).background(color, RoundedCornerShape(3.dp)))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = color))
    }
}

@Composable
fun IdealRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = ValueIdeal,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
        )
    }
}

fun accentForTitle(title: String): Color = when {
    title.contains("glance", true) -> BrandEmerald
    title.contains("Growth", true) -> BrandEmerald
    title.contains("Feed", true) || title.contains("Stock", true) -> DomainFeed
    title.contains("Water", true) -> DomainWater
    title.contains("Vent", true) || title.contains("Climate", true) || title.contains("Air", true) -> DomainVent
    title.contains("Space", true) || title.contains("Density", true) -> DomainInsight
    title.contains("Health", true) || title.contains("Mortality", true) -> DomainMed
    title.contains("Ideal", true) -> DomainInsight
    title.contains("Performance", true) || title.contains("Curve", true) -> DomainVent
    else -> BrandEmerald
}

@Composable
fun OutputCard(
    title: String,
    content: @Composable () -> Unit
) {
    // One consistent accent for every card title so the screen reads as one organised system.
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 18.dp)
                        .background(accent, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
            content()
        }
    }
}

@Composable
fun MeasuredMetric(
    label: String,
    measured: Double?,
    unit: String,
    decimals: Int,
    idealText: String
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = measured?.let { String.format("%.${decimals}f", it) + if (unit.isNotBlank()) " $unit" else "" } ?: "— no reading",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                color = if (measured == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = idealText,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        )
    }
}

@Composable
fun BigMetric(
    label: String,
    value: String,
    unit: String,
    toleranceText: String,
    statusTag: String,
    isHero: Boolean = false,
    kind: ValueKind = ValueKind.PRESENT
) {
    val effectiveKind = if (value == "—") ValueKind.NEUTRAL else kind
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Header: label (ellipsised) on the left, status tag on the right — never overlaps.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (statusTag.isNotBlank()) {
                Text(
                    text = statusTag,
                    maxLines = 1,
                    softWrap = false,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (statusTag.contains("crit") || statusTag.contains("over")) StatusCrit
                        else if (statusTag.contains("warn") || statusTag.contains("behind")) StatusWarn
                        else BrandEmerald
                    ),
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }

        // Value row: big value + unit on the left, provenance chip pinned to the right.
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
        ) {
            // Word values (e.g. "Minimum Ventilation") get a smaller, non-mono style so they
            // don't blow up like a big number.
            val isWord = value.any { it.isLetter() }
            Text(
                text = value,
                color = kindColor(effectiveKind),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = when {
                    isWord -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    isHero -> MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 36.sp
                    )
                    else -> MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                    )
                }
            )
            if (unit.isNotBlank()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = unit,
                    maxLines = 1,
                    softWrap = false,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            if (kindTag(effectiveKind).isNotEmpty()) {
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    color = kindWash(effectiveKind),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        text = kindTag(effectiveKind),
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = kindColor(effectiveKind),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }

        Text(
            text = toleranceText,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

/**
 * Population size distribution from today's 5-spot weight sample — shows how big the birds
 * are and how uniform the flock is (the infographic the original workbook had for CV%).
 */
@Composable
fun PopulationDistributionCard(entry: DailyDataEntity) {
    data class Loc(val label: String, val avg: Double, val count: Int)
    val locs = buildList {
        val pairs = listOf(
            "L1" to (entry.w1 to entry.n1), "L2" to (entry.w2 to entry.n2), "L3" to (entry.w3 to entry.n3),
            "L4" to (entry.w4 to entry.n4), "L5" to (entry.w5 to entry.n5)
        )
        for ((label, wn) in pairs) {
            val w = wn.first ?: 0.0
            val n = wn.second ?: 0
            if (w > 0 && n > 0) add(Loc(label, w / n, n))
        }
    }

    OutputCard(title = "Bird size & uniformity") {
        if (locs.isEmpty()) {
            Text(
                "No weights entered today — the dashboard is on ideal / projected targets. " +
                        "Weigh 5 spots (in the ENTRY tab) to see the live size distribution.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@OutputCard
        }

        val totalN = locs.sumOf { it.count }
        val mean = entry.avgWeight ?: (locs.sumOf { it.avg * it.count } / totalN)
        val cv = entry.cv
        val minScale = mean * 0.8
        val maxScale = mean * 1.2

        // Header numbers
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GistTile(GistItem("Mean wt", "${Fmt.n(mean, 1)}g", "$totalN birds"), Modifier.weight(1f))
            GistTile(
                GistItem("CV%", Fmt.n(cv, 2),
                    if (cv == null) "need ≥2 spots" else if (cv < 10) "uniform" else if (cv < 12) "uneven" else "very uneven"),
                Modifier.weight(1f)
            )
            val spread = (locs.maxOf { it.avg } - locs.minOf { it.avg })
            GistTile(GistItem("Spread", "${Fmt.n(spread, 1)}g", "min→max"), Modifier.weight(1f))
        }

        // Bars — one per weighed location, height ∝ average weight, colour ∝ deviation from mean
        Row(
            modifier = Modifier.fillMaxWidth().height(150.dp).padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            for (loc in locs) {
                val frac = ((loc.avg - minScale) / (maxScale - minScale)).coerceIn(0.08, 1.0)
                val dev = if (mean > 0) (loc.avg - mean) / mean else 0.0
                val barColor = when {
                    abs(dev) <= 0.05 -> BrandEmerald
                    abs(dev) <= 0.10 -> StatusWarn
                    else -> StatusCrit
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        Fmt.n(loc.avg, 1),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((frac * 110).dp)
                            .background(barColor, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    )
                    Text(loc.label, style = MaterialTheme.typography.labelSmall)
                    Text("n=${loc.count}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant))
                }
            }
        }

        // Size bands relative to the flock mean
        val light = locs.count { it.avg < mean * 0.95 }
        val onTarget = locs.count { it.avg >= mean * 0.95 && it.avg <= mean * 1.05 }
        val heavy = locs.count { it.avg > mean * 1.05 }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BandChip("Light <95%", light, StatusWarn, Modifier.weight(1f))
            BandChip("On-target", onTarget, BrandEmerald, Modifier.weight(1f))
            BandChip("Heavy >105%", heavy, StatusCrit, Modifier.weight(1f))
        }
        Text(
            text = if ((cv ?: 0.0) >= 10.0)
                "Uneven flock — grade/sort the lighter spots and check feeder/drinker access there."
            else "Uniform flock — birds are close to the mean across all sampled spots.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BandChip(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$count", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = color, fontFamily = FontFamily.Monospace))
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** Smooth (Catmull-Rom → cubic-bezier) path through the given points. */
private fun buildSmoothPath(pts: List<Offset>): Path {
    val path = Path()
    if (pts.isEmpty()) return path
    path.moveTo(pts[0].x, pts[0].y)
    if (pts.size == 1) return path
    if (pts.size == 2) { path.lineTo(pts[1].x, pts[1].y); return path }
    for (i in 0 until pts.size - 1) {
        val p0 = pts[if (i - 1 < 0) 0 else i - 1]
        val p1 = pts[i]
        val p2 = pts[i + 1]
        val p3 = pts[if (i + 2 > pts.size - 1) pts.size - 1 else i + 2]
        val c1x = p1.x + (p2.x - p0.x) / 6f
        val c1y = p1.y + (p2.y - p0.y) / 6f
        val c2x = p2.x - (p3.x - p1.x) / 6f
        val c2y = p2.y - (p3.y - p1.y) / 6f
        path.cubicTo(c1x, c1y, c2x, c2y, p2.x, p2.y)
    }
    return path
}

private fun smoothFill(pts: List<Offset>, baseline: Float): Path {
    val p = buildSmoothPath(pts)
    if (pts.isNotEmpty()) { p.lineTo(pts.last().x, baseline); p.lineTo(pts.first().x, baseline); p.close() }
    return p
}

@Composable
fun GrowthCurveCanvas(dailyRows: List<DailyDataEntity>, breed: String, markerDay: Int = -1) {
    val gridC = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val idealC = ValueIdeal
    val presentC = ValuePresent
    val markerC = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier.fillMaxWidth().height(200.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp)
    ) {
        val maxDays = 42f; val maxWeight = 3600f
        val padL = 34f; val padB = 18f
        val gw = size.width - padL; val gh = size.height - padB
        fun px(d: Float) = padL + (d / maxDays) * gw
        fun py(wt: Float) = gh - (wt / maxWeight) * gh
        val lbl = Paint().apply { color = labelArgb; textSize = 22f; isAntiAlias = true }

        for (gv in listOf(1000f, 2000f, 3000f)) {
            val y = py(gv)
            drawLine(gridC, Offset(padL, y), Offset(padL + gw, y), strokeWidth = 1f)
            drawContext.canvas.nativeCanvas.drawText("${(gv / 1000).toInt()}k", 0f, y + 7f, lbl)
        }
        for (d in listOf(0, 14, 28, 42)) drawContext.canvas.nativeCanvas.drawText("$d", px(d.toFloat()) - 5f, size.height, lbl)

        // faint ±5% band
        val band = Path().apply {
            moveTo(px(0f), py(PhysiologicalEngine.bwFromDay(0.0, breed).toFloat() * 1.05f))
            for (d in 1..42) lineTo(px(d.toFloat()), py(PhysiologicalEngine.bwFromDay(d.toDouble(), breed).toFloat() * 1.05f))
            for (d in 42 downTo 0) lineTo(px(d.toFloat()), py(PhysiologicalEngine.bwFromDay(d.toDouble(), breed).toFloat() * 0.95f))
            close()
        }
        drawPath(band, color = idealC.copy(alpha = 0.07f))

        // ideal centre line (smooth, solid)
        val idealPts = (0..42).map { Offset(px(it.toFloat()), py(PhysiologicalEngine.bwFromDay(it.toDouble(), breed).toFloat())) }
        drawPath(buildSmoothPath(idealPts), color = idealC.copy(alpha = 0.85f), style = Stroke(width = 2.5f, cap = StrokeCap.Round))

        // present/projected line + gradient fill; dots only on measured points
        val rows = dailyRows.sortedBy { it.dayNumber }
        val pts = rows.mapNotNull { r ->
            val w = r.avgWeight ?: (if (r.projected) PhysiologicalEngine.bwFromDay(r.weightAge ?: r.dayNumber.toDouble(), breed) else null)
            if (w != null && w > 0) Offset(px(r.dayNumber.toFloat()), py(w.toFloat())) else null
        }
        if (pts.isNotEmpty()) {
            drawPath(smoothFill(pts, gh), brush = Brush.verticalGradient(listOf(presentC.copy(alpha = 0.28f), presentC.copy(alpha = 0f)), startY = 0f, endY = gh))
            drawPath(buildSmoothPath(pts), color = presentC, style = Stroke(width = 4.5f, cap = StrokeCap.Round))
            rows.filter { it.sampleEntered }.forEach { r ->
                val w = r.avgWeight ?: return@forEach
                val c = Offset(px(r.dayNumber.toFloat()), py(w.toFloat()))
                drawCircle(presentC, 5.5f, c)
                drawCircle(Color.White, 2.2f, c)
            }
        }
        if (markerDay in 0..42) drawLine(markerC.copy(alpha = 0.5f), Offset(px(markerDay.toFloat()), 0f), Offset(px(markerDay.toFloat()), gh), strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
    }
}

@Composable
fun FcrCurveCanvas(dailyRows: List<DailyDataEntity>, breed: String = "Ross308", markerDay: Int = -1) {
    val gridC = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val idealC = ValueIdeal
    val presentC = ValuePresent
    val critC = StatusCrit
    val markerC = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier.fillMaxWidth().height(175.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp)
    ) {
        val maxDays = 42f; val maxFcr = 2.2f; val minFcr = 0.9f; val range = maxFcr - minFcr
        val padL = 34f; val padB = 18f
        val gw = size.width - padL; val gh = size.height - padB
        fun px(d: Float) = padL + (d / maxDays) * gw
        fun py(f: Float) = gh - ((f - minFcr) / range) * gh
        val lbl = Paint().apply { color = labelArgb; textSize = 22f; isAntiAlias = true }

        for (fv in listOf(1.0f, 1.5f, 2.0f)) {
            val y = py(fv)
            drawLine(gridC, Offset(padL, y), Offset(padL + gw, y), strokeWidth = 1f)
            drawContext.canvas.nativeCanvas.drawText(String.format("%.1f", fv), 0f, y + 7f, lbl)
        }
        for (d in listOf(0, 14, 28, 42)) drawContext.canvas.nativeCanvas.drawText("$d", px(d.toFloat()) - 5f, size.height, lbl)

        val critPts = (5..42).map { Offset(px(it.toFloat()), py((PhysiologicalEngine.stdFcrFromDay(it.toDouble(), breed).toFloat() * 1.15f).coerceAtMost(maxFcr))) }
        drawPath(buildSmoothPath(critPts), color = critC.copy(alpha = 0.55f), style = Stroke(width = 2f, cap = StrokeCap.Round))
        val idealPts = (5..42).map { Offset(px(it.toFloat()), py(PhysiologicalEngine.stdFcrFromDay(it.toDouble(), breed).toFloat())) }
        drawPath(buildSmoothPath(idealPts), color = idealC.copy(alpha = 0.85f), style = Stroke(width = 2.5f, cap = StrokeCap.Round))

        // present — skip the first few days (FCR is meaningless before real weight gain)
        val pts = dailyRows.filter { it.dayNumber >= 5 }.sortedBy { it.dayNumber }
            .mapNotNull { r -> r.fcr?.toFloat()?.let { if (it in minFcr..maxFcr) Offset(px(r.dayNumber.toFloat()), py(it)) else null } }
        if (pts.size > 1) drawPath(buildSmoothPath(pts), color = presentC, style = Stroke(width = 4.5f, cap = StrokeCap.Round))
        pts.forEach { drawCircle(presentC, 4.5f, it); drawCircle(Color.White, 1.8f, it) }

        if (markerDay in 0..42) drawLine(markerC.copy(alpha = 0.5f), Offset(px(markerDay.toFloat()), 0f), Offset(px(markerDay.toFloat()), gh), strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
    }
}

@Composable
fun MortalityCurveCanvas(dailyRows: List<DailyDataEntity>, markerDay: Int = -1) {
    val gridC = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val critC = StatusCrit
    val presentC = ValuePresent
    val markerC = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier.fillMaxWidth().height(175.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp)
    ) {
        val maxDays = 42f; val maxMort = 6.0f
        val padL = 34f; val padB = 18f
        val gw = size.width - padL; val gh = size.height - padB
        fun px(d: Float) = padL + (d / maxDays) * gw
        fun py(m: Float) = gh - (m / maxMort) * gh
        val lbl = Paint().apply { color = labelArgb; textSize = 22f; isAntiAlias = true }

        for (mv in listOf(2f, 4f, 6f)) {
            val y = py(mv)
            drawLine(gridC, Offset(padL, y), Offset(padL + gw, y), strokeWidth = 1f)
            drawContext.canvas.nativeCanvas.drawText("${mv.toInt()}%", 0f, y + 7f, lbl)
        }
        for (d in listOf(0, 14, 28, 42)) drawContext.canvas.nativeCanvas.drawText("$d", px(d.toFloat()) - 5f, size.height, lbl)

        val ceilPts = (0..42).map { Offset(px(it.toFloat()), py(PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_MAXMORT_BY_AGE, it.toDouble()).toFloat())) }
        drawPath(buildSmoothPath(ceilPts), color = critC.copy(alpha = 0.7f), style = Stroke(width = 2.5f, cap = StrokeCap.Round))

        val pts = dailyRows.sortedBy { it.dayNumber }.mapNotNull { r -> r.cumMortPct?.toFloat()?.let { Offset(px(r.dayNumber.toFloat()), py(it.coerceAtMost(maxMort))) } }
        if (pts.isNotEmpty()) {
            drawPath(smoothFill(pts, gh), brush = Brush.verticalGradient(listOf(presentC.copy(alpha = 0.28f), presentC.copy(alpha = 0f)), startY = 0f, endY = gh))
            if (pts.size > 1) drawPath(buildSmoothPath(pts), color = presentC, style = Stroke(width = 4f, cap = StrokeCap.Round))
            drawCircle(presentC, 4.5f, pts.last()); drawCircle(Color.White, 1.8f, pts.last())
        }
        if (markerDay in 0..42) drawLine(markerC.copy(alpha = 0.5f), Offset(px(markerDay.toFloat()), 0f), Offset(px(markerDay.toFloat()), gh), strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
    }
}

/** Air, heat load and air quality — ideals and metabolic estimates (controller settings are above). */
@Composable
fun VentThermoCard(entry: DailyDataEntity, farm: FarmEntity, vk: ValueKind) {
    OutputCard(title = "Air, heat load & air quality") {
        val liveBirds = entry.liveBirds
        val avgKg = (entry.avgWeight ?: entry.idealWeight) / 1000.0
        val effFanCfm = farm.fanRatedCfm * (1.0 - farm.fanDerate)
        val maxCoolCfm = farm.fanCount * effFanCfm
        val cross = farm.usableWidthFt * farm.heightFt
        val houseVolFt3 = farm.usableLengthFt * farm.usableWidthFt * farm.heightFt
        val heatPerBirdW = 10.62 * Math.pow(avgKg.coerceAtLeast(0.04), 0.75)
        val heatTotalKw = heatPerBirdW * liveBirds / 1000.0
        val sensibleFrac = (0.61 * (1 + 0.02 * (20 - entry.tempIdeal)) - 0.000228 * entry.tempIdeal * entry.tempIdeal).coerceIn(0.15, 0.75)
        val I = ValueKind.IDEAL
        LedgerTable(
            headers = listOf("Value", "Note"),
            rows = listOf(
                LRow("All fans airflow", "cfm", listOf(LCell(Fmt.n(maxCoolCfm, 0), I), LCell("${farm.fanCount} × ${Fmt.n(effFanCfm, 0)}", I))),
                LRow("Top air speed", "ft/min", listOf(LCell(Fmt.n(if (cross > 0) maxCoolCfm / cross else 0.0, 0), I), LCell("age cap ${Fmt.n(PhysiologicalEngine.maxAirSpeedFpm(entry.dayNumber), 0)}", I))),
                LRow("Air changes at all fans", "per hour", listOf(LCell(Fmt.n(if (houseVolFt3 > 0) maxCoolCfm * 60 / houseVolFt3 else 0.0, 1), I), LCell("", I))),
                LRow("Bird heat", "W per bird · kW house", listOf(LCell(Fmt.n(heatPerBirdW, 2), vk), LCell(Fmt.n(heatTotalKw, 1) + " kW", vk))),
                LRow("Sensible / latent", "W per bird", listOf(LCell(Fmt.n(heatPerBirdW * sensibleFrac, 2), vk), LCell(Fmt.n(heatPerBirdW * (1 - sensibleFrac), 2), vk))),
                LRow("Static pressure", "Pa", listOf(LCell("20–25", I), LCell("tunnel 30–37 · alarm <15 / >45", I))),
                LRow("Humidity", "% RH", listOf(LCell(Fmt.n(entry.rhIdeal, 0), I), LCell(if (entry.dayNumber <= 10) "60–70 brooding" else "50–60 after", I))),
                LRow("CO₂ / NH₃ / CO", "ppm", listOf(LCell("< 3000 / 10 / 10", I), LCell("crit ${Fmt.n(entry.co2Max, 0)} / ${Fmt.n(entry.nh3Max, 0)}", I))),
                LRow("Dust · O₂", "mg/m³ · %", listOf(LCell("< 5 · ≥ 19.6", I), LCell("", I))),
                LRow("Lighting", "hours light", listOf(LCell(Fmt.n(entry.lightHours, 1), I), LCell("dark ${Fmt.n(24.0 - entry.lightHours, 1)} h", I)))
            )
        )
        Text(
            "Pads only after all allowed fans run; off above 80–85 % RH. Bird heat: CIGR 10.62 × kg^0.75 W.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Holder for a BigMetric's args so two can sit side-by-side neatly. */
data class BM(
    val label: String, val value: String, val unit: String,
    val tol: String, val tag: String, val kind: ValueKind = ValueKind.PRESENT
)

@Composable
fun TwoMetric(a: BM, b: BM) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            BigMetric(a.label, a.value, a.unit, a.tol, a.tag, kind = a.kind)
        }
        Column(modifier = Modifier.weight(1f)) {
            BigMetric(b.label, b.value, b.unit, b.tol, b.tag, kind = b.kind)
        }
    }
}

@Composable
fun GraphLegend() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        GraphDot(ValuePresent, "Present")
        GraphDot(ValueIdeal, "Ideal")
        GraphDot(StatusCrit, "Critical")
    }
}

@Composable
private fun GraphDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
        Spacer(modifier = Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = color))
    }
}

@Composable
fun CfcrCurveCanvas(dailyRows: List<DailyDataEntity>, breed: String = "Ross308", markerDay: Int = -1) {
    val gridC = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val idealC = ValueIdeal
    val presentC = ValuePresent
    val markerC = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier.fillMaxWidth().height(175.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp)
    ) {
        val maxDays = 42f; val maxV = 2.2f; val minV = 0.9f; val range = maxV - minV
        val padL = 34f; val padB = 18f
        val gw = size.width - padL; val gh = size.height - padB
        fun px(d: Float) = padL + (d / maxDays) * gw
        fun py(v: Float) = gh - ((v - minV) / range) * gh
        val lbl = Paint().apply { color = labelArgb; textSize = 22f; isAntiAlias = true }

        for (fv in listOf(1.0f, 1.5f, 2.0f)) {
            val y = py(fv)
            drawLine(gridC, Offset(padL, y), Offset(padL + gw, y), strokeWidth = 1f)
            drawContext.canvas.nativeCanvas.drawText(String.format("%.1f", fv), 0f, y + 7f, lbl)
        }
        for (d in listOf(0, 14, 28, 42)) drawContext.canvas.nativeCanvas.drawText("$d", px(d.toFloat()) - 5f, size.height, lbl)

        val idealPts = (5..42).map {
            val idealKg = PhysiologicalEngine.bwFromDay(it.toDouble(), breed) / 1000.0
            val v = ((2.0 - idealKg) * 0.25 + PhysiologicalEngine.stdFcrFromDay(it.toDouble(), breed)).toFloat().coerceIn(minV, maxV)
            Offset(px(it.toFloat()), py(v))
        }
        drawPath(buildSmoothPath(idealPts), color = idealC.copy(alpha = 0.85f), style = Stroke(width = 2.5f, cap = StrokeCap.Round))

        val pts = dailyRows.filter { it.dayNumber >= 5 }.sortedBy { it.dayNumber }
            .mapNotNull { r -> r.cFcr?.toFloat()?.let { if (it in minV..maxV) Offset(px(r.dayNumber.toFloat()), py(it)) else null } }
        if (pts.size > 1) drawPath(buildSmoothPath(pts), color = presentC, style = Stroke(width = 4.5f, cap = StrokeCap.Round))
        pts.forEach { drawCircle(presentC, 4.5f, it); drawCircle(Color.White, 1.8f, it) }

        if (markerDay in 0..42) drawLine(markerC.copy(alpha = 0.5f), Offset(px(markerDay.toFloat()), 0f), Offset(px(markerDay.toFloat()), gh), strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
    }
}
