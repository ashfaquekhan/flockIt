package com.example.flock.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.engine.CompanyStandard
import com.example.flock.engine.IbController
import com.example.flock.engine.PhysiologicalEngine
import com.example.flock.ui.Fmt
import com.example.flock.ui.components.HouseFloorPlan
import com.example.ui.theme.StatusCrit
import com.example.ui.theme.StatusWarn
import com.example.ui.theme.ValueIdeal
import com.example.ui.theme.ValuePredicted
import com.example.ui.theme.ValuePresent
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private val P = ValueKind.PRESENT
private val PR = ValueKind.PREDICTED
private val I = ValueKind.IDEAL
private val C = ValueKind.COMMERCIAL
private val MN = ValueKind.MIN
private val MX = ValueKind.MAX

private val TempDial = Color(0xFFFF8A50)
private val RhDial = Color(0xFF64B5F6)

// =================================== topic dispatcher ===================================

@Composable
fun TopicView(d: OutputData, t: Topic) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("${t.emoji}  ${t.title} · Day ${d.day}", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface)
        AlertPanel(d.alerts[t].orEmpty())
        when (t) {
            Topic.VENT -> VentTopic(d)
            Topic.ENV -> EnvTopic(d)
            Topic.BIRDS -> BirdsTopic(d)
            Topic.FEED -> FeedTopic(d)
            Topic.STOCK -> StockTopic(d)
        }
    }
}

