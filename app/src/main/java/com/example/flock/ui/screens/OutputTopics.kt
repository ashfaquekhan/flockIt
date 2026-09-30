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

/** 3 tiles per row, 2 on narrow screens or with large system text. */
@Composable
fun StatGrid(tiles: List<@Composable (Modifier) -> Unit>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = if (maxWidth < 380.dp || LocalDensity.current.fontScale > 1.1f) 2 else 3
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.chunked(cols).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { it(Modifier.weight(1f)) }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
fun OverviewStats(d: OutputData, feeder: FeederState) {
    val e = d.e
    StatGrid(listOf(
        { m -> StatTile("Live birds", vi(d.live, P), "entry ${Fmt.i(d.entryBirds)}", m) },
        { m -> StatTile("Weight g", v(d.bw, 1, d.vk), d.bwCom?.let { "com ${Fmt.n(it, 1)}" }, m) },
        { m -> StatTile("CV %", v(e.cv, 2, P), "max 10.00", m) },
        { m -> StatTile("FCR", v(e.fcr, 3, P), d.fcrCom?.let { "com ${Fmt.n(it, 3)}" }, m) },
        { m -> StatTile("Deaths today", vi(d.mortToday, P), d.mortTodayPct?.let { Fmt.pct(it, 3) }, m) },
        { m -> StatTile("Mortality %", v(d.mortTDPct, 2, P), "com ${Fmt.n(d.comCumPct, 2)}", m) },
        { m -> StatTile("Feed bags", v(d.planBags, 2, PR), "${d.feedings} × ${Fmt.n(d.bagsPerFeeding, 2)}", m) },
        { m -> StatTile("Feeder %", if (feeder.lastFedAt == null) vt("—", ValueKind.NEUTRAL) else v(feeder.fillFrac * 100, 1, if (feeder.levelKg > 0) PR else MX),
            when { feeder.lastFedAt == null -> "no feeding logged"; feeder.levelKg > 0 -> "${Fmt.n(feeder.hoursToEmpty ?: 0.0, 1)} h left"; else -> "empty ${Fmt.n(feeder.emptyForH, 1)} h" }, m) },
        { m -> StatTile("Water L", v(e.totalWaterL, 1, PR), "${Fmt.n(e.totalWaterL / d.tankL * d.refillF, 2)} tanks", m) }
    ))
}

@Composable
fun GrowthBlock(d: OutputData) {
    val e = d.e
    val live = d.live.toDouble()
    KpiCard("Growth", listOf(
        Kpi("Body weight", "g", d.bw, d.vk, d.bwCom, d.bwIdeal, Better.HIGHER, 1, totalFactor = live / 1000, totalUnit = "kg"),
        Kpi("Daily gain", "g/day", d.gain, d.gainKind, d.gainCom, d.gainIdeal, Better.HIGHER, 1, totalFactor = live / 1000, totalUnit = "kg"),
        Kpi("FCR", "", e.fcr, P, d.fcrCom, d.fcrIdeal, Better.LOWER, 3, settling = d.day < 7),
        Kpi("cFCR", "2 kg", e.cFcr, P, d.cfcrCom, d.cfcrIdeal, Better.LOWER, 3, settling = d.day < 7),
        Kpi("EPEF", "", d.epef, P, d.epefCom, d.epefIdeal, Better.HIGHER, 1, settling = d.day < 7)
    ))
    if (e.sampleEntered) PopulationDistributionCard(entry = e)
    BirdCharts(d)
}

// =================================== FEED ===================================

@Composable
fun FeedSection(d: OutputData) {
    FeedingPlanCard(d)
    if (d.day <= 7) FirstWeekCard(d)
    val yLive = (d.byDay[d.day - 1]?.liveBirds ?: d.live).toDouble()
    KpiCard("Eaten", listOf(
        Kpi("Yesterday", "g/bird", d.usedPerBirdY, P, d.comPerBirdY, d.idealPerBirdY, Better.CLOSER, 1, totalFactor = yLive / 1000, totalUnit = "kg"),
        Kpi("Till date", "g/bird", d.cumPerBird, P, d.cumPerBirdCom, d.cumPerBirdIdeal, Better.CLOSER, 1, totalFactor = d.live / 1000.0, totalUnit = "kg")
    ))
    StockBlock(d)
    WaterCard(d)
    FeedCharts(d)
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
    // 2 or 3 feedings: the recommended one first, the other a tap away
    var pick by remember(d.day, d.recommendedOption, d.dayBags) { mutableIntStateOf(d.recommendedOption) }
    val o = d.feedOptions[pick]
    val pat = if (o.safe) o.pattern else d.patterns.first()
    val hopper = max(0.0, o.bagsPerFeeding - d.feederLines * pat.openPerLine / d.pansPerBag)
    OutputCard(title = "Feeding plan · ${d.phase}" + (d.nextPhaseDay?.let { " → ${CompanyStandard.feedPhase(it)} day $it" } ?: "")) {
        Step(1, "Required today") {
            ValueRow(listOf(v(d.giveBags, 2, PR), v(d.comPerBird?.let { it * live / 1000 / bag }, 2, C), v(d.idealPerBird * live / 1000 / bag, 2, I)), "bags")
            ValueRow(listOf(v(d.givePerBird, 1, PR), v(d.comPerBird, 1, C), v(d.idealPerBird, 1, I)), "g/bird")
        }
        Step(2, "Day in whole bags") {
            ValueRow(listOf(v(d.dayBags, 2, PR, "Bags"), v(d.extraBags, 2, PR, "Rounded up")), "day")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("feedings", modifier = Modifier.width(56.dp), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
                d.feedOptions.forEachIndexed { i, opt ->
                    val tag = when { !opt.safe -> "Unsafe"; i == d.recommendedOption -> "Best"; else -> "Also safe" }
                    val kind = when { !opt.safe -> MX; i == d.recommendedOption -> P; else -> PR }
                    ValueChip(vi(opt.feedings, kind, tag), Modifier.weight(1f)
                        .border(if (i == pick) 2.dp else 0.dp, if (i == pick) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable { pick = i })
                }
            }
        }
        Step(3, "Each feeding") {
            ValueRow(listOf(v(o.bagsPerFeeding, 2, PR, "Bags"), v(o.bagsPerLine, 2, PR, "Per line"), v(o.kgPerLine, 1, PR, "kg / line")), "pour")
        }
        Step(4, "Pan series") {
            ValueRow(listOf(vt(pat.label, if (o.safe) PR else MX, "Series")), "line")
            ValueRow(listOf(vi(pat.openPerLine, PR, "On"), vi(d.pansInArea, P, "In area"), vi(d.pansPerLine, P, "Feed pans")), "pans")
            ValueRow(listOf(v(o.fillPct, 1, PR, "Filled %"), v(hopper, 2, PR, "Hopper bags")), "reach")
            FarmTopView(d, pat)
            KeyLine(kindColor(PR) to "on", Color.White.copy(alpha = 0.6f) to "off", Color.White to "sensor", kindColor(MN) to "drinker")
        }
        Step(5, "One pan covers") {
            ValueRow(listOf(v(pat.cellFt2, 1, PR, "ft²"), v(pat.cellBirds, 1, PR, "Birds")), "cell")
            ValueRow(listOf(v(pat.birdsPerPan, 1, PR, "Birds / pan"), v(d.birdsPerPanMax, 1, MX, "Max")), "load")
            ValueRow(listOf(v(pat.travelM, 2, PR), v(ALLOWED_TRAVEL_M, 2, MX)), "walk m")
            PanCellView(d, pat)
        }
        Step(6, "Times") {
            ValueRow(d.feedTimesFor(o.feedings).mapIndexed { i, t -> vt(t, PR, "Feed ${i + 1}") }, "time")
        }
    }
}

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
private fun WaterCard(d: OutputData) {
    val e = d.e
    val live = d.liveSafe.toDouble()
    OutputCard(title = "Water") {
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

@Composable
fun MortalitySection(d: OutputData) {
    val e = d.e
    KpiCard("Mortality", listOf(
        Kpi("Till date", "%", d.mortTDPct, P, d.comCumPct, d.ceilingPct, Better.LOWER, 2, points = true, totalFactor = d.entryBirds / 100.0, totalUnit = "birds", scope = "% entry")
    ))
    OutputCard(title = "Birds") {
        val comCumBirds = d.comCumPct * d.entryBirds / 100.0
        Param("Live", "", vi(d.live, P), v(d.entryBirds - comCumBirds, 1, C), strong = true)
        Param("Start", "", vi(d.placed, P, "Placed"), vi(d.reception, P, "Reception"), vi(d.entryBirds, P, "Entry"))
        Param("Deaths", "", listOf(
            "today" to listOf(vi(d.mortToday, P), v(d.comMortBirdsToday, 1, C), v(d.mortTodayPct, 3, P, "%")),
            "till date" to listOf(vi(d.mortTD, P), v(comCumBirds, 1, C), v(d.mortTDPct, 2, P, "%"))
        ))
        Param("Livability", "%", v(e.livability, 2, P), v(100 - d.comCumPct, 2, C))
        Param("Culls · lifted", "", vi(d.lameTD, P, "Culls"), vi(d.liftTD, P, "Lifted"), v(d.liftKgTD, 1, P, "Lifted kg"))
    }
}

// =================================== ENVIRONMENT ===================================

@Composable
fun EnvironmentSection(d: OutputData) {
    val e = d.e
    OutputCard(title = "House air") {
        RangeParam("Temperature", "°C", e.tempMin, e.tempIdeal, e.tempMax, null)
        RangeParam("Humidity", "%", e.rhMin, e.rhIdeal, e.rhMax, null)
    }
    OutputCard(title = "Litter") {
        RangeParam("Temperature", "°C", d.litterTemp.first, d.litterTemp.second, d.litterTemp.third, null)
        RangeParam("Moisture", "%", 20.0, 25.0, 30.0, null)
    }
    OutputCard(title = "Bird") {
        RangeParam("Body (vent)", "°C", d.bodyTemp.first, d.bodyTemp.second, d.bodyTemp.third, null)
        RangeParam("Feet", "°C", d.footTemp.first, d.footTemp.second, d.footTemp.third, null)
    }
    OutputCard(title = "Air quality") {
        RangeParam("CO₂", "ppm", null, null, e.co2Max, e.measuredCo2)
        RangeParam("NH₃", "ppm", null, null, e.nh3Max, e.measuredNh3)
        RangeParam("Static pressure", "Pa", 20.0, null, 25.0, e.measuredPressure)
        RangeParam("Air speed", "ft/min", null, null, PhysiologicalEngine.maxAirSpeedFpm(d.day), e.measuredAirspeed)
    }
    OutputCard(title = "Light") {
        Param("Hours", "", v(e.lightHours, 1, I, "Light"), v(24.0 - e.lightHours, 1, I, "Dark"))
        RangeParam("Intensity", "lux", d.lightLux.first, null, d.lightLux.second, e.luxPerFt2, idealBand = d.lightLux)
    }
    OutputCard(title = "Space") {
        RangeParam("Density", "kg/ft²", null, null, kgPerFt2(d.farm.densityCapDefault), e.densityKgM2?.let { kgPerFt2(it) }, 3, d.vk)
        RangeParam("Floor", "ft²/bird", e.minFtPerBird.takeIf { it > 0 }, null, null, e.ftPerBird.takeIf { it > 0 }, 3, d.vk)
    }
}
