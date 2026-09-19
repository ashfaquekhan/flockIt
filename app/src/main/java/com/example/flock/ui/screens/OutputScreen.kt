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
import com.example.ui.theme.BrandEmerald
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
        // (Alert banner removed — no alerts/reminders for now.)

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
        OutputCard(title = "Feed & Stock on Hand") {
            // Big number: Feed bags to give today
            BigMetric(
                label = "Feed Bags to Give Today",
                value = "${entry.feedBags}",
                unit = "bags (${farm.feedBagKg.toInt()}kg ea)",
                toleranceText = "Total: ${String.format("%.1f", entry.totalFeedKg)} kg · Per bird: ${String.format("%.1f", entry.feedPerBird)} g/day",
                statusTag = "daily target",
                isHero = true
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

@Composable
fun OutputCard(
    title: String,
    content: @Composable () -> Unit
) {
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
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