@Composable
private fun AlertPanel(alerts: List<TopicAlert>) {
    val worst = alerts.maxOfOrNull { it.level } ?: 0
    Surface(color = alertColor(worst).copy(alpha = 0.14f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (alerts.isEmpty()) Text("✅  All in range", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = alertColor(0))
            alerts.sortedByDescending { it.level }.forEach { a ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(if (a.level == 2) "🔴" else "🟠", fontSize = 15.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(a.text, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

// =================================== VENTILATION ===================================

@Composable
private fun VentTopic(d: OutputData) {
    var tC by rememberSaveable(d.day) { mutableDoubleStateOf((d.dialStartC * 2).let { Math.round(it) / 2.0 }) }
    var rh by rememberSaveable(d.day) { mutableDoubleStateOf((d.dialStartRh / 5).let { Math.round(it) * 5.0 }) }
    MinVentCard(d)
    FanFinderCard(d, tC, rh, { tC = it }, { rh = it })
    CoolingGridCard(d) { t, h -> tC = t; rh = h }
    ControllerCard(d)
}

@Composable
private fun MinVentCard(d: OutputData) {
    val f = d.farm
    val l = d.minLevel
    val live = d.liveSafe.toDouble()
    OutputCard(title = "🔄 Minimum ventilation · air quality") {
        HouseAirflow(l, f.fanCount, f.hasEC, false, false, d.airFpm(l.avgFans))
        Text(
            if (l.isTimer && l.cont.isEmpty()) "Fan ${l.cyc.joinToString(", ")} on a timer: ${l.on}.0 s ON / ${l.off}.0 s OFF"
            else "${l.cont.size}.0 fan(s) non-stop" + if (l.cyc.isNotEmpty()) " + fan ${l.cyc.joinToString(", ")} ${l.on}.0 / ${l.off}.0 s" else "",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Param("Air", "cfm", listOf(
            "per bird" to listOf(v(d.plan.needCfm / live, 3, I, "Needed"), v(d.minAvgCfm / live, 3, PR, "Delivered")),
            "whole house" to listOf(v(d.plan.needCfm, 1, I, "Needed"), v(d.minAvgCfm, 1, PR, "Delivered"))
        ), note = "Needed = Ross air-quality floor × 1.30 × your min-vent calibration (${Fmt.n(f.minVentFactor, 2)}).", strong = true)
        Param("Fans running on average", "fans", v(l.avgFans, 2, PR), v(l.duty * 100, 1, PR, "Timer duty %"))
        Param("Air changes", "per hour", v(if (d.houseVolFt3 > 0) d.minAvgCfm * 60 / d.houseVolFt3 else null, 2, PR))
        if (d.day <= 14) Param("Air speed at birds", "ft/min", v(d.airFpm(l.avgFans), 1, PR), v(29.5, 1, MX, "Draught limit"),
            note = "Ross: air at chick level should stay below 0.15 m/s (29.5 ft/min) while brooding.")
        else Param("Air speed at birds", "ft/min", v(d.airFpm(l.avgFans), 1, PR))
        Note("The animation speeds the timer up; the real cycle is ${l.on + l.off}.0 s.")
    }
}

@Composable
private fun FanFinderCard(d: OutputData, tC: Double, rh: Double, setT: (Double) -> Unit, setRh: (Double) -> Unit) {
    val f = d.farm
    val e = d.e
    val live = d.liveSafe.toDouble()
    OutputCard(title = "🎛️ Fan finder · turn to the outside air") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Knob("Outside temperature", "°C", tC, 5.0, 48.0, 0.5, TempDial, setT)
            Knob("Outside humidity", "% RH", rh, 10.0, 100.0, 5.0, RhDial, setRh)
        }
        val s = remember(tC, rh, d) { d.sim(tC, rh) }
        val lv = d.levelOf(s)
        val cfm = s.fans * d.fanCfm
        HouseAirflow(lv, f.fanCount, f.hasEC, s.padEff > 0, s.heaterKw > 0.05, d.airFpm(s.fans))
        Text(d.modeOf(s), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = ValuePredicted)
        Param("Fans to run", "fans", v(s.fans, 2, PR, "Average"), vt("${lv.fansOn}.0", PR, "Switched on"), v(d.plan.maxFans.toDouble(), 1, MX, "Allowed"), strong = true)
        Param("Air", "cfm", listOf("per bird" to listOf(v(cfm / live, 3, PR)), "whole house" to listOf(v(cfm, 1, PR))))
        Param("Air speed at birds", "ft/min", v(d.airFpm(s.fans), 1, PR), v(PhysiologicalEngine.maxAirSpeedFpm(d.day), 1, MX))
        Param("House air", "°C", v(s.houseC, 1, PR), v(e.tempMin, 1, MN), v(e.tempIdeal, 1, I), v(e.tempMax, 1, MX))
        Param("House humidity", "% RH", v(s.houseRh, 1, PR), v(e.rhMin, 1, MN), v(e.rhIdeal, 1, I), v(e.rhMax, 1, MX))
        Param("Birds feel", "°C", v(s.feltC, 1, PR), v(d.plan.comfort, 1, I, "Comfort"), strong = true)
        Param("Heaters · pads", "", v(s.heaterKw, 1, PR, "Heaters kW"), v(s.padEff * 100, 1, PR, "Pads %"))
        Note("Starts at ${if (d.weather != null) "the weather now" else "the hottest hour"}. Tap a cell in the cooling grid to load it here.")
    }
}

@Composable
private fun CoolingGridCard(d: OutputData, onPick: (Double, Double) -> Unit) {
    val states = remember(d) { d.gridTemps.map { t -> d.gridRh.map { h -> d.sim(t, h) } } }
    OutputCard(title = "🌡️ Cooling grid · fans to run") {
        Note("Outside air, 3 temperatures × 3 humidities around the hottest hour (${d.scenarioSource}). Big number = fans running (average) · small = what birds feel.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Spacer(Modifier.width(62.dp))
            d.gridRh.forEach { h ->
                Text("${Fmt.n(h, 1)}% RH", Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = RhDial)
            }
        }
        d.gridTemps.forEachIndexed { i, t ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${Fmt.n(t, 1)} °C", Modifier.width(62.dp), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = TempDial)
                d.gridRh.forEachIndexed { j, h ->
                    val s = states[i][j]
                    val over = s.feltC - d.plan.comfort
                    val col = when { over > 4 -> StatusCrit; over > 2 -> StatusWarn; over < -3 -> ValueIdeal; else -> ValuePresent }
                    Surface(color = col.copy(alpha = 0.20f), shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).clickable { onPick(t, h) }) {
                        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(Fmt.n(s.fans, 1), style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black), color = col)
                            Text("feel ${Fmt.n(s.feltC, 1)}°", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(if (s.padEff > 0) "pads on" else if (s.level >= 12) "tunnel" else "timer", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        KeyLine(ValuePresent to "comfortable", StatusWarn to "warm", StatusCrit to "hot", ValueIdeal to "cool")
    }
}

@Composable
private fun ControllerCard(d: OutputData) {
    val f = d.farm
    OutputCard(title = "⚙️ Controller for day ${d.day}") {
        Param("Bird comfort", "°C · still air, 65% RH", v(d.plan.comfort, 1, I), strong = true)
        Param("Temperatures", "°C", v(d.plan.set, 1, I, "SET"), v(d.plan.heat, 1, I, "Heat on below"), v(d.plan.target, 1, I, "Target at min"))
        Param("Limits for this age", "", v(d.plan.maxFans.toDouble(), 1, MX, "Fans allowed"), v(PhysiologicalEngine.maxAirSpeedFpm(d.day), 1, MX, "Air speed ft/min"))
        Param("Fan airflow", "cfm (rated × ${Fmt.n(1 - f.fanDerate, 2)})", listOf(
            "one fan" to listOf(v(d.fanCfm, 1, I), v(d.airFpm(1.0), 1, I, "ft/min at birds")),
            "all ${f.fanCount}.0" to listOf(v(d.allFansCfm, 1, I), v(d.airFpm(f.fanCount.toDouble()), 1, I, "ft/min at birds"))
        ))
        Param("Air changes at all fans", "per hour", v(if (d.houseVolFt3 > 0) d.allFansCfm * 60 / d.houseVolFt3 else null, 2, I))
        Note("House cross-section ${Fmt.n(d.plan.crossFt2, 1)} ft². Birds and sensors sit ~1 ft above the litter, where air runs at ~${Fmt.n(IbController.FLOOR_AIR_FACTOR * 100, 1)}% of the average speed.")
    }
}

// =================================== ENVIRONMENT ===================================

@Composable
private fun EnvTopic(d: OutputData) {
    val e = d.e
    OutputCard(title = "🏠 House air") {
        RangeParam("Air temperature", "°C", e.tempMin, e.tempIdeal, e.tempMax, null)
        RangeParam("Relative humidity", "%", e.rhMin, e.rhIdeal, e.rhMax, null,
            note = if (d.day <= 10) "Ross: 60.0–70.0% at placement, above 50.0% until day 10." else null)
        Param("Bird comfort", "°C · still air, 65% RH", v(d.plan.comfort, 1, I))
        d.weather?.let { w ->
            Param("Outside air (weather)", "°C · % RH", vt("${Fmt.n(w.tempC, 1)} · ${Fmt.n(w.rhPercent, 1)}", P, "Outside now"),
                note = "Outside air only — the house figures above are targets, not estimates from the weather.")
        }
    }
    OutputCard(title = "🟫 Litter") {
        RangeParam("Litter / floor temperature", "°C", d.litterTemp.first, d.litterTemp.second, d.litterTemp.third, null,
            note = if (d.day <= 7) "Ross: floor 28.0–30.0 °C and litter 28.0–32.0 °C at placement — pre-heat the house." else "After brooding the litter follows the house air.")
        RangeParam("Litter moisture", "%", 20.0, 25.0, 30.0, null,
            note = "20.0–30.0% is ideal (Mississippi State); above ~25.0% ammonia rises. Squeeze test: a handful should fall apart.")
        Param("Litter depth at placement", "cm", vt("2.0–4.0", I))
    }
    OutputCard(title = "🐥 Bird temperature") {
        RangeParam("Body (vent) temperature", "°C", d.bodyTemp.first, d.bodyTemp.second, d.bodyTemp.third, null,
            note = if (d.day <= 2) "Ross: 39.4–40.5 °C in the first 2 days — check 10 chicks at 5 places." else "Rises to the adult 41–42 °C as the chick's own heating matures (~day 10). Above 42.0 °C = heat stress.")
        RangeParam("Foot temperature", "°C", d.footTemp.first, d.footTemp.second, d.footTemp.third, null,
            note = "No official figure: feet should feel warm against your cheek or neck (Ross, Cobb) — cold feet mean a cold floor. Thermal-camera studies: leg skin ≈33.9 °C at 14 d and ≈32.4 °C at 21 d in comfortable birds, ~2 °C higher in heat stress.")
    }
    OutputCard(title = "💨 Air quality") {
        RangeParam("CO₂", "ppm", null, null, e.co2Max, e.measuredCo2)
        RangeParam("Ammonia NH₃", "ppm", null, null, e.nh3Max, e.measuredNh3)
        RangeParam("Carbon monoxide CO", "ppm", null, null, 10.0, null)
        RangeParam("Oxygen O₂", "%", 19.6, null, null, e.measuredO2, 2)
        RangeParam("Dust", "mg/m³", null, null, 5.0, null)
        RangeParam("Static pressure", "Pa", 20.0, null, 25.0, e.measuredPressure, note = "20.0–25.0 Pa on minimum ventilation · 30.0–37.0 Pa in tunnel.")
        RangeParam("Air speed at birds", "ft/min", null, null, PhysiologicalEngine.maxAirSpeedFpm(d.day), e.measuredAirspeed)
        if (e.padWetMin != null || e.padDryMin != null) Param("Cooling pads", "min", v(e.padWetMin, 1, P, "Wet time"), v(e.padDryMin, 1, P, "Dry time"))
    }
    OutputCard(title = "💡 Light") {
        Param("Day length", "hours", v(e.lightHours, 1, I, "Light"), v(24.0 - e.lightHours, 1, I, "Dark"),
            note = if (d.day <= 7) "Ross: 23 h light on arrival; 4–6 h dark by day 7." else null)
        RangeParam("Light intensity", "lux", d.lightLux.first, null, d.lightLux.second, e.luxPerFt2, idealBand = d.lightLux)
    }
    OutputCard(title = "🔥 Bird heat & moisture") {
        val live = d.live.toDouble()
        Param("Heat from the birds", "", listOf(
            "per bird W" to listOf(v(d.heatPerBirdW, 2, d.vk, "Total"), v(d.heatPerBirdW * d.sensibleFrac, 2, d.vk, "Warms air"), v(d.heatPerBirdW * (1 - d.sensibleFrac), 2, d.vk, "As moisture")),
            "house kW" to listOf(v(d.heatPerBirdW * live / 1000, 2, d.vk, "Total"), v(d.heatPerBirdW * d.sensibleFrac * live / 1000, 2, d.vk, "Warms air"), v(d.heatPerBirdW * (1 - d.sensibleFrac) * live / 1000, 2, d.vk, "As moisture"))
        ), strong = true)
        Param("Moisture breathed out", "", listOf("per bird g/h" to listOf(v(d.moistureGPerBirdHr, 2, d.vk)), "house kg/h" to listOf(v(d.moistureGPerBirdHr * live / 1000, 2, d.vk))))
        Note("This is the heat and water the ventilation has to carry out (CIGR: 10.62 × kg^0.75 W per bird).")
    }
}

// =================================== BIRDS ===================================

@Composable
private fun BirdsTopic(d: OutputData) {
    val e = d.e
    val live = d.live.toDouble()
    KpiCard(
        "🎯 How the flock compares",
        listOf(
            Kpi("Body weight", "g", d.bw, d.vk, d.bwCom, d.bwIdeal, Better.HIGHER, 1, totalFactor = live / 1000, totalUnit = "kg"),
            Kpi("Daily gain", "g/day", d.gain, d.gainKind, d.gainCom, d.gainIdeal, Better.HIGHER, 1, totalFactor = live / 1000, totalUnit = "kg/day"),
            Kpi("FCR", "kg feed / kg bird", e.fcr, P, d.fcrCom, d.fcrIdeal, Better.LOWER, 3, settling = d.day < 7),
            Kpi("cFCR", "to 2 kg", e.cFcr, P, d.cfcrCom, d.cfcrIdeal, Better.LOWER, 3, settling = d.day < 7),
            Kpi("EPEF", "efficiency", d.epef, P, d.epefCom, d.epefIdeal, Better.HIGHER, 1, settling = d.day < 7),
            Kpi("Mortality", "till date", d.mortTDPct, P, d.comCumPct, d.ceilingPct, Better.LOWER, 2, points = true,
                totalFactor = d.placed / 100.0, totalUnit = "birds", scope = "% of placed")
        )
    )
    OutputCard(title = "🐣 Birds today") {
        val comCumBirds = d.comCumPct * d.placed / 100.0
        Param("Live birds", "head", vi(d.live, P), v(d.placed - comCumBirds, 1, C), vi(d.placed, P, "Placed"), strong = true)
        Param("Deaths today", "", listOf(
            "birds" to listOf(vi(d.mortToday, P), v(d.comMortBirdsToday, 1, C)),
            "% of live" to listOf(v(d.mortTodayPct, 3, P), v(d.comDailyPct, 3, C))
        ))
        Param("Livability", "%", v(e.livability, 2, P), v(100 - d.comCumPct, 2, C))
        Param("Reception / transit deaths", "head", vi(d.reception, P))
        Param("Culls / lame", "birds", vi(e.lameSeparated, P, "Today"), vi(d.lameTD, P, "Till date"))
        Param("Lifted", "", listOf("birds" to listOf(vi(e.birdsLifted, P, "Today"), vi(d.liftTD, P, "Till date")),
            "kg" to listOf(v(e.weightLifted, 2, P, "Today"), v(d.liftKgTD, 2, P, "Till date"))))
    }
    OutputCard(title = "⚖️ Growth detail") {
        Param("Weight-age", "days", v(e.weightAge, 2, d.vk), v(d.day.toDouble(), 1, I, "Calendar age"))
        RangeParam("Uniformity CV", "%", null, null, 10.0, e.cv, 2)
        Param("7-day weight multiple", "day-7 weight ÷ chick weight", v(d.sevenDayMultiple, 2, P), v(4.5, 2, MN))
    }
    PopulationDistributionCard(entry = e)
    OutputCard(title = "🏠 Space") {
        RangeParam("Stocking density", "kg per ft²", null, null, kgPerFt2(d.farm.densityCapDefault), e.densityKgM2?.let { kgPerFt2(it) }, 3, d.vk)
        RangeParam("Floor space", "ft² per bird", e.minFtPerBird.takeIf { it > 0 }, null, null, e.ftPerBird.takeIf { it > 0 }, 3, d.vk)
        Param("Floor in use", "ft²", v(e.occupiedFt2, 1, d.vk, "Birds' area"), v(d.houseFloorFt2, 1, I, "Whole house"))
    }
    HouseFloorPlan(entry = e, farm = d.farm)
    BirdCharts(d)
}

// =================================== FEED & WATER ===================================

@Composable
private fun FeedTopic(d: OutputData) {
    FeedingPlanCard(d)
    if (d.day <= 7) FirstWeekCard(d)
    val yLive = (d.byDay[d.day - 1]?.liveBirds ?: d.live).toDouble()
    KpiCard(
        "🎯 Eaten vs standards",
        listOf(
            Kpi("Eaten yesterday", "g per bird", d.usedPerBirdY, P, d.comPerBirdY, d.idealPerBirdY, Better.CLOSER, 1, totalFactor = yLive / 1000, totalUnit = "kg"),
            Kpi("Eaten till date", "g per bird", d.cumPerBird, P, d.cumPerBirdCom, d.cumPerBirdIdeal, Better.CLOSER, 1, totalFactor = d.live / 1000.0, totalUnit = "kg")
        )
    )
    WaterCard(d)
    FeedCharts(d)
}

@Composable
private fun FeedingPlanCard(d: OutputData) {
    val live = d.live.toDouble()
    val bag = d.bagKg
    OutputCard(title = "🥣 Feeding plan") {
        Text("Phase ${d.phase}" + (d.nextPhaseDay?.let { " until day ${it - 1} · ${CompanyStandard.feedPhase(it)} from day $it" } ?: " until lifting"),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Param("Feed today", "", listOf(
            "per bird g" to listOf(v(d.givePerBird, 1, PR, "To give"), v(d.comPerBird, 1, C), v(d.idealPerBird, 1, I)),
            "farm kg" to listOf(v(d.giveKg, 1, PR, "To give"), v(d.comPerBird?.let { it * live / 1000 }, 1, C), v(d.idealPerBird * live / 1000, 1, I)),
            "bags" to listOf(v(d.giveBags, 2, PR, "To give"), v(d.comPerBird?.let { it * live / 1000 / bag }, 2, C), v(d.idealPerBird * live / 1000 / bag, 2, I))
        ), strong = true, note = "To give = company feed curve at your flock's weight, adjusted for heat. Bag = ${Fmt.n(bag, 1)} kg.")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        SubHeader("Feedings")
        Param("Feed", "times today", vt("${d.feedings}.0 ×", PR, "Feed"), vt(d.feedTimes, PR, "At"))
        Note(d.feedingReason)
        Param("Each feeding", "bags", listOf(
            "all lines" to listOf(v(d.bagsPerFeeding, 2, PR)),
            "per line" to listOf(v(d.bagsPerLinePerFeeding, 2, PR))
        ), strong = true)
        Param("Charge the lines", "bags to fill every open pan", v(d.bagsFillOpen, 2, I, "All lines"), v(d.bagsFillOpen / d.feederLines, 2, I, "Per line"),
            v(d.fillsPossible, 2, PR, "Charges today"))
        val emptyReach = d.reachOfOpen.coerceAtMost(1.0) * 100
        Text(
            if (d.reachOfOpen >= 1.0) "✅ Each feeding reaches the last open pan even if the pans are empty."
            else "⚠ Pans fill in order from the hopper. Poured into EMPTY pans, one ${Fmt.n(d.bagsPerFeeding, 2)}-bag feeding reaches only ${Fmt.n(emptyReach, 1)}% of the open pans " +
                "(${Fmt.n(d.reachFt, 1)} of ${Fmt.n(d.openLenFt, 1)} ft). Keep the lines charged: fill them with ${Fmt.n(d.bagsFillOpen, 2)} bags once, then each top-up only replaces what was eaten and reaches every pan — so feed again before the pans run empty.",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (d.reachOfOpen >= 1.0) ValuePresent else StatusWarn
        )
        if (d.day >= 10) Note(
            if (d.cleanOutOk) "Clean-out (Ross, from day 10–12): once a day let the birds empty the pans, then refill with ${Fmt.n(d.bagsFillOpen, 2)} bags at once and give the other ${Fmt.n(d.giveBags - d.bagsFillOpen, 2)} bags as top-ups."
            else "Skip the daily clean-out for now: refilling empty lines takes ${Fmt.n(d.bagsFillOpen, 2)} bags — more than today's ${Fmt.n(d.giveBags, 2)} bags."
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        SubHeader("Feeder lines")
        FeederLinePicture(d)
        Param("Line", "", v(d.lineLenFt, 1, P, "Length ft"), v(d.bagsFullLine, 1, P, "Bags fill 1 line"), v(d.openLenFt, 1, PR, "Open ft"))
        Param("Pans", "", listOf(
            "open now" to listOf(vi(d.pansOpen, PR, "All lines"), vi(d.pansOpenPerLine, PR, "Per line")),
            "house" to listOf(vi(d.pansPerLine * d.feederLines, P, "All pans"), v(d.panSpacingFt, 2, P, "Spacing ft"))
        ))
        Param("Birds per open pan", "birds", v(d.birdsPerPan, 1, PR), v(d.birdsPerPanMin, 1, MN), v(d.birdsPerPanMax, 1, MX),
            note = "Ross: 45–80 birds per pan (the lower figure above 3.5 kg).")
    }
}

/** One feeder line: the whole line, the part open to the birds, and how far one feeding reaches. */
@Composable
private fun FeederLinePicture(d: OutputData) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val openC = ValueIdeal
    val reachC = kindColor(PR)
    Canvas(Modifier.fillMaxWidth().height(46.dp)) {
        val left = 26f; val right = size.width - 8f
        val w = right - left
        val y = size.height * 0.24f
        // hopper
        drawRect(reachC, Offset(2f, y - 16f), Size(20f, 32f))
        drawRoundRect(track, Offset(left, y - 10f), Size(w, 20f), CornerRadius(10f, 10f))
        drawRoundRect(openC.copy(alpha = 0.30f), Offset(left, y - 10f), Size((w * d.openFrac).toFloat(), 20f), CornerRadius(10f, 10f))
        val reach = (d.reachFt / d.lineLenFt).coerceIn(0.0, 1.0).toFloat()
        drawRoundRect(reachC, Offset(left, y - 5f), Size(w * reach, 10f), CornerRadius(5f, 5f))
        // pans
        val n = d.pansPerLine
        val step = max(1, n / 60)
        for (i in 0 until n step step) {
            val px = left + w * (i + 0.5f) / n
            val open = i < d.pansOpenPerLine
            val fed = (i + 0.5f) / n <= reach
            drawCircle(if (fed && open) reachC else if (open) openC else track.copy(alpha = 0.9f), 4.5f, Offset(px, size.height * 0.5f))
        }
        val lbl = Paint().apply { color = labelArgb; textSize = 26f; isAntiAlias = true }
        drawContext.canvas.nativeCanvas.drawText("0.0 ft", left, size.height - 4f, lbl)
        lbl.textAlign = Paint.Align.CENTER
        drawContext.canvas.nativeCanvas.drawText("open ${Fmt.n(d.openLenFt, 1)} ft", left + (w * d.openFrac).toFloat() * 0.5f, size.height - 4f, lbl)
        lbl.textAlign = Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText("${Fmt.n(d.lineLenFt, 1)} ft × ${d.feederLines}.0 lines", right, size.height - 4f, lbl)
    }
    KeyLine(kindColor(PR) to "one feeding into empty pans", ValueIdeal to "open to birds", MaterialTheme.colorScheme.onSurfaceVariant to "closed")
}

@Composable
private fun FirstWeekCard(d: OutputData) {
    val f = d.farm
    val start = max(4, d.panReachDay)
    val end = max(7, start + 3)
    OutputCard(title = "🐤 First week · trays, paper, drinkers") {
        Param("Feeder trays (manual feeders)", "", listOf(
            "keep today" to listOf(v(ceil(f.manualFeeders * d.trayKeepFrac), 1, PR, "Yours"), v(d.traysKeepIdeal, 1, I)),
            "full set" to listOf(vi(f.manualFeeders, P, "Yours"), v(d.traysIdeal, 1, I, "1 per 100 chicks"))
        ), strong = true, note = "Keep all trays until day ${start - 1}; take them out over days $start–${end - 1}; none from day $end (Ross: on the main feeders by day 6–7).")
        if (d.day <= 4) Param("Feed paper", "ft²", v(d.paperFt2, 1, I, "At least"),
            note = "Ross: feed on paper over ≥ 70.0% of the brooding area; top it up often; take the paper out by the end of day 4.")
        Param("Manual drinkers", "", listOf(
            "keep today" to listOf(v(ceil(f.manualDrinkers * d.drinkerKeepFrac), 1, PR, "Yours"), v(ceil(d.miniDrinkersIdeal * d.drinkerKeepFrac), 1, I)),
            "full set" to listOf(vi(f.manualDrinkers, P, "Yours"), v(d.miniDrinkersIdeal, 1, I, "12 per 1,000"))
        ), note = "Ross: supplementary drinkers for the first 3 days; half on day 4; none from day 5." +
            if (f.manualDrinkers == 0) " Set how many you use in farm settings." else "")
        Param("Can birds reach the pans?", "cm", v(d.breastNowCm, 1, PR, "Breast height"), v(f.panLipCm, 1, MX, "Pan lip"), vt("day ${d.panReachDay}", PR, "Reach from"),
            note = "Breast height estimated from weight (≈4.0 cm at 40 g, growing with weight^⅓). Ross: the pan lip should be level with the top of the breast. Set your pan lip height in farm settings.")
    }
}

@Composable
private fun WaterCard(d: OutputData) {
    val e = d.e
    val live = d.liveSafe.toDouble()
    val tank = d.tankL; val fct = d.refillF
    OutputCard(title = "💧 Water") {
        Param("Water", "projected — water isn't logged", listOf(
            "per bird mL" to listOf(v(e.waterPerBird, 1, PR, "Today"), v(e.waterHighL * 1000 / live, 1, PR, "Hot +3 °C"), v(e.waterLowL * 1000 / live, 1, PR, "Cool −3 °C")),
            "farm L" to listOf(v(e.totalWaterL, 1, PR, "Today"), v(e.waterHighL, 1, PR, "Hot +3 °C"), v(e.waterLowL, 1, PR, "Cool −3 °C")),
            "tank fills" to listOf(v(e.totalWaterL / tank * fct, 2, PR, "Today"), v(e.waterHighL / tank * fct, 2, PR, "Hot +3 °C"), v(e.waterLowL / tank * fct, 2, PR, "Cool −3 °C"))
        ), strong = true, note = "Tank ${Fmt.n(tank, 1)} L; fills include your calibration × ${Fmt.n(fct, 2)}.")
        Param("Water till date", "farm L", v(d.waterTD, 1, PR))
        Param("Per drinker line", "L/hour over 16.0 h", v(e.drinkerFlowLHrLine, 2, PR))
        RangeParam("Water : feed", "ratio", 1.8, null, 2.0, if (e.totalFeedKg > 0) e.totalWaterL / e.totalFeedKg else null, 2, PR)
        if (d.birdsPerNipple != null) RangeParam("Birds per nipple", "birds", null, null, d.birdsPerNippleMax, d.birdsPerNipple, 1, PR,
            note = "Ross: 10–12 while brooding, 12 below 3 kg, 9 above 3 kg.")
        else Note("Set nipples per drinker line in farm settings to check birds per nipple.")
        RangeParam("Nipple flow", "mL/min", d.nippleFlow.first, null, d.nippleFlow.second, null, idealBand = d.nippleFlow, note = "Ross flow guide for this age.")
        RangeParam("Water temperature", "°C", 18.0, null, 21.0, e.waterTempC, idealBand = 18.0 to 21.0, note = "Ross: 18–21 °C drinks best; above 30 °C intake drops — flush lines on hot days.")
        RangeParam("Water pH", "", 6.0, null, 6.8, e.waterPh, 2)
        Param("Drinker line", "inches", v(d.drinkerHtIn, 1, I, "Height"), v(e.drinkerPressureIn, 1, I, "Pressure"),
            note = "Ross: nipple height — bird's back at 35–45° to the floor under 7 days, 75–85° after.")
    }
}

// =================================== STOCK ===================================

@Composable
private fun StockTopic(d: OutputData) {
    val f = d.farm
    OutputCard(title = "🏚️ Godown") {
        if (f.godownBags > 0) {
            FillBar(d.stockBagsTotal / f.godownBags, ValuePresent)
            Param("Space", "bags", v(d.stockBagsTotal, 2, P, "In store"), v(f.godownBags, 1, MX, "Holds"), v(d.godownFree, 2, PR, "Free"), strong = true,
                note = "Free space is what the next delivery can be.")
        } else Note("Set how many bags the godown holds (Farm settings → Godown) to see free space.")
        Note("Store below 25.0 °C and 60.0% RH, on pallets, first in – first out.")
    }
    OutputCard(title = "📦 Feed by type") {
        val maxBags = d.allCodes.maxOfOrNull { max(d.recByCode[it] ?: 0.0, d.stockBags(it)) } ?: 0.0
        val unit = listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0).firstOrNull { maxBags / it <= 20 } ?: 200.0
        if (d.allCodes.isEmpty()) Note("No deliveries logged yet. Enter feed received on the Entry tab.")
        d.allCodes.forEach { code -> FeedTypeBlock(d, code, unit) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Param("All types", "bags", listOf(
            "logged" to listOf(v(d.recBagsTD, 2, P, "Received"), v(d.usedBagsTD, 2, P, "Used"), v(d.stockBagsTotal, 2, P, "In store")),
            "to lifting" to listOf(v(d.needByCode.values.sum(), 2, PR, "Needed"), v(d.allCodes.sumOf { d.orderBags(it) }, 2, PR, "To order"),
                v(d.lastsDays?.takeIf { d.stockKgTotal > 0 }, 1, PR, "Days left"))
        ), strong = true, note = "In store = ${Fmt.n(d.stockKgTotal, 1)} kg. Each sack drawn = ${Fmt.n(unit, 1)} bags · filled = in store · outline = used.")
    }
    OutputCard(title = "⛽ Diesel") {
        val canL = f.dieselCanL
        Param("Diesel used", "", listOf(
            "cans" to listOf(v(d.e.dieselCansUsed, 2, P, "Today"), v(d.dieselTD, 2, P, "Till date")),
            "litres" to listOf(v(d.e.dieselCansUsed * canL, 1, P, "Today"), v(d.dieselTD * canL, 1, P, "Till date")),
            "L / 1,000 birds" to listOf(v(d.e.dieselCansUsed * canL * 1000 / d.liveSafe, 2, P, "Today"), v(d.dieselTD * canL * 1000 / d.liveSafe, 2, P, "Till date"))
        ), note = "Can = ${Fmt.n(canL, 1)} L.")
    }
}

@Composable
private fun FeedTypeBlock(d: OutputData, code: String, unit: Double) {
    val ft = d.feedTypes.firstOrNull { it.code == code }
    val col = feedColor(code, d.feedTypes)
    val rec = d.recByCode[code] ?: 0.0
    val used = d.usedByCode[code] ?: 0.0
    val stock = d.stockBags(code)
    val kgBag = d.kgPerBag(code)
    val isPhase = code == d.phase
    Surface(color = col.copy(alpha = 0.10f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(col, RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("$code · ${ft?.name ?: "Feed"}" + if (isPhase) "  ◀ feeding now" else "", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = col)
                    Text("${Fmt.n(kgBag, 1)} kg per bag", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${Fmt.n(stock, 2)} bags", style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black),
                    color = if (stock < 0) StatusCrit else col)
            }
            if (rec > 0 || used > 0) SackStrip(max(0.0, stock) / unit, min(used, rec) / unit, col)
            val need = d.needByCode[code] ?: 0.0
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ValueChip(v(rec, 2, P, "Received"), Modifier.weight(1f))
                ValueChip(v(used, 2, P, "Used"), Modifier.weight(1f))
                ValueChip(v(stock * kgBag, 1, P, "In store kg"), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ValueChip(v(need, 2, PR, "Needed to lifting"), Modifier.weight(1f))
                ValueChip(v(d.orderBags(code), 2, PR, "To order"), Modifier.weight(1f))
                ValueChip(v(if (isPhase && d.giveKg > 0 && stock > 0) stock * kgBag / d.giveKg else null, 1, PR, "Days left"), Modifier.weight(1f))
            }
            if (stock < 0) Text("⚠ more used than received — a delivery is missing", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = StatusCrit)
        }
    }
}
