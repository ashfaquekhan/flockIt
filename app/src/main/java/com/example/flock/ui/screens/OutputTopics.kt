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
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
            Text(value.text, style = MaterialTheme.typography.titleMedium.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Bold),
                color = kindColor(if (value.text == "—") ValueKind.NEUTRAL else value.kind), maxLines = 1, softWrap = false)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f), maxLines = 2)
        }
    }
}

// =================================== TABS ===================================

/** Birds: how many, deaths, growth, uniformity, comfort and the curves — everything about the birds in one place. */
/** The few numbers that decide the batch, right under the clock. */
@Composable
fun KeyKpis(d: OutputData, feeder: FeederState) {
    val e = d.e
    val live = d.live.toDouble()
    val bag = d.bagKg
    OutputCard(title = "Today", info = "kpis") {
        KpiLine(Kpi("Body weight", "g", d.bw, d.vk, d.bwCom, d.bwIdeal, Better.HIGHER, 1, totalFactor = live / 1000, totalUnit = "kg"))
        KpiLine(Kpi("FCR", "", e.fcr, d.fcrKind, d.fcrCom, d.fcrIdeal, Better.LOWER, 3, settling = d.day < 7))
        KpiLine(Kpi("Mortality till date", "%", d.mortTDPct, d.mk, d.comCumPct, d.ceilingPct, Better.LOWER, 2,
            totalFactor = d.entryBirds / 100.0, totalUnit = "birds", totalDec = 0))
        KpiLine(Kpi("Feed today", "bags", d.giveBags, PR, d.comPerBird?.let { it * live / 1000 / bag }, d.idealPerBird * live / 1000 / bag, Better.CLOSER, 2))
        ValueRow(listOf(vi(d.live, d.mk, "Live birds"), v(d.dayBags, 2, PR, "Bags to load"), v(e.totalWaterL, 1, PR, "Water L")))
    }
}

/** Birds: how many, deaths, growth, uniformity, comfort and the curves — everything about the birds in one place. */
@Composable
fun BirdsTab(d: OutputData, onFlockPlan: ((Double, Int) -> Unit)? = null) {
    val e = d.e
    val live = d.live.toDouble()
    val comCumBirds = d.comCumPct * d.entryBirds / 100.0
    KpiCard("Flock", listOf(
        Kpi("Live birds", "", live, d.mk, d.entryBirds - comCumBirds, null, Better.HIGHER, 0),
        Kpi("Deaths today", "%", d.mortTodayPct, d.mk, d.comDailyPct, null, Better.LOWER, 3,
            totalFactor = (d.live + d.mortToday) / 100.0, totalUnit = "birds", totalDec = 0),
        Kpi("Mortality till date", "%", d.mortTDPct, d.mk, d.comCumPct, d.ceilingPct, Better.LOWER, 2,
            totalFactor = d.entryBirds / 100.0, totalUnit = "birds", totalDec = 0),
        Kpi("Livability", "%", e.livability, d.mk, 100 - d.comCumPct, 100 - d.ceilingPct, Better.HIGHER, 2)
    ), info = "flock")
    OutputCard(title = "Start and removals") {
        Param("Start", "", vi(d.placed, P, "Placed"), vi(d.reception, P, "Reception"), vi(d.entryBirds, P, "Entry"))
        Param("Removed", "", vi(d.lameTD, P, "Culls"), vi(d.liftTD, P, "Lifted"), v(d.liftKgTD, 1, P, "Lifted kg"))
    }
    KpiCard("Growth", listOf(
        Kpi("Body weight", "g", d.bw, d.vk, d.bwCom, d.bwIdeal, Better.HIGHER, 1, totalFactor = live / 1000, totalUnit = "kg"),
        Kpi("Daily gain", "g/day", d.gain, d.gainKind, d.gainCom, d.gainIdeal, Better.HIGHER, 1, totalFactor = live / 1000, totalUnit = "kg"),
        Kpi("FCR", "", e.fcr, d.fcrKind, d.fcrCom, d.fcrIdeal, Better.LOWER, 3, settling = d.day < 7),
        Kpi("cFCR", "2 kg", e.cFcr, d.fcrKind, d.cfcrCom, d.cfcrIdeal, Better.LOWER, 3, settling = d.day < 7),
        Kpi("EPEF", "", d.epef, d.fcrKind, d.epefCom, d.epefIdeal, Better.HIGHER, 1, settling = d.day < 7)
    ), info = "growth")
    TrendsCard(d)
    ForecastCard(d, onFlockPlan)
    AccuracyCard(d)
    UniformityCard(d)
    OutputCard(title = "Comfort", info = "comfort") {
        RangeParam("Vent temp", "°C", d.bodyTemp.first, d.bodyTemp.second, d.bodyTemp.third, null)
        RangeParam("Feet", "°C", d.footTemp.first, d.footTemp.second, d.footTemp.third, null)
        RangeParam("Density", "kg/ft²", null, null, kgPerFt2(d.farm.densityCapDefault), e.densityKgM2?.let { kgPerFt2(it) }, 3, d.vk)
        RangeParam("Floor", "ft²/bird", e.minFtPerBird.takeIf { it > 0 }, null, null, e.ftPerBird.takeIf { it > 0 }, 3, d.vk)
        Param("Light", "h", v(e.lightHours, 1, I, "Light"), v(24.0 - e.lightHours, 1, I, "Dark"))
        RangeParam("Light intensity", "lux", d.lightLux.first, null, d.lightLux.second, e.luxPerFt2, idealBand = d.lightLux)
    }
    BirdCharts(d)
}

