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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
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
 * The parts of the Output page, in the order they show. Every one can be hidden on this phone ("Choose what shows");
 * the longer ones fold to one line that says what is in them.
 */
enum class OutSection(val key: String, val title: String, val openByDefault: Boolean, val folds: Boolean = true) {
    GLANCE("glance", "At a glance", true, false),
    HOUSE("house", "House view and weather", true, false),
    NEEDS("needs", "Hungry, thirsty, panting", true, false),
    FEEDLOG("feedlog", "Log a feeding", true, false),
    ALERTS("alerts", "Alerts", true, false),
    TODAY("today", "Today: entered and projected", true),
    FEED("feed", "Feed today", true),
    BIRDS("birds", "Birds and growth", false),
    LIFTING("lifting", "Lifting and FCR", false),
    SPACE("space", "Space and density", false),
    TRENDS("trends", "Last 3 and 7 days", false),
    WATER("water", "Water", false),
    VENT("vent", "Ventilation", false),
    WEATHER("weather", "What the weather does", false)
}

/** One number the "At a glance" grid can show. */
enum class GlanceItem(val key: String, val label: String) {
    WEIGHT("weight", "Weight"), GAIN("gain", "Gain a day"), FCR("fcr", "FCR"), CFCR("cfcr", "cFCR company"),
    CFCRB("cfcrb", "cFCR breed"), MORT("mort", "Mortality"), LIVE("live", "Live birds"), RECEPTION("reception", "Reception deaths"),
    FEED("feed", "Feed today"), WATER("water", "Water today"), DENSITY("density", "Density"), SPACE("space", "Space a bird"),
    CV("cv", "Evenness CV"), LIFTED("lifted", "Lifted"), AHEAD("ahead", "Days vs company"), TEMP("temp", "Hold the house at"),
    EPEF("epef", "EPEF"), STORE("store", "Feed in store");

    companion object {
        val DEFAULT = listOf(WEIGHT, GAIN, FCR, CFCR, MORT, LIVE, FEED, DENSITY)
        fun of(key: String) = values().firstOrNull { it.key == key }
    }
}

/** A glance value: the number (coloured by what it is), its unit, and the company's figure beside it when there is one. */
data class GlanceVal(val value: V, val unit: String, val ref: String? = null)

fun glanceValue(g: GlanceItem, d: OutputData, now: OutputData.NowView?): GlanceVal {
    val m = d.mass
    fun com(x: Double?, dec: Int) = x?.let { "company " + Fmt.n(it, dec) }
    return when (g) {
        GlanceItem.WEIGHT -> GlanceVal(v(d.weightShown(now), 0, d.vk), "g", com(d.bwCom, 0))
        GlanceItem.GAIN -> GlanceVal(v(d.gain, 1, d.gainKind), "g/day", com(d.gainCom, 1))
        GlanceItem.FCR -> GlanceVal(v(d.fcrShown(now), 3, d.fcrKind), "", com(d.fcrCom, 3))
        GlanceItem.CFCR -> GlanceVal(v(m?.cFcrCompany ?: d.e.cFcr, 3, d.fcrKind), "at 2 kg", com(d.cfcrCom, 3))
        GlanceItem.CFCRB -> GlanceVal(v(m?.cFcrBreed, 3, d.fcrKind), "at 2 kg")
        GlanceItem.MORT -> GlanceVal(v(d.mortTDPct, 2, d.mk), "%", com(d.comCumPct, 2))
        GlanceItem.LIVE -> GlanceVal(vi(d.live, d.mk), "birds")
        GlanceItem.RECEPTION -> GlanceVal(vi(d.reception, P), "birds", d.receptionPct?.let { Fmt.n(it, 2) + " % of placed" })
        GlanceItem.FEED -> GlanceVal(v(d.dayBags, 1, PR), "bags", com(d.comPerBird?.let { it * d.live / 1000 / d.bagKg }, 1))
        GlanceItem.WATER -> GlanceVal(v(d.waterL, 0, PR), "L")
        GlanceItem.DENSITY -> GlanceVal(v(d.space.kgM2, 1, d.vk), "kg/m²", "comfortable " + Fmt.n(d.space.targetKgM2, 1))
        GlanceItem.SPACE -> GlanceVal(v(d.space.ft2PerBird, 2, d.vk), "ft²", "needs " + Fmt.n(d.space.needFt2, 2))
        GlanceItem.CV -> GlanceVal(v(d.cvEst, 1, if (d.cvMeasured) P else PR), "%", if (d.cvMeasured) null else "estimated")
        GlanceItem.LIFTED -> GlanceVal(vi(m?.liftedBirds ?: d.liftTD, P), "birds", m?.takeIf { it.liftedKg > 0 }?.let { Fmt.n(it.liftedKg, 0) + " kg" })
        GlanceItem.AHEAD -> GlanceVal(vt(Fmt.signed(d.daysAheadCom, 1), d.vk), "days")
        GlanceItem.TEMP -> GlanceVal(v(d.e.tempIdeal, 1, I), "°C", "${Fmt.n(d.e.tempMin, 1)}–${Fmt.n(d.e.tempMax, 1)}")
        GlanceItem.EPEF -> GlanceVal(v(d.epef, 0, d.fcrKind), "", com(d.epefCom, 0))
        GlanceItem.STORE -> GlanceVal(v(d.stockBagsTotal, 1, P), "bags", d.lastsDays?.let { "lasts " + Fmt.n(it, 1) + " days" })
    }
}

