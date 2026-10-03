package com.example.flock.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.engine.CompanyStandard
import com.example.flock.engine.PhysiologicalEngine
import com.example.flock.ui.Fmt
import com.example.ui.theme.GlassLine
import com.example.ui.theme.StatusCrit
import com.example.ui.theme.StatusWarn
import com.example.ui.theme.ValueIdeal
import com.example.ui.theme.ValuePresent
import kotlin.math.max
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.ceil

private val P = ValueKind.PRESENT
private val PR = ValueKind.PREDICTED
private val I = ValueKind.IDEAL
private val C = ValueKind.COMMERCIAL
private val MN = ValueKind.MIN
private val MX = ValueKind.MAX

private val TempDial = Color(0xFFFFB38A)
private val RhDial = Color(0xFFA9CCF0)

// =================================== page pieces ===================================

@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp),
        color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 10.dp))
}

@Composable
fun AlertList(alerts: List<TopicAlert>) {
    if (alerts.isEmpty()) return
    OutputCard(title = "Needs attention") {
        alerts.sortedByDescending { it.level }.take(6).forEach { a ->
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 6.dp).size(7.dp).border(4.dp, alertColor(a.level), RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(10.dp))
                Text(a.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** A compact number tile. */
@Composable
fun StatTile(label: String, value: V, sub: String?, modifier: Modifier = Modifier) {
    GlassBox(modifier) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
            Text(value.text, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                color = kindColor(if (value.text == "—") ValueKind.NEUTRAL else value.kind), maxLines = 1, softWrap = false)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f), maxLines = 2)
        }
    }
}

// =================================== TABS ===================================

/** Birds: how many, deaths, growth, uniformity, comfort and the curves — everything about the birds in one place. */
@Composable
fun BirdsTab(d: OutputData) {
    val e = d.e
    val live = d.live.toDouble()
    val comCumBirds = d.comCumPct * d.entryBirds / 100.0
    KpiCard("Flock", listOf(
        Kpi("Live birds", "", live, P, d.entryBirds - comCumBirds, null, Better.HIGHER, 0),
        Kpi("Deaths today", "%", d.mortTodayPct, P, d.comDailyPct, null, Better.LOWER, 3,
            totalFactor = (d.live + d.mortToday) / 100.0, totalUnit = "birds", totalDec = 0),
        Kpi("Mortality till date", "%", d.mortTDPct, P, d.comCumPct, d.ceilingPct, Better.LOWER, 2,
            totalFactor = d.entryBirds / 100.0, totalUnit = "birds", totalDec = 0),
        Kpi("Livability", "%", e.livability, P, 100 - d.comCumPct, null, Better.HIGHER, 2)
    ))
    OutputCard(title = "Start and removals") {
        Param("Start", "", vi(d.placed, P, "Placed"), vi(d.reception, P, "Reception"), vi(d.entryBirds, P, "Entry"))
        Param("Removed", "", vi(d.lameTD, P, "Culls"), vi(d.liftTD, P, "Lifted"), v(d.liftKgTD, 1, P, "Lifted kg"))
    }
    KpiCard("Growth", listOf(
        Kpi("Body weight", "g", d.bw, d.vk, d.bwCom, d.bwIdeal, Better.HIGHER, 1, totalFactor = live / 1000, totalUnit = "kg"),
        Kpi("Daily gain", "g/day", d.gain, d.gainKind, d.gainCom, d.gainIdeal, Better.HIGHER, 1, totalFactor = live / 1000, totalUnit = "kg"),
        Kpi("FCR", "", e.fcr, P, d.fcrCom, d.fcrIdeal, Better.LOWER, 3, settling = d.day < 7),
        Kpi("cFCR", "2 kg", e.cFcr, P, d.cfcrCom, d.cfcrIdeal, Better.LOWER, 3, settling = d.day < 7),
        Kpi("EPEF", "", d.epef, P, d.epefCom, d.epefIdeal, Better.HIGHER, 1, settling = d.day < 7)
    ))
    OutputCard(title = "Uniformity") {
        RangeParam("CV · birds weighed one by one", "%", null, 8.0, 10.0, e.cv, 2)
        RangeParam("Within ±10 % of the mean", "%", 80.0, null, null, e.uniformityPct, 1)
        if (e.locSpreadPct != null) Param("Spread between locations", "%", v(e.locSpreadPct, 2, P, "Bulk weighing"))
    }
    if (e.cv != null && e.sampleEntered) PopulationDistributionCard(entry = e)
    OutputCard(title = "Comfort") {
        RangeParam("Body (vent)", "°C", d.bodyTemp.first, d.bodyTemp.second, d.bodyTemp.third, null)
        RangeParam("Feet", "°C", d.footTemp.first, d.footTemp.second, d.footTemp.third, null)
        RangeParam("Density", "kg/ft²", null, null, kgPerFt2(d.farm.densityCapDefault), e.densityKgM2?.let { kgPerFt2(it) }, 3, d.vk)
        RangeParam("Floor", "ft²/bird", e.minFtPerBird.takeIf { it > 0 }, null, null, e.ftPerBird.takeIf { it > 0 }, 3, d.vk)
        Param("Light", "h", v(e.lightHours, 1, I, "Light"), v(24.0 - e.lightHours, 1, I, "Dark"))
        RangeParam("Light intensity", "lux", d.lightLux.first, null, d.lightLux.second, e.luxPerFt2, idealBand = d.lightLux)
    }
    BirdCharts(d)
}