/** Trends worked out from the flock's own weights, feed and deaths. */
@Composable
private fun TrendsCard(d: OutputData) {
    val k = d.kpis
    OutputCard(title = "Flock trends", info = "trends") {
        KpiLine(Kpi("Average daily gain", "g/day", k.adg, d.vk, d.adgCom, d.adgIdeal, Better.HIGHER, 1))
        KpiLine(Kpi("FCR, last 7 days", "", k.fcr7, d.fcr7Kind, d.fcr7Com, d.fcr7Ideal, Better.LOWER, 3))
        // growth in days: + ahead of the standard, − behind it
        Param("Days ahead (+) or behind (−)", "", vt(Fmt.signed(d.daysAheadCom, 1), d.vk, "vs commercial"), vt(Fmt.signed(d.daysAheadIdeal, 1), d.vk, "vs ideal"))
        KpiLine(Kpi("First-week mortality", "%", k.firstWeekMortPct, if (k.firstWeekComplete) P else PR, d.firstWeekMortCom, d.firstWeekMortIdeal, Better.LOWER, 2,
            settling = !k.firstWeekComplete))
        KpiLine(Kpi("Mortality, last 7 days", "%", k.mort7Pct, d.mk, d.mort7Com, null, Better.LOWER, 2))
        KpiLine(Kpi("FCR with losses counted", "", k.adjFcr, d.fcrKind, null, null, Better.LOWER, 3))
    }
}

