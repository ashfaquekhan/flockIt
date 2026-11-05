package com.example.flock.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.flock.domain.Litter
import com.example.flock.domain.SpacePlan
import com.example.flock.engine.PhysiologicalEngine
import com.example.flock.ui.Fmt
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val P = ValueKind.PRESENT
private val PR = ValueKind.PREDICTED
private val C = ValueKind.COMMERCIAL
private val I = ValueKind.IDEAL
private val MN = ValueKind.MIN
private val MX = ValueKind.MAX
private val NE = ValueKind.NEUTRAL

// =================================== the pieces every section uses ===================================

/**
 * One number of a section, in the v46 line: its name, the arrow and the value (coloured by what it is: entered,
 * projected …) on the first line; com and ideal (or the limits that apply) on the second; then the slim bar — the
 * company's ±5 % band and tick under the line, the ideal's above, the flock's dot — or, for a number with a safe range,
 * the range bar. [sensor] numbers show where a house sensor's reading will go (none yet).
 */
class PLine(
    val name: String, val value: V, val unit: String = "",
    val trend: TrendMark? = null,
    val com: Double? = null, val ideal: Double? = null, val dec: Int = 1, val better: Better = Better.HIGHER,
    /** the ideal as a band or a limit ("≤ 8.0") when it is not one number */
    val idealText: String? = null,
    /** lines in place of com and ideal, each in its own colour */
    val refs: List<Pair<String, Color>>? = null,
    /** a short note on the second line's left (how many feedings, a likelihood …) */
    val sub: String? = null,
    /** what the note's numbers are (entered, projected …): coloured so (v56); null = words, grey */
    val subKind: ValueKind? = null,
    val actual: Double? = null,
    /** a safe range: the range bar instead of the comparison bar */
    val min: Double? = null, val max: Double? = null, val band: Pair<Double, Double>? = null,
    val sensor: Boolean = false,
    val tag: String? = null,
    val extra: (@Composable () -> Unit)? = null
)

@Composable
fun ParamLine(t: PLine) {
    fun f(x: Double) = if (t.dec == 0) Fmt.i(Math.round(x).toInt()) else Fmt.n(x, t.dec)
    val ranged = t.min != null || t.max != null || t.band != null
    val refs = t.refs ?: buildList {
        if (!ranged) add("com " + (t.com?.let { f(it) } ?: "NA") to kindColor(C))
        // a number with only a limit (air speed, CO₂) shows the limit alone
        if (!ranged || t.ideal != null || t.idealText != null) add("ideal " + (t.idealText ?: t.ideal?.let { f(it) } ?: "NA") to kindColor(I))
        t.min?.let { add("min " + f(it) to kindColor(MN)) }
        t.max?.let { add("max " + f(it) to kindColor(MX)) }
    }
    Column(Modifier.fillMaxWidth().let { if (t.tag != null) it.testTag(t.tag) else it }) {
        CompactLine(t.name, t.unit, t.sub ?: if (t.sensor) "sensor —" else null, t.value, t.trend, refs,
            subColor = t.subKind?.let { if (t.sub != null) kindColor(it) else null })
        when {
            ranged -> RangeBar(t.min, t.ideal, t.max, t.actual, if (t.value.text == "—") NE else t.value.kind, t.band, slim = true)
            t.com != null || t.ideal != null -> SlimCompareBar(Kpi(t.name, t.unit, t.actual, t.value.kind, t.com, t.ideal, t.better, t.dec))
        }
        t.extra?.invoke()
    }
}

/** The numbers of a section, one under the other. */
@Composable
fun LineList(lines: List<PLine>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { lines.forEach { ParamLine(it) } }
}

/**
 * A section of the page, straight on the black (no outline, no panel): its sign and name (and what sits beside it), its
 * ⓘ, then what it holds.
 */
@Composable
fun SectionCard(icon: GIcon, title: String, info: String?, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null,
                content: @Composable ColumnScope.() -> Unit) {
    val anchors = LocalScrollAnchors.current
    Column(modifier.fillMaxWidth().padding(top = 10.dp).onGloballyPositioned { anchors?.report("card:$title", it.positionInRoot().y) },
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Color.White) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GameIcon(icon, 20.dp)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium), color = Color.White, maxLines = 1, softWrap = false,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            trailing?.invoke()
            if (info != null) { Spacer(Modifier.width(6.dp)); InfoButton(info) }
        }
        content()
        }
    }
}

/** A row of choices, the chosen one white, and a small word under each when given. */
@Composable
fun PickChips(items: List<Pair<String, String>>, current: String, onPick: (String) -> Unit, tagPrefix: String, subs: Map<String, String> = emptyMap()) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (key, label) ->
            val on = key == current
            Column(Modifier.weight(1f).background(if (on) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                .border(1.dp, Color.White.copy(alpha = if (on) 1f else 0.35f), RoundedCornerShape(10.dp)).clickable { onPick(key) }
                .padding(horizontal = 4.dp, vertical = 5.dp).testTag("${tagPrefix}_$key"), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) Color.Black else Color.White, maxLines = 1, softWrap = false)
                subs[key]?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = if (on) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.6f), maxLines = 1, softWrap = false) }
            }
        }
    }
}

private fun pct(x: Double) = Fmt.n(x * 100, 1)

// =================================== 1. birds: the window and their numbers ===================================

/**
 * The birds in the farm's order — live birds, weight, FCR, cFCR, mortality, CV (with how the weights spread) and
 * stocking density — each against the company and the ideal; the one-score radar under them.
 */