/** Ventilation: the house air and litter it controls, then the fan plan. */
@Composable
fun VentTab(d: OutputData) {
    val e = d.e
    OutputCard(title = "House air") {
        RangeParam("Temperature", "°C", e.tempMin, e.tempIdeal, e.tempMax, null)
        RangeParam("Humidity", "%", e.rhMin, e.rhIdeal, e.rhMax, null)
        RangeParam("Air speed", "ft/min", null, null, PhysiologicalEngine.maxAirSpeedFpm(d.day), e.measuredAirspeed)
        RangeParam("Static pressure", "Pa", 20.0, null, 25.0, e.measuredPressure)
        RangeParam("CO₂", "ppm", null, null, e.co2Max, e.measuredCo2)
        RangeParam("NH₃", "ppm", null, null, e.nh3Max, e.measuredNh3)
    }
    OutputCard(title = "Litter") {
        RangeParam("Temperature", "°C", d.litterTemp.first, d.litterTemp.second, d.litterTemp.third, null)
        RangeParam("Moisture", "%", 20.0, 25.0, 30.0, null)
    }
    VentSection(d)
}

/** Feed, water and stock: today's plan, what was eaten, water, and what is in store. */
@Composable
fun FeedTab(d: OutputData, onFarmChange: ((com.example.flock.data.FarmEntity) -> Unit)? = null) {
    FeedingPlanCard(d)
    if (d.day <= 7) FirstWeekCard(d)
    val yLive = (d.byDay[d.day - 1]?.liveBirds ?: d.live).toDouble()
    KpiCard("Eaten", listOf(
        Kpi("Yesterday", "g/bird", d.usedPerBirdY, P, d.comPerBirdY, d.idealPerBirdY, Better.CLOSER, 1, totalFactor = yLive / 1000, totalUnit = "kg"),
        Kpi("Till date", "g/bird", d.cumPerBird, P, d.cumPerBirdCom, d.cumPerBirdIdeal, Better.CLOSER, 1, totalFactor = d.live / 1000.0, totalUnit = "kg")
    ))
    FeedCharts(d)
    WaterCard(d, onFarmChange)
    StockBlock(d)
}

/** A numbered step of the feeding plan: circle with the number, name, then its values. */
@Composable
private fun Step(n: Int, label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                Text("$n", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
        }
        content()
    }
}