/** The target the flock is grown to, when the flock's own weighings say it will get there, and what it will weigh at harvest. */
@Composable
private fun ForecastCard(d: OutputData, onFlockPlan: ((Double, Int) -> Unit)?) {
    val g = d.growth
    var edit by remember { androidx.compose.runtime.mutableStateOf(false) }
    val start = remember(d.flock?.startDate) { try { java.time.LocalDate.parse(d.flock?.startDate) } catch (e: Exception) { null } }
    fun dateOf(day: Int?) = if (day == null || start == null) null else start.plusDays(day.toLong()).format(java.time.format.DateTimeFormatter.ofPattern("dd MMM", java.util.Locale.US))
    OutputCard(title = "Target and forecast", info = "forecast") {
        ValueRow(listOf(v(d.targetG, 1, I, "Target g"), vi(d.harvestAge, I, "Harvest"), v(g.share * 100, 1, if (g.samples > 0) P else PR, "% of com")), "plan")
        // when the target weight is reached: the likely day, with the earliest and the latest it may be
        ValueRow(listOf(
            vt(g.targetDay?.let { "day $it" } ?: "—", PR, dateOf(g.targetDay) ?: "Likely"),
            vt(g.targetDayEarly?.let { "day $it" } ?: "—", MN, "Earliest"),
            vt(g.targetDayLate?.let { "day $it" } ?: "—", MX, "Latest")), "target")
        g.atHarvest?.let { h ->
            ValueRow(listOf(v(h.mean, 1, PR, "Weight g"), v(h.low, 1, MN, "Low"), v(h.high, 1, MX, "High")), "harvest")
            ValueRow(listOf(v((g.chanceTargetAtHarvest ?: 0.0) * 100, 1, PR, "Target chance %"), vi(g.samples, P, "Weighings used")), "chance")
        }
        if (onFlockPlan != null) androidx.compose.material3.OutlinedButton(onClick = { edit = true },
            border = androidx.compose.foundation.BorderStroke(1.dp, GlassLine), modifier = Modifier.fillMaxWidth()) {
            Text("Set target weight and harvest day", color = Color.White)
        }
    }
    if (edit && onFlockPlan != null) {
        var w by remember { androidx.compose.runtime.mutableStateOf(Fmt.n(d.targetG, 0).replace(",", "")) }
        var h by remember { androidx.compose.runtime.mutableStateOf(d.harvestAge.toString()) }
        val wv = w.replace(",", "").toDoubleOrNull(); val hv = h.toIntOrNull()
        val ok = wv != null && wv in 500.0..6000.0 && hv != null && hv in 14..PhysiologicalEngine.MAX_FLOCK_DAY
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { edit = false },
            title = { Text("Target for this flock") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.OutlinedTextField(value = w, onValueChange = { w = it.filter { c -> c.isDigit() || c == '.' } }, singleLine = true,
                        label = { Text("Target weight, g (500 – 6,000)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                    androidx.compose.material3.OutlinedTextField(value = h, onValueChange = { h = it.filter { c -> c.isDigit() } }, singleLine = true,
                        label = { Text("Planned harvest day (14 – ${PhysiologicalEngine.MAX_FLOCK_DAY})") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                    Text("The flock can run past the harvest day: the day bar grows by a day at a time, up to day ${PhysiologicalEngine.MAX_FLOCK_DAY}.",
                        style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(enabled = ok, onClick = { onFlockPlan(wv!!, hv!!); edit = false }) { Text("Save") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { edit = false }) { Text("Cancel") } }
        )
    }
}

/** How close the app's projections were to what was then entered: the latest check and the average miss. */
@Composable
private fun AccuracyCard(d: OutputData) {
    data class Line(val name: String, val acc: com.example.flock.domain.FlockKpis.Accuracy, val dec: Int)
    val lines = listOf(Line("Weight g", d.weightAccuracy.upTo(d.day), 1), Line("Feed, bags", d.feedAccuracy.upTo(d.day), 2), Line("FCR", d.fcrAccuracy.upTo(d.day), 3))
    fun offColor(pct: Double?) = when { pct == null -> Color.White.copy(alpha = 0.5f); abs(pct) <= 3 -> TrendGood; abs(pct) <= 8 -> TrendAverage; else -> TrendBad }
    val mono = MaterialTheme.typography.labelLarge.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
    val grey = Color.White.copy(alpha = 0.6f)
    OutputCard(title = "Projection check", info = "accuracy") {
        Column(Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1.3f))
                listOf("Projected" to 1.25f, "Actual" to 1.0f, "Off %" to 0.85f, "Avg %" to 0.8f).forEach { (t, w) ->
                    Text(t, Modifier.weight(w), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = grey, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
                }
            }
            lines.forEach { l ->
                val c = l.acc.on(d.day) ?: l.acc.latest
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1.3f)) {
                        Text(l.name, style = MaterialTheme.typography.labelMedium, color = grey, maxLines = 1, softWrap = false)
                        Text(c?.let { "day ${it.day}" } ?: "no check yet", style = MaterialTheme.typography.labelSmall.copy(fontFamily = com.example.ui.theme.NumberFont), color = grey.copy(alpha = 0.45f), maxLines = 1, softWrap = false)
                    }
                    Text(c?.let { Fmt.n(it.projected, l.dec) } ?: "—", Modifier.weight(1.25f), style = mono, color = if (c == null) grey else kindColor(PR), textAlign = TextAlign.End, maxLines = 1, softWrap = false)
                    Text(c?.let { Fmt.n(it.actual, l.dec) } ?: "—", Modifier.weight(1.0f), style = mono, color = if (c == null) grey else kindColor(P), textAlign = TextAlign.End, maxLines = 1, softWrap = false)
                    Text(c?.let { Fmt.signed(it.errorPct, 1) } ?: "—", Modifier.weight(0.85f), style = mono, color = offColor(c?.errorPct), textAlign = TextAlign.End, maxLines = 1, softWrap = false)
                    Text(l.acc.averageMissPct?.let { Fmt.n(it, 1) } ?: "—", Modifier.weight(0.8f), style = mono, color = offColor(l.acc.averageMissPct), textAlign = TextAlign.End, maxLines = 1, softWrap = false)
                }
            }
        }
        Text("Off %: + the app projected too high, − too low. Avg %: the average miss over ${lines.maxOf { it.acc.checks.size }} checks so far.",
            style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
    }
}

/**
 * Uniformity: CV and the share of even birds, with the weight distribution — from birds weighed one by
 * one when there are 10 or more, otherwise the spread to expect at a typical CV (marked as not measured).
 */