/** The numbers chosen for a quick look, two to a row, with the colour legend under them. */
@Composable
fun GlanceCard(d: OutputData, now: OutputData.NowView?, items: List<GlanceItem>, onChoose: () -> Unit) {
    GlassBox(Modifier.fillMaxWidth().testTag("glance")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("At a glance", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Text("Choose", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { onChoose() }.padding(horizontal = 12.dp, vertical = 6.dp).testTag("glance_choose"))
            }
            items.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { g -> GlanceTile(g.label, glanceValue(g, d, now), Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            TagLegend()
        }
    }
}

@Composable
private fun GlanceTile(label: String, g: GlanceVal, modifier: Modifier) {
    val col = kindColor(if (g.value.text == "—") NE else g.value.kind)
    Column(modifier.border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 7.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f), maxLines = 2)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(g.value.text, style = MaterialTheme.typography.titleLarge.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Bold),
                color = col, maxLines = 1, softWrap = false)
            if (g.unit.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(g.unit, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f), maxLines = 1, softWrap = false,
                    modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        if (g.ref != null) Text(g.ref, style = MaterialTheme.typography.labelSmall.copy(fontFamily = com.example.ui.theme.NumberFont),
            color = if (g.ref.startsWith("company")) kindColor(C) else Color.White.copy(alpha = 0.6f), maxLines = 1)
    }
}

/**
 * A section's title line: tap to open or fold it. Folded, it says in one line what is in it (and shows a dot when
 * it holds an alert).
 */