@Composable
fun BirdsSection(d: OutputData, view: OutputData.NowView, entryHHmm: String?, live: Boolean, onNow: () -> Unit, hour: Int) {
    val m = d.mass
    val beforeToday = (d.mortTD - d.mortToday).coerceAtLeast(0).toDouble()
    val mort = if (d.mk == P) d.mortTDPct else (beforeToday + view.deaths) * 100.0 / d.entryBirds
    val weight = if (d.vk == P) d.bwNow else view.weightG
    val fcr = m?.fcr ?: view.fcr
    val liveNow = if (d.mortEntered) d.live.toDouble() else view.live
    val s = d.space
    val u = remember(d) { uniformityOf(d) }
    val lines = listOf(
        PLine("Live birds", if (d.mortEntered) vi(d.live, P) else vi(Math.round(view.live).toInt(), PR), "",
            trendOf(liveNow, d.liveCom, Better.HIGHER, 1.0), d.liveCom, d.liveIdeal, 0, actual = liveNow, tag = "tile_live"),
        PLine("Weight", v(weight, 1, d.vk), "g", trendOf(weight, d.bwCom, Better.HIGHER), d.bwCom, d.bwIdeal, 1, actual = weight),
        PLine("FCR", v(fcr, 3, d.fcrKind), "", trendOf(fcr, d.fcrCom, Better.LOWER), d.fcrCom, d.fcrIdeal, 3, Better.LOWER, actual = fcr),
        PLine("cFCR", v(m?.cFcrCompany, 3, d.fcrKind), "", trendOf(m?.cFcrCompany, d.cfcrCom, Better.LOWER), d.cfcrCom, d.cfcrIdeal, 3, Better.LOWER,
            sub = m?.fcr?.let { Fmt.n(it, 3) + " + (2 − " + Fmt.n(m.cumBwKg, 3) + ") × 0.25" }, subKind = d.fcrKind, actual = m?.cFcrCompany),
        PLine("Mortality", v(mort, 2, d.mk), "%", trendOf(mort, d.comCumPct, Better.LOWER), d.comCumPct, d.ceilingPct, 2, Better.LOWER, actual = mort),
        PLine("CV, how even", v(d.cvEst, 1, if (d.cvMeasured) P else PR), "%", d.cvEst?.let { rangeTrend(it, null, 10.0) },
            refs = listOf("ideal ≤ 8.0" to kindColor(I), "max 10.0" to kindColor(MX)), actual = d.cvEst, max = 10.0, band = 0.0 to 8.0, tag = "tile_cv",
            extra = {
                WeightHistogram(u.bins, u.mean, u.measured)
                Row(Modifier.fillMaxWidth()) {
                    listOf("light" to u.lightPct, "even" to u.evenPct, "heavy" to u.heavyPct).forEach { (w, x) ->
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                            Text(Fmt.n(x, 1) + "%", style = MaterialTheme.typography.labelLarge.copy(fontFamily = com.example.ui.theme.NumberFont),
                                color = kindColor(if (u.measured) P else PR), maxLines = 1, softWrap = false)
                            Spacer(Modifier.width(4.dp))
                            Text(w, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f), maxLines = 1, softWrap = false)
                        }
                    }
                }
                Text(if (u.measured) "${u.n} birds weighed one by one" else "expected at this CV · weigh 10 one by one",
                    style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
            }),
        PLine("Stocking density", v(s.kgM2, 1, d.vk), "kg/m²", trendOf(s.kgM2, s.targetKgM2, Better.LOWER, 5.0),
            refs = listOf("ideal ≤ " + Fmt.n(s.targetKgM2, 1) to kindColor(I), "max " + Fmt.n(d.farm.densityCapDefault, 1) to kindColor(MX)),
            sub = Fmt.n(s.ft2PerBird, 2) + " ft² per bird", subKind = d.vk, actual = s.kgM2, max = d.farm.densityCapDefault, band = 0.0 to s.targetKgM2)
    )
    SectionCard(GIcon.BIRDS, "Birds", "glance", Modifier.testTag("glance"), trailing = {
        val sky = d.hourly.firstOrNull { it.date == d.e.date && it.hour == hour }
        Text("Day ${d.day} ", style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.8f), maxLines = 1, softWrap = false)
        Text(view.hhmm, style = MaterialTheme.typography.titleSmall.copy(fontFamily = com.example.ui.theme.NumberFont), color = Color.White,
            maxLines = 1, softWrap = false, modifier = Modifier.testTag("time_shown"))
        if (sky?.emoji != null) Text(" " + sky.emoji, style = MaterialTheme.typography.titleSmall)
        if (!live) {
            Spacer(Modifier.width(6.dp))
            Text("Now", style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, softWrap = false,
                modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).clickable { onNow() }
                    .padding(horizontal = 8.dp, vertical = 4.dp).testTag("time_now"))
        }
    }) {
        ColourKey(listOf(kindColor(P) to "entered " + (entryHHmm ?: "—"), kindColor(PR) to "projected " + view.hhmm,
            kindColor(C) to "company", kindColor(I) to "ideal (Ross)"))
        LineList(lines)
        BirdRadar(d, view)
    }
}

/** How even the flock is: shares of birds in 5 % steps round the average, from single weights (10 or more) or the CV. */
class FlockSpread(val bins: List<Double>, val mean: Double, val measured: Boolean, val n: Int, val lightPct: Double, val evenPct: Double, val heavyPct: Double)

fun uniformityOf(d: OutputData): FlockSpread {
    val singles = PhysiologicalEngine.parseWeights(d.e.indivWeights)
    val measured = singles.size >= PhysiologicalEngine.MIN_BIRDS_FOR_CV
    val mean = if (measured) singles.average() else d.bw
    val cvFrac = ((d.cvEst?.coerceIn(3.0, 25.0) ?: 8.0) / 100.0).coerceAtLeast(0.01)
    val edges = (-5..5).map { it * 0.05 }
    val bins: List<Double> = if (measured) {
        val c = DoubleArray(10)
        singles.forEach { w -> val dev = (w - mean) / mean; c[((dev + 0.25) / 0.05).toInt().coerceIn(0, 9)] += 1.0 }
        c.map { it / singles.size }
    } else (0 until 10).map { i ->
        val lo = if (i == 0) -10.0 else edges[i] / cvFrac; val hi = if (i == 9) 10.0 else edges[i + 1] / cvFrac
        normCdf(hi) - normCdf(lo)
    }
    val light = if (measured) singles.count { it < mean * 0.9 } * 100.0 / singles.size else normCdf(-0.1 / cvFrac) * 100
    val heavy = if (measured) singles.count { it > mean * 1.1 } * 100.0 / singles.size else (1 - normCdf(0.1 / cvFrac)) * 100
    return FlockSpread(bins, mean, measured, singles.size, light, 100 - light - heavy, heavy)
}