@Composable
private fun UniformityCard(d: OutputData) {
    val e = d.e
    val singles = PhysiologicalEngine.parseWeights(e.indivWeights)
    val measured = singles.size >= PhysiologicalEngine.MIN_BIRDS_FOR_CV
    val mean = if (measured) singles.average() else d.bw
    val cvFrac = ((e.cv ?: 8.0) / 100.0).coerceAtLeast(0.01)
    // share of birds in 5 % steps from −25 % to +25 % of the average
    val edges = (-5..5).map { it * 0.05 }
    val bins: List<Double> = if (measured) {
        val c = DoubleArray(10)
        singles.forEach { w -> val dev = (w - mean) / mean; val i = ((dev + 0.25) / 0.05).toInt().coerceIn(0, 9); c[i] += 1.0 }
        c.map { it / singles.size }
    } else (0 until 10).map { i ->
        val lo = if (i == 0) -10.0 else edges[i] / cvFrac; val hi = if (i == 9) 10.0 else edges[i + 1] / cvFrac
        normCdf(hi) - normCdf(lo)
    }
    val light = if (measured) singles.count { it < mean * 0.9 } * 100.0 / singles.size else normCdf(-0.1 / cvFrac) * 100
    val heavy = if (measured) singles.count { it > mean * 1.1 } * 100.0 / singles.size else (1 - normCdf(0.1 / cvFrac)) * 100
    val even = 100 - light - heavy
    OutputCard(title = "Uniformity", info = "uniformity") {
        RangeParam("CV", "%", null, 8.0, 10.0, e.cv, 2)
        RangeParam("Even birds", "%", 80.0, null, null, e.uniformityPct ?: (if (measured) even else null), 1)
        Text(if (measured) "Weight spread of the ${singles.size} birds weighed one by one, share of birds in each 5 % step"
             else "Expected weight spread at CV ${Fmt.n(cvFrac * 100, 1)} % — weigh 10 or more birds one by one to see the real one",
            style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.65f))
        WeightHistogram(bins, mean, measured)
        ValueRow(listOf(v(light, 1, if (measured) P else PR, "Light"), v(even, 1, if (measured) P else PR, "Even"),
            v(heavy, 1, if (measured) P else PR, "Heavy")), "birds %")
        Text("Light: more than 10 % under the average · Even: within ±10 % · Heavy: more than 10 % over",
            style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
        if (measured) ValueRow(listOf(v(singles.min(), 1, P, "Lightest"), v(mean, 1, P, "Average"), v(singles.max(), 1, P, "Heaviest")), "weight g")
        else ValueRow(listOf(vi(singles.size, P, "Weighed"), vi(PhysiologicalEngine.MIN_BIRDS_FOR_CV, MN, "Needed")), "singles")
        if (e.locSpreadPct != null) Param("Spread between locations", "%", v(e.locSpreadPct, 2, P, "Bulk weighing"))
    }
}

private fun normCdf(z: Double): Double {
    // Abramowitz & Stegun 7.1.26
    val t = 1 / (1 + 0.3275911 * kotlin.math.abs(z) / Math.sqrt(2.0))
    val y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-z * z / 2)
    return if (z >= 0) (1 + y) / 2 else (1 - y) / 2
}

/** Bars of the share of birds in each 5 % step around the average; the even band (±10 %) stands out. */
@Composable
private fun WeightHistogram(bins: List<Double>, mean: Double, measured: Boolean) {
    val top = bins.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val evenCol = if (measured) ValuePresent else ValueIdeal
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(130.dp)) {
        val px = density
        val padB = 30f * px; val padT = 16f * px
        val bw = size.width / bins.size
        val paint = android.graphics.Paint().apply { isAntiAlias = true; textSize = 10.5f * px; typeface = com.example.ui.theme.AppFonts.mono; textAlign = android.graphics.Paint.Align.CENTER }
        bins.forEachIndexed { i, f ->
            val h = ((f / top) * (size.height - padB - padT)).toFloat()
            val isEven = i in 3..6
            drawRect((if (isEven) evenCol else Color.White.copy(alpha = 0.4f)).copy(alpha = if (measured) 1f else 0.7f),
                androidx.compose.ui.geometry.Offset(i * bw + 2f * px, size.height - padB - h), androidx.compose.ui.geometry.Size(bw - 4f * px, h))
            if (f >= 0.005) { paint.color = Color.White.copy(alpha = 0.75f).toArgb(); drawContext.canvas.nativeCanvas.drawText(Fmt.n(f * 100, 0), i * bw + bw / 2, size.height - padB - h - 3f * px, paint) }
        }
        // the average and ±10 % marks, with weights under them
        paint.color = Color.White.copy(alpha = 0.6f).toArgb()
        listOf(-0.2, -0.1, 0.0, 0.1, 0.2).forEach { dev ->
            val x = ((dev + 0.25) / 0.5 * size.width).toFloat()
            drawLine(Color.White.copy(alpha = if (dev == 0.0) 0.8f else 0.3f), androidx.compose.ui.geometry.Offset(x, padT), androidx.compose.ui.geometry.Offset(x, size.height - padB), 1f * px)
            drawContext.canvas.nativeCanvas.drawText((if (dev > 0) "+" else "") + Fmt.n(dev * 100, 0) + "%", x, size.height - padB + 12f * px, paint)
            drawContext.canvas.nativeCanvas.drawText(Fmt.n(mean * (1 + dev), 0), x, size.height - padB + 25f * px, paint)
        }
    }
}

