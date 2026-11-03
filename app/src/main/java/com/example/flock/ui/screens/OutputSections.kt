package com.example.flock.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.flock.domain.SpacePlan
import com.example.flock.ui.Fmt
import com.example.ui.theme.StatusCrit
import com.example.ui.theme.StatusWarn
import kotlin.math.max

private val P = ValueKind.PRESENT
private val PR = ValueKind.PREDICTED
private val I = ValueKind.IDEAL
private val C = ValueKind.COMMERCIAL
private val MX = ValueKind.MAX
private val NE = ValueKind.NEUTRAL

/**
 * Feed today, one conclusion and the picture of it: the bags, how many feedings (picked here, best marked), so much
 * each time and on each line, the pans to open on the house drawn to scale (barricade and shut pans shown), and the
 * feeding log. How the bags were worked out sits under it as numbers.
 */
@Composable
fun FeedTodayCard(d: OutputData, pick: Int, onPick: (Int) -> Unit, feedLog: @Composable () -> Unit) {
    val o = d.feedOptions[pick.coerceIn(0, d.feedOptions.size - 1)]
    val pat = if (o.safe) o.pattern else d.patterns.first()
    val plan = d.floorPlan
    val fillBags = d.feederLines * pat.openPerLine / d.pansPerBag
    OutputCard(title = "Feed today", info = "plan") {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Fmt.n(d.dayBags, 1), style = MaterialTheme.typography.headlineMedium.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Bold),
                color = kindColor(PR))
            Spacer(Modifier.width(6.dp))
            Text("bags today", style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 4.dp))
        }
        Text("${o.feedings} feeding${if (o.feedings > 1) "s" else ""} of ${Fmt.n(o.bagsPerFeeding, 1)} bags · ${Fmt.n(o.bagsPerLine, 2)} a line",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.testTag("feed_conclusion"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            LeadLabel("feedings")
            d.feedOptions.forEachIndexed { i, opt ->
                val tag = when { !opt.safe -> "too few bags"; i == d.recommendedOption -> "best"; else -> "also ok" }
                ValueChip(vi(opt.feedings, if (opt.safe) PR else NE, tag), Modifier.weight(1f)
                    .border(if (i == pick) 2.dp else 0.dp, if (i == pick) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onPick(i) }.testTag("feedings_${opt.feedings}"))
            }
        }
        ValueRow(listOf(vt(d.feedTimesFor(o.feedings).joinToString(" "), PR, "Times"), v(d.giveBags, 1, C, "Company bags")), "when")
        Text("Pans ${pat.label.lowercase()} on every line", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = kindColor(PR))
        ValueRow(listOf(vi(pat.openPerLine, PR, "Open a line"), v(pat.birdsPerPan, 1, PR, "Birds a pan"),
            v(fillBags, 1, PR, "Bags to fill"), v(o.fillPct, 0, PR, "Each fills %")), "pans")
        FarmTopView(d, pat, plan?.penFt ?: d.barricadeFtNow, plan?.pansInPen ?: d.pansInArea)
        KeyLine(kindColor(PR) to "on", Color.White.copy(alpha = 0.6f) to "off", Color.White to "sensor", kindColor(ValueKind.MIN) to "drinker")
        feedLog()
        BagsWorkedOut(d)
    }
}

/**
 * Lifting and FCR: where the flock's meat is (in the house, on the trucks, dead), the FCR on what was produced, and
 * both corrected FCRs worked out in front of the reader.
 */