// =================================== 2. feed and floor ===================================

/** The day as it would be with the barricades at [fromFt]–[toFt] (ft from the front wall): the plan for a floor not yet set. */
fun withBarricades(d: OutputData, fromFt: Double, toFt: Double): OutputData {
    val L = d.farm.usableLengthFt; val W = d.farm.usableWidthFt
    val e2 = d.e.copy(penStartFt = fromFt, broodingLength = if (toFt >= L - 0.5) 0.0 else toFt, occupiedFt2 = (toFt - fromFt).coerceAtLeast(10.0) * W)
    return OutputData(d.flock, d.farm, e2, d.rows.map { if (it.dayNumber == d.day) e2 else it }, d.feedTypes, d.weather, d.hourly, d.isToday, d.envOn)
}

/**
 * Feed and floor: the first and last barricade (set here, from this day on), how many feedings, the whole house with
 * every pan as the plan opens it — then the bags, the grams a bird, yesterday's gain and today's from the curve, the
 * pans, the barricades, the walk to feed and the rest of the feed numbers. The plan follows the barricades: no modes.
 */
@Composable
fun FeedSection(d0: OutputData, pick: Int, onPick: (Int) -> Unit, needs: com.example.flock.domain.FlockNeeds.State,
                onBarricades: ((Double, Double) -> Unit)? = null) {
    val L = d0.farm.usableLengthFt
    val curFrom = d0.penStartFt; val curTo = d0.barricadeFtNow
    // barricades moved but not set yet: the plan, the drawing and the numbers show them at once
    var pending by remember(d0.day, curFrom, curTo) { mutableStateOf<Pair<Double, Double>?>(null) }
    val d = remember(d0, pending) { pending?.let { withBarricades(d0, it.first, it.second) } ?: d0 }
    val o = d.feedOptions[pick.coerceIn(0, d.feedOptions.size - 1)]
    val pat = d.patternFor(pick)
    val from = d.penStartFt
    val to = d.barricadeFtNow
    // set on this day or before (else the brooding plan's floor, drawn dashed)
    val isSet = pending == null && d0.rows.any { it.dayNumber <= d0.day && (it.penStartFt != null || it.broodingLength != null) }
    val iso = remember(d, pat, from, to, isSet) { isoFarmOf(d, pat, from, to, !isSet, d.hour) }
    val fillBags = d.bagsToFill(pat.openPerLine)
    val perBird = d.dayBags * d.bagKg * 1000 / d.liveSafe
    val walk = d.feedWalkM(pat)
    val lim = d.walkLimits
    fun ft(x: Double) = x.roundToInt().toString()
    val barText = when {
        from > 0.5 && to < L - 0.5 -> "${ft(from)}–${ft(to)}"
        to < L - 0.5 -> "0–${ft(to)}"
        from > 0.5 -> "${ft(from)}–${ft(L)}"
        else -> "none"
    }
    val lines = buildList {
        add(PLine("Bags to use", v(d.dayBags, 1, PR), "bags", trendOf(d.dayBags, d.giveBags, Better.CLOSER, 10.0), d.giveBags, d.idealBags, 1, Better.CLOSER,
            sub = "${o.feedings} × ${Fmt.n(o.bagsPerFeeding, 1)} · ${Fmt.n(o.bagsPerLine, 2)} per line", subKind = PR, actual = d.dayBags, tag = "feed_conclusion"))
        add(PLine("Feed per bird", v(perBird, 1, PR), "g", trendOf(perBird, d.comPerBird, Better.CLOSER), d.comPerBird, d.idealPerBird, 1, Better.CLOSER, actual = perBird))
        add(PLine("Yesterday's gain", v(d.gainYesterday, 1, d.gainYesterdayKind), "g", trendOf(d.gainYesterday, d.gainYesterdayCom, Better.HIGHER),
            d.gainYesterdayCom, d.gainYesterdayIdeal, 1, actual = d.gainYesterday))
        add(PLine("Gain today, curve", v(d.gainToday, 1, PR), "g", null, d.gainTodayCom, d.gainTodayIdeal, 1, actual = d.gainToday))
        add(PLine("Pans", vt(pat.label.lowercase(), PR), "", null,
            refs = listOf((if (pat.birdsPerPan < 1e6) Fmt.n(pat.birdsPerPan, 1) else "—") + " per pan" to kindColor(PR),
                "ideal " + SpacePlan.AIM_BIRDS_PER_PAN.roundToInt() to kindColor(I), "max " + d.birdsPerPanMax.roundToInt() to kindColor(MX)),
            sub = "${pat.openPerLine} open per line · fill ${Fmt.n(fillBags, 1)} bags", subKind = PR, actual = pat.birdsPerPan.takeIf { it < 1e6 },
            min = SpacePlan.FEW_BIRDS_PER_PAN, max = d.birdsPerPanMax, band = SpacePlan.FEW_BIRDS_PER_PAN to SpacePlan.AIM_BIRDS_PER_PAN * 1.4, tag = "tile_pans"))
        add(PLine("Barricades", vt(barText, if (isSet) P else PR), if (barText == "none") "" else "ft", null,
            refs = emptyList(), sub = if (pending != null) "not set yet" else if (isSet) "as set" else "brooding plan", tag = "tile_barricades"))
        add(PLine("Walk to feed", v(walk, 2, PR), "m", rangeTrend(walk, null, lim.maxM),
            refs = listOf("ideal ≤ " + Fmt.n(lim.safeM, 1) to kindColor(I), "max " + Fmt.n(lim.maxM, 1) to kindColor(MX)), actual = walk, max = lim.maxM, band = 0.0 to lim.safeM,
            tag = "line_walk_feed", extra = { WalkFeedDrawing(d, pat) }))
        add(PLine("Hungry now", vt(pct(needs.hungry), PR), "%", null, refs = emptyList(), sub = "likelihood"))
        add(PLine("Eaten yesterday", v(d.usedPerBirdY, 1, P), "g", trendOf(d.usedPerBirdY, d.comPerBirdY, Better.CLOSER), d.comPerBirdY, d.idealPerBirdY, 1,
            Better.CLOSER, actual = d.usedPerBirdY))
        add(PLine("Feedings", vt(d.modelFeedTimes(o.feedings).joinToString(" "), PR), "", null, refs = emptyList(), sub = "each fills ${o.fillPct.roundToInt()} %"))
        val c = d.correction
        if (c.pct != 0.0) add(PLine("Correction", vt((if (c.pct > 0) "+" else "") + Fmt.n(c.pct, 1), PR), "%", null, refs = emptyList(),
            sub = Fmt.n(d.correctedBags, 1) + " bags with it", subKind = PR))
        if (d.day <= 7) {
            add(PLine("Feeder trays", v(kotlin.math.ceil(d.farm.manualFeeders * d.trayKeepFrac), 0, PR), "", null,
                refs = listOf("ideal " + Fmt.n(d.traysKeepIdeal, 0) to kindColor(I)), sub = "yours ${d.farm.manualFeeders}", subKind = P))
            if (d.day <= 4) add(PLine("Feed paper", v(d.paperFt2, 0, I), "ft²", null, refs = emptyList(), sub = "≥ 70 % of the floor"))
        }
    }
    SectionCard(GIcon.BAGS, "Feed and floor", "plan") {
        // the first and last barricade: drag them along the house (5 ft steps), then set them from this day on
        if (onBarricades != null) {
            val shown = pending ?: (curFrom to curTo)
            fun snap(x: Float) = ((x / 5f).roundToInt() * 5f).toDouble().let { if (it > L - 2.5) L else it }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Barricades", style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.weight(1f))
                    Text("first " + (if (shown.first > 0.5) ft(shown.first) + " ft" else "none") + " · last " + (if (shown.second < L - 0.5) ft(shown.second) + " ft" else "none"),
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = com.example.ui.theme.NumberFont), color = kindColor(if (pending != null || !isSet) PR else P))
                }
                ThinRangeSlider(shown.first.toFloat(), shown.second.toFloat(), L.toFloat(), { a0, b0 ->
                    val a = snap(a0); val b = snap(b0); if (b - a >= 10) pending = a to b }, Modifier.testTag("barricade_slider"))
                if (pending != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Set from day ${d0.day}", style = MaterialTheme.typography.labelLarge, color = Color.Black,
                        modifier = Modifier.background(Color.White, RoundedCornerShape(10.dp)).clickable {
                            pending?.let { (a, b) -> onBarricades(a, b) }; pending = null
                        }.padding(horizontal = 14.dp, vertical = 8.dp).testTag("barricade_set"))
                    Text("Cancel", style = MaterialTheme.typography.labelLarge, color = Color.White,
                        modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).clickable { pending = null }
                            .padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }
        PickChips(d.feedOptions.mapIndexed { i, opt -> i.toString() to "${opt.feedings} ×" }, pick.toString(), { onPick(it.toInt()) },
            "feedings_pick", subs = d.feedOptions.mapIndexed { i, opt -> i.toString() to when { !opt.safe -> "too few bags"; i == d.recommendedOption -> "best"; else -> "ok" } }.toMap())
        Text(d.space.why, style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.testTag("space_why"))
        FarmIso(iso, IsoView.FEED)
        KeyLine(kindColor(PR) to "pan on", Color.White.copy(alpha = 0.55f) to "off", Color.White to "barricade")
        LineList(lines)
    }
}

