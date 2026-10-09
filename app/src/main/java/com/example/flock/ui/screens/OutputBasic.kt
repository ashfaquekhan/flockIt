package com.example.flock.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.domain.BirdEnvironment
import com.example.flock.ui.Fmt
import com.example.ui.theme.ValuePredicted
import com.example.ui.theme.ValuePresent

private val P = ValueKind.PRESENT
private val PR = ValueKind.PREDICTED
private val I = ValueKind.IDEAL
private val MN = ValueKind.MIN
private val MX = ValueKind.MAX

/** How an hour of the house reads for the birds, in the colour of its kind. */
fun stateKind(s: BirdEnvironment.State): ValueKind = when (s) {
    BirdEnvironment.State.COMFORT -> ValueKind.PREDICTED
    BirdEnvironment.State.WARM, BirdEnvironment.State.COOL -> ValueKind.PREDICTED
    else -> ValueKind.MAX
}

/**
 * The switch by the farm window: let the weather at the farm's location act on the house and the birds
 * (their behaviour in the window, feed, water, growth and the risk of losing birds), or take the house at
 * its ideal. Under it: outside → the house it makes → what the birds feel, for [hour] of the day.
 */
@Composable
fun WeatherRow(d: OutputData, hour: Int, on: Boolean, onChange: (Boolean) -> Unit) {
    GlassBox(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Weather at the farm", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    Text(if (!on) "Off: the house is taken at its ideal"
                         else if (d.outsideSource == BirdEnvironment.Source.WEATHER) "Acts on the house and the birds · ${String.format("%02d", hour)}:00"
                         else "No forecast for this day: the season's typical day",
                        style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                }
                InfoButton("weather")
                Spacer(Modifier.width(6.dp))
                Switch(checked = on, onCheckedChange = onChange, modifier = Modifier.testTag("weather_switch"),
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = com.example.ui.theme.AccentBlue,
                        uncheckedThumbColor = Color.White.copy(alpha = 0.75f), uncheckedTrackColor = com.example.ui.theme.SoftFillStrong, uncheckedBorderColor = Color.Transparent))
            }
            val h = d.env?.at(hour)
            if (on && h != null) {
                val outKind = if (d.outsideSource == BirdEnvironment.Source.WEATHER) P else PR
                ValueRow(listOf(v(h.outC, 1, outKind, "Outside"), v(h.air.tempC, 1, PR, "House"), v(h.r.feltC, 1, stateKind(h.r.state), "Birds feel")), "°C")
                ValueRow(listOf(v(h.outRh, 1, outKind, "Outside"), v(h.air.rhPct, 1, PR, "House"), vt(h.r.state.short, stateKind(h.r.state), "Birds are")), "RH %")
            }
        }
    }
}

/**
 * The day in two columns that never mix: what was ENTERED (as entered, it does not move) and what is PROJECTED
 * — for today the figure right now, moving with the clock; for an earlier day what the app had projected for
 * it before the entry.
 */
