package com.example.flock.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.abs
import kotlin.math.max

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 0. GIST — quick glance summary, then detailed topic cards below
        OutputCard(title = "Today at a glance · Day $day") {
            val gAvg = entry.avgWeight ?: PhysiologicalEngine.bwFromDay(weightAge, breed)
            val gIdeal = PhysiologicalEngine.bwFromDay(day.toDouble(), breed)
            val gStdFcr = PhysiologicalEngine.stdFcrFromDay(day.toDouble(), breed)
            GistRow(
                GistItem("Avg wt", "${String.format("%.0f", gAvg)}g", "ideal ${String.format("%.0f", gIdeal)}"),
                GistItem("Wt-age", "${String.format("%.1f", weightAge)}d", "cal. day $day"),
                GistItem("CV%", entry.cv?.let { String.format("%.1f", it) } ?: "—", "<10 ideal")
            )
            GistRow(
                GistItem("FCR", entry.fcr?.let { String.format("%.2f", it) } ?: "—", "std ${String.format("%.2f", gStdFcr)}"),
                GistItem("cFCR", entry.cFcr?.let { String.format("%.2f", it) } ?: "—", "→ 2kg"),
                GistItem("Cum mort", entry.cumMortPct?.let { String.format("%.1f", it) + "%" } ?: "—", "≤ ${String.format("%.1f", entry.maxMortPct)}%")
            )
            GistRow(
                GistItem("Feed", "${entry.feedBags} bags", "${String.format("%.0f", entry.totalFeedKg)} kg"),
                GistItem("Water", "${String.format("%.0f", entry.totalWaterL)}L", "${entry.tankRefills} fills"),
                GistItem("Stock", String.format("%.0f", feedStockSummary.totalOnHandBags), "bags left")
            )
            GistRow(
                GistItem("Fans", "${entry.fansToRun}/${farm.fanCount}", entry.cycleText),
                GistItem("Density", entry.densityKgM2?.let { String.format("%.1f", it) } ?: "—", "≤ ${farm.densityCapDefault}"),
                GistItem("Set °C", String.format("%.1f", entry.tempIdeal), "${String.format("%.1f", entry.tempMin)}–${String.format("%.1f", entry.tempMax)}")
            )
            GistRow(
                GistItem("Live birds", "${entry.liveBirds}", "of ${flock?.birdsPlaced ?: 0}"),
                GistItem("Feed/bird", "${String.format("%.0f", entry.feedPerBird)}g", "per day"),
                GistItem("Livability", entry.livability?.let { String.format("%.1f", it) + "%" } ?: "—", "alive")
            )
            Text(
                "Detailed, topic-wise breakdown below ↓",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 0b. IDEAL TARGETS — pure reference from the breed/industry curves, shown for
        // every parameter whether or not you have measured/computed data for it today.
        OutputCard(title = "Ideal targets today (Day $day)") {
            val idBw = PhysiologicalEngine.bwFromDay(day.toDouble(), breed)
            val idFeed = PhysiologicalEngine.dailyFeedFromDay(max(1.0, day.toDouble()), breed)
            val idSetTemp = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_TEMP_BY_BW, idBw)
            val idRh = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_RH_BY_AGE, day.toDouble())
            val idAir = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_AIRSPEED_BY_AGE, day.toDouble())
            val idCfm = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_MINVENT_BY_AGE, day.toDouble())
            val idLight = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_LIGHT_BY_AGE, day.toDouble())
            val idMaxMort = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_MAXMORT_BY_AGE, day.toDouble())
            val idDrinkPress = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_WATERLINE_BY_AGE, day.toDouble())
            val idDrinkHt = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_DRINKERHT_BY_AGE, day.toDouble())
            val idFcr = PhysiologicalEngine.stdFcrFromDay(day.toDouble(), breed)
            IdealRow("Ideal body weight", "${String.format("%.0f", idBw)} g")
            IdealRow("Feed / bird", "${String.format("%.0f", idFeed)} g/day")
            IdealRow("Water : feed", "1.8 : 1 (+6%/°C >20°C)")
            IdealRow("Std FCR", String.format("%.2f", idFcr))
            IdealRow("cFCR", "(2 − avg kg) × 0.25 + FCR")
            IdealRow("Uniformity CV%", "< 10 %")
            IdealRow("Set-point temp", "${String.format("%.1f", idSetTemp)} °C")
            IdealRow("Humidity", "${String.format("%.0f", idRh)} % (50–70)")
            IdealRow("Air speed (bird)", "${idAir.toInt()} ft/min")
            IdealRow("Min-vent air", "${String.format("%.2f", idCfm)} cfm/bird")
            IdealRow("Static pressure", "25 Pa (15–35)")
            IdealRow("CO₂ / NH₃ / O₂", "≤3000 / ≤10 ppm / 20.9 %")
            IdealRow("Light", "${idLight.toInt()} h/day")
            IdealRow("Drinker pressure / height", "${idDrinkPress.toInt()} / ${idDrinkHt.toInt()} in")
            IdealRow("Water pH / temp", "6.0–6.8 / < 25 °C")
            IdealRow("Max mortality (cum)", "≤ ${String.format("%.1f", idMaxMort)} %")
        }

        // PROJECTED NOTICE TAG
        if (isProjected) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PROJECTED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = StatusProjected
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "No weight sample entered today. Targets driven by growth curve extrapolation.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }

        // 1. GROWTH & CONVERSION
        OutputCard(title = "Growth & Conversion") {
            val avgG = entry.avgWeight ?: PhysiologicalEngine.bwFromDay(weightAge, breed)
            val idealBw = PhysiologicalEngine.bwFromDay(day.toDouble(), breed)
            val bwDiffPct = if (idealBw > 0) ((avgG - idealBw) / idealBw) * 100.0 else 0.0

            // Avg Weight
            BigMetric(
                label = "Average Body Weight",
                value = String.format("%.0f", avgG),
                unit = "g",
                toleranceText = "Safe: ${String.format("%.0f", idealBw * 0.95)}–${String.format("%.0f", idealBw * 1.05)} g · Ideal: ${String.format("%.0f", idealBw)} g",
                statusTag = if (abs(bwDiffPct) > 5.0) (if (bwDiffPct > 0) "▲ +${String.format("%.1f", bwDiffPct)}%" else "▼ ${String.format("%.1f", bwDiffPct)}%") else "ok"
            )

            // Weight-Age anchor comparison
            val ageDiff = weightAge - day.toDouble()
            val ageDiffStr = if (ageDiff >= 0) "+${String.format("%.1f", ageDiff)}d ahead" else "${String.format("%.1f", ageDiff)}d behind"
            BigMetric(
                label = "Weight-Age",
                value = String.format("%.1f", weightAge),
                unit = "days",
                toleranceText = "Calendar Day $day · Difference: $ageDiffStr",
                statusTag = if (abs(ageDiff) > 1.5) (if (ageDiff > 0) "ahead" else "behind") else "on curve"
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
                        statusTag = if (fcrVal != null && fcrVal > stdFcr * 1.15) "▲ crit" else "ok"
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    BigMetric(
                        label = "cFCR (2.0kg std)",
                        value = cFcrVal?.let { String.format("%.2f", it) } ?: "—",
                        unit = "",
                        toleranceText = "(2 - avgKg) × 0.25 + FCR",
                        statusTag = "standardised"
                    )
                }
            }
            BigMetric(
                label = "Approx. Daily Gain / bird",
                value = String.format("%.0f", entry.gainPerBird),
                unit = "g/day",
                toleranceText = "Expected growth at weight-age ${String.format("%.1f", weightAge)} d",
                statusTag = "curve"
            )
        }

        // 2. FEED & STOCK ON HAND
        OutputCard(title = "Feed plan & stock") {
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
                isHero = true
            )

            // Plan (required) vs actual (consumed) vs stock (on-hand)
            GistRow(
                GistItem("Req. to date", "$reqToDateBags", "Day 0→$day, ideal"),
                GistItem("Consumed", "${consumedBags.toInt()}", "you logged"),
                GistItem("On-hand", "${onHandBags.toInt()}", "in store")
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
                statusTag = if (entry.feedPerBird < idealFeedPerBird * 0.9) "heat-reduced" else "on curve"
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
        }

        // 3. WATER MANAGEMENT
        OutputCard(title = "Water Requirements") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    BigMetric(
                        label = "Water per Bird",
                        value = String.format("%.0f", entry.waterPerBird),
                        unit = "mL",
                        toleranceText = "Water:Feed ratio 1.8x adjusted for heat",
                        statusTag = "nominal"
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    BigMetric(
                        label = "Total Water Today",
                        value = String.format("%.0f", entry.totalWaterL),
                        unit = "L",
                        toleranceText = "Tank refills: ~${entry.tankRefills} (${farm.drinkTankL.toInt()}L tank)",
                        statusTag = "${entry.tankRefills} refills"
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
                        statusTag = "age"
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    BigMetric(
                        label = "Line Flow",
                        value = String.format("%.0f", entry.drinkerFlowLHrLine),
                        unit = "L/hr·line",
                        toleranceText = "Across ${farm.drinkerLines} drinker lines",
                        statusTag = "flow"
                    )
                }
            }
            BigMetric(
                label = "Water range on a ±3°C day",
                value = "${String.format("%.0f", entry.waterLowL)}–${String.format("%.0f", entry.waterHighL)}",
                unit = "L",
                toleranceText = "Cooler day → less; hotter day → more (plan tank fills for the high end)",
                statusTag = "range"
            )
            Text(
                text = "Drinker Line Check: ~12 birds/nipple, flush lines before midday heat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Animated fan bank (spins only the running fans)
        FanVisualizer(entry = entry, farm = farm)

        // 4. VENTILATION & CYCLING
        OutputCard(title = "Ventilation & Climate Targets") {
            BigMetric(
                label = "Ventilation Mode",
                value = entry.ventText,
                unit = "",
                toleranceText = "Cycle: ${entry.cycleText} · Fans to run: ${entry.fansToRun} of ${farm.fanCount}",
                statusTag = if (entry.ventMode == 2) "tunnel" else "min-vent"
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    BigMetric(
                        label = "Target Airflow",
                        value = String.format("%.2f", entry.cfmPerBird),
                        unit = "CFM/bird",
                        toleranceText = "NPTC minimum ventilation standard",
                        statusTag = "ok"
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    BigMetric(
                        label = "Air Speed",
                        value = "${entry.airspeed.toInt()}",
                        unit = "ft/min",
                        toleranceText = "Tunnel wind chill delta: ~${String.format("%.1f", 0.0114 * entry.airspeed)}°C",
                        statusTag = "nominal"
                    )
                }
            }

            Divider(modifier = Modifier.padding(vertical = 4.dp))

            // Temperature & RH min/ideal/max
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    BigMetric(
                        label = "House Set Temp",
                        value = String.format("%.1f", entry.tempIdeal),
                        unit = "°C",
                        toleranceText = "Safe min: ${String.format("%.1f", entry.tempMin)} · Ideal: ${String.format("%.1f", entry.tempIdeal)} · Max: ${String.format("%.1f", entry.tempMax)}°C",
                        statusTag = "±1.5°C band"
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    BigMetric(
                        label = "Target RH",
                        value = String.format("%.0f", entry.rhIdeal),
                        unit = "%",
                        toleranceText = "Safe min: ${entry.rhMin.toInt()}% · Ideal: ${entry.rhIdeal.toInt()}% · Max: ${entry.rhMax.toInt()}%",
                        statusTag = "ok"
                    )
                }
            }

            // Felt Temp if house temp logged
            if (entry.windChill != null) {
                BigMetric(
                    label = "Wind-Chill / Felt Temp",
                    value = String.format("%.1f", entry.windChill),
                    unit = "°C",
                    toleranceText = "Measured ${entry.outTemp}°C minus wind chill (${String.format("%.1f", 0.0114 * entry.airspeed)}°C)",
                    statusTag = "felt"
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    BigMetric(
                        label = "Air Quality Limits",
                        value = "CO₂ ≤ ${entry.co2Max.toInt()}",
                        unit = "ppm",
                        toleranceText = "NH₃ critical ceiling: ≤ ${entry.nh3Max.toInt()} ppm",
                        statusTag = "max"
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    BigMetric(
                        label = "Lighting Schedule",
                        value = String.format("%.1f", entry.lightHours),
                        unit = "hours",
                        toleranceText = "Dark period: ${String.format("%.1f", 24.0 - entry.lightHours)} hours",
                        statusTag = "light"
                    )
                }
            }
        }

        // 4b. AIR QUALITY & MEASURED READINGS (always shown; "— no reading" when blank)
        OutputCard(title = "Air Quality & Measured Readings") {
            MeasuredMetric("CO₂", entry.measuredCo2, "ppm", 0, "Ideal < 3000 · Max ${entry.co2Max.toInt()} ppm")
            MeasuredMetric("NH₃ ammonia", entry.measuredNh3, "ppm", 0, "Ideal < 10 · Max ${entry.nh3Max.toInt()} ppm")
            MeasuredMetric("O₂ oxygen", entry.measuredO2, "%", 1, "Ideal 20.9 · Min 19.5 %")
            MeasuredMetric("Static pressure", entry.measuredPressure, "Pa", 0, "Ideal 25 · Band 15–35 Pa")
            MeasuredMetric("Air speed (measured)", entry.measuredAirspeed, "ft/min", 0, "Target ${entry.airspeed.toInt()} ft/min for the day")
            MeasuredMetric("Light intensity", entry.luxPerFt2, "lux", 0, "Brooding 30–40 · Grow-out 5–10 lux")
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            MeasuredMetric("Water temperature", entry.waterTempC, "°C", 1, "Ideal < 25 °C (cool water lifts intake)")
            MeasuredMetric("Water pH", entry.waterPh, "", 1, "Ideal 6.0 – 6.8")
            MeasuredMetric("Feed moisture", entry.feedMoisturePct, "%", 1, "Safe < 13 % (mould risk above)")
            MeasuredMetric("Diesel cans used", entry.dieselCansUsed.takeIf { it > 0 }, "cans", 1, "≈ ${farm.dieselCanL.toInt()} L per can")
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            MeasuredMetric("Pad wet time", entry.padWetMin, "min", 0, "Evaporative cooling on-cycle")
            MeasuredMetric("Pad dry time", entry.padDryMin, "min", 0, "Off-cycle so litter stays dry")
        }

        // Brooding barricade / floor-plan diagram
        HouseFloorPlan(entry = entry, farm = farm)

        // 5. SPACE & DENSITY
        OutputCard(title = "Space & Density") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    val density = entry.densityKgM2
                    BigMetric(
                        label = "Stocking Density",
                        value = density?.let { String.format("%.1f", it) } ?: "—",
                        unit = "kg/m²",
                        toleranceText = "Safe cap: ≤ ${farm.densityCapDefault} kg/m²",
                        statusTag = if (density != null && density > farm.densityCapDefault) "▲ over cap" else "ok"
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    BigMetric(
                        label = "Floor Space",
                        value = String.format("%.2f", entry.ftPerBird),
                        unit = "ft²/bird",
                        toleranceText = "Minimum recommended: ≥ ${String.format("%.2f", entry.minFtPerBird)} ft²",
                        statusTag = if (entry.ftPerBird < entry.minFtPerBird) "▼ crowded" else "ok"
                    )
                }
            }

            if (entry.barricadeFt > 0) {
                BigMetric(
                    label = "Brooding Barricade",
                    value = "${entry.barricadeFt}",
                    unit = "ft",
                    toleranceText = "Occupied floor area: ${String.format("%.0f", entry.occupiedFt2)} ft²",
                    statusTag = "brooding"
                )
            }
        }

        // 6. FLOCK HEALTH & MORTALITY
        OutputCard(title = "Flock Health & Mortality") {
            BigMetric(
                label = "Balance Birds (alive in shed)",
                value = "${entry.liveBirds}",
                unit = "birds",
                toleranceText = "Placed ${flock?.birdsPlaced ?: 0} − reception − mortality (${entry.cumMort}) − lifted − culls",
                statusTag = "live",
                isHero = true
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    val cMort = entry.cumMortPct
                    BigMetric(
                        label = "Cumulative Mortality",
                        value = cMort?.let { String.format("%.2f", it) } ?: "—",
                        unit = "%",
                        toleranceText = "Ceiling: ≤ ${String.format("%.1f", entry.maxMortPct)}% · Total: ${entry.cumMort} dead",
                        statusTag = if (cMort != null && cMort > entry.maxMortPct) "▲ crit" else "ok"
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    BigMetric(
                        label = "Flock Livability",
                        value = entry.livability?.let { String.format("%.1f", it) } ?: "—",
                        unit = "%",
                        toleranceText = "Live birds in shed: ${entry.liveBirds}",
                        statusTag = "live"
                    )
                }
            }
        }

        // 7. GRAPHS WITH THRESHOLD LINES
        OutputCard(title = "Performance Curves & Critical Lines") {
            Text(
                text = "Growth Curve & ±5% Tolerance Band",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            GrowthCurveCanvas(dailyRows = dailyRows, breed = breed)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "FCR vs Standard with Critical Limit (x1.15)",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            FcrCurveCanvas(dailyRows = dailyRows)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Cumulative Mortality vs Ceiling Limit",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            MortalityCurveCanvas(dailyRows = dailyRows)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

data class GistItem(val label: String, val value: String, val sub: String)

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
    val accent = accentForTitle(title)
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
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = accent)
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
    isHero: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = statusTag,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (statusTag.contains("crit") || statusTag.contains("over")) StatusCrit
                    else if (statusTag.contains("warn") || statusTag.contains("behind")) StatusWarn
                    else BrandEmerald
                )
            )
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(vertical = 2.dp)
        ) {
            Text(
                text = value,
                style = if (isHero) MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 36.sp
                ) else MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            )
            if (unit.isNotBlank()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
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

@Composable
fun GrowthCurveCanvas(dailyRows: List<DailyDataEntity>, breed: String) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        val w = size.width
        val h = size.height
        val maxDays = 42f
        val maxWeight = 3600f

        // Draw horizontal grid lines
        for (gridVal in listOf(1000f, 2000f, 3000f)) {
            val y = h - (gridVal / maxWeight) * h
            drawLine(
                color = Color.LightGray.copy(alpha = 0.5f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
        }

        // Draw ±5% Safe Band
        val upperBandPath = Path()
        val lowerBandPath = Path()
        for (d in 0..42) {
            val stdBw = PhysiologicalEngine.bwFromDay(d.toDouble(), breed).toFloat()
            val x = (d / maxDays) * w
            val yUpper = h - ((stdBw * 1.05f) / maxWeight) * h
            val yLower = h - ((stdBw * 0.95f) / maxWeight) * h
            if (d == 0) {
                upperBandPath.moveTo(x, yUpper)
                lowerBandPath.moveTo(x, yLower)
            } else {
                upperBandPath.lineTo(x, yUpper)
                lowerBandPath.lineTo(x, yLower)
            }
        }
        drawPath(upperBandPath, color = Color.Gray.copy(alpha = 0.35f), style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
        drawPath(lowerBandPath, color = Color.Gray.copy(alpha = 0.35f), style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))

        // Draw Actual / Projected Points
        val actualPath = Path()
        var firstPoint = true
        for (r in dailyRows.sortedBy { it.dayNumber }) {
            val weight = r.avgWeight ?: (if (r.projected) PhysiologicalEngine.bwFromDay(r.weightAge ?: r.dayNumber.toDouble(), breed) else null)
            if (weight != null && weight > 0) {
                val x = (r.dayNumber / maxDays) * w
                val y = h - (weight.toFloat() / maxWeight) * h
                if (firstPoint) {
                    actualPath.moveTo(x, y)
                    firstPoint = false
                } else {
                    actualPath.lineTo(x, y)
                }
                drawCircle(
                    color = if (r.sampleEntered) BrandEmerald else StatusProjected,
                    radius = if (r.sampleEntered) 4.5f else 3f,
                    center = Offset(x, y)
                )
            }
        }
        drawPath(actualPath, color = BrandEmerald, style = Stroke(width = 2.5f))
    }
}

@Composable
fun FcrCurveCanvas(dailyRows: List<DailyDataEntity>, breed: String = "Ross308") {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        val w = size.width
        val h = size.height
        val maxDays = 42f
        val maxFcr = 2.2f
        val minFcr = 0.9f
        val range = maxFcr - minFcr

        // Standard FCR curve + Crit limit line (x1.15)
        val stdPath = Path()
        val critPath = Path()
        for (d in 1..42) {
            val std = PhysiologicalEngine.stdFcrFromDay(d.toDouble(), breed).toFloat()
            val crit = std * 1.15f
            val x = (d / maxDays) * w
            val yStd = h - ((std - minFcr) / range) * h
            val yCrit = h - ((crit - minFcr) / range) * h
            if (d == 1) {
                stdPath.moveTo(x, yStd)
                critPath.moveTo(x, yCrit)
            } else {
                stdPath.lineTo(x, yStd)
                critPath.lineTo(x, yCrit)
            }
        }
        drawPath(stdPath, color = Color.Gray, style = Stroke(width = 1.5f))
        drawPath(critPath, color = StatusCrit.copy(alpha = 0.7f), style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))

        // Actual FCR points
        for (r in dailyRows) {
            val f = r.fcr?.toFloat()
            if (f != null && f in minFcr..maxFcr) {
                val x = (r.dayNumber / maxDays) * w
                val y = h - ((f - minFcr) / range) * h
                drawCircle(color = BrandEmerald, radius = 4f, center = Offset(x, y))
            }
        }
    }
}