// =================================== 3. lifting ===================================

/**
 * Lifting: where the flock's meat is (in the house, lifted, dead and culled — each with its kilos and its share), the birds
 * lifted, livability, EPEF and what was produced. (v56: no target weight or harvest day — the app follows the flock and
 * its curves; FCR and cFCR are in Birds, one of each.)
 */
@Composable
fun LiftSection(d: OutputData) {
    val m = d.mass
    val lines = buildList {
        val lifted = m?.liftedBirds ?: d.liftTD
        val days = d.rows.filter { it.birdsLifted > 0 && it.dayNumber <= d.day }.map { it.dayNumber }
        add(PLine("Birds lifted", vi(lifted, P), "", null, refs = listOf(Fmt.n(m?.liftedKg ?: d.liftKgTD, 1) + " kg" to kindColor(P)),
            sub = if (days.isEmpty()) "none yet" else "days " + days.joinToString(","), subKind = if (days.isEmpty()) null else P))
        if (m != null && m.liftedBirds > 0) add(PLine("Lifted, per bird", v(m.liftedKg / m.liftedBirds, 3, P), "kg", null,
            refs = listOfNotNull(if (m.liftedToday > 0) "left " + Fmt.n(m.avgAfterKg * 1000, 0) + " g" to kindColor(PR) else null),
            sub = if (m.liftedToday > 0) "today ${m.liftedToday} · " + Fmt.n(m.liftAvgKg, 3) + " kg" else null, subKind = P))
        add(PLine("Livability", v(d.e.livability, 2, d.mk), "%", trendOf(d.e.livability, 100 - d.comCumPct, Better.HIGHER, 1.0),
            100 - d.comCumPct, 100 - d.ceilingPct, 2, actual = d.e.livability))
        add(PLine("EPEF", v(d.epef, 1, d.fcrKind), "", trendOf(d.epef, d.epefCom, Better.HIGHER), d.epefCom, d.epefIdeal, 1, actual = d.epef))
        if (m != null) add(PLine("Produced", v(m.producedKg, 0, d.vk), "kg", null, refs = emptyList(),
            sub = Fmt.n(m.cumBwKg, 3) + " kg per bird, lifted ones too", subKind = d.vk))
    }
    SectionCard(GIcon.TRUCK, "Lifting", "lifting") {
        if (m != null) MeatShares(m, d.vk)
        LineList(lines)
    }
}