@Composable
private fun FeedingPlanCard(d: OutputData) {
    val live = d.live.toDouble()
    val bag = d.bagKg
    // 2 or 3 feedings a day: the best one first, the other a tap away
    var pick by remember(d.day, d.recommendedOption, d.dayBags) { mutableIntStateOf(d.recommendedOption) }
    val o = d.feedOptions[pick]
    val pat = if (o.safe) o.pattern else d.patterns.first()
    val hopper = max(0.0, o.bagsPerFeeding - d.feederLines * pat.openPerLine / d.pansPerBag)
    val shift = rememberClockShift("feed_${d.farm.spreadsheetId}")
    val light = LightProgram(d.e.lightHours)
    val lines = d.feederLines
    OutputCard(title = "Feeding plan") {
        // today, for the whole house
        KpiLine(Kpi("Feed needed", "bags", d.giveBags, PR, d.comPerBird?.let { it * live / 1000 / bag }, d.idealPerBird * live / 1000 / bag, Better.CLOSER, 2))
        KpiLine(Kpi("Feed per bird", "g", d.givePerBird, PR, d.comPerBird, d.idealPerBird, Better.CLOSER, 1))
        ValueRow(listOf(v(d.dayBags, 2, PR, "Full bags"), v(d.extraBags, 2, PR, "Above need")), "give")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("times a day", modifier = Modifier.width(62.dp), maxLines = 2, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
            d.feedOptions.forEachIndexed { i, opt ->
                val tag = when { !opt.safe -> "Not safe"; i == d.recommendedOption -> "Best"; else -> "Also OK" }
                val kind = when { !opt.safe -> MX; i == d.recommendedOption -> P; else -> PR }
                ValueChip(vi(opt.feedings, kind, tag), Modifier.weight(1f)
                    .border(if (i == pick) 2.dp else 0.dp, if (i == pick) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { pick = i })
            }
        }
        ValueRow(listOf(vt(pat.label, if (o.safe) PR else MX, "Pans on / off")), "pattern")
        ValueRow(listOf(v(o.fillPct, 1, PR, "Pans filled %"), v(hopper, 2, PR, "Hopper bags")), "fill")
        // macro and micro: the whole house next to one line and one pan, per feeding
        MacroMicroTable(listOf(
            MMRow("Bags / feeding", v(o.bagsPerFeeding, 2, PR), v(o.bagsPerLine, 2, PR), null),
            MMRow("kg / feeding", v(o.bagsPerFeeding * bag, 1, PR), v(o.kgPerLine, 1, PR), v(if (pat.openPerLine > 0) o.kgPerLine / pat.openPerLine else null, 2, PR)),
            MMRow("Pans on", vi(pat.openPerLine * lines, PR), vi(pat.openPerLine, PR), null),
            MMRow("Birds", vi(d.live, P), v(live / lines, 1, P), v(pat.birdsPerPan, 1, if (pat.birdsPerPan <= d.birdsPerPanMax) PR else MX)),
            MMRow("Floor ft²", v(d.areaInUseFt2, 1, P), v(d.areaInUseFt2 / lines, 1, P), v(pat.cellFt2, 1, PR)),
            MMRow("Walk m", null, null, v(pat.travelM, 2, if (pat.travelM <= ALLOWED_TRAVEL_M) PR else MX))
        ))
        ValueRow(listOf(v(d.birdsPerPanMax, 1, MX, "Max birds / pan"), v(ALLOWED_TRAVEL_M, 2, MX, "Max walk m")), "limits")
        FarmTopView(d, pat)
        KeyLine(kindColor(PR) to "on", Color.White.copy(alpha = 0.6f) to "off", Color.White to "sensor", kindColor(MN) to "drinker")
        PanCellView(d, pat)
        DayClock(d.feedTimesFor(o.feedings).map { hoursOf(it) }, shift.value, { shift.value = it }, kindColor(PR),
            light.darkStartHour to light.darkEndHour, farmZone(d.farm), tag = "feedClock")
    }
}

/** The farm's time zone (the phone's if the setting can't be read). */
fun farmZone(f: com.example.flock.data.FarmEntity): java.time.ZoneId = try { java.time.ZoneId.of(f.timeZone) } catch (e: Exception) { java.time.ZoneId.systemDefault() }

@Composable
private fun FirstWeekCard(d: OutputData) {
    val f = d.farm
    OutputCard(title = "First week") {
        Param("Feeder trays", "", listOf(
            "today" to listOf(v(kotlin.math.ceil(f.manualFeeders * d.trayKeepFrac), 1, PR, "Keep"), v(d.traysKeepIdeal, 1, I, "Ideal")),
            "total" to listOf(vi(f.manualFeeders, P, "Yours"), v(d.traysIdeal, 1, I, "1 / 100"))
        ))
        if (d.day <= 4) Param("Feed paper", "ft²", v(d.paperFt2, 1, I, "Min"))
        Param("Manual drinkers", "", listOf(
            "today" to listOf(v(kotlin.math.ceil(f.manualDrinkers * d.drinkerKeepFrac), 1, PR, "Keep"), v(kotlin.math.ceil(d.miniDrinkersIdeal * d.drinkerKeepFrac), 1, I, "Ideal")),
            "total" to listOf(vi(f.manualDrinkers, P, "Yours"), v(d.miniDrinkersIdeal, 1, I, "12 / 1,000"))
        ))
        Param("Pan reach", "cm", v(d.breastNowCm, 1, PR, "Breast"), v(f.panLipCm, 1, MX, "Pan lip"), vi(d.panReachDay, PR, "From day"))
    }
}

@Composable
private fun WaterCard(d: OutputData, onFarmChange: ((com.example.flock.data.FarmEntity) -> Unit)? = null) {
    val e = d.e
    val live = d.liveSafe.toDouble()
    val shift = rememberClockShift("water_${d.farm.spreadsheetId}")
    val light = LightProgram(e.lightHours)
    // tank refills: what the day's water needs, times the farm's multiplier (fresher, cooler water)
    val standard = max(1.0, ceil(e.totalWaterL / d.tankL))
    val factor = if (d.refillF > 0) d.refillF else 1.0
    val refills = (standard * factor).roundToInt().coerceIn(1, 24)
    OutputCard(title = "Water") {
        DayClock((0 until refills).map { 6.0 + it * 24.0 / refills }, shift.value, { shift.value = it }, kindColor(MN),
            light.darkStartHour to light.darkEndHour, farmZone(d.farm), tag = "waterClock")
        ValueRow(listOf(vi(refills, PR, "Refills"), v(standard, 1, P, "Needed"), v(e.totalWaterL / refills, 1, PR, "L each")), "tank")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("refill ×", modifier = Modifier.width(62.dp), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
            listOf(1.0, 2.0, 3.0, 4.0).forEach { m ->
                val on = abs(factor - m) < 0.01
                ValueChip(vt(Fmt.n(m, 1) + "×", if (on) P else PR, if (on) "Set" else ""), Modifier.weight(1f)
                    .border(if (on) 2.dp else 0.dp, if (on) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable(enabled = onFarmChange != null) { onFarmChange?.invoke(d.farm.copy(waterRefillFactor = m)) })
            }
        }
        Param("Water", "", listOf(
            "mL/bird" to listOf(v(e.waterPerBird, 1, PR, "Today"), v(e.waterHighL * 1000 / live, 1, PR, "Hot +3°"), v(e.waterLowL * 1000 / live, 1, PR, "Cool −3°")),
            "farm L" to listOf(v(e.totalWaterL, 1, PR, "Today"), v(e.waterHighL, 1, PR, "Hot +3°"), v(e.waterLowL, 1, PR, "Cool −3°"))
        ), strong = true)
        if (d.birdsPerNipple != null) RangeParam("Birds / nipple", "", null, null, d.birdsPerNippleMax, d.birdsPerNipple, 1, PR)
        RangeParam("Nipple flow", "mL/min", d.nippleFlow.first, null, d.nippleFlow.second, null, idealBand = d.nippleFlow)
        RangeParam("Water temperature", "°C", 18.0, null, 21.0, e.waterTempC, idealBand = 18.0 to 21.0)
        RangeParam("Water pH", "", 6.0, null, 6.8, e.waterPh, 2)
    }
}

@Composable
fun StockBlock(d: OutputData) {
    val f = d.farm
    OutputCard(title = "Feed store") {
        if (f.godownBags > 0) {
            FillBar(d.stockBagsTotal / f.godownBags, Color.White.copy(alpha = 0.7f))
            Param("Godown", "bags", v(d.stockBagsTotal, 2, P, "In store"), v(f.godownBags, 1, MX, "Holds"), v(d.godownFree, 2, PR, "Free"))
        }
        val maxBags = d.allCodes.maxOfOrNull { max(d.recByCode[it] ?: 0.0, d.stockBags(it)) } ?: 0.0
        val unit = listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0).firstOrNull { maxBags / it <= 20 } ?: 200.0
        d.allCodes.forEach { code -> FeedTypeBlock(d, code, unit) }
        Param("All types", "bags", v(d.stockBagsTotal, 2, P, "In store"), v(d.needByCode.values.sum(), 2, PR, "Needed"),
            v(d.allCodes.sumOf { d.orderBags(it) }, 2, PR, "To order"), v(d.lastsDays?.takeIf { d.stockKgTotal > 0 }, 1, PR, "Days"), strong = true)
        val canL = f.dieselCanL
        Param("Diesel", "L", v(d.e.dieselCansUsed * canL, 1, P, "Today"), v(d.dieselTD * canL, 1, P, "Till date"))
    }
}

@Composable
private fun FeedTypeBlock(d: OutputData, code: String, unit: Double) {
    val ft = d.feedTypes.firstOrNull { it.code == code }
    val rec = d.recByCode[code] ?: 0.0
    val used = d.usedByCode[code] ?: 0.0
    val stock = d.stockBags(code)
    val isPhase = code == d.phase
    Column(Modifier.fillMaxWidth().border(1.dp, GlassLine.copy(alpha = 0.35f), RoundedCornerShape(10.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$code · ${ft?.name ?: "Feed"}" + if (isPhase) " · now" else "", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            Text(Fmt.n(stock, 2), style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black),
                color = if (stock < 0) StatusCrit else kindColor(P))
        }
        if (rec > 0 || used > 0) SackStrip(max(0.0, stock) / unit, kotlin.math.min(used, rec) / unit, Color.White.copy(alpha = 0.8f))
        ValueRow(listOf(v(d.needByCode[code] ?: 0.0, 2, PR, "Needed"), v(d.orderBags(code), 2, PR, "To order"),
            v(if (isPhase && d.giveKg > 0 && stock > 0) stock * d.kgPerBag(code) / d.giveKg else null, 1, PR, "Days")))
    }
}

// =================================== VENTILATION ===================================

@Composable
fun VentSection(d: OutputData) {
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
    OutputCard(title = "Minimum ventilation") {
        HouseAirflow(l, f.fanCount, f.hasEC, false, false, d.airFpm(l.avgFans))
        Param("Timer", "s", vt(l.cyc.joinToString(",").ifEmpty { l.cont.joinToString(",") }, I, "Fan"), vi(if (l.isTimer) l.on else 0, I, "On"),
            vi(if (l.isTimer) l.off else 0, I, "Off"), v(l.avgFans, 2, PR, "Avg fans"), strong = true)
        Param("Air", "cfm", listOf(
            "per bird" to listOf(v(d.plan.needCfm / live, 3, I, "Needed"), v(d.minAvgCfm / live, 3, PR, "Delivered")),
            "house" to listOf(v(d.plan.needCfm, 1, I, "Needed"), v(d.minAvgCfm, 1, PR, "Delivered"))
        ))
        Param("Air speed", "ft/min", v(d.airFpm(l.avgFans), 1, PR), v(if (d.day <= 14) 29.5 else null, 1, MX, "Draught max"))
    }
}

@Composable
private fun FanFinderCard(d: OutputData, tC: Double, rh: Double, setT: (Double) -> Unit, setRh: (Double) -> Unit) {
    val f = d.farm
    val e = d.e
    val live = d.liveSafe.toDouble()
    OutputCard(title = "Fan finder") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Knob("Outside", "°C", tC, 5.0, 48.0, 0.5, TempDial, setT)
            Knob("Outside", "% RH", rh, 10.0, 100.0, 5.0, RhDial, setRh)
        }
        val s = remember(tC, rh, d) { d.sim(tC, rh) }
        val lv = d.levelOf(s)
        val cfm = s.fans * d.fanCfm
        HouseAirflow(lv, f.fanCount, f.hasEC, s.padEff > 0, s.heaterKw > 0.05, d.airFpm(s.fans))
        Param("Fans", "", v(s.fans, 2, PR, "Average"), vi(lv.fansOn, PR, "On"), vi(d.plan.maxFans, MX, "Allowed"), vi(s.level, PR, "Level"), strong = true)
        Param("Air", "cfm", listOf("per bird" to listOf(v(cfm / live, 3, PR)), "house" to listOf(v(cfm, 1, PR))))
        Param("Air speed", "ft/min", v(d.airFpm(s.fans), 1, PR), v(PhysiologicalEngine.maxAirSpeedFpm(d.day), 1, MX))
        Param("House", "", v(s.houseC, 1, PR, "°C"), v(e.tempIdeal, 1, I, "Ideal °C"), v(s.houseRh, 1, PR, "% RH"), v(e.rhIdeal, 1, I, "Ideal %"))
        Param("Birds feel", "°C", v(s.feltC, 1, PR), v(d.plan.comfort, 1, I, "Comfort"), v(s.heaterKw, 1, PR, "Heat kW"), v(s.padEff * 100, 1, PR, "Pads %"))
    }
}

