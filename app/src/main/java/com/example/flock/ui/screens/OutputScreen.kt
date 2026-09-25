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
        // Colour/position legend so the three kinds of numbers are never confused.
        ValueLegend(isProjected = isProjected)

        // 0. GIST — quick glance summary, then detailed topic cards below
        OutputCard(title = "Today at a glance · Day $day") {
            val gAvg = entry.avgWeight ?: PhysiologicalEngine.bwFromDay(weightAge, breed)
            val gIdeal = PhysiologicalEngine.bwFromDay(day.toDouble(), breed)
            val gStdFcr = PhysiologicalEngine.stdFcrFromDay(day.toDouble(), breed)
            GistRow(
                GistItem("Avg wt", "${String.format("%.0f", gAvg)}g", "ideal ${String.format("%.0f", gIdeal)}", vk),
                GistItem("Wt-age", "${String.format("%.1f", weightAge)}d", "cal. day $day", vk),
                GistItem("CV%", entry.cv?.let { String.format("%.1f", it) } ?: "—", "<10 ideal", if (entry.cv != null) ValueKind.PRESENT else ValueKind.NEUTRAL)
            )
            GistRow(
                GistItem("FCR", entry.fcr?.let { String.format("%.2f", it) } ?: "—", "std ${String.format("%.2f", gStdFcr)}", if (entry.fcr != null) ValueKind.PRESENT else ValueKind.NEUTRAL),
                GistItem("cFCR", entry.cFcr?.let { String.format("%.2f", it) } ?: "—", "→ 2kg", if (entry.cFcr != null) ValueKind.PRESENT else ValueKind.NEUTRAL),
                GistItem("Cum mort", entry.cumMortPct?.let { String.format("%.1f", it) + "%" } ?: "—", "≤ ${String.format("%.1f", entry.maxMortPct)}%", ValueKind.PRESENT)
            )
            GistRow(
                GistItem("Feed", "${entry.feedBags} bags", "${String.format("%.0f", entry.totalFeedKg)} kg", vk),
                GistItem("Water", "${String.format("%.0f", entry.totalWaterL)}L", "${entry.tankRefills} fills", vk),
                GistItem("Stock", String.format("%.0f", feedStockSummary.totalOnHandBags), "bags left", ValueKind.PRESENT)
            )
            GistRow(
                GistItem("Min-vent", "${entry.fansToRun} fan", entry.cycleText, vk),
                GistItem("Density", entry.densityKgM2?.let { String.format("%.2f", kgPerFt2(it)) } ?: "—", "≤ ${String.format("%.2f", kgPerFt2(farm.densityCapDefault))} kg/ft²", vk),
                GistItem("Set °C", String.format("%.1f", entry.tempIdeal), "${String.format("%.1f", entry.tempMin)}–${String.format("%.1f", entry.tempMax)}", ValueKind.IDEAL)
            )
            GistRow(
                GistItem("Live birds", "${entry.liveBirds}", "of ${flock?.birdsPlaced ?: 0}", ValueKind.PRESENT),
                GistItem("Feed/bird", "${String.format("%.0f", entry.feedPerBird)}g", "per day", vk),
                GistItem("Livability", entry.livability?.let { String.format("%.1f", it) + "%" } ?: "—", "alive", if (entry.livability != null) ValueKind.PRESENT else ValueKind.NEUTRAL)
            )
            Text(
                "Detailed, topic-wise breakdown below ↓",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ============================ VENTILATION & THERMODYNAMICS ============================
        VentThermoCard(entry = entry, farm = farm, vk = vk)
        FanVisualizer(entry = entry, farm = farm)

        // ================================= BIRDS =================================
        OutputCard(title = "Birds — Growth & Conversion") {
            val avgG = entry.avgWeight ?: PhysiologicalEngine.bwFromDay(weightAge, breed)
            val idealBw = PhysiologicalEngine.bwFromDay(day.toDouble(), breed)
            val bwDiffPct = if (idealBw > 0) ((avgG - idealBw) / idealBw) * 100.0 else 0.0

            // Avg Weight
            BigMetric(
                label = "Average Body Weight",
                value = String.format("%.0f", avgG),
                unit = "g",
                toleranceText = "Safe: ${String.format("%.0f", idealBw * 0.95)}–${String.format("%.0f", idealBw * 1.05)} g · Ideal: ${String.format("%.0f", idealBw)} g",
                statusTag = if (abs(bwDiffPct) > 5.0) (if (bwDiffPct > 0) "▲ +${String.format("%.1f", bwDiffPct)}%" else "▼ ${String.format("%.1f", bwDiffPct)}%") else "ok",
                kind = vk
            )

            // Weight-Age anchor comparison
            val ageDiff = weightAge - day.toDouble()
            val ageDiffStr = if (ageDiff >= 0) "+${String.format("%.1f", ageDiff)}d ahead" else "${String.format("%.1f", ageDiff)}d behind"
            BigMetric(
                label = "Weight-Age",
                value = String.format("%.1f", weightAge),
                unit = "days",
                toleranceText = "Calendar Day $day · Difference: $ageDiffStr",
                statusTag = if (abs(ageDiff) > 1.5) (if (ageDiff > 0) "ahead" else "behind") else "on curve",
                kind = vk
            )

            // Uniformity CV%
            val cvVal = entry.cv
            BigMetric(
                label = "Flock Uniformity (CV%)",
                value = cvVal?.let { String.format("%.1f", it) } ?: "—",
                unit = "%",
                toleranceText = "Ideal: <10.0% · Warn: ≥10.0% · Crit: ≥12.0%",
                statusTag = if (cvVal == null) "no sample" else if (cvVal >= 12.0) "▲ crit high" else if (cvVal >= 10.0) "▲ warn" else "ok"
            )

            // FCR & cFCR
            val stdFcr = PhysiologicalEngine.stdFcrFromDay(day.toDouble(), breed)
            val fcrVal = entry.fcr
            val cFcrVal = entry.cFcr
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    BigMetric(
                        label = "FCR",
                        value = fcrVal?.let { String.format("%.2f", it) } ?: "—",
                        unit = "",
                        toleranceText = "Ideal: ${String.format("%.2f", stdFcr)} · Crit: >${String.format("%.2f", stdFcr * 1.15)}",
                        // FCR is meaningless in the first week (birds have barely gained weight).
                        statusTag = if (day < 7) "settling" else if (fcrVal != null && fcrVal > stdFcr * 1.15) "▲ crit" else "ok"
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    BigMetric(
                        label = "cFCR → 2 kg",
                        value = cFcrVal?.let { String.format("%.2f", it) } ?: "—",
                        unit = "",
                        toleranceText = "(2 − avg kg) × 0.25 + FCR",
                        statusTag = if (day < 7) "settling" else "std"
                    )
                }
            }
            BigMetric(
                label = "Approx. Daily Gain / bird",
                value = String.format("%.0f", entry.gainPerBird),
                unit = "g/day",
                toleranceText = "Expected growth at weight-age ${String.format("%.1f", weightAge)} d",
                statusTag = "curve",
                kind = vk
            )
            // Brooding quality check: day-7 weight should be at least 4.5× the chick weight.
            val w7 = dailyRows.firstOrNull { it.dayNumber == 7 }?.avgWeight
            if (day >= 7 && w7 != null) {
                val w0 = dailyRows.firstOrNull { it.dayNumber == 0 }?.avgWeight ?: PhysiologicalEngine.bwFromDay(0.0, breed)
                val mult = if (w0 > 0) w7 / w0 else 0.0
                BigMetric(
                    label = "7-day weight multiple",
                    value = String.format("%.1f", mult),
                    unit = "×",
                    toleranceText = "Day 7 ${String.format("%.0f", w7)} g ÷ chick ${String.format("%.0f", w0)} g · target ≥ 4.5×",
                    statusTag = if (mult < 4.0) "▼ low" else if (mult < 4.5) "watch" else "ok",
                    kind = ValueKind.PRESENT
                )
            }
        }

        // 1b. BIRD SIZE & UNIFORMITY — population distribution from today's 5-spot sample
        PopulationDistributionCard(entry = entry)

        // Brooding barricade / floor-plan (updates daily)
        HouseFloorPlan(entry = entry, farm = farm)

        OutputCard(title = "Birds — Space & Density") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    val density = entry.densityKgM2
                    BigMetric(
                        label = "Stocking Density",
                        value = density?.let { String.format("%.2f", kgPerFt2(it)) } ?: "—",
                        unit = "kg/ft²",
                        toleranceText = "Safe cap: ≤ ${String.format("%.2f", kgPerFt2(farm.densityCapDefault))} kg/ft²",
                        statusTag = if (density != null && density > farm.densityCapDefault) "▲ over cap" else "ok",
                        kind = vk
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    BigMetric(
                        label = "Floor Space",
                        value = String.format("%.2f", entry.ftPerBird),
                        unit = "ft²/bird",
                        toleranceText = "Minimum recommended: ≥ ${String.format("%.2f", entry.minFtPerBird)} ft²",
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
                    toleranceText = "Occupied floor area: ${String.format("%.0f", entry.occupiedFt2)} ft²",
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

        OutputCard(title = "Birds — Health & Mortality") {
            val avgKgH = (entry.avgWeight ?: entry.idealWeight) / 1000.0
            val totalFarmKg = entry.liveBirds * avgKgH
            BigMetric(
                label = "Balance Birds (alive in shed)",
                value = "${entry.liveBirds}",
                unit = "birds",
                toleranceText = "Placed ${flock?.birdsPlaced ?: 0} − reception − mortality (${entry.cumMort}) − lifted − culls",
                statusTag = "live",
                isHero = true,
                kind = ValueKind.PRESENT
            )
            BigMetric(
                label = "Total live weight in farm",
                value = String.format("%,.0f", totalFarmKg),
                unit = "kg",
                toleranceText = "${entry.liveBirds} birds × ${String.format("%.3f", avgKgH)} kg avg",
                statusTag = "total",
                kind = vk
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    val cMort = entry.cumMortPct
                    BigMetric(
                        label = "Cumulative Mortality",
                        value = cMort?.let { String.format("%.2f", it) } ?: "—",
                        unit = "%",
                        toleranceText = "Ceiling: ≤ ${String.format("%.1f", entry.maxMortPct)}% · Total: ${entry.cumMort} dead",
                        // Early ceilings are tiny and reception deaths skew them, so don't cry "crit" in week 1.
                        statusTag = if (cMort != null && cMort > entry.maxMortPct) (if (day < 7) "settling" else "▲ crit") else "ok",
                        kind = ValueKind.PRESENT
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    BigMetric(
                        label = "Flock Livability",
                        value = entry.livability?.let { String.format("%.1f", it) } ?: "—",
                        unit = "%",
                        toleranceText = "Live birds in shed: ${entry.liveBirds}",
                        statusTag = "live",
                        kind = ValueKind.PRESENT
                    )
                }
            }
            // European Production Efficiency Factor — the integrator's one-number flock score.
            val epef = if (day >= 7 && entry.fcr != null && entry.fcr > 0 && entry.livability != null)
                entry.livability * avgKgH * 100.0 / (day * entry.fcr) else null
            BigMetric(
                label = "EPEF (efficiency factor)",
                value = epef?.let { String.format("%.0f", it) } ?: "—",
                unit = "",
                toleranceText = "Livability % × kg × 100 ÷ (age × FCR) · good ≥ 350 at lifting",
                statusTag = if (epef == null) (if (day < 7) "settling" else "needs FCR") else if (day >= 28 && epef < 300) "▼ low" else "ok",
                kind = if (entry.projected) ValueKind.PREDICTED else ValueKind.PRESENT
            )
        }

        // ================================= FEED =================================
        OutputCard(title = "Feed") {
            val reqToDateBags = dailyRows.filter { it.dayNumber <= day }.sumOf { it.feedBags }
            val fullCycleBags = dailyRows.sumOf { it.feedBags }
            val consumedBags = feedStockSummary.totalUsedBags
            val onHandBags = feedStockSummary.totalOnHandBags

            // REQUIRED (the plan) — what to give today
            BigMetric(
                label = "Feed bags required today",
                value = "${entry.feedBags}",
                unit = "bags (${farm.feedBagKg.toInt()}kg ea)",
                toleranceText = "Ideal plan · ${String.format("%.1f", entry.totalFeedKg)} kg on ${entry.liveBirds} live birds" +
                        (if (day == 0) " · Day 0 = pre-load the Day-1 starter ration" else ""),
                statusTag = "required",
                isHero = true,
                kind = vk
            )

            // Plan (required) vs actual (consumed) vs stock (on-hand)
            GistRow(
                GistItem("Req. to date", "$reqToDateBags", "Day 0→$day, plan", vk),
                GistItem("Consumed", "${consumedBags.toInt()}", "you logged", ValueKind.PRESENT),
                GistItem("On-hand", "${onHandBags.toInt()}", "in store", ValueKind.PRESENT)
            )
            Text(
                "Whole-cycle plan ≈ $fullCycleBags bags (${String.format("%,.0f", fullCycleBags * farm.feedBagKg)} kg). " +
                        "“Required” is the ideal plan from the curve; “consumed” is what you logged as used.",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            )

            val idealFeedPerBird = PhysiologicalEngine.dailyFeedFromDay(max(1.0, weightAge), breed)
            BigMetric(
                label = "Feed / bird",
                value = String.format("%.0f", entry.feedPerBird),
                unit = "g/day",
                toleranceText = "Ideal (breed curve): ${String.format("%.0f", idealFeedPerBird)} g/day" +
                        (if (entry.feedPerBird < idealFeedPerBird * 0.98) " · trimmed for heat" else ""),
                statusTag = if (entry.feedPerBird < idealFeedPerBird * 0.9) "heat-reduced" else "on curve",
                kind = vk
            )
            val (phase, nextPhase) = when {
                day <= 11 -> "B1 · Starter" to "B2 from day 12"
                day <= 23 -> "B2 · Grower" to "B3 from day 24"
                else -> "B3 · Finisher" to "until lifting"
            }
            BigMetric(
                label = "Feed phase",
                value = phase,
                unit = "",
                toleranceText = "$nextPhase · Ross: starter ≤ 10, grower 11–24, finisher 25+",
                statusTag = "by age",
                kind = ValueKind.IDEAL
            )

            Divider(modifier = Modifier.padding(vertical = 4.dp))

            // Stock Inventory breakdown by feed type
            Text(
                text = "Feed Stock on Hand",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Feed on Hand:", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text(
                            text = "${String.format("%.1f", feedStockSummary.totalOnHandBags)} bags",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (feedStockSummary.totalOnHandBags < 20.0) StatusCrit else BrandEmerald
                            )
                        )
                    }

                    if (feedStockSummary.perTypeOnHand.isNotEmpty()) {
                        for ((code, qty) in feedStockSummary.perTypeOnHand) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("• Type $code:", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "${String.format("%.1f", qty)} bags",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "No feed delivery records yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IdealRow("Ideal godown (store) temp", "< 25 °C, cool & shaded")
            IdealRow("Ideal godown humidity", "< 60 % RH, dry & off the floor")
        }

        // ================================= WATER =================================
        OutputCard(title = "Water") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    BigMetric(
                        label = "Water per Bird",
                        value = String.format("%.0f", entry.waterPerBird),
                        unit = "mL",
                        toleranceText = "Water:Feed ratio 1.8x adjusted for heat",
                        statusTag = "nominal",
                        kind = vk
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    BigMetric(
                        label = "Total Water Today",
                        value = String.format("%.0f", entry.totalWaterL),
                        unit = "L",
                        toleranceText = "Tank refills: ~${entry.tankRefills} (${farm.drinkTankL.toInt()}L tank)",
                        statusTag = "${entry.tankRefills} refills",
                        kind = vk
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    BigMetric(
                        label = "Drinker Line Pressure",
                        value = String.format("%.0f", entry.drinkerPressureIn),
                        unit = "in",
                        toleranceText = "Nipple column height for the day",
                        statusTag = "age",
                        kind = ValueKind.IDEAL
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    BigMetric(
                        label = "Line Flow",
                        value = String.format("%.0f", entry.drinkerFlowLHrLine),
                        unit = "L/hr·line",
                        toleranceText = "Across ${farm.drinkerLines} drinker lines",
                        statusTag = "flow",
                        kind = vk
                    )
                }
            }
            BigMetric(
                label = "Water range on a ±3°C day",
                value = "${String.format("%.0f", entry.waterLowL)}–${String.format("%.0f", entry.waterHighL)}",
                unit = "L",
                toleranceText = "Cooler day → less; hotter day → more (plan tank fills for the high end)",
                statusTag = "range",
                kind = vk
            )

            val tankL = if (farm.drinkTankL > 0) farm.drinkTankL else 2000.0
            val refillsLow = kotlin.math.ceil(entry.waterLowL / tankL * farm.waterRefillFactor).toInt()
            val refillsHigh = kotlin.math.ceil(entry.waterHighL / tankL * farm.waterRefillFactor).toInt()
            val waterPerBirdMax = if (entry.liveBirds > 0) entry.waterHighL / entry.liveBirds * 1000.0 else 0.0
            val drinkerHt = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_DRINKERHT_BY_AGE, day.toDouble())
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    BigMetric(
                        label = "Water / bird (hot-day max)",
                        value = String.format("%.0f", waterPerBirdMax),
                        unit = "mL",
                        toleranceText = "On a +3 °C day",
                        statusTag = "max",
                        kind = vk
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    BigMetric(
                        label = "Refills today (min–max)",
                        value = "$refillsLow–$refillsHigh",
                        unit = "fills",
                        toleranceText = "${tankL.toInt()} L tank · plan for the high end",
                        statusTag = "range",
                        kind = vk
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    BigMetric(
                        label = "Drinker height",
                        value = "${drinkerHt.toInt()}",
                        unit = "in",
                        toleranceText = "Nipple at bird eye level for the day",
                        statusTag = "age",
                        kind = ValueKind.IDEAL
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    BigMetric(
                        label = "Water pH",
                        value = "6.0–6.8",
                        unit = "",
                        toleranceText = "Ideal drinking-water pH",
                        statusTag = "ideal",
                        kind = ValueKind.IDEAL
                    )
                }
            }
            BigMetric(
                label = "Nipple flow (target)",
                value = "60–90",
                unit = "mL/min",
                toleranceText = "Per nipple, adjusted up with age/heat",
                statusTag = "ideal",
                kind = ValueKind.IDEAL
            )
            BigMetric(
                label = "Water temperature",
                value = "10–25",
                unit = "°C",
                toleranceText = "Ideal ~20 °C · warm water cuts intake; flush lines in heat",
                statusTag = "ideal",
                kind = ValueKind.IDEAL
            )
            Text(
                text = "Drinker Line Check: ~12 birds/nipple, flush lines before midday heat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ============================ PERFORMANCE GRAPHS ============================
        OutputCard(title = "Performance curves (present vs ideal)") {
            GraphLegend()
            Text(
                text = "Body weight vs breed standard (±5% band)",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            GrowthCurveCanvas(dailyRows = dailyRows, breed = breed, markerDay = day)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "FCR vs standard (critical = ×1.15)",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            FcrCurveCanvas(dailyRows = dailyRows, breed = breed, markerDay = day)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "cFCR (corrected to 2.0 kg)",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            CfcrCurveCanvas(dailyRows = dailyRows, breed = breed, markerDay = day)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Cumulative mortality vs ceiling",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            MortalityCurveCanvas(dailyRows = dailyRows, markerDay = day)
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
            GistTile(GistItem("Mean wt", "${String.format("%.0f", mean)}g", "$totalN birds"), Modifier.weight(1f))
            GistTile(
                GistItem("CV%", cv?.let { String.format("%.1f", it) } ?: "—",
                    if (cv == null) "need ≥2 spots" else if (cv < 10) "uniform" else if (cv < 12) "uneven" else "very uneven"),
                Modifier.weight(1f)
            )
            val spread = (locs.maxOf { it.avg } - locs.minOf { it.avg })
            GistTile(GistItem("Spread", "${String.format("%.0f", spread)}g", "min→max"), Modifier.weight(1f))
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
                        String.format("%.0f", loc.avg),
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

/** Ventilation & thermodynamics topic card. Thermo figures are metabolic estimates. */
@Composable
fun VentThermoCard(entry: DailyDataEntity, farm: FarmEntity, vk: ValueKind) {
    OutputCard(title = "Ventilation & Thermodynamics") {
        val day = entry.dayNumber
        val liveBirds = entry.liveBirds
        val avgKg = (entry.avgWeight ?: entry.idealWeight) / 1000.0
        val effFanCfm = farm.fanRatedCfm * (1.0 - farm.fanDerate)
        val crossFt2 = farm.usableWidthFt * farm.heightFt
        val ladder = PhysiologicalEngine.controllerLadder(
            day, entry.setTemp, entry.fansToRun, entry.fanOnSec, entry.fanOffSec,
            farm.fanCount, effFanCfm, crossFt2
        )
        val rossPerBird = PhysiologicalEngine.rossMinVentCfmPerBird(avgKg)
        val designPerBird = entry.cfmPerBird
        val designTotal = designPerBird * liveBirds
        val cycle = entry.fanOnSec + entry.fanOffSec
        val duty = if (cycle > 0 && entry.fanOffSec > 0) entry.fanOnSec.toDouble() / cycle else 1.0
        val timerCfm = entry.fansToRun * effFanCfm * duty
        val maxCoolCfm = farm.fanCount * effFanCfm
        val houseVolFt3 = farm.usableLengthFt * farm.usableWidthFt * farm.heightFt
        val achMin = if (houseVolFt3 > 0) timerCfm * 60.0 / houseVolFt3 else 0.0
        val achMax = if (houseVolFt3 > 0) maxCoolCfm * 60.0 / houseVolFt3 else 0.0
        val heatPerBirdW = 10.0 * Math.pow(avgKg.coerceAtLeast(0.04), 0.75)
        val heatTotalKw = heatPerBirdW * liveBirds / 1000.0
        val sensibleFrac = (0.80 - 0.015 * (entry.tempIdeal - 20.0)).coerceIn(0.35, 0.80)
        val f1 = { v: Double -> String.format("%.1f", v) }

        // Level 1: the minimum-ventilation timer — set daily, runs whatever the weather.
        BigMetric(
            label = "Level 1 · min-vent timer",
            value = if (entry.fanOffSec > 0) "${entry.fanOnSec} / ${entry.fanOffSec}" else "Continuous",
            unit = if (entry.fanOffSec > 0) "s on/off" else "",
            toleranceText = "${entry.fansToRun} fan${if (entry.fansToRun > 1) "s" else ""} · ${cycle}s cycle · never ON < ${PhysiologicalEngine.MIN_ON_FLOOR_SEC}s",
            statusTag = "daily",
            kind = vk
        )
        TwoMetric(
            BM("Min vent / bird", String.format("%.2f", designPerBird), "CFM", "Ross ${String.format("%.2f", rossPerBird)} + 30 %", "design", vk),
            BM("Min vent total", String.format("%,.0f", designTotal), "CFM", "Timer gives ${String.format("%,.0f", timerCfm)}", "total", vk)
        )
        if (designTotal > 0 && timerCfm > designTotal * 1.5) {
            Text(
                "One fan is bigger than the chicks need, so the timer over-ventilates ${String.format("%.1f", timerCfm / designTotal)}× — heaters absorb it. Watch RH (keep 60–70 %), not CO₂.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Divider(modifier = Modifier.padding(vertical = 4.dp))
        // Temperature ladder: heaters below set-point, fans added one by one above it.
        TwoMetric(
            BM("Heat ON below", f1(ladder.heatOnC), "°C", "Set-point − ${f1(entry.setTemp - ladder.heatOnC)}", "heater", ValueKind.IDEAL),
            BM("Set-point", f1(entry.setTemp), "°C", "Band ${f1(entry.tempMin)}–${f1(entry.tempMax)}", "target", ValueKind.IDEAL)
        )
        TwoMetric(
            BM("Fans start", f1(ladder.fansStartC), "°C", "Set-point + ${f1(ladder.fansStartC - entry.setTemp)}", "level 2", ValueKind.IDEAL),
            BM("All ${ladder.maxFans} fans by", f1(ladder.allFansC), "°C", "+${PhysiologicalEngine.LADDER_STEP_C} °C per fan", "age cap", ValueKind.IDEAL)
        )
        TwoMetric(
            BM("High-temp alarm", f1(ladder.alarmHighC), "°C", "Set-point + 3.5", "alarm", ValueKind.IDEAL),
            BM("Low-temp alarm", f1(ladder.alarmLowC), "°C", "Set-point − 2.0", "alarm", ValueKind.IDEAL)
        )
        if (entry.outTemp != null) {
            BigMetric(
                label = "Expected today",
                value = entry.ventText,
                unit = "",
                toleranceText = "Outside ${String.format("%.0f", entry.outTemp)} °C vs set-point ${f1(entry.setTemp)} °C",
                statusTag = when (entry.ventMode) { 2 -> "tunnel"; 1 -> "transitional"; else -> "min-vent" },
                kind = ValueKind.PREDICTED
            )
        }

        Divider(modifier = Modifier.padding(vertical = 4.dp))
        TwoMetric(
            BM("Max cooling airflow", String.format("%,.0f", maxCoolCfm), "CFM", "All ${farm.fanCount} fans (tunnel)", "cooling", ValueKind.IDEAL),
            BM("Air changes", "${f1(achMin)}–${String.format("%.0f", achMax)}", "/hr", "timer → all fans", "range", vk)
        )
        TwoMetric(
            BM("Air speed at birds", "${entry.airspeed.toInt()}", "ft/min", "Age cap ${ladder.maxAirSpeedFpm.toInt()} ft/min", "target", ValueKind.IDEAL),
            BM("Humidity", String.format("%.0f", entry.rhIdeal), "%", if (day <= 10) "60–70 % while brooding" else "50–60 % after brooding", "ideal", ValueKind.IDEAL)
        )
        TwoMetric(
            BM("Static · min-vent", "20–25", "Pa", "0.08–0.10 in w.c.", "levels 1–6", ValueKind.IDEAL),
            BM("Static · tunnel", "30–37", "Pa", "Alarm < 15 or > 45 Pa", "tunnel", ValueKind.IDEAL)
        )
        Text(
            "Pads: only after all ${ladder.maxFans} fans run and the house is still above ${f1(ladder.allFansC)} °C. Stop pads when RH passes 80–85 %.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Divider(modifier = Modifier.padding(vertical = 4.dp))
        TwoMetric(
            BM("Heat / bird", String.format("%.1f", heatPerBirdW), "W", "Metabolic (estimate)", "heat", vk),
            BM("Total house heat", String.format("%.1f", heatTotalKw), "kW", "$liveBirds birds", "heat", vk)
        )
        TwoMetric(
            BM("Sensible heat", String.format("%.1f", heatPerBirdW * sensibleFrac), "W/bird", "Dry heat to remove", "sensible", vk),
            BM("Latent heat", String.format("%.1f", heatPerBirdW * (1 - sensibleFrac)), "W/bird", "Moisture (water) load", "latent", vk)
        )

        Divider(modifier = Modifier.padding(vertical = 4.dp))
        TwoMetric(
            BM("CO₂", "< 3,000", "ppm", "Critical ${entry.co2Max.toInt()}", "ideal", ValueKind.IDEAL),
            BM("NH₃", "< 10", "ppm", "Critical ${entry.nh3Max.toInt()}", "ideal", ValueKind.IDEAL)
        )
        TwoMetric(
            BM("CO", "< 10", "ppm", "Heater exhaust", "ideal", ValueKind.IDEAL),
            BM("Dust", "< 5", "mg/m³", "Dry house = more dust", "ideal", ValueKind.IDEAL)
        )
        TwoMetric(
            BM("O₂", "20.9", "%", "Minimum 19.6 %", "ideal", ValueKind.IDEAL),
            BM("Lighting", String.format("%.1f", entry.lightHours), "h", "Dark ${String.format("%.1f", 24.0 - entry.lightHours)} h", "ideal", ValueKind.IDEAL)
        )
        Text(
            "Heat figures are metabolic estimates. Air-quality limits: Aviagen.",
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