/** Ventilation: the house air and litter it controls, then the fan plan. */
@Composable
fun VentTab(d: OutputData) {
    val e = d.e
    OutputCard(title = "House air", info = "air") {
        RangeParam("Temperature", "°C", e.tempMin, e.tempIdeal, e.tempMax, null)
        RangeParam("Humidity", "%", e.rhMin, e.rhIdeal, e.rhMax, null)
        RangeParam("Air speed", "ft/min", null, null, PhysiologicalEngine.maxAirSpeedFpm(d.day), e.measuredAirspeed)
        RangeParam("Static pressure", "Pa", 20.0, null, 25.0, e.measuredPressure)
        RangeParam("CO₂", "ppm", null, null, e.co2Max, e.measuredCo2)
        RangeParam("NH₃", "ppm", null, null, e.nh3Max, e.measuredNh3)
    }
    OutputCard(title = "Litter", info = "litter") {
        RangeParam("Temperature", "°C", d.litterTemp.first, d.litterTemp.second, d.litterTemp.third, null)
        RangeParam("Moisture", "%", 20.0, 25.0, 30.0, null)
    }
    VentSection(d)
}

/** Feed and water: log a feeding, today's plan, what was eaten, and water. */
@Composable
fun FeedTab(d: OutputData, pick: Int, onPick: (Int) -> Unit, onFarmChange: ((com.example.flock.data.FarmEntity) -> Unit)? = null,
            feedLog: @Composable () -> Unit = {}) {
    feedLog()
    FeedingPlanCard(d, pick, onPick)
    if (d.day <= 7) FirstWeekCard(d)
    EatenCard(d)
    FeedCharts(d)
    WaterCard(d, onFarmChange)
}