@Composable
fun TodayCard(d: OutputData, now: OutputData.NowView?) {
    val e = d.e
    val pr = d.proj[d.day]
    data class Line(val label: String, val unit: String, val entered: V, val projected: V)
    fun none() = vt("—", ValueKind.NEUTRAL)
    val weighedToday = e.avgWeight != null
    val lines = buildList {
        add(Line("Weight", "g", if (weighedToday) v(e.avgWeight, 1, P) else none(), v(now?.weightG ?: pr?.weightG, 1, PR)))
        add(Line("FCR", "", if (d.fcrKind == P) v(e.fcr, 3, P) else none(), v(now?.fcr ?: pr?.fcr, 3, PR)))
        add(Line("Deaths today", "birds", if (d.mortEntered) vi(d.mortToday, P) else none(), v(now?.deaths ?: pr?.deaths, 1, PR)))
        add(Line("Live birds", "", if (d.mortEntered) vi(d.live, P) else none(), vi((now?.live ?: pr?.live)?.let { Math.round(it).toInt() }, PR)))
        add(Line("Fed yesterday", "bags", if (d.usedBagsToday > 0) v(d.usedBagsToday, 2, P) else none(), v(pr?.feedKg?.let { it / d.bagKg }, 2, PR)))
        if (now != null) {
            add(Line("Eaten today", "bags", none(), v(now.eatenBags, 2, PR)))
            add(Line("Water today", "L", none(), v(now.drunkL, 1, PR)))
        }
    }
    val grey = Color.White.copy(alpha = 0.6f)
    OutputCard(title = if (now != null) "Today" else "Day ${d.day}", info = "today") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Spacer(Modifier.weight(1.25f))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Entered", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = ValuePresent, maxLines = 1, softWrap = false)
                Text(if (weighedToday || d.mortEntered || d.usedBagsToday > 0) "as entered" else "nothing yet", style = MaterialTheme.typography.labelSmall, color = grey, maxLines = 1, softWrap = false)
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Projected", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = ValuePredicted, maxLines = 1, softWrap = false)
                Text(if (now != null) "now · ${now.hhmm}" else if (d.day > d.lastEnteredDay) "forecast" else "before entry", style = MaterialTheme.typography.labelSmall, color = grey, maxLines = 1, softWrap = false)
            }
        }
        lines.forEach { l ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // at large text the name wraps between its words; it is never cut
                Column(Modifier.weight(1.25f).padding(end = 6.dp)) {
                    Text(l.label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), maxLines = 2)
                    if (l.unit.isNotEmpty()) Text(l.unit, style = MaterialTheme.typography.labelSmall, color = grey, maxLines = 1, softWrap = false)
                }
                Cell(l.entered, Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                Cell(l.projected, Modifier.weight(1f))
            }
        }
        if (now?.sinceWeighingH != null && d.weighedDay != null)
            Text("Weight: grown from the weighing of day ${d.weighedDay}, ${Fmt.n(now.sinceWeighingH, 1)} h ago" + if (d.envOn && d.envGainF < 0.995) " (slowed by the weather)" else "",
                style = MaterialTheme.typography.labelSmall, color = grey)
    }
}

/** One number in its own outlined box. */
@Composable
private fun Cell(x: V, modifier: Modifier = Modifier) {
    val col = kindColor(if (x.text == "—") ValueKind.NEUTRAL else x.kind)
    Text(x.text, modifier.then(com.example.ui.theme.softBox(RoundedCornerShape(10.dp))).padding(horizontal = 6.dp, vertical = 5.dp),
        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = (-0.3).sp),
        color = col, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
}

// =================================== the short view ===================================

/** Birds in brief: weight, FCR and mortality against the standards, and where the flock is heading. */
@Composable
fun BasicBirds(d: OutputData, now: OutputData.NowView? = null) {
    val e = d.e
    val g = d.growth
    OutputCard(title = "Birds", info = "kpis") {
        KpiLine(Kpi("Body weight", "g", d.weightShown(now), d.vk, d.bwCom, d.bwIdeal, Better.HIGHER, 1))
        KpiLine(Kpi("FCR", "", d.fcrShown(now), d.fcrKind, d.fcrCom, d.fcrIdeal, Better.LOWER, 3, settling = d.day < 7))
        KpiLine(Kpi("Mortality till date", "%", d.mortTDPct, d.mk, d.comCumPct, d.ceilingPct, Better.LOWER, 2))
        ValueRow(listOf(
            vt(Fmt.signed(d.daysAheadCom, 1), d.vk, "Days vs com"),
            v(d.cvEst, 1, if (d.cvMeasured) P else PR, if (d.cvMeasured) "CV %" else "CV % est."),
            vt(g.targetDay?.let { "day $it" } ?: "—", PR, "Target " + Fmt.n(d.targetG / 1000, 2) + " kg")))
    }
}

/** Feed and water in brief: how many bags, how often, how the pans stand, and the water for the day. */
@Composable
fun BasicFeed(d: OutputData, pick: Int) {
    val o = d.feedOptions[pick.coerceIn(0, d.feedOptions.size - 1)]
    val pat = if (o.safe) o.pattern else d.patterns.first()
    val a = d.intakeAdvice
    OutputCard(title = "Feed and water", info = "plan") {
        PlanFlow(listOf(
            Triple("Pour today, bags", v(d.dayBags, 2, PR), "chart ${Fmt.n(d.giveBags, 2)}"),
            Triple("Times a day", vi(o.feedings, if (o.safe) PR else MX), d.feedTimesFor(o.feedings).joinToString(" ") { it.take(2) + "h" }),
            Triple("Each time, bags", v(o.bagsPerFeeding, 2, PR), "${Fmt.n(o.bagsPerLine, 2)} a line"),
            Triple("Pans", vt(pat.label, if (o.safe) PR else MX), "${pat.openPerLine} of ${d.pansInArea} on")
        ))
        if (!d.intake.learning) ValueRow(listOf(v(a.likelyBags, 2, PR, "Likely eaten"), v(a.lowBags, 2, MN, "Low"), v(a.highBags, 2, MX, "High")), "bags")
        ValueRow(listOf(v(d.waterL, 1, PR, "Water L"), vi(d.waterRefills, PR, "Refills"), v(d.waterL / d.waterRefills, 1, PR, "L each")), "water")
    }
}