@Composable
fun MortalityCurveCanvas(dailyRows: List<DailyDataEntity>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        val w = size.width
        val h = size.height
        val maxDays = 42f
        val maxMort = 6.0f // 6%

        // Draw Ceiling Line
        val ceilingPath = Path()
        for (d in 0..42) {
            val ceil = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_MAXMORT_BY_AGE, d.toDouble()).toFloat()
            val x = (d / maxDays) * w
            val y = h - (ceil / maxMort) * h
            if (d == 0) ceilingPath.moveTo(x, y) else ceilingPath.lineTo(x, y)
        }
        drawPath(ceilingPath, color = StatusCrit.copy(alpha = 0.8f), style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))

        // Actual Cumulative Mortality
        val actualMortPath = Path()
        var first = true
        for (r in dailyRows.sortedBy { it.dayNumber }) {
            val mort = r.cumMortPct?.toFloat()
            if (mort != null) {
                val x = (r.dayNumber / maxDays) * w
                val y = h - (mort / maxMort) * h
                if (first) {
                    actualMortPath.moveTo(x, y)
                    first = false
                } else {
                    actualMortPath.lineTo(x, y)
                }
                drawCircle(color = BrandEmerald, radius = 3.5f, center = Offset(x, y))
            }
        }
        drawPath(actualMortPath, color = BrandEmerald, style = Stroke(width = 2f))
    }
}