/**
 * Where the flock's meat is, read at a glance (v56): one line each — in the house, lifted, dead and culled — its name and
 * kilos above a thin bar filled to its share of all of it.
 */
@Composable
private fun MeatShares(m: com.example.flock.data.FlockCalc.Mass, liveKind: ValueKind) {
    val parts = listOf(Triple("In the house", m.liveKg, liveKind), Triple("Lifted", m.liftedKg, P), Triple("Dead and culled", m.deadKg, PR))
    val total = parts.sumOf { it.second }.takeIf { it > 0 } ?: return
    Column(Modifier.fillMaxWidth().testTag("meat_bar"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Where the meat is · ${Fmt.i(total.roundToInt())} kg in all", style = MaterialTheme.typography.labelLarge, color = Color.White)
        parts.forEach { (name, kg, kind) ->
            val share = kg / total
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(name, style = MaterialTheme.typography.labelMedium, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(Fmt.i(kg.roundToInt()) + " kg", style = MaterialTheme.typography.labelLarge.copy(fontFamily = com.example.ui.theme.NumberFont),
                        color = kindColor(kind), maxLines = 1, softWrap = false)
                    Text("  " + Fmt.n(share * 100, 1) + " %", style = MaterialTheme.typography.labelMedium.copy(fontFamily = com.example.ui.theme.NumberFont),
                        color = kindColor(kind), maxLines = 1, softWrap = false)
                }
                Canvas(Modifier.fillMaxWidth().height(3.dp)) {
                    drawRoundRect(Color.White.copy(alpha = 0.12f), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f))
                    drawRoundRect(kindColor(kind), size = Size((share * size.width).toFloat().coerceAtLeast(2f), size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f))
                }
            }
        }
    }
}

// =================================== 4. air and ventilation ===================================

/**
 * Air and ventilation in four views of the same house (air · fans · pads · litter), the whole house small and still;
 * each view's numbers under it with their ideal and the place for a sensor's reading.
 */