/** Boxes joined by arrows, two to a row: the plan read left to right. */
@Composable
private fun PlanFlow(steps: List<Triple<String, V, String?>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        steps.chunked(2).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (r > 0) Text("→ ", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.5f))
                row.forEachIndexed { i, (label, value, sub) ->
                    if (i > 0) Text(" → ", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.5f))
                    Column(Modifier.weight(1f).border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 5.dp)) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
                        Text(value.text, style = (if (value.text.length > 7) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium)
                                .copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Bold),
                            color = kindColor(value.kind), maxLines = 1, softWrap = false)
                        if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall.copy(fontFamily = com.example.ui.theme.NumberFont), color = Color.White.copy(alpha = 0.6f), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedingPlanCard(d: OutputData, pick: Int, onPick: (Int) -> Unit) {
    val live = d.live.toDouble()
    val bag = d.bagKg
    val o = d.feedOptions[pick.coerceIn(0, d.feedOptions.size - 1)]
    val pat = if (o.safe) o.pattern else d.patterns.first()
    val hopper = max(0.0, o.bagsPerFeeding - d.feederLines * pat.openPerLine / d.pansPerBag)
    val lines = d.feederLines
    OutputCard(title = "Feeding plan", info = "plan") {
        // total bags → times a day → each time and per line → the pans on and off
        PlanFlow(listOf(
            Triple("Bags today", v(d.dayBags, 2, PR), "need ${Fmt.n(d.giveBags, 2)}"),
            Triple("Times a day", vi(o.feedings, if (o.safe) PR else MX), if (pick == d.recommendedOption) "best" else "chosen"),
            Triple("Each time", v(o.bagsPerFeeding, 2, PR), "${Fmt.n(o.bagsPerLine, 2)} / line"),
            Triple("Pans", vt(pat.label, if (o.safe) PR else MX), "${pat.openPerLine} of ${d.pansInArea} on")
        ))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            LeadLabel("times")
            d.feedOptions.forEachIndexed { i, opt ->
                val tag = when { !opt.safe -> "Not safe"; i == d.recommendedOption -> "Best"; else -> "Also OK" }
                val kind = when { !opt.safe -> MX; i == d.recommendedOption -> P; else -> PR }
                ValueChip(vi(opt.feedings, kind, tag), Modifier.weight(1f)
                    .border(if (i == pick) 2.dp else 0.dp, if (i == pick) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onPick(i) })
            }
        }
        KpiLine(Kpi("Feed needed", "bags", d.giveBags, PR, d.comPerBird?.let { it * live / 1000 / bag }, d.idealPerBird * live / 1000 / bag, Better.CLOSER, 2))
        KpiLine(Kpi("Feed per bird", "g", d.givePerBird, PR, d.comPerBird, d.idealPerBird, Better.CLOSER, 1))
        ValueRow(listOf(v(d.dayBags, 2, PR, "Full bags"), v(d.extraBags, 2, PR, "Rounding up")), "give")
        IntakeSuggestion(d)
        // weight / FCR correction (small, conditional) — the ⓘ says when and why
        val c = d.correction
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                val why = when (c.why) {
                    com.example.flock.domain.FeedCorrection.Why.UNDER_WEIGHT -> "Under weight"
                    com.example.flock.domain.FeedCorrection.Why.HIGH_FCR -> "High FCR"
                    com.example.flock.domain.FeedCorrection.Why.FIRST_WEEK -> "Week 1: none"
                    com.example.flock.domain.FeedCorrection.Why.NO_STANDARD -> "No sample"
                    else -> "On track"
                }
                val first = vt((if (c.pct > 0) "+" else "") + Fmt.n(c.pct, 1) + " %", if (c.pct == 0.0) P else PR, why)
                ValueRow(listOf(first, v(d.correctedBags, 2, PR, "Bags with it")), "correct")
            }
            Spacer(Modifier.width(6.dp))
            InfoButton("correction")
        }
        ValueRow(listOf(v(o.fillPct, 1, PR, "Pans filled %"), v(hopper, 2, PR, "Hopper bags")), "fill")
        // macro and micro: the whole house next to one line and one pan, per feeding
        Text("Each feeding: house, one line, one pan", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.65f))
        MacroMicroTable(listOf(
            MMRow("Bags", v(o.bagsPerFeeding, 2, PR), v(o.bagsPerLine, 2, PR), null),
            MMRow("kg", v(o.bagsPerFeeding * bag, 1, PR), v(o.kgPerLine, 1, PR), v(if (pat.openPerLine > 0) o.kgPerLine / pat.openPerLine else null, 2, PR)),
            MMRow("Pans on", vi(pat.openPerLine * lines, PR), vi(pat.openPerLine, PR), null),
            MMRow("Birds", vi(d.live, d.mk), v(live / lines, 1, d.mk), v(pat.birdsPerPan, 1, if (pat.birdsPerPan <= d.birdsPerPanMax) PR else MX)),
            MMRow("Floor ft²", v(d.areaInUseFt2, 1, P), v(d.areaInUseFt2 / lines, 1, P), v(pat.cellFt2, 1, PR)),
            MMRow("Travel m", null, null, v(pat.travelM, 2, if (pat.travelM <= ALLOWED_TRAVEL_M) PR else MX))
        ))
        ValueRow(listOf(v(d.birdsPerPanMax, 1, MX, "Max birds / pan"), v(ALLOWED_TRAVEL_M, 2, MX, "Max travel m")), "limits")
        FarmTopView(d, pat)
        KeyLine(kindColor(PR) to "on", Color.White.copy(alpha = 0.6f) to "off", Color.White to "sensor", kindColor(MN) to "drinker")
        PanCellView(d, pat)
    }
}

/**
 * More or fewer bags than the plan? What this flock is likely to eat today, learned from what it has eaten
 * so far (its appetite against the plan, tracked day by day), with the range it will most likely fall in and
 * the chance each whole-bag amount is enough.
 */