@Composable
fun LiftingCard(d: OutputData) {
    val m = d.mass
    OutputCard(title = "FCR and lifting", info = "lifting") {
        if (m == null) { Text("No data for this day.", style = MaterialTheme.typography.bodySmall); return@OutputCard }
        if (m.liftedBirds == 0) Text("No birds lifted yet. Enter the birds lifted and the truck's kg on the Entry page: FCR then counts them, and the birds left get their own average.",
            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
        else {
            Param("Lifted till date", "", vi(m.liftedBirds, P, "Birds"), v(m.liftedKg, 1, P, "kg"), v(m.liftedKg / m.liftedBirds, 3, P, "kg a bird"))
            if (m.liftedToday > 0) Param("Lifted today", "", vi(m.liftedToday, P, "Birds"), v(m.liftAvgKg, 3, P, "kg a bird"),
                v(m.avgAtLiftKg * 1000, 0, PR, "Flock then g"), v(m.avgAfterKg * 1000, 0, PR, "Birds left g"))
        }
        Param("The flock's meat", "kg", v(m.liveKg, 0, PR, "In the house"), v(m.liftedKg, 0, P, "Lifted"), v(m.deadKg, 0, PR, "Dead, est."))
        Param("Produced", "", v(m.producedKg, 0, PR, "kg"), v(m.cumBwKg, 3, PR, "kg a bird"))
        KpiLine(Kpi("FCR", "", m.fcr, d.fcrKind, d.fcrCom, d.fcrIdeal, Better.LOWER, 3, settling = d.day < 7))
        Param("FCR with the dead", "", v(m.fcrAll, 3, PR, "Feed a kg grown"))
        KpiLine(Kpi("cFCR, company", "at 2 kg", m.cFcrCompany, d.fcrKind, d.cfcrCom, d.cfcrIdeal, Better.LOWER, 3, settling = d.day < 7))
        Param("cFCR, breed curve", "at 2 kg", v(m.cFcrBreed, 3, d.fcrKind, "Ross table"))
        KpiLine(Kpi("EPEF", "", d.epef, d.fcrKind, d.epefCom, d.epefIdeal, Better.HIGHER, 1, settling = d.day < 7))
        if (m.fcr != null) {
            val adj = (2 - m.cumBwKg) * 0.25
            Text("cFCR company = FCR + (2 − ${Fmt.n(m.cumBwKg, 3)} kg) × 0.25 = ${Fmt.n(m.fcr, 3)} ${if (adj >= 0) "+" else "−"} ${Fmt.n(kotlin.math.abs(adj), 3)} = ${Fmt.n(m.cFcrCompany, 3)}",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = com.example.ui.theme.NumberFont), color = Color.White.copy(alpha = 0.8f))
        }
    }
}