@Composable
fun AirSection(d: OutputData, hour: Int, needs: com.example.flock.domain.FlockNeeds.State, weatherOn: Boolean, onWeatherOn: (Boolean) -> Unit,
               map: com.example.flock.models.HouseMap.Field?) {
    var view by rememberSaveable { mutableStateOf("air") }
    val iv = when (view) { "fans" -> IsoView.FANS; "pads" -> IsoView.PADS; "litter" -> IsoView.LITTER; else -> IsoView.AIR }
    val layer = when (iv) { IsoView.AIR -> com.example.flock.models.HouseMap.Layer.HEAT; IsoView.LITTER -> com.example.flock.models.HouseMap.Layer.LITTER; else -> null }
    val iso = remember(d, hour, map) { isoFarmOf(d, d.feedPattern, d.penStartFt, d.barricadeFtNow, false, hour, map) }
    val e = d.e
    val h = d.envAt(hour)
    val sky = d.env?.at(hour)
    val l = remember(d, hour) { litterOf(d, hour) }
    val fans = h?.fans ?: d.minLevel.avgFans
    val fpm = d.airFpm(fans)
    val lv = d.minLevel
    val live = d.liveSafe.toDouble()
    val out = d.outsideHours.getOrNull(hour.coerceIn(0, 23))
    val padNow = remember(d, hour) { out?.let { d.sim(it.second, it.third, hour) } }
    val hot = d.scenarios.last().state
    // the weather at the farm: hours gone by are as measured (green), hours to come a forecast (amber); the season's typical day projected
    val wx = if (d.outsideSource != com.example.flock.domain.BirdEnvironment.Source.WEATHER || d.isFuture || (d.isToday && hour > d.hour)) PR else P
    val maxAir = PhysiologicalEngine.maxAirSpeedFpm(d.day)
    val lines: List<PLine> = when (view) {
        "fans" -> listOf(
            PLine("Min vent air", v(d.minAvgCfm / live, 3, PR), "cfm per bird", trendOf(d.minAvgCfm, d.plan.needCfm, Better.HIGHER), null, d.plan.needCfm / live, 3,
                sub = "house " + Fmt.i(d.minAvgCfm.roundToInt()) + " cfm", subKind = PR, actual = d.minAvgCfm / live),
            PLine("Fan timer", vt(if (lv.isTimer) "${lv.on} / ${lv.off}" else "on", I), if (lv.isTimer) "s" else "", null,
                refs = listOf("cycle " + (if (lv.isTimer) "${lv.on + lv.off} s" else "running") to kindColor(I)),
                sub = "fan " + lv.cyc.joinToString(",").ifEmpty { lv.cont.joinToString(",") }, subKind = I),
            PLine("Fans now", v(fans, 1, PR), "", null, refs = listOf("min vent " + Fmt.n(lv.avgFans, 2) to kindColor(I), "allowed ${d.plan.maxFans}" to kindColor(MX))),
            PLine("Air speed", v(fpm, 1, PR), "ft/min", rangeTrend(fpm, null, maxAir), actual = fpm, max = maxAir, sensor = true),
            PLine("Air changes", v(if (d.houseVolFt3 > 0) d.minAvgCfm * 60 / d.houseVolFt3 else null, 1, PR), "per hour", null,
                refs = listOf("ideal " + Fmt.n(if (d.houseVolFt3 > 0) d.plan.needCfm * 60 / d.houseVolFt3 else null, 1) to kindColor(I))),
            PLine("Controller", v(d.plan.set, 1, I), "°C SET", null,
                refs = listOf("heat on " + Fmt.n(d.plan.heat, 1) to kindColor(I), "comfort " + Fmt.n(d.plan.comfort, 1) to kindColor(I)))
        )
        "pads" -> listOf(
            PLine("Pads now", vt(if ((padNow?.padEff ?: 0.0) > 0.05) "wet" else "dry", PR), "", null,
                refs = listOf("cooling " + Fmt.n((padNow?.padEff ?: 0.0) * 100, 0) + " %" to kindColor(PR)), sensor = true),
            PLine("Hottest hour", vt(if (hot.padEff > 0.05) "wet" else "dry", PR), "", null,
                refs = listOf("cooling " + Fmt.n(hot.padEff * 100, 0) + " %" to kindColor(PR)), sub = "%02d:00 · ".format(d.hottestHour) + Fmt.n(d.hottestC, 1) + " °C", subKind = PR),
            PLine("Pad layout", vt(if (d.farm.padLayout == "c") "C-type" else "side by side", NE), "", null, refs = emptyList(),
                sub = Fmt.n(d.farm.padAreaFt2, 0) + " ft² · ${d.farm.padCount} pads", subKind = P),
            PLine("Outside air", v(out?.third, 0, wx), "% RH", null, refs = listOf(Fmt.n(out?.second, 1) + " °C" to kindColor(wx)), sub = "damp air: pads cool less"),
            PLine("Wet / dry", vt(listOf(e.padWetMin, e.padDryMin).joinToString(" / ") { Fmt.n(it, 0) }, P), "min", null, refs = emptyList(), sensor = true)
        )
        "litter" -> listOf(
            PLine("Moisture", v(l.moisturePct, 1, PR), "%", rangeTrend(l.moisturePct, null, Litter.IDEAL_MOIST_HI), actual = l.moisturePct,
                max = Litter.MOIST_MAX, band = Litter.IDEAL_MOIST_LO to Litter.IDEAL_MOIST_HI, idealText = Fmt.n(Litter.IDEAL_MOIST_LO, 0) + "–" + Fmt.n(Litter.IDEAL_MOIST_HI, 0), sensor = true),
            PLine("Litter temp", v(l.tempC, 1, PR), "°C", rangeTrend(l.tempC, d.litterTemp.first, d.litterTemp.third), ideal = d.litterTemp.second, actual = l.tempC,
                min = d.litterTemp.first, max = d.litterTemp.third, sensor = true),
            PLine("Under drinkers", v(l.underDrinkersPct, 1, PR), "%", rangeTrend(l.underDrinkersPct, null, Litter.MOIST_MAX), actual = l.underDrinkersPct,
                max = Litter.MOIST_MAX, band = Litter.IDEAL_MOIST_LO to Litter.IDEAL_MOIST_HI, idealText = Fmt.n(Litter.IDEAL_MOIST_LO, 0) + "–" + Fmt.n(Litter.IDEAL_MOIST_HI, 0)),
            PLine("Ammonia", v(l.nh3Ppm, 1, PR), "ppm", rangeTrend(l.nh3Ppm, null, Litter.NH3_MAX), actual = l.nh3Ppm, max = Litter.NH3_MAX,
                band = 0.0 to Litter.NH3_IDEAL, idealText = "≤ " + Fmt.n(Litter.NH3_IDEAL, 0), sensor = true),
            PLine("Depth", v(l.depthCm, 1, PR), "cm", null, actual = l.depthCm, band = Litter.IDEAL_DEPTH_LO to Litter.IDEAL_DEPTH_HI,
                idealText = Fmt.n(Litter.IDEAL_DEPTH_LO, 0) + "–" + Fmt.n(Litter.IDEAL_DEPTH_HI, 0), sub = "bedding " + Fmt.n(l.beddingCm, 1), subKind = I),
            PLine("Caked floor", v(l.cakedPct, 1, PR), "%", rangeTrend(l.cakedPct, null, 20.0), ideal = 0.0, actual = l.cakedPct, max = 20.0)
        )
        else -> buildList {
            add(PLine("Temperature", v(h?.air?.tempC ?: e.tempIdeal, 1, if (h != null) PR else I), "°C", rangeTrend(h?.air?.tempC, e.tempMin, e.tempMax),
                ideal = e.tempIdeal, actual = h?.air?.tempC, min = e.tempMin, max = e.tempMax, sensor = true))
            add(PLine("Humidity", v(h?.air?.rhPct ?: e.rhIdeal, 1, if (h != null) PR else I), "%", rangeTrend(h?.air?.rhPct, e.rhMin, e.rhMax),
                ideal = e.rhIdeal, actual = h?.air?.rhPct, min = e.rhMin, max = e.rhMax, sensor = true))
            add(PLine("Birds feel", v(h?.r?.feltC, 1, PR), "°C", h?.r?.feltC?.let { trendOf(it, d.plan.comfort, Better.CLOSER) },
                refs = listOf("ideal " + Fmt.n(d.plan.comfort, 1) to kindColor(I)), sub = h?.r?.state?.label ?: "weather off"))
            add(PLine("Air speed", v(fpm, 1, PR), "ft/min", rangeTrend(fpm, null, maxAir), actual = fpm, max = maxAir, sensor = true))
            add(PLine("Static pressure", v(e.measuredPressure, 1, P), "Pa", rangeTrend(e.measuredPressure, 20.0, 25.0), actual = e.measuredPressure,
                band = 20.0 to 25.0, idealText = "20–25", sensor = true))
            add(PLine("CO₂", v(e.measuredCo2, 0, P), "ppm", rangeTrend(e.measuredCo2, null, e.co2Max), actual = e.measuredCo2, max = e.co2Max, sensor = true))
            add(PLine("Outside", v(sky?.outC ?: out?.second, 1, wx), "°C", null,
                refs = listOfNotNull(Fmt.n(sky?.outRh ?: out?.third, 0) + " % RH" to kindColor(wx)),
                sub = d.env?.let { "low " + Fmt.n(it.outMinC, 1) + " · high " + Fmt.n(it.outMaxC, 1) }, subKind = wx))
            add(PLine("Body temp", v(h?.r?.bodyTempC, 1, PR), "°C", h?.r?.bodyTempC?.let { rangeTrend(it, d.bodyTemp.first, d.bodyTemp.third) },
                ideal = d.bodyTemp.second, actual = h?.r?.bodyTempC, min = d.bodyTemp.first, max = d.bodyTemp.third))
            add(PLine("Panting now", vt(pct(needs.panting), PR), "%", null, refs = emptyList(), sub = "likelihood"))
            add(PLine("Light", v(e.lightHours, 1, I), "h", null,
                refs = listOf("ideal " + Fmt.n(d.lightLux.first, 0) + "–" + Fmt.n(d.lightLux.second, 0) + " lux" to kindColor(I)), sub = "dark " + Fmt.n(24 - e.lightHours, 1) + " h", subKind = I, sensor = true))
            if (d.envOn && d.env != null) add(PLine("Weather on the day", vt(Fmt.signed((d.envFeedF - 1) * 100, 1), PR), "% feed", null,
                refs = listOf("water " + Fmt.signed((d.envWaterF - 1) * 100, 1) + " %" to kindColor(PR), "growth " + Fmt.signed((d.envGainF - 1) * 100, 1) + " %" to kindColor(PR))))
        }
    }
    SectionCard(GIcon.FAN, "Air and ventilation", "air", trailing = {
        Text("weather", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = if (weatherOn) 0.9f else 0.5f))
        Spacer(Modifier.width(6.dp))
        androidx.compose.material3.Switch(checked = weatherOn, onCheckedChange = onWeatherOn, modifier = Modifier.testTag("weather_switch"),
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color.White,
                uncheckedThumbColor = Color.White.copy(alpha = 0.7f), uncheckedTrackColor = Color.Black, uncheckedBorderColor = Color.White.copy(alpha = 0.5f)))
    }) {
        PickChips(listOf("air" to "Air", "fans" to "Fans", "pads" to "Pads", "litter" to "Litter"), view, { view = it }, "air_view")
        FarmIso(iso, iv, zoomable = false, layer = layer)
        if (iv == IsoView.FANS) FanBank(iso)
        if (layer != null && map != null) LayerKey(map, layer)
        Text("No sensors yet · projected from the weather and the flock", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
        LineList(lines)
        if (view == "litter") LitterSideView(l, d.litterTemp.second)
    }
    // the fans for another outside temperature: turn the dials (fans view)
    if (view == "fans") VentSection(d, hour)
}