@Composable
fun FoldHeader(sec: OutSection, summary: String?, open: Boolean, alert: Int = 0, onToggle: () -> Unit) {
    GlassBox(Modifier.fillMaxWidth().clickable { onToggle() }.testTag("fold_${sec.key}")) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp).heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            if (alert > 0) {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(5.dp)).border(5.dp, if (alert >= 2) StatusCrit else StatusWarn, RoundedCornerShape(5.dp)))
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(sec.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                if (!open && summary != null) Text(summary, style = MaterialTheme.typography.labelMedium.copy(fontFamily = com.example.ui.theme.NumberFont),
                    color = Color.White.copy(alpha = 0.7f), maxLines = 2)
            }
            Canvas(Modifier.size(14.dp)) {
                val w = size.width; val h = size.height
                val path = androidx.compose.ui.graphics.Path().apply {
                    if (open) { moveTo(w * 0.1f, h * 0.7f); lineTo(w / 2, h * 0.25f); lineTo(w * 0.9f, h * 0.7f) }
                    else { moveTo(w * 0.1f, h * 0.3f); lineTo(w / 2, h * 0.75f); lineTo(w * 0.9f, h * 0.3f) }
                }
                drawPath(path, Color.White.copy(alpha = 0.85f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

/** What shows on the Output page: the numbers at a glance and the sections, ticked on or off. */
@Composable
fun ChooseDialog(glance: List<GlanceItem>, hidden: Set<String>, onDone: (List<GlanceItem>, Set<String>) -> Unit, onDismiss: () -> Unit) {
    val pick = remember { mutableStateListOf<GlanceItem>().apply { addAll(glance) } }
    val hide = remember { mutableStateListOf<String>().apply { addAll(hidden) } }
    val box = CheckboxDefaults.colors(checkedColor = Color.White, checkmarkColor = Color.Black, uncheckedColor = Color.White.copy(alpha = 0.6f))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose what shows") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text("At a glance — the numbers in the grid", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                GlanceItem.values().forEach { g ->
                    Row(Modifier.fillMaxWidth().clickable { if (g in pick) pick.remove(g) else pick.add(g) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = g in pick, onCheckedChange = { if (it) pick.add(g) else pick.remove(g) }, colors = box)
                        Text(g.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Sections — on the page or not", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                OutSection.values().forEach { s ->
                    Row(Modifier.fillMaxWidth().clickable { if (s.key in hide) hide.remove(s.key) else hide.add(s.key) }.testTag("choose_sec_${s.key}"), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = s.key !in hide, onCheckedChange = { if (it) hide.remove(s.key) else hide.add(s.key) }, colors = box)
                        Text(s.title, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(GlanceItem.values().filter { it in pick }, hide.toSet()) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Feed today, with one conclusion: how many bags, in how many feedings, so much each time and on each line — the
 * number of feedings picked here (best one marked). How the bags were worked out folds away.
 */
@Composable
fun FeedTodayCard(d: OutputData, pick: Int, onPick: (Int) -> Unit) {
    val o = d.feedOptions[pick.coerceIn(0, d.feedOptions.size - 1)]
    val pat = if (o.safe) o.pattern else d.patterns.first()
    val hopper = max(0.0, o.bagsPerFeeding - d.feederLines * pat.openPerLine / d.pansPerBag)
    OutputCard(title = "Feed today", info = "plan") {
        // the conclusion first, in words and big numbers
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Fmt.n(d.dayBags, 1), style = MaterialTheme.typography.headlineMedium.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Bold),
                color = kindColor(PR))
            Spacer(Modifier.width(6.dp))
            Text("bags today", style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 4.dp))
        }
        Text("${o.feedings} feedings of ${Fmt.n(o.bagsPerFeeding, 1)} bags — ${Fmt.n(o.bagsPerLine, 2)} bags on each of the ${d.feederLines} lines",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.testTag("feed_conclusion"))
        Text("At ${d.feedTimesFor(o.feedings).joinToString(", ")} · company ration ${Fmt.n(d.giveBags, 1)} bags",
            style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
        // how many feedings: picked here
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            LeadLabel("feedings")
            d.feedOptions.forEachIndexed { i, opt ->
                val tag = when { !opt.safe -> "not safe"; i == d.recommendedOption -> "best"; else -> "also ok" }
                ValueChip(vi(opt.feedings, if (opt.safe) PR else NE, tag), Modifier.weight(1f)
                    .border(if (i == pick) 2.dp else 0.dp, if (i == pick) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onPick(i) }.testTag("feedings_${opt.feedings}"))
            }
        }
        // filling the lines: what one feeding does to the pans
        val fillAll = d.feederLines * d.bagsFullLine
        Text("A line takes ${Fmt.n(d.bagsFullLine, 1)} bags to fill from empty (${Fmt.n(fillAll, 1)} for all ${d.feederLines}). " +
            "Each feeding fills ${Fmt.n(o.fillPct, 0)} % of the open pans" + (if (hopper > 0.05) "; ${Fmt.n(hopper, 1)} bags wait in the hoppers." else "."),
            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
        Text("Pans: ${pat.label.lowercase()} — ${pat.openPerLine} of ${d.pansInArea} on each line, ${Fmt.n(pat.birdsPerPan, 1)} birds a pan" +
            (if (!o.safe) " (no safe series for this many feedings: all pans on)" else ""),
            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
        More("How the bags were worked out", "feed_how") {
            BagsWorkedOut(d)
            FarmTopView(d, pat)
            KeyLine(kindColor(PR) to "on", Color.White.copy(alpha = 0.6f) to "off", Color.White to "sensor", kindColor(ValueKind.MIN) to "drinker")
        }
    }
}

/**
 * Lifting and FCR: where the flock's meat is (in the house, on the trucks, dead), the FCR on what was produced, and
 * both corrected FCRs worked out in front of the reader.
 */
@Composable
fun LiftingCard(d: OutputData) {
    val m = d.mass
    OutputCard(title = "Lifting and FCR", info = "lifting") {
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
        if (m.fcr != null) {
            val adj = (2 - m.cumBwKg) * 0.25
            Text("Company: FCR + (2 − ${Fmt.n(m.cumBwKg, 3)} kg) × 0.25 = ${Fmt.n(m.fcr, 3)} ${if (adj >= 0) "+" else "−"} ${Fmt.n(kotlin.math.abs(adj), 3)} = ${Fmt.n(m.cFcrCompany, 3)}. " +
                "Heavier birds need more feed a kilo, so the rule takes 0.25 off for every kilo above 2 kg (adds it below) — flocks lifted at different weights can then be compared. " +
                "The breed curve does the same with the Ross table's own FCR at each weight.",
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
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
 * Space and density: kg on each m² the birds have, the floor a bird has against what it needs today, how far it
 * walks to feed and water — and, when birds are lifted or the house is roomy, barricade or pans, worked out.
 */
@Composable
fun SpaceCard(d: OutputData) {
    val s = d.space
    OutputCard(title = "Space and density", info = "space") {
        Param("Density", "kg/m²", v(s.kgM2, 1, d.vk, "Now"), v(s.targetKgM2, 1, I, "Comfortable"), v(d.farm.densityCapDefault, 1, MX, "Most"))
        Param("Floor a bird", "ft²", v(s.ft2PerBird, 2, d.vk, "Has"), v(s.needFt2, 2, I, "Needs"), v(s.roomRatio, 2, PR, "Has ÷ needs"))
        Param("Birds", "a m²", v(s.birdsM2, 1, d.vk, "Now"), v(d.areaInUseFt2, 0, P, "Floor ft²"))
        RoomGauge(s.roomRatio)
        Param("Walk to feed and water", "m", v(s.walkM, 2, PR, "Furthest"), v(SpacePlan.MAX_WALK_M, 1, MX, "Most"))
        Param("Roaming", "× usual", v(d.roam, 2, PR, "Walking"), v(kotlin.math.sqrt(s.ft2PerBird / 10.7639) * 100, 0, PR, "cm of floor across"))
        Text(s.why, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.testTag("space_why"))
        if (s.options.size > 1) {
            Text("The ways to set the floor today", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.65f))
            s.options.forEachIndexed { i, o ->
                val best = i == s.best
                Column(Modifier.fillMaxWidth().border(if (best) 2.dp else 1.dp, Color.White.copy(alpha = if (best) 0.9f else 0.25f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text((if (best) "✓ " else "") + o.title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    Text("${Fmt.n(o.kgM2, 1)} kg/m² · ${Fmt.n(o.ft2PerBird, 2)} ft² a bird · ${o.openPans} pans, ${Fmt.n(o.birdsPerPan, 1)} birds each · walk ${Fmt.n(o.walkM, 2)} m",
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

/** The one-line summaries of the folded sections. */
fun sectionSummary(sec: OutSection, d: OutputData, now: OutputData.NowView?): String? = when (sec) {
    OutSection.TODAY -> "Weight ${Fmt.n(d.weightShown(now), 0)} g · FCR ${Fmt.n(d.fcrShown(now), 3)} · deaths ${if (d.mortEntered) d.mortToday.toString() else "—"}"
    OutSection.FEED -> "${Fmt.n(d.dayBags, 1)} bags · ${d.feedings} feedings of ${Fmt.n(d.bagsPerFeeding, 1)}"
    OutSection.BIRDS -> "${Fmt.i(d.live)} birds · mortality ${Fmt.n(d.mortTDPct, 2)} % · reception ${d.reception}"
    OutSection.LIFTING -> d.mass?.let { m -> if (m.liftedBirds > 0) "${Fmt.i(m.liftedBirds)} lifted, ${Fmt.n(m.liftedKg, 0)} kg · FCR ${Fmt.n(m.fcr, 3)} · cFCR ${Fmt.n(m.cFcrCompany, 3)}"
        else "None lifted · FCR ${Fmt.n(m.fcr, 3)} · cFCR ${Fmt.n(m.cFcrCompany, 3)}" }
    OutSection.SPACE -> "${Fmt.n(d.space.kgM2, 1)} kg/m² · ${Fmt.n(d.space.ft2PerBird, 2)} ft² a bird · ${d.space.room.label.lowercase()}"
    OutSection.TRENDS -> d.suggest.lines.lastOrNull()?.text
    OutSection.WATER -> "${Fmt.n(d.waterL, 0)} L · ${d.waterRefills} refills"
    OutSection.VENT -> "Hold ${Fmt.n(d.e.tempIdeal, 1)} °C · min vent ${d.e.cycleText}"
    OutSection.WEATHER -> if (d.envOn) "Feed ${Fmt.signed((d.envFeedF - 1) * 100, 1)} % · growth ${Fmt.signed((d.envGainF - 1) * 100, 1)} %" else "Weather switched off"
    else -> null
}