@Composable
private fun IntakeSuggestion(d: OutputData) {
    val f = d.intake
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("From this flock's own feed record", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
        InfoButton("suggest")
    }
    if (f.days < 3) {
        ValueRow(listOf(vi(f.days, P, "Days of feed entered"), vi(3, MN, "Needed")), "learn")
        return
    }
    val a = d.intakeAdvice
    ValueRow(listOf(v(a.forecastBags, 2, PR, "Likely"), v(a.lowBags, 2, MN, "Low"), v(a.highBags, 2, MX, "High")), "eat")
    // each whole-bag amount near the plan: the chance it is enough for the day
    val near = a.choices.filter { abs(it.bags - d.dayBags) <= 1.01 }.ifEmpty { a.choices.take(3) }
    // "enough" = the chance that many whole bags cover the day
    ValueRow(near.map { c -> vt(Fmt.n(c.chanceEnough * 100, 1) + " %", if (c.chanceEnough >= 0.8) P else if (c.chanceEnough >= 0.5) PR else MX, Fmt.i(c.bags.toInt()) + " bags") }, "enough")
    ValueRow(listOf(v(a.safeFrom, 2, MN, "From bags"), v(a.safeTo, 2, MX, "To bags")), "safe")
    ValueRow(listOf(vt(when (a.direction) { 1 -> "give more: " + Fmt.n(a.suggestedBags, 2) + " bags"; -1 -> "can give less: " + Fmt.n(a.suggestedBags, 2) + " bags"; else -> "keep the plan: " + Fmt.n(d.dayBags, 2) + " bags" },
        if (a.direction == 0) P else PR, "Suggestion")), "advice")
    ValueRow(listOf(v(f.ratio * 100, 1, P, "Appetite %"), v(f.pastErrorPct, 1, PR, "Avg miss %"), vi(f.days, P, "Days used")), "flock")
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

/** Feed used: per bird (yesterday and till date, against the standards) and bags till date by type. */
@Composable
private fun EatenCard(d: OutputData) {
    val yLive = (d.byDay[d.day - 1]?.liveBirds ?: d.live).toDouble()
    val order = d.feedTypes.sortedBy { it.sortOrder }.map { it.code }
    val codes = (order + d.usedByCode.keys.sorted()).distinct().filter { (d.usedByCode[it] ?: 0.0) > 0 }
    OutputCard(title = "Eaten", info = "eaten") {
        KpiLine(Kpi("Yesterday", "g/bird", d.usedPerBirdY, P, d.comPerBirdY, d.idealPerBirdY, Better.CLOSER, 1, totalFactor = yLive / 1000, totalUnit = "kg"))
        KpiLine(Kpi("Till date", "g/bird", d.cumPerBird, d.fk, d.cumPerBirdCom, d.cumPerBirdIdeal, Better.CLOSER, 1, totalFactor = d.live / 1000.0, totalUnit = "kg"))
        Text("Used till date, by feed type", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.65f))
        ValueRow(codes.map { v(d.usedByCode[it], 2, P, it) } + v(d.usedBagsTD, 2, P, "Total"), "bags")
        ValueRow(codes.map { v((d.usedByCode[it] ?: 0.0) * d.kgPerBag(it), 1, P, it) } + v(d.usedKgTD, 1, P, "Total"), "kg")
    }
}

/** A small tank with the water level the day needs, and "× n" for the refills. */
@Composable
private fun TankPicture(tankL: Double, refills: Int, needL: Double) {
    val holds = tankL * refills
    val frac = if (holds > 0) (needL / holds).coerceIn(0.05, 1.0) else 0.0
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.foundation.Canvas(Modifier.size(width = 54.dp, height = 66.dp)) {
            val w = size.width; val h = size.height; val ry = h * 0.09f
            val water = kindColor(MN)
            // water inside (filled to the share of the day's refills it needs)
            val top = ry + (h - 2 * ry) * (1 - frac).toFloat()
            drawRect(water.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset(2f, top), androidx.compose.ui.geometry.Size(w - 4f, h - ry - top))
            drawOval(water.copy(alpha = 0.6f), androidx.compose.ui.geometry.Offset(2f, top - ry), androidx.compose.ui.geometry.Size(w - 4f, ry * 2))
            // tank outline
            val line = Color.White.copy(alpha = 0.75f)
            drawOval(line, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Size(w, ry * 2), style = androidx.compose.ui.graphics.drawscope.Stroke(1.6f * density))
            drawLine(line, androidx.compose.ui.geometry.Offset(0f, ry), androidx.compose.ui.geometry.Offset(0f, h - ry), 1.6f * density)
            drawLine(line, androidx.compose.ui.geometry.Offset(w, ry), androidx.compose.ui.geometry.Offset(w, h - ry), 1.6f * density)
            drawArc(line, 0f, 180f, false, androidx.compose.ui.geometry.Offset(0f, h - 2 * ry), androidx.compose.ui.geometry.Size(w, ry * 2), style = androidx.compose.ui.graphics.drawscope.Stroke(1.6f * density))
        }
        Column {
            Text("× $refills", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Bold), color = kindColor(MN))
            Text("refills of ${Fmt.n(tankL, 1)} L", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.65f))
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.n(needL, 1) + " L", style = MaterialTheme.typography.titleMedium.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Bold), color = kindColor(PR))
            Text("needed of ${Fmt.n(holds, 1)} L", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.65f))
        }
    }
}