/** The fans on the end wall, numbered, those running now lit — in the order the controller starts them. */
@Composable
private fun FanBank(f: IsoFarm) {
    if (f.fans <= 0) return
    val lit = f.fanOrder.take(f.fansRunning.roundToInt().coerceIn(if (f.fansRunning > 0.05) 1 else 0, f.fans)).toSet()
    // time for the blades: a running fan turns; on a timer it turns for its on-share of each cycle (shown 20 × faster)
    var t by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) { while (true) androidx.compose.runtime.withFrameNanos { t = (it / 1_000_000L % 3_600_000L) / 1000f } }
    val cycle = (f.fanOnS + f.fanOffS).takeIf { it > 0 }
    val onNow = cycle == null || ((t * 20f) % cycle) < f.fanOnS
    Row(Modifier.fillMaxWidth().testTag("fan_bank"), horizontalArrangement = Arrangement.SpaceEvenly) {
        (1..f.fans).forEach { k ->
            val on = k in lit
            val spinning = on && onNow
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(22.dp)) {
                    val r = size.minDimension / 2 - 1.5f
                    if (on) drawCircle(Color.White.copy(alpha = if (spinning) 0.25f else 0.12f), r, center)
                    drawCircle(if (on) Color.White else Color.White.copy(alpha = 0.35f), r, center, style = Stroke(1.5f))
                    val turn = if (spinning) t * 12.0 + k else 0.3 * k
                    for (b in 0 until 3) { val a = b * 2 * Math.PI / 3 + turn
                        drawLine(if (on) Color.White else Color.White.copy(alpha = 0.3f), center, Offset(center.x + (r * 0.8f * kotlin.math.cos(a)).toFloat(), center.y + (r * 0.8f * kotlin.math.sin(a)).toFloat()), 1.5f) }
                }
                Text("$k", style = MaterialTheme.typography.labelSmall.copy(fontFamily = com.example.ui.theme.NumberFont),
                    color = if (on) Color.White else Color.White.copy(alpha = 0.45f))
            }
        }
    }
}

// =================================== 5. water ===================================