@Composable
private fun CoolingGridCard(d: OutputData, onPick: (Double, Double) -> Unit) {
    val states = remember(d) { d.gridTemps.map { t -> d.gridRh.map { h -> d.sim(t, h) } } }
    OutputCard(title = "Fans by outside air") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Spacer(Modifier.width(56.dp))
            d.gridRh.forEach { h -> Text("${Fmt.n(h, 1)}%", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge, color = RhDial) }
        }
        d.gridTemps.forEachIndexed { i, t ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${Fmt.n(t, 1)}°", Modifier.width(56.dp), style = MaterialTheme.typography.labelLarge, color = TempDial)
                d.gridRh.forEachIndexed { j, h ->
                    val s = states[i][j]
                    val over = s.feltC - d.plan.comfort
                    val col = when { over > 4 -> StatusCrit; over > 2 -> StatusWarn; over < -3 -> ValueIdeal; else -> ValuePresent }
                    Column(Modifier.weight(1f).border(1.dp, GlassLine, RoundedCornerShape(10.dp)).clickable { onPick(t, h) }.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Fmt.n(s.fans, 1), style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black), color = col)
                        Text("${Fmt.n(s.feltC, 1)}°", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }
        KeyLine(ValuePresent to "comfy", StatusWarn to "warm", StatusCrit to "hot", ValueIdeal to "cool")
    }
}

@Composable
private fun ControllerCard(d: OutputData) {
    OutputCard(title = "Controller · day ${d.day}") {
        Param("Temperature", "°C", v(d.plan.set, 1, I, "SET"), v(d.plan.heat, 1, I, "Heat on"), v(d.plan.comfort, 1, I, "Comfort"), strong = true)
        Param("Limits", "", vi(d.plan.maxFans, MX, "Fans"), v(PhysiologicalEngine.maxAirSpeedFpm(d.day), 1, MX, "ft/min"), vi(d.plan.minLevel, I, "Min level"))
        Param("Fan", "cfm", v(d.fanCfm, 1, I, "One"), v(d.allFansCfm, 1, I, "All"))
    }
}

// =================================== MORTALITY ===================================


// =================================== ENVIRONMENT ===================================