@Composable
private fun WaterCard(d: OutputData, onFarmChange: ((com.example.flock.data.FarmEntity) -> Unit)? = null) {
    val e = d.e
    val live = d.liveSafe.toDouble()
    val factor = if (d.refillF > 0) d.refillF else 1.0
    val refills = d.waterRefills
    OutputCard(title = "Water", info = "water") {
        TankPicture(d.tankL, refills, e.totalWaterL)
        ValueRow(listOf(v(d.tankL, 1, P, "Tank L"), v(d.waterTanksNeeded, 1, P, "Fills needed"), v(e.totalWaterL / refills, 1, PR, "L each")), "tank")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            LeadLabel("refill ×")
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
        Param("Nipple line", "", v(e.drinkerPressureIn.takeIf { it > 0 }, 2, I, "Pressure in"), v(d.drinkerHtIn, 1, I, "Height in"),
            v(e.drinkerFlowLHrLine.takeIf { it > 0 }, 1, I, "L/h per line"))
        if (d.birdsPerNipple != null) RangeParam("Birds / nipple", "", null, null, d.birdsPerNippleMax, d.birdsPerNipple, 1, PR)
        RangeParam("Nipple flow", "mL/min", d.nippleFlow.first, null, d.nippleFlow.second, null, idealBand = d.nippleFlow)
        RangeParam("Water temp", "°C", 18.0, null, 21.0, e.waterTempC, idealBand = 18.0 to 21.0)
        RangeParam("Water pH", "", 6.0, null, 6.8, e.waterPh, 2, idealBand = 6.0 to 6.8)
    }
}

@Composable
fun StockBlock(d: OutputData) {
    val f = d.farm
    OutputCard(title = "Feed store", info = "stock") {
        if (f.godownBags > 0) {
            FillBar(d.stockBagsTotal / f.godownBags, Color.White.copy(alpha = 0.7f))
            Param("Godown", "bags", v(d.stockBagsTotal, 2, P, "In store"), v(f.godownBags, 1, MX, "Holds"), v(d.godownFree, 2, PR, "Free"))
        }
        val maxBags = d.stockCodes.maxOfOrNull { max(d.recByCode[it] ?: 0.0, d.stockBags(it)) } ?: 0.0
        val unit = listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0).firstOrNull { maxBags / it <= 20 } ?: 200.0
        d.stockCodes.forEach { code -> FeedTypeBlock(d, code, unit) }
        Param("All types", "bags", v(d.recBagsTD, 2, P, "Received"), v(d.usedBagsTD, 2, P, "Used"), v(d.stockBagsTotal, 2, P, "In store"), strong = true)
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
    Column(Modifier.fillMaxWidth().border(1.dp, GlassLine.copy(alpha = 0.35f), RoundedCornerShape(10.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$code · ${ft?.name ?: "Feed"}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            Text(Fmt.n(stock, 2), style = MaterialTheme.typography.titleMedium.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Black),
                color = if (stock < 0) StatusCrit else kindColor(P))
        }
        if (rec > 0 || used > 0) SackStrip(max(0.0, stock) / unit, kotlin.math.min(used, rec) / unit, Color.White.copy(alpha = 0.8f))
        ValueRow(listOf(v(rec, 2, P, "Received"), v(used, 2, P, "Used"), v(stock, 2, if (stock < 0) MX else P, "In store")))
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
    OutputCard(title = "Minimum ventilation", info = "minvent") {
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
    OutputCard(title = "Fan finder", info = "fanfinder") {
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
    OutputCard(title = "Fans by outside air", info = "grid") {
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
                        Text(Fmt.n(s.fans, 1), style = MaterialTheme.typography.titleMedium.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Black), color = col)
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
    OutputCard(title = "Controller · day ${d.day}", info = "controller") {
        Param("Temperature", "°C", v(d.plan.set, 1, I, "SET"), v(d.plan.heat, 1, I, "Heat on"), v(d.plan.comfort, 1, I, "Comfort"), strong = true)
        Param("Limits", "", vi(d.plan.maxFans, MX, "Fans"), v(PhysiologicalEngine.maxAirSpeedFpm(d.day), 1, MX, "ft/min"), vi(d.plan.minLevel, I, "Min level"))
        Param("Fan", "cfm", v(d.fanCfm, 1, I, "One"), v(d.allFansCfm, 1, I, "All"))
    }
}

// =================================== MORTALITY ===================================


// =================================== ENVIRONMENT ===================================