/** Water: the refills, and the water numbers with their ideals (no house drawing: the numbers say it). */
@Composable
fun WaterSection(d: OutputData, needs: com.example.flock.domain.FlockNeeds.State, onFarmChange: ((com.example.flock.data.FarmEntity) -> Unit)?) {
    val e = d.e
    val factor = if (d.refillF > 0) d.refillF else 1.0
    val lim = d.walkLimits
    val lines = buildList {
        add(PLine("Water today", v(d.waterL, 0, PR), "L", trendOf(d.waterL, d.waterIdealL, Better.CLOSER), null, d.waterIdealL, 0, Better.CLOSER,
            sub = "${d.waterRefills} " + (if (d.waterRefills == 1) "refill" else "refills") + " of " + d.tankL.roundToInt() + " L", subKind = PR, actual = d.waterL, tag = "tile_water"))
        add(PLine("Water per bird", v(d.waterPerBird, 1, PR), "mL", trendOf(d.waterPerBird, d.waterIdealPerBird, Better.CLOSER), null,
            d.waterIdealPerBird, 1, Better.CLOSER, actual = d.waterPerBird))
        add(PLine("Water per hour, lit", v(d.waterL / max(0.5, e.lightHours), 1, PR), "L", null,
            refs = listOf("ideal " + Fmt.n(e.drinkerFlowLHrLine.takeIf { it > 0 }, 1) + " L/h per line" to kindColor(I))))
        add(PLine("Walk to water", v(d.waterWalkM, 2, PR), "m", rangeTrend(d.waterWalkM, null, lim.maxM),
            refs = listOf("ideal ≤ " + Fmt.n(lim.safeM, 1) to kindColor(I), "max " + Fmt.n(lim.maxM, 1) to kindColor(MX)), actual = d.waterWalkM, max = lim.maxM, band = 0.0 to lim.safeM,
            tag = "line_walk_water", extra = { WalkWaterDrawing(d) }))
        add(PLine("Birds per nipple", v(d.birdsPerNipple, 1, PR), "", d.birdsPerNipple?.let { rangeTrend(it, null, d.birdsPerNippleMax) },
            refs = listOf("max " + Fmt.n(d.birdsPerNippleMax, 0) to kindColor(MX)), actual = d.birdsPerNipple, max = d.birdsPerNippleMax))
        add(PLine("Nipple height", v(d.drinkerHtIn, 1, I), "in", null, refs = listOf("pressure " + Fmt.n(e.drinkerPressureIn.takeIf { it > 0 }, 2) + " in" to kindColor(I))))
        add(PLine("Nipple flow", vt(Fmt.n(d.nippleFlow.first, 0) + "–" + Fmt.n(d.nippleFlow.second, 0), I), "mL/min", null, refs = emptyList(), sub = "for the age", sensor = true))
        add(PLine("Water temp", v(e.waterTempC, 1, P), "°C", rangeTrend(e.waterTempC, 18.0, 21.0), actual = e.waterTempC, band = 18.0 to 21.0, idealText = "18–21", sensor = true))
        add(PLine("Water pH", v(e.waterPh, 2, P), "", rangeTrend(e.waterPh, 6.0, 6.8), actual = e.waterPh, band = 6.0 to 6.8, idealText = "6.0–6.8", sensor = true))
        add(PLine("Thirsty now", vt(pct(needs.thirsty), PR), "%", null, refs = emptyList(), sub = "likelihood"))
    }
    SectionCard(GIcon.WATER, "Water", "water") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("refill ×", style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1, softWrap = false)
            listOf(1.0, 2.0, 3.0, 4.0).forEach { mlt ->
                val on = abs(factor - mlt) < 0.01
                Text("${mlt.roundToInt()}×", style = MaterialTheme.typography.labelLarge.copy(fontFamily = com.example.ui.theme.NumberFont),
                    color = if (on) Color.Black else Color.White, maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).background(if (on) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = if (on) 1f else 0.35f), RoundedCornerShape(8.dp))
                        .clickable(enabled = onFarmChange != null) { onFarmChange?.invoke(d.farm.copy(waterRefillFactor = mlt)) }.padding(vertical = 6.dp)
                        .testTag("refill_${mlt.toInt()}"))
            }
        }
        LineList(lines)
    }
}

// =================================== 6. trends (with the graphs and the book) ===================================

/**
 * The flock's trends that rest on what was counted and weighed: the start, the removals, the average daily gain, the
 * first week's and the last week's deaths. (v54: no FCR over 7 days — daily feed entries swing too much — no "days
 * ahead", no 3 / 7-day advice, no projection check.)
 */
@Composable
fun TrendsSection(d: OutputData) {
    val k = d.kpis
    val lines = listOf(
        PLine("Placed", vi(d.placed, P), "", null, refs = listOf("started " + Fmt.i(d.entryBirds) to kindColor(P)),
            sub = "reception ${d.reception} · " + Fmt.n(d.receptionPct, 2) + " %", subKind = P),
        PLine("Removed", vi(d.mortTD + d.lameTD, d.mk), "", null, refs = listOf("dead ${d.mortTD}" to kindColor(d.mk), "culls ${d.lameTD}" to kindColor(P))),
        PLine("Daily gain, average", v(k.adg, 1, d.vk), "g", trendOf(k.adg, d.adgCom, Better.HIGHER), d.adgCom, d.adgIdeal, 1, actual = k.adg),
        PLine("First week deaths", v(k.firstWeekMortPct, 2, if (k.firstWeekComplete) P else PR), "%", trendOf(k.firstWeekMortPct, d.firstWeekMortCom, Better.LOWER),
            d.firstWeekMortCom, d.firstWeekMortIdeal, 2, Better.LOWER, actual = k.firstWeekMortPct),
        PLine("Deaths, last 7 days", v(k.mort7Pct, 2, d.mk), "%", trendOf(k.mort7Pct, d.mort7Com, Better.LOWER), d.mort7Com, null, 2, Better.LOWER, actual = k.mort7Pct)
    )
    SectionCard(GIcon.TREND, "Trends", "trends") { LineList(lines) }
}

/**
 * Two handles on a thin line (v56: the Material slider was too thick): drag the nearer handle, or tap to move it there.
 * [from] and [to] are in 0…[max]; [onChange] gets the handles where the finger puts them.
 */
@Composable
fun ThinRangeSlider(from: Float, to: Float, max: Float, onChange: (Float, Float) -> Unit, modifier: Modifier = Modifier) {
    val cur by androidx.compose.runtime.rememberUpdatedState(from to to)
    val change by androidx.compose.runtime.rememberUpdatedState(onChange)
    fun moved(xPx: Float, width: Float, which: Int) {
        val v = (xPx / width * max).coerceIn(0f, max)
        if (which == 0) change(minOf(v, cur.second), cur.second) else change(cur.first, maxOf(v, cur.first))
    }
    Canvas(modifier.fillMaxWidth().height(26.dp)
        .pointerInput(max) {
            var which = 0
            detectHorizontalDragGestures(onDragStart = { o ->
                val w = size.width.toFloat(); which = if (abs(o.x - cur.first / max * w) <= abs(o.x - cur.second / max * w)) 0 else 1
            }) { ch, _ -> ch.consume(); moved(ch.position.x, size.width.toFloat(), which) }
        }
        .pointerInput(max) {
            detectTapGestures { o -> val w = size.width.toFloat()
                moved(o.x, w, if (abs(o.x - cur.first / max * w) <= abs(o.x - cur.second / max * w)) 0 else 1) }
        }) {
        val y = size.height / 2
        val xa = from / max * size.width; val xb = to / max * size.width
        drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx())
        drawLine(Color.White.copy(alpha = 0.9f), Offset(xa, y), Offset(xb, y), 2.dp.toPx())
        listOf(xa, xb).forEach { x -> drawCircle(Color.White, 6.dp.toPx(), Offset(x, y)); drawCircle(Color.Black, 2.dp.toPx(), Offset(x, y)) }
    }
}