/** Tight · right · roomy, with a marker where the flock stands (room ratio: floor a bird has ÷ floor it needs). */
@Composable
private fun RoomGauge(ratio: Double) {
    val x = ((ratio - 0.5) / 1.5).coerceIn(0.0, 1.0).toFloat()
    Column {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val w = size.width; val h = size.height
            val a = ((0.95 - 0.5) / 1.5).toFloat() * w; val b = ((1.6 - 0.5) / 1.5).toFloat() * w
            drawRoundRect(StatusWarn.copy(alpha = 0.45f), Offset(0f, h * 0.3f), Size(a, h * 0.4f), CornerRadius(4f))
            drawRect(kindColor(I).copy(alpha = 0.55f), Offset(a, h * 0.3f), Size(b - a, h * 0.4f))
            drawRoundRect(kindColor(ValueKind.MIN).copy(alpha = 0.45f), Offset(b, h * 0.3f), Size(w - b, h * 0.4f), CornerRadius(4f))
            drawLine(Color.White, Offset(x * w, 0f), Offset(x * w, h), 3.dp.toPx(), cap = StrokeCap.Round)
        }
        Row(Modifier.fillMaxWidth()) {
            Text("Tight", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
            Text("Right", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
            Text("Roomy", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

/**
 * Floor, pans and feedings: kg on each m² the birds have, the floor a bird has against what it needs today, how far
 * it walks — and once the birds are grown or lifted, the plan in three steps (floor → pans → feedings) and the ways
 * to set the floor side by side.
 */
@Composable
fun SpaceCard(d: OutputData) {
    val s = d.space
    OutputCard(title = "Floor, pans and feedings", info = "space") {
        Param("Density", "kg/m²", v(s.kgM2, 1, d.vk, "Now"), v(s.targetKgM2, 1, I, "Comfortable"), v(d.farm.densityCapDefault, 1, MX, "Most"))
        Param("Floor a bird", "ft²", v(s.ft2PerBird, 2, d.vk, "Has"), v(s.needFt2, 2, I, "Needs"), v(s.roomRatio, 2, PR, "Has ÷ needs"), v(s.birdsM2, 1, d.vk, "Birds a m²"))
        RoomGauge(s.roomRatio)
        Param("Walk to feed and water", "m", v(s.walkM, 2, PR, "Furthest"), v(SpacePlan.MAX_WALK_M, 1, MX, "Most"), v(d.roam, 2, PR, "Roaming ×"))
        Text(s.why, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.testTag("space_why"))
        s.steps.forEachIndexed { k, step ->
            Row(Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 7.dp)
                .testTag("plan_step_$k"), verticalAlignment = Alignment.Top) {
                Text("${k + 1}", style = MaterialTheme.typography.titleMedium.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Bold),
                    color = kindColor(PR), modifier = Modifier.width(22.dp))
                Text(step, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
            }
        }
        if (s.options.size > 1) {
            Text("The ways to set the floor today", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.65f))
            s.options.forEachIndexed { i, o ->
                val best = i == s.best
                Column(Modifier.fillMaxWidth().border(if (best) 2.dp else 1.dp, Color.White.copy(alpha = if (best) 0.9f else 0.25f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text((if (best) "✓ " else "") + o.title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    Text("${Fmt.n(o.kgM2, 1)} kg/m² · ${o.openPans} pans, ${Fmt.n(o.birdsPerPan, 1)} birds each · walk ${Fmt.n(o.walkM, 2)} m · " +
                        "${o.feedings} × ${Fmt.n(o.bagsPerFeeding, 1)} bags (fill ${Fmt.n(o.fillBags, 1)})",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = com.example.ui.theme.NumberFont), color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

/** The last 3 and 7 days: eaten and gained against the company, how even, and what to do. */
@Composable
fun SuggestCard(d: OutputData) {
    val s = d.suggest
    OutputCard(title = "Last 3 and 7 days", info = "lastdays") {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1.3f))
            Text("3 days", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1)
            Text("7 days", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1)
        }
        @Composable fun box(x: V, m: Modifier) = Text(x.text, m.border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 5.dp),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Bold),
            color = kindColor(if (x.text == "—") NE else x.kind), maxLines = 1, softWrap = false,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        @Composable fun row(label: String, a: V, b: V) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1.3f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
            box(a, Modifier.weight(1f).padding(end = 4.dp)); box(b, Modifier.weight(1f))
        }
        fun pct(x: Double?) = if (x == null) vt("—", NE) else vt(Fmt.signed(x, 1) + " %", P)
        row("Eaten vs company", pct(s.w3.feedPct), pct(s.w7.feedPct))
        row("Gain, g a day", v(s.w3.gainG, 1, P), v(s.w7.gainG, 1, P))
        row("Company gain", v(s.w3.comGainG, 1, C), v(s.w7.comGainG, 1, C))
        row("CV, first → last", vt(cvText(s.w3), P), vt(cvText(s.w7), P))
        s.lines.forEach { l ->
            Text(l.text, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (l.level > 0) FontWeight.Bold else FontWeight.Normal),
                color = when (l.level) { 2 -> StatusCrit; 1 -> StatusWarn; else -> Color.White.copy(alpha = 0.9f) })
        }
    }
    TrendsCard(d)
}

private fun cvText(w: com.example.flock.domain.Suggest.Window): String =
    if (w.cvFrom == null) "—" else if (w.cvTo == null) Fmt.n(w.cvFrom, 1) else Fmt.n(w.cvFrom, 1) + "→" + Fmt.n(w.cvTo, 1)
