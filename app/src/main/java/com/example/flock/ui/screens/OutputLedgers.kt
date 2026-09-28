package com.example.flock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FarmEntity
import com.example.flock.data.FeedTypeEntity
import com.example.flock.data.FlockEntity
import com.example.flock.data.parseFeedBreakdown
import com.example.flock.engine.CompanyStandard
import com.example.flock.engine.IbController
import com.example.flock.engine.PhysiologicalEngine
import com.example.flock.network.HourPoint
import com.example.flock.network.WeatherResult
import com.example.flock.ui.Fmt
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max

// =====================================================================================
// Ledger table: one consistent layout for every topic — a label, then aligned columns
// (e.g. Today · Till date · Plan). Values keep their measured / predicted / ideal colour.
// =====================================================================================

data class LCell(val text: String, val kind: ValueKind = ValueKind.NEUTRAL)
data class LRow(val label: String, val unit: String, val cells: List<LCell>, val strong: Boolean = false)

private fun c(text: String, kind: ValueKind = ValueKind.NEUTRAL) = LCell(text, kind)
private val DASH = LCell("—")

@Composable
fun LedgerTable(headers: List<String>, rows: List<LRow>) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Spacer(Modifier.weight(1.45f))
            headers.forEach {
                Text(
                    it.uppercase(), modifier = Modifier.weight(1f), textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.Bold),
                    color = muted, maxLines = 1
                )
            }
        }
        rows.forEachIndexed { idx, r ->
            if (idx > 0) Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1.45f)) {
                    Text(
                        r.label,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (r.strong) FontWeight.Bold else FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface, maxLines = 2
                    )
                    if (r.unit.isNotEmpty()) Text(r.unit, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = muted)
                }
                r.cells.forEach { cell ->
                    Text(
                        cell.text, modifier = Modifier.weight(1f), textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (r.strong) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = if (cell.text == "—") kindColor(ValueKind.NEUTRAL)
                                else if (cell.kind == ValueKind.NEUTRAL) MaterialTheme.colorScheme.onSurface else kindColor(cell.kind),
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ------------------------------- shared day maths -------------------------------

/** Integrator's daily mortality standard (% of live birds per day). */
fun stdDailyMortPct(day: Int): Double = CompanyStandard.dailyMortPct(day)
fun stdCumMortPct(day: Int): Double = CompanyStandard.cumMortPct(day)

private fun bagKgOf(code: String, feedTypes: List<FeedTypeEntity>, farm: FarmEntity) =
    feedTypes.firstOrNull { it.code == code }?.bagKg ?: farm.feedBagKg

/** Feed used logged on a day row, per type code → bags. */
fun usedByType(r: DailyDataEntity): Map<String, Double> {
    val b = parseFeedBreakdown(r.feedUsedBreakdown)
    if (b.isNotEmpty()) return b.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
    return if (r.feedBagsUsed > 0) mapOf(r.feedUsedType.ifBlank { "B1" } to r.feedBagsUsed) else emptyMap()
}

fun receivedByType(r: DailyDataEntity): Map<String, Double> {
    val m = HashMap<String, Double>()
    fun add(code: String, bags: Double) { if (bags > 0 && code.isNotBlank()) m[code] = (m[code] ?: 0.0) + bags }
    add(r.feedTypeB1, r.feedRecB1); add(r.feedTypeB2, r.feedRecB2); add(r.feedTypeB3, r.feedRecB3)
    return m
}

// ================================== BIRDS ==================================

@Composable
fun BirdsLedgerCard(entry: DailyDataEntity, flock: FlockEntity?, dailyRows: List<DailyDataEntity>) {
    val day = entry.dayNumber
    val upto = dailyRows.filter { it.dayNumber <= day }
    val placed = flock?.birdsPlaced ?: 0
    val reception = flock?.receptionMort ?: 0
    val mortTD = upto.sumOf { it.mortality }
    val lameTD = upto.sumOf { it.lameSeparated }
    val liftTD = upto.sumOf { it.birdsLifted }
    val liftKgTD = upto.sumOf { it.weightLifted }
    val live = entry.liveBirds
    val todayPct = if (live + entry.mortality > 0) entry.mortality * 100.0 / (live + entry.mortality) else null
    val stdToday = stdDailyMortPct(day) * (live + entry.mortality) / 100.0
    val cumPct = if (placed > 0) (mortTD + reception) * 100.0 / placed else null
    val stdCum = stdCumMortPct(day)
    val P = ValueKind.PRESENT; val I = ValueKind.IDEAL
    OutputCard(title = "Birds & mortality · Day $day") {
        LedgerTable(
            headers = listOf("Today", "Till date", "Standard"),
            rows = listOf(
                LRow("Birds placed", "head", listOf(DASH, c(Fmt.i(placed), P), DASH)),
                LRow("Reception / transit dead", "head", listOf(DASH, c(Fmt.i(reception), P), DASH)),
                LRow("Mortality", "birds", listOf(c(Fmt.i(entry.mortality), P), c(Fmt.i(mortTD + reception), P), c(Fmt.n(stdToday, 1) + " /day", I))),
                LRow("Mortality %", "of birds", listOf(c(Fmt.pct(todayPct, 3), P), c(Fmt.pct(cumPct, 2), P), c(Fmt.pct(stdDailyMortPct(day), 2) + " · " + Fmt.pct(stdCum, 2), I))),
                LRow("Culls / lame", "birds", listOf(c(Fmt.i(entry.lameSeparated), P), c(Fmt.i(lameTD), P), DASH)),
                LRow("Lifted", "birds", listOf(c(Fmt.i(entry.birdsLifted), P), c(Fmt.i(liftTD), P), DASH)),
                LRow("Lifted weight", "kg", listOf(c(Fmt.n(entry.weightLifted, 2), P), c(Fmt.n(liftKgTD, 2), P), DASH)),
                LRow("Live birds now", "head", listOf(c(Fmt.i(live), P), DASH, DASH), strong = true),
                LRow("Livability", "%", listOf(DASH, c(Fmt.pct(entry.livability, 2), P), c(Fmt.pct(100 - stdCum, 2), I)))
            )
        )
        Note("Standard = company daily mortality: 0.15 % to day 12, 0.10 % days 13–28, 0.15 % after. Till-date mortality includes reception deaths.")
    }
}

@Composable
fun WeightLedgerCard(entry: DailyDataEntity, dailyRows: List<DailyDataEntity>, breed: String, vk: ValueKind) {
    val day = entry.dayNumber
    val now = entry.avgWeight ?: PhysiologicalEngine.bwFromDay(entry.weightAge, breed)
    val ross = PhysiologicalEngine.bwFromDay(day.toDouble(), breed)
    val rossGain = ross - PhysiologicalEngine.bwFromDay(max(0, day - 1).toDouble(), breed)
    val prev = dailyRows.filter { it.dayNumber < day && it.avgWeight != null }.maxByOrNull { it.dayNumber }
    val actualGain = if (entry.avgWeight != null && prev?.avgWeight != null)
        (entry.avgWeight - prev.avgWeight) / (day - prev.dayNumber) else null
    val live = entry.liveBirds
    val rossFcr = PhysiologicalEngine.stdFcrFromDay(day.toDouble(), breed)
    val rossCfcr = PhysiologicalEngine.computeCorrectedFcr(ross / 1000.0, rossFcr)
    val w7 = dailyRows.firstOrNull { it.dayNumber == 7 }?.avgWeight
    val w0 = dailyRows.firstOrNull { it.dayNumber == 0 }?.avgWeight ?: PhysiologicalEngine.bwFromDay(0.0, breed)
    val epef = if (day >= 7 && entry.fcr != null && entry.fcr > 0 && entry.livability != null)
        entry.livability * (now / 1000.0) * 100.0 / (day * entry.fcr) else null
    val coBw = CompanyStandard.bw(day)
    val I = ValueKind.IDEAL; val P = ValueKind.PRESENT
    fun co(v: Double?, dec: Int) = LCell(Fmt.n(v, dec), ValueKind.NEUTRAL)
    OutputCard(title = "Weight & conversion") {
        LedgerTable(
            headers = listOf("Now", "Company", "Ross"),
            rows = listOfNotNull(
                LRow("Average weight", if (entry.avgWeight != null) "g · sampled" else "g · projected",
                    listOf(c(Fmt.n(now, 1), vk), co(coBw, 0), c(Fmt.n(ross, 1), I)), strong = true),
                LRow("vs Company", "difference", listOf(c(coBw?.let { Fmt.signed((now - it) / it * 100, 1) + "%" } ?: "—", vk), DASH, DASH)),
                LRow("Weight-age", "days", listOf(c(Fmt.n(entry.weightAge, 2), vk), DASH, c(Fmt.i(day), I))),
                LRow("Daily gain", if (actualGain != null) "g/day · between samples" else "g/day · curve",
                    listOf(c(Fmt.n(actualGain ?: entry.gainPerBird, 1), if (actualGain != null) P else ValueKind.PREDICTED), co(CompanyStandard.gain(day), 0), c(Fmt.n(rossGain, 1), I))),
                LRow("Uniformity CV", "%", listOf(c(Fmt.n(entry.cv, 2), P), DASH, c("< 10", I))),
                LRow("Total live weight", "kg in shed", listOf(c(Fmt.n(live * now / 1000.0, 1), vk), co(coBw?.let { live * it / 1000.0 }, 1), c(Fmt.n(live * ross / 1000.0, 1), I))),
                LRow("FCR", "kg feed / kg bird", listOf(c(Fmt.n(entry.fcr, 3), P), co(CompanyStandard.fcr(day), 2), c(Fmt.n(rossFcr, 3), I))),
                LRow("cFCR → 2 kg", "(2 − kg) × 0.25 + FCR", listOf(c(Fmt.n(entry.cFcr, 3), P), co(CompanyStandard.cfcr(day), 2), c(Fmt.n(rossCfcr, 3), I))),
                LRow("EPEF", "efficiency factor", listOf(c(Fmt.n(epef, 1), P), DASH, c("≥ 350", I))),
                if (day >= 7 && w7 != null) LRow("7-day weight ×", "day-7 ÷ chick", listOf(c(Fmt.n(w7 / w0, 2) + "×", P), DASH, c("≥ 4.5×", I))) else null
            )
        )
    }
}

// ================================== FEED ==================================

@Composable
fun FeedLedgerCard(entry: DailyDataEntity, farm: FarmEntity, dailyRows: List<DailyDataEntity>, feedTypes: List<FeedTypeEntity>, harvestAge: Int, breed: String = "Ross308") {
    val day = entry.dayNumber
    val bagKg = if (farm.feedBagKg > 0) farm.feedBagKg else 50.0
    val upto = dailyRows.filter { it.dayNumber <= day }
    fun usedKg(r: DailyDataEntity) = usedByType(r).entries.sumOf { (code, bags) -> bags * bagKgOf(code, feedTypes, farm) }
    fun recKg(r: DailyDataEntity) = receivedByType(r).entries.sumOf { (code, bags) -> bags * bagKgOf(code, feedTypes, farm) }
    val usedBagsToday = usedByType(entry).values.sum()
    val usedKgToday = usedKg(entry)
    val usedBagsTD = upto.sumOf { usedByType(it).values.sum() }
    val usedKgTD = upto.sumOf { usedKg(it) }
    val recBagsToday = receivedByType(entry).values.sum()
    val recBagsTD = upto.sumOf { receivedByType(it).values.sum() }
    val recKgTD = upto.sumOf { recKg(it) }
    // Feed logged on a day is yesterday's use, so compare it with the plan up to yesterday.
    val planKgTD = dailyRows.filter { it.dayNumber < day }.sumOf { it.totalFeedKg }
    val planKgToday = entry.totalFeedKg
    val stockBags = recBagsTD - usedBagsTD
    val stockKg = recKgTD - usedKgTD
    val remainPlanKg = dailyRows.filter { it.dayNumber in day..harvestAge }.sumOf { it.totalFeedKg }
    val live = entry.liveBirds.coerceAtLeast(1)
    val yLive = dailyRows.firstOrNull { it.dayNumber == day - 1 }?.liveBirds?.coerceAtLeast(1) ?: live
    val P = ValueKind.PRESENT; val I = ValueKind.IDEAL
    val vk = if (entry.projected) ValueKind.PREDICTED else ValueKind.PRESENT
    OutputCard(title = "Feed · Day $day") {
        LedgerTable(
            headers = listOf("Today", "Till date", "Plan"),
            rows = listOf(
                LRow("Required for live stock", "bags · ${Fmt.n(bagKg, 1)} kg", listOf(c(Fmt.n(planKgToday / bagKg, 2), vk), DASH, c(Fmt.n(planKgToday / bagKg, 2), I)), strong = true),
                LRow("Required", "kg", listOf(c(Fmt.n(planKgToday, 2), vk), DASH, c(Fmt.n(planKgToday, 2), I))),
                LRow("Feed per bird", "g · for ${Fmt.i(entry.liveBirds)} birds", listOf(c(Fmt.n(entry.feedPerBird, 1), vk), DASH, c(Fmt.n(PhysiologicalEngine.dailyFeedFromDay(max(1.0, entry.weightAge), breed), 1), I))),
                LRow("Used (entered today = yesterday)", "bags", listOf(c(Fmt.n(usedBagsToday, 2), P), c(Fmt.n(usedBagsTD, 2), P), c(Fmt.n(planKgTD / bagKg, 2), I))),
                LRow("Used", "kg", listOf(c(Fmt.n(usedKgToday, 2), P), c(Fmt.n(usedKgTD, 2), P), c(Fmt.n(planKgTD, 2), I))),
                LRow("Used vs plan", "kg (+ = more than plan)", listOf(DASH, c(Fmt.signed(usedKgTD - planKgTD, 2), P), DASH)),
                LRow("Actual feed per bird", "g yesterday · kg till date", listOf(c(Fmt.n(usedKgToday * 1000.0 / yLive, 1), P), c(Fmt.n(usedKgTD / live, 3), P), c(Fmt.n(planKgTD / live, 3), I))),
                LRow("Received", "bags", listOf(c(Fmt.n(recBagsToday, 2), P), c(Fmt.n(recBagsTD, 2), P), DASH)),
                LRow("In stock", "bags · kg", listOf(c(Fmt.n(stockBags, 2), P), c(Fmt.n(stockKg, 1) + " kg", P), DASH), strong = true),
                LRow("Stock lasts", "days at today's need", listOf(c(if (planKgToday > 0) Fmt.n(stockKg / planKgToday, 1) else "—", P), DASH, DASH)),
                LRow("Needed to lifting", "bags · day $day–$harvestAge", listOf(DASH, DASH, c(Fmt.n(remainPlanKg / bagKg, 2), I))),
                LRow("Still to order", "bags", listOf(DASH, DASH, c(Fmt.n(max(0.0, (remainPlanKg - stockKg) / bagKg), 2), I)))
            )
        )
        val (phase, next) = when {
            day <= 11 -> "B1 · Starter" to "B2 from day 12"
            day <= 23 -> "B2 · Grower" to "B3 from day 24"
            else -> "B3 · Finisher" to "until lifting"
        }
        Note("Feed phase: $phase ($next). Plan = breed curve × heat derate for live birds. Values are exact; round up only when issuing bags.")
    }
}

/** Distinct, stable colours for feed types (by their order in the farm's feed list). */
private val FEED_COLORS = listOf(
    Color(0xFF46B98C), Color(0xFF5B9BD5), Color(0xFFE0A33A), Color(0xFFB07CC6),
    Color(0xFFE06C75), Color(0xFF4FB3BF), Color(0xFF9CA3AF)
)

fun feedColor(code: String, feedTypes: List<FeedTypeEntity>): Color {
    val idx = feedTypes.sortedBy { it.sortOrder }.indexOfFirst { it.code == code }
    return if (idx >= 0) FEED_COLORS[idx % FEED_COLORS.size] else FEED_COLORS[(code.hashCode() and 0x7fffffff) % FEED_COLORS.size]
}

@Composable
fun FeedStockCard(entry: DailyDataEntity, farm: FarmEntity, dailyRows: List<DailyDataEntity>, feedTypes: List<FeedTypeEntity>) {
    val day = entry.dayNumber
    val upto = dailyRows.filter { it.dayNumber <= day }
    val rec = HashMap<String, Double>(); val used = HashMap<String, Double>()
    upto.forEach { r ->
        receivedByType(r).forEach { (k, v) -> rec[k] = (rec[k] ?: 0.0) + v }
        usedByType(r).forEach { (k, v) -> used[k] = (used[k] ?: 0.0) + v }
    }
    val order = feedTypes.sortedBy { it.sortOrder }.map { it.code }
    val codes = (order + rec.keys + used.keys).distinct().filter { (rec[it] ?: 0.0) > 0 || (used[it] ?: 0.0) > 0 }
    val stock = codes.associateWith { (rec[it] ?: 0.0) - (used[it] ?: 0.0) }
    val total = stock.values.sumOf { max(0.0, it) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    OutputCard(title = "Feed stock by type") {
        if (codes.isEmpty()) {
            Note("No deliveries logged yet. Enter feed received on the Entry screen.")
            return@OutputCard
        }
        // Stacked bar of what is in the store right now.
        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            codes.forEach { code ->
                val v = max(0.0, stock[code] ?: 0.0)
                if (total > 0 && v > 0) Box(Modifier.weight((v / total).toFloat()).fillMaxHeight().background(feedColor(code, feedTypes)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Spacer(Modifier.weight(1.45f))
            listOf("Received", "Used", "In stock").forEach {
                Text(it.uppercase(), modifier = Modifier.weight(1f), textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold), color = muted)
            }
        }
        codes.forEach { code ->
            val ft = feedTypes.firstOrNull { it.code == code }
            val col = feedColor(code, feedTypes)
            val s = stock[code] ?: 0.0
            Surface(color = col.copy(alpha = 0.10f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1.45f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(col, RoundedCornerShape(3.dp)))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(code, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = col)
                            Text("${ft?.name ?: "Feed"} · ${Fmt.n(ft?.bagKg ?: farm.feedBagKg, 1)} kg", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = muted, maxLines = 1)
                        }
                    }
                    listOf(rec[code] ?: 0.0, used[code] ?: 0.0, s).forEachIndexed { i, v ->
                        Text(Fmt.n(v, 2), modifier = Modifier.weight(1f), textAlign = TextAlign.End,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = if (i == 2) FontWeight.Bold else FontWeight.SemiBold),
                            color = if (i == 2 && v < 0) com.example.ui.theme.StatusCrit else if (i == 2) col else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        Note("Bags, up to day $day. A negative stock means more use was logged than deliveries. Store below 25 °C and 60 % RH, off the floor.")
    }
}

// ================================== WATER & DIESEL ==================================

@Composable
fun WaterLedgerCard(entry: DailyDataEntity, farm: FarmEntity, dailyRows: List<DailyDataEntity>) {
    val day = entry.dayNumber
    val tank = if (farm.drinkTankL > 0) farm.drinkTankL else 2000.0
    val f = farm.waterRefillFactor
    val cum = dailyRows.filter { it.dayNumber <= day }.sumOf { it.totalWaterL }
    val live = entry.liveBirds.coerceAtLeast(1)
    val vk = if (entry.projected) ValueKind.PREDICTED else ValueKind.PRESENT
    val I = ValueKind.IDEAL
    val drinkerHt = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_DRINKERHT_BY_AGE, day.toDouble())
    OutputCard(title = "Water · Day $day") {
        LedgerTable(
            headers = listOf("Today", "Hot day", "Till date"),
            rows = listOf(
                LRow("Total water", "L", listOf(c(Fmt.n(entry.totalWaterL, 1), vk), c(Fmt.n(entry.waterHighL, 1), vk), c(Fmt.n(cum, 1), I)), strong = true),
                LRow("Per bird", "mL", listOf(c(Fmt.n(entry.waterPerBird, 1), vk), c(Fmt.n(entry.waterHighL * 1000 / live, 1), vk), DASH)),
                LRow("Tank refills", "${Fmt.n(tank, 0)} L tank × ${Fmt.n(f, 2)}", listOf(c(Fmt.n(entry.totalWaterL / tank * f, 2), vk), c(Fmt.n(entry.waterHighL / tank * f, 2), vk), c(Fmt.n(cum / tank * f, 1), I))),
                LRow("Per drinker line", "L/hr over 16 h", listOf(c(Fmt.n(entry.drinkerFlowLHrLine, 2), vk), c(Fmt.n(entry.waterHighL / max(1, farm.drinkerLines) / 16.0, 2), vk), DASH)),
                LRow("Water : feed", "ratio", listOf(c(if (entry.totalFeedKg > 0) Fmt.n(entry.totalWaterL / entry.totalFeedKg, 2) else "—", vk), DASH, DASH))
            )
        )
        LedgerTable(
            headers = listOf("Ideal"),
            rows = listOf(
                LRow("Drinker height", "in", listOf(c(Fmt.n(drinkerHt, 1), I))),
                LRow("Line pressure", "in water column", listOf(c(Fmt.n(entry.drinkerPressureIn, 1), I))),
                LRow("Nipple flow", "mL/min", listOf(c("60–90", I))),
                LRow("Water pH", "", listOf(c("6.0–6.8", I))),
                LRow("Water temperature", "°C", listOf(c("10–25", I)))
            )
        )
        Note("Water isn't logged, so these are planned figures. Hot day = +3 °C. Refills include your calibration × ${Fmt.n(f, 2)}.")
    }
}

@Composable
fun DieselLedgerCard(entry: DailyDataEntity, farm: FarmEntity, dailyRows: List<DailyDataEntity>) {
    val td = dailyRows.filter { it.dayNumber <= entry.dayNumber }.sumOf { it.dieselCansUsed }
    val canL = farm.dieselCanL
    val P = ValueKind.PRESENT
    OutputCard(title = "Diesel") {
        LedgerTable(
            headers = listOf("Today", "Till date"),
            rows = listOf(
                LRow("Cans", "", listOf(c(Fmt.n(entry.dieselCansUsed, 2), P), c(Fmt.n(td, 2), P))),
                LRow("Litres", "${Fmt.n(canL, 1)} L per can", listOf(c(Fmt.n(entry.dieselCansUsed * canL, 1), P), c(Fmt.n(td * canL, 1), P)))
            )
        )
    }
}

// ================================== VENTILATION ==================================

private fun levelMeaning(l: IbController.Level): String = when {
    l.cont.isEmpty() -> "fan ${l.cyc.joinToString(",")} · ${l.on}s on / ${l.off}s off"
    l.cyc.isNotEmpty() -> "fan ${l.cont.joinToString(",")} non-stop + fan ${l.cyc.joinToString(",")} ${l.on}/${l.off}s"
    else -> "${l.cont.size} fan${if (l.cont.size > 1) "s" else ""} non-stop"
}

fun currentHour(farm: FarmEntity): Int = try { LocalTime.now(ZoneId.of(farm.timeZone)).hour } catch (e: Exception) { 12 }