/** The house in brief: what to hold, the minimum fan timer, and — with the weather on — what the day asks of the fans. */
@Composable
fun BasicHouse(d: OutputData, hour: Int) {
    val e = d.e
    val l = d.minLevel
    OutputCard(title = "House", info = "air") {
        ValueRow(listOf(v(e.tempIdeal, 1, I, "Hold °C"), v(e.tempMin, 1, MN, "Min"), v(e.tempMax, 1, MX, "Max"), v(e.rhIdeal, 1, I, "Humidity %")), "air")
        ValueRow(listOf(vt(l.cyc.joinToString(",").ifEmpty { l.cont.joinToString(",") }, I, "Fan"), vi(if (l.isTimer) l.on else 0, I, "On s"), vi(if (l.isTimer) l.off else 0, I, "Off s")), "min vent")
        val day = d.env
        if (d.envOn && day != null) {
            day.at(hour)?.let { h ->
                ValueRow(listOf(v(h.fans, 1, PR, "Fans"), v(h.air.tempC, 1, PR, "House °C"), v(h.r.feltC, 1, stateKind(h.r.state), "Feels °C")), "now")
            }
            val hot = day.hottest
            ValueRow(listOf(vt(String.format("%02d:00", hot.hour), PR, "Hottest at"), v(hot.fans, 1, PR, "Fans then"),
                vt("${Fmt.n(hot.r.feltC, 1)}° " + hot.r.state.short, stateKind(hot.r.state), "Feels")), "today")
        }
    }
}

/** What the weather does to the day's plan, in four multipliers (shown when the switch is on and anything moves). */
@Composable
fun WeatherEffects(d: OutputData) {
    val day = d.env ?: return
    if (!d.envOn) return
    fun pct(f: Double) = Fmt.signed((f - 1) * 100, 1) + " %"
    fun kind(f: Double, worseUp: Boolean) = if (kotlin.math.abs(f - 1) < 0.005) P else if ((f > 1) == worseUp) MX else PR
    OutputCard(title = "What the weather does today", info = "weather") {
        ValueRow(listOf(vt(pct(d.envFeedF), kind(d.envFeedF, false), "Feed"), vt(pct(d.envWaterF), PR, "Water"),
            vt(pct(d.envGainF), kind(d.envGainF, false), "Growth"), vt("× " + Fmt.n(d.envMortF, 2), kind(d.envMortF, true), "Deaths risk")), "effect")
        val ok = if (d.outsideSource == BirdEnvironment.Source.WEATHER) P else PR
        ValueRow(listOf(v(day.outMinC, 1, ok, "Low °C"), v(day.outMeanC, 1, ok, "Mean °C"), v(day.outMaxC, 1, ok, "High °C"), v(day.outMeanRh, 0, ok, "RH %")), "outside")
        ValueRow(listOf(vi(day.hoursWarm, PR, "Warm h"), vi(day.hoursHot, if (day.hoursHot > 0) MX else PR, "Hot h"), vi(day.hoursCold, if (day.hoursCold > 0) MN else PR, "Cold h")), "hours")
    }
}

/**
 * How likely the birds are hungry, thirsty or panting — now, and in three hours if nothing more is poured.
 * Likelihoods, not readings: there are no sensors in the house.
 */
@Composable
fun NeedsRow(now: com.example.flock.domain.FlockNeeds.State, soon: com.example.flock.domain.FlockNeeds.State, fedKnown: Boolean) {
    fun pct(x: Double, tag: String) = vt(Fmt.n(x * 100, 1) + " %", if (x >= 0.5) MX else PR, tag)
    GlassBox(Modifier.fillMaxWidth().testTag("needs_row")) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("How likely the birds are", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    Text(if (fedKnown) "From the feedings logged, the lights and the weather" else "No feeding logged today: the plan's feedings are assumed",
                        style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                }
                InfoButton("needs")
            }
            ValueRow(listOf(pct(now.hungry, "Hungry"), pct(now.thirsty, "Thirsty"), pct(now.panting, "Panting")), "now")
            ValueRow(listOf(pct(soon.hungry, "Hungry"), pct(soon.thirsty, "Thirsty"), pct(soon.panting, "Panting")), "in 3 h")
        }
    }
}
