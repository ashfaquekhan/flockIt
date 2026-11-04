package com.example.flock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * One number of a section: its sign, its name, the value in the colour of what it is (entered, projected …) with the
 * arrow against the company, then the company's and the ideal (or the limits that apply) and the line between them.
 * [sensor] tiles show where a house sensor's reading will go (none yet).
 */
class PTile(
    val icon: GIcon, val name: String, val value: V, val unit: String = "",
    val trend: TrendMark? = null,
    val com: Double? = null, val ideal: Double? = null, val dec: Int = 1,
    /** the ideal as a band or a limit ("≤ 8.0", "26.5–29.5") when it is not one number */
    val idealText: String? = null,
    /** lines in place of company and ideal, each in its own colour */
    val refs: List<Pair<String, Color>>? = null,
    val actual: Double? = null,
    val sensor: Boolean = false,
    val wide: Boolean = false,
    val tag: String? = null,
    val extra: (@Composable () -> Unit)? = null
)

@Composable
fun ParamTile(t: PTile, modifier: Modifier = Modifier) {
    val col = kindColor(if (t.value.text == "—") NE else t.value.kind)
    val mono = MaterialTheme.typography.labelMedium.copy(fontFamily = com.example.ui.theme.NumberFont, letterSpacing = (-0.3).sp)
    fun f(x: Double) = if (t.dec == 0) Fmt.i(Math.round(x).toInt()) else Fmt.n(x, t.dec)
    // long values (a pan series, a range) in smaller type, so they never break
    val len = t.value.text.length + (if (t.unit.isNotEmpty()) t.unit.length / 2 + 1 else 0)
    val big = when { len <= 7 -> 21.sp; len <= 10 -> 18.sp; len <= 13 -> 15.sp; else -> 13.sp }
    Column(modifier.border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 7.dp)
        .let { if (t.tag != null) it.testTag(t.tag) else it }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GameIcon(t.icon, 26.dp)
            Spacer(Modifier.width(6.dp))
            Text(t.name, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 2, modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(t.value.text, style = MaterialTheme.typography.titleLarge.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Medium,
                fontSize = big, letterSpacing = (-0.5).sp), color = col, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f, fill = false))
            if (t.unit.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(t.unit, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f), maxLines = 1, softWrap = false,
                    modifier = Modifier.padding(bottom = 3.dp))
            }
            if (t.trend != null) { Spacer(Modifier.width(4.dp)); Box(Modifier.padding(bottom = 5.dp)) { TrendIcon(t.trend, 11.dp) } }
        }
        if (t.refs != null) t.refs.forEach { (s, c) -> Text(s, style = mono, color = c, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
        else {
            Text("co " + (t.com?.let { f(it) } ?: "NA"), style = mono, color = kindColor(C).copy(alpha = if (t.com == null) 0.55f else 1f), maxLines = 1, softWrap = false)
            Text("id " + (t.idealText ?: t.ideal?.let { f(it) } ?: "NA"), style = mono,
                color = kindColor(I).copy(alpha = if (t.ideal == null && t.idealText == null) 0.55f else 1f), maxLines = 1, softWrap = false)
            MiniCompare(t.actual, t.value.kind, t.com, t.ideal)
        }
        if (t.sensor) Row(verticalAlignment = Alignment.CenterVertically) {
            GameIcon(GIcon.SENSOR, 14.dp, dim = true)
            Spacer(Modifier.width(4.dp))
            Text("sensor —", style = mono, color = Color.White.copy(alpha = 0.45f), maxLines = 1, softWrap = false)
        }
        t.extra?.invoke()
    }
}

/** Tiles two to a row (a wide one takes its row), the two of a row as tall as each other. */
@Composable
fun TileGrid(tiles: List<PTile>) {
    val rows = mutableListOf<List<PTile>>()
    var cur = mutableListOf<PTile>()
    tiles.forEach { t ->
        if (t.wide) { if (cur.isNotEmpty()) { rows += cur; cur = mutableListOf() }; rows += listOf(t) }
        else { cur += t; if (cur.size == 2) { rows += cur; cur = mutableListOf() } }
    }
    if (cur.isNotEmpty()) rows += cur
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            if (row.size == 1 && row[0].wide) ParamTile(row[0], Modifier.fillMaxWidth())
            else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { ParamTile(it, Modifier.weight(1f).fillMaxHeight()) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** A section of the page: its sign, its name (and what sits beside it), its ⓘ, then what it holds. */
@Composable
fun SectionCard(icon: GIcon, title: String, info: String?, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null,
                content: @Composable ColumnScope.() -> Unit) {
    val anchors = LocalScrollAnchors.current
    GlassBox(modifier.fillMaxWidth().onGloballyPositioned { anchors?.report("card:$title", it.positionInRoot().y) }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GameIcon(icon, 30.dp)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, softWrap = false,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                trailing?.invoke()
                if (info != null) { Spacer(Modifier.width(6.dp)); InfoButton(info) }
            }
            content()
        }
    }
}

/** A row of choices, the chosen one white; each with its sign when given, and a small word under it when given. */
@Composable
fun PickChips(items: List<Triple<String, String, GIcon?>>, current: String, onPick: (String) -> Unit, tagPrefix: String, subs: Map<String, String> = emptyMap()) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (key, label, icon) ->
            val on = key == current
            Column(Modifier.weight(1f).background(if (on) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                .border(1.dp, Color.White.copy(alpha = if (on) 1f else 0.4f), RoundedCornerShape(10.dp)).clickable { onPick(key) }
                .padding(horizontal = 4.dp, vertical = 5.dp).testTag("${tagPrefix}_$key"), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) { GameIcon(icon, 18.dp); Spacer(Modifier.width(4.dp)) }
                    Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) Color.Black else Color.White, maxLines = 1, softWrap = false)
                }
                subs[key]?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = if (on) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.6f), maxLines = 1, softWrap = false) }
            }
        }
    }
}

private fun pct(x: Double) = Fmt.n(x * 100, 1)

// =================================== 1. birds: the window and the seven numbers ===================================

/**
 * The birds in seven numbers, in the farm's order — live birds, weight, FCR, cFCR, mortality, CV (with how the weights
 * spread) and stocking density — each with the company and the ideal; the one-score radar under them.
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
    val tiles = listOf(
        PTile(GIcon.LIVE, "Live birds", if (d.mortEntered) vi(d.live, P) else vi(Math.round(view.live).toInt(), PR),
            trend = trendOf(liveNow, d.liveCom, Better.HIGHER, 1.0), com = d.liveCom, ideal = d.liveIdeal, dec = 0, actual = liveNow, tag = "tile_live"),
        PTile(GIcon.WEIGHT, "Weight", v(weight, 1, d.vk), "g", trendOf(weight, d.bwCom, Better.HIGHER), d.bwCom, d.bwIdeal, 1, actual = weight),
        PTile(GIcon.FCR, "FCR", v(fcr, 3, d.fcrKind), "", trendOf(fcr, d.fcrCom, Better.LOWER), d.fcrCom, d.fcrIdeal, 3, actual = fcr),
        PTile(GIcon.CFCR, "cFCR 2 kg", v(m?.cFcrCompany, 3, d.fcrKind), "", trendOf(m?.cFcrCompany, d.cfcrCom, Better.LOWER), d.cfcrCom, d.cfcrIdeal, 3,
            actual = m?.cFcrCompany),
        PTile(GIcon.MORTALITY, "Mortality", v(mort, 2, d.mk), "%", trendOf(mort, d.comCumPct, Better.LOWER), d.comCumPct, d.ceilingPct, 2, actual = mort),
        PTile(GIcon.DENSITY, "Stocking density", v(s.kgM2, 1, d.vk), "kg/m²", trendOf(s.kgM2, s.targetKgM2, Better.LOWER, 5.0),
            refs = listOf("id ≤ " + Fmt.n(s.targetKgM2, 1) to kindColor(I), "max " + Fmt.n(d.farm.densityCapDefault, 1) to kindColor(MX),
                Fmt.n(s.ft2PerBird, 2) + " ft²/bird" to Color.White.copy(alpha = 0.75f))),
        PTile(GIcon.CV, "CV, how even", v(d.cvEst, 1, if (d.cvMeasured) P else PR), "%", d.cvEst?.let { rangeTrend(it, null, 10.0) }, null, 8.0, 1,
            idealText = "≤ 8.0", actual = d.cvEst, wide = true, tag = "tile_cv", extra = {
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
            })
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
        TileGrid(tiles)
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

/**
 * Feed and floor: how the floor is set (barricades or pans), how many feedings, the whole house with every pan as the
 * plan opens them and the barricades where they go — then the bags, the grams a bird, yesterday's gain and today's from
 * the curve, the pans, the barricades, the walk to feed and the rest of the feed numbers.
 */
@Composable
fun FeedSection(d: OutputData, pick: Int, onPick: (Int) -> Unit, needs: com.example.flock.domain.FlockNeeds.State,
                onFarmChange: ((com.example.flock.data.FarmEntity) -> Unit)?) {
    val o = d.feedOptions[pick.coerceIn(0, d.feedOptions.size - 1)]
    val pat = d.patternFor(pick)
    val plan = d.floorPlan
    val from = plan?.penStartFt ?: d.penStartFt
    val to = plan?.penFt ?: d.barricadeFtNow
    val houseL = d.farm.usableLengthFt
    // barricades the plan puts where none stand yet are drawn dashed
    val planned = abs(from - d.penStartFt) > 0.5 || abs(to - d.barricadeFtNow) > 0.5
    val iso = remember(d, pat, from, to, planned) { isoFarmOf(d, pat, from, to, planned, d.hour) }
    val fillBags = d.bagsToFill(pat.openPerLine)
    val perBird = d.dayBags * d.bagKg * 1000 / d.liveSafe
    val walk = d.feedWalkM(pat)
    val lim = d.walkLimits
    val used = listOf(from > 0.5, to < houseL - 0.5).count { it }
    val barText = when {
        from > 0.5 && to < houseL - 0.5 -> "${Fmt.n(from, 0)}–${Fmt.n(to, 0)}"
        to < houseL - 0.5 -> "at ${Fmt.n(to, 0)}"
        from > 0.5 -> "at ${Fmt.n(from, 0)}"
        else -> "none"
    }
    val tiles = buildList {
        add(PTile(GIcon.BAGS, "Bags to use", v(d.dayBags, 1, PR), "bags", trendOf(d.dayBags, d.giveBags, Better.CLOSER, 10.0),
            refs = listOf("${o.feedings} × ${Fmt.n(o.bagsPerFeeding, 1)} bags" to kindColor(PR), Fmt.n(o.bagsPerLine, 2) + " a line" to kindColor(PR),
                "co " + Fmt.n(d.giveBags, 1) to kindColor(C), "id " + Fmt.n(d.idealBags, 1) to kindColor(I)),
            tag = "feed_conclusion"))
        add(PTile(GIcon.GRAM, "Feed a bird", v(perBird, 1, PR), "g", trendOf(perBird, d.comPerBird, Better.CLOSER), d.comPerBird, d.idealPerBird, 1, actual = perBird))
        add(PTile(GIcon.GAIN_Y, "Yesterday's gain", v(d.gain, 1, d.gainKind), "g", trendOf(d.gain, d.gainCom, Better.HIGHER), d.gainCom, d.gainIdeal, 1, actual = d.gain))
        add(PTile(GIcon.GAIN_T, "Gain today, curve", v(d.gainToday, 1, PR), "g", null, d.gainTodayCom, d.gainTodayIdeal, 1, actual = d.gainToday))
        add(PTile(GIcon.PANS, "Pans", vt(pat.label.lowercase(), PR), "", null,
            refs = listOf("${pat.openPerLine} open a line" to kindColor(PR),
                (if (pat.birdsPerPan < 1e6) Fmt.n(pat.birdsPerPan, 1) else "—") + " a pan" to kindColor(PR),
                "max " + d.birdsPerPanMax.roundToInt() + " a pan" to kindColor(MX), "fill " + Fmt.n(fillBags, 1) + " bags" to kindColor(PR)), tag = "tile_pans"))
        add(PTile(GIcon.BARRICADE, "Barricades", vt(barText, if (planned) PR else P), if (barText == "none") "" else "ft", null,
            refs = listOf((if (planned) "plan" else "as set") + " · $used used" to Color.White.copy(alpha = 0.8f),
                "you have ${d.farm.barricades}" to Color.White.copy(alpha = 0.6f)), tag = "tile_barricades"))
        add(PTile(GIcon.WALK, "Walk to feed", v(walk, 2, PR), "m", rangeTrend(walk, null, lim.safeM),
            refs = listOf("id ≤ " + Fmt.n(lim.safeM, 1) to kindColor(I), "max " + Fmt.n(lim.maxM, 1) to kindColor(MX))))
        add(PTile(GIcon.HUNGRY, "Hungry now", vt(pct(needs.hungry), PR), "%", null, refs = listOf("likelihood" to Color.White.copy(alpha = 0.6f))))
        add(PTile(GIcon.EATEN, "Eaten yesterday", v(d.usedPerBirdY, 1, P), "g", trendOf(d.usedPerBirdY, d.comPerBirdY, Better.CLOSER), d.comPerBirdY,
            d.idealPerBirdY, 1, actual = d.usedPerBirdY))
        add(PTile(GIcon.CLOCK, "Feedings", vt(d.modelFeedTimes(o.feedings).joinToString(" "), PR), "", null,
            refs = listOf("each fills ${o.fillPct.roundToInt()} %" to Color.White.copy(alpha = 0.7f))))
        val c = d.correction
        if (c.pct != 0.0) add(PTile(GIcon.TARGET, "Correction", vt((if (c.pct > 0) "+" else "") + Fmt.n(c.pct, 1), PR), "%", null,
            refs = listOf(Fmt.n(d.correctedBags, 1) + " bags with it" to kindColor(PR))))
        if (d.day <= 7) {
            add(PTile(GIcon.TRAYS, "Feeder trays", v(kotlin.math.ceil(d.farm.manualFeeders * d.trayKeepFrac), 0, PR), "", null,
                refs = listOf("id " + Fmt.n(d.traysKeepIdeal, 0) to kindColor(I), "yours ${d.farm.manualFeeders}" to Color.White.copy(alpha = 0.6f))))
            if (d.day <= 4) add(PTile(GIcon.PAPER, "Feed paper", v(d.paperFt2, 0, I), "ft²", null, refs = listOf("≥ 70 % of the floor" to kindColor(I))))
        }
    }
    SectionCard(GIcon.BAGS, "Feed and floor", "plan") {
        // the farm's way to set a floor too big for the birds (only with barricades on the farm)
        if (d.farm.barricades > 0 && onFarmChange != null) PickChips(listOf(Triple("auto", "Auto", null), Triple("barricade", "Barricades", null),
            Triple("pans", "Pans", null)), d.farm.spaceBy, { onFarmChange(d.farm.copy(spaceBy = it)) }, "space_by")
        PickChips(d.feedOptions.mapIndexed { i, opt -> Triple(i.toString(), "${opt.feedings} ×", null as GIcon?) }, pick.toString(), { onPick(it.toInt()) },
            "feedings_pick", subs = d.feedOptions.mapIndexed { i, opt -> i.toString() to when { !opt.safe -> "too few bags"; i == d.recommendedOption -> "best"; else -> "ok" } }.toMap())
        Text(d.space.why, style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.testTag("space_why"))
        FarmIso(iso, IsoView.FEED)
        KeyLine(kindColor(PR) to "pan on", Color.White.copy(alpha = 0.55f) to "off", Color.White to "barricade")
        TileGrid(tiles)
    }
}

// =================================== 3. air and ventilation ===================================

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
    val wx = if (d.outsideSource == com.example.flock.domain.BirdEnvironment.Source.WEATHER) NE else PR
    fun range(lo: Double?, hi: Double?, dec: Int = 1) = listOfNotNull(lo?.let { "min " + Fmt.n(it, dec) to kindColor(MN) }, hi?.let { "max " + Fmt.n(it, dec) to kindColor(MX) })
    val tiles: List<PTile> = when (view) {
        "fans" -> listOf(
            PTile(GIcon.FAN, "Min vent air", v(d.minAvgCfm / live, 3, PR), "cfm/bird", trendOf(d.minAvgCfm, d.plan.needCfm, Better.HIGHER),
                refs = listOf("id " + Fmt.n(d.plan.needCfm / live, 3) to kindColor(I), "house " + Fmt.n(d.minAvgCfm, 0) + " cfm" to kindColor(PR))),
            PTile(GIcon.TIMER, "Fan timer", vt(if (lv.isTimer) "${lv.on}/${lv.off}" else "on", I), if (lv.isTimer) "s" else "", null,
                refs = listOf("fan " + lv.cyc.joinToString(",").ifEmpty { lv.cont.joinToString(",") } to kindColor(I),
                    "cycle " + (if (lv.isTimer) "${lv.on + lv.off} s" else "running") to Color.White.copy(alpha = 0.7f))),
            PTile(GIcon.FAN, "Fans now", v(fans, 1, PR), "", null, refs = listOf("avg min vent " + Fmt.n(lv.avgFans, 2) to kindColor(I),
                "allowed ${d.plan.maxFans}" to kindColor(MX))),
            PTile(GIcon.WIND, "Air speed", v(fpm, 1, PR), "ft/min", rangeTrend(fpm, null, PhysiologicalEngine.maxAirSpeedFpm(d.day)),
                refs = range(null, PhysiologicalEngine.maxAirSpeedFpm(d.day)), sensor = true),
            PTile(GIcon.AIRCHANGE, "Air changes", v(if (d.houseVolFt3 > 0) d.minAvgCfm * 60 / d.houseVolFt3 else null, 1, PR), "/h", null,
                refs = listOf("id " + Fmt.n(if (d.houseVolFt3 > 0) d.plan.needCfm * 60 / d.houseVolFt3 else null, 1) to kindColor(I))),
            PTile(GIcon.CONTROL, "Controller", v(d.plan.set, 1, I), "°C SET", null,
                refs = listOf("heat on " + Fmt.n(d.plan.heat, 1) to kindColor(I), "comfort " + Fmt.n(d.plan.comfort, 1) to kindColor(I)))
        )
        "pads" -> listOf(
            PTile(if ((padNow?.padEff ?: 0.0) > 0.05) GIcon.PAD_WET else GIcon.PAD_DRY, "Pads now", vt(if ((padNow?.padEff ?: 0.0) > 0.05) "wet" else "dry", PR), "", null,
                refs = listOf("cooling " + Fmt.n((padNow?.padEff ?: 0.0) * 100, 0) + " %" to kindColor(PR)), sensor = true),
            PTile(GIcon.PANT, "Hottest hour", vt(if (hot.padEff > 0.05) "wet" else "dry", PR), "", null,
                refs = listOf("%02d:00 · ".format(d.hottestHour) + Fmt.n(d.hottestC, 1) + " °C" to kindColor(PR), "cooling " + Fmt.n(hot.padEff * 100, 0) + " %" to kindColor(PR))),
            PTile(GIcon.PAD_WET, "Pad layout", vt(if (d.farm.padLayout == "c") "C-type" else "side by side", NE), "", null,
                refs = listOf(Fmt.n(d.farm.padAreaFt2, 0) + " ft² · ${d.farm.padCount} pads" to Color.White.copy(alpha = 0.7f))),
            PTile(GIcon.HUMIDITY, "Outside air", v(out?.third, 0, wx), "% RH", null,
                refs = listOf(Fmt.n(out?.second, 1) + " °C" to kindColor(wx), "pads cool less in damp air" to Color.White.copy(alpha = 0.55f))),
            PTile(GIcon.TIMER, "Wet / dry", vt(listOf(e.padWetMin, e.padDryMin).joinToString("/") { Fmt.n(it, 0) }, P), "min", null,
                refs = listOf("as entered" to Color.White.copy(alpha = 0.6f)), sensor = true)
        )
        "litter" -> listOf(
            PTile(GIcon.MOIST, "Moisture", v(l.moisturePct, 1, PR), "%", rangeTrend(l.moisturePct, null, Litter.IDEAL_MOIST_HI),
                refs = listOf("id " + Fmt.n(Litter.IDEAL_MOIST_LO, 0) + "–" + Fmt.n(Litter.IDEAL_MOIST_HI, 0) to kindColor(I), "max " + Fmt.n(Litter.MOIST_MAX, 0) to kindColor(MX)), sensor = true),
            PTile(GIcon.LITTER_TEMP, "Litter temp", v(l.tempC, 1, PR), "°C", rangeTrend(l.tempC, d.litterTemp.first, d.litterTemp.third),
                refs = listOf("id " + Fmt.n(d.litterTemp.second, 1) to kindColor(I)) + range(d.litterTemp.first, d.litterTemp.third), sensor = true),
            PTile(GIcon.DRINKER, "Under drinkers", v(l.underDrinkersPct, 1, PR), "%", rangeTrend(l.underDrinkersPct, null, Litter.MOIST_MAX),
                refs = listOf("max " + Fmt.n(Litter.MOIST_MAX, 0) to kindColor(MX))),
            PTile(GIcon.AMMONIA, "Ammonia", v(l.nh3Ppm, 1, PR), "ppm", rangeTrend(l.nh3Ppm, null, Litter.NH3_MAX),
                refs = listOf("id ≤ " + Fmt.n(Litter.NH3_IDEAL, 0) to kindColor(I), "max " + Fmt.n(Litter.NH3_MAX, 0) to kindColor(MX)), sensor = true),
            PTile(GIcon.DEPTH, "Depth", v(l.depthCm, 1, PR), "cm", null,
                refs = listOf("id " + Fmt.n(Litter.IDEAL_DEPTH_LO, 0) + "–" + Fmt.n(Litter.IDEAL_DEPTH_HI, 0) to kindColor(I), "bedding " + Fmt.n(l.beddingCm, 1) to Color.White.copy(alpha = 0.6f))),
            PTile(GIcon.LITTER, "Caked floor", v(l.cakedPct, 1, PR), "%", rangeTrend(l.cakedPct, null, 20.0), refs = listOf("max 20" to kindColor(MX)))
        )
        else -> buildList {
            add(PTile(GIcon.TEMP, "Temperature", v(h?.air?.tempC ?: e.tempIdeal, 1, if (h != null) PR else I), "°C",
                rangeTrend(h?.air?.tempC, e.tempMin, e.tempMax), refs = listOf("id " + Fmt.n(e.tempIdeal, 1) to kindColor(I)) + range(e.tempMin, e.tempMax), sensor = true))
            add(PTile(GIcon.HUMIDITY, "Humidity", v(h?.air?.rhPct ?: e.rhIdeal, 1, if (h != null) PR else I), "%",
                rangeTrend(h?.air?.rhPct, e.rhMin, e.rhMax), refs = listOf("id " + Fmt.n(e.rhIdeal, 1) to kindColor(I)) + range(e.rhMin, e.rhMax), sensor = true))
            add(PTile(GIcon.FEELS, "Birds feel", v(h?.r?.feltC, 1, PR), "°C", h?.r?.feltC?.let { trendOf(it, d.plan.comfort, Better.CLOSER) },
                refs = listOf("id " + Fmt.n(d.plan.comfort, 1) to kindColor(I), (h?.r?.state?.label ?: "weather off") to Color.White.copy(alpha = 0.6f))))
            add(PTile(GIcon.WIND, "Air speed", v(fpm, 1, PR), "ft/min", rangeTrend(fpm, null, PhysiologicalEngine.maxAirSpeedFpm(d.day)),
                refs = range(null, PhysiologicalEngine.maxAirSpeedFpm(d.day)), sensor = true))
            add(PTile(GIcon.PRESSURE, "Static pressure", v(e.measuredPressure, 1, P), "Pa", rangeTrend(e.measuredPressure, 20.0, 25.0),
                refs = listOf("id 20–25" to kindColor(I)), sensor = true))
            add(PTile(GIcon.GAS, "CO₂", v(e.measuredCo2, 0, P), "ppm", rangeTrend(e.measuredCo2, null, e.co2Max), refs = range(null, e.co2Max, 0), sensor = true))
            add(PTile(GIcon.OUTSIDE, "Outside", v(sky?.outC ?: out?.second, 1, wx), "°C", null,
                refs = listOfNotNull(Fmt.n(sky?.outRh ?: out?.third, 0) + " % RH" to kindColor(wx),
                    d.env?.let { "low " + Fmt.n(it.outMinC, 1) to kindColor(wx) }, d.env?.let { "high " + Fmt.n(it.outMaxC, 1) to kindColor(wx) })))
            add(PTile(GIcon.BODY, "Body temp", v(h?.r?.bodyTempC, 1, PR), "°C", h?.r?.bodyTempC?.let { rangeTrend(it, d.bodyTemp.first, d.bodyTemp.third) },
                refs = listOf("id " + Fmt.n(d.bodyTemp.second, 1) to kindColor(I)) + range(d.bodyTemp.first, d.bodyTemp.third)))
            add(PTile(GIcon.PANT, "Panting now", vt(pct(needs.panting), PR), "%", null, refs = listOf("likelihood" to Color.White.copy(alpha = 0.6f))))
            add(PTile(GIcon.LIGHT, "Light", v(e.lightHours, 1, I), "h", null,
                refs = listOf("dark " + Fmt.n(24 - e.lightHours, 1) + " h" to kindColor(I), "id " + Fmt.n(d.lightLux.first, 0) + "–" + Fmt.n(d.lightLux.second, 0) + " lux" to kindColor(I)), sensor = true))
            if (d.envOn && d.env != null) add(PTile(GIcon.OUTSIDE, "Weather on the day", vt(Fmt.signed((d.envFeedF - 1) * 100, 1), PR), "% feed", null,
                refs = listOf("water " + Fmt.signed((d.envWaterF - 1) * 100, 1) + " %" to kindColor(PR), "growth " + Fmt.signed((d.envGainF - 1) * 100, 1) + " %" to kindColor(PR))))
        }
    }
    SectionCard(GIcon.FAN, "Air and ventilation", "air", trailing = {
        GameIcon(GIcon.OUTSIDE, 20.dp, dim = !weatherOn)
        Spacer(Modifier.width(2.dp))
        androidx.compose.material3.Switch(checked = weatherOn, onCheckedChange = onWeatherOn, modifier = Modifier.testTag("weather_switch"),
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color.White,
                uncheckedThumbColor = Color.White.copy(alpha = 0.7f), uncheckedTrackColor = Color.Black, uncheckedBorderColor = Color.White.copy(alpha = 0.5f)))
    }) {
        PickChips(listOf(Triple("air", "Air", GIcon.TEMP), Triple("fans", "Fans", GIcon.FAN), Triple("pads", "Pads", GIcon.PAD_WET),
            Triple("litter", "Litter", GIcon.LITTER)), view, { view = it }, "air_view")
        FarmIso(iso, iv, zoomable = false, layer = layer)
        if (iv == IsoView.FANS) FanBank(iso)
        if (layer != null && map != null) LayerKey(map, layer)
        Text("No sensors yet · projected from the weather and the flock", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
        TileGrid(tiles)
        if (view == "litter") LitterSideView(l, d.litterTemp.second)
    }
    // the fans for another outside temperature: turn the dials (fans view)
    if (view == "fans") VentSection(d, hour)
}

// =================================== 4. lifting ===================================

/** Where the flock's meat is (in the house, lifted, dead), the lifting, the FCRs on what was produced, the target and harvest. */
@Composable
fun LiftSection(d: OutputData, onFlockPlan: ((Double, Int) -> Unit)?) {
    val m = d.mass
    val g = d.growth
    var edit by remember { mutableStateOf(false) }
    val tiles = buildList {
        val lifted = m?.liftedBirds ?: d.liftTD
        val days = d.rows.filter { it.birdsLifted > 0 && it.dayNumber <= d.day }.map { it.dayNumber }
        add(PTile(GIcon.CRATE, "Birds lifted", vi(lifted, P), "", null, refs = listOf(
            (if (days.isEmpty()) "none yet" else "days " + days.joinToString(",")) to Color.White.copy(alpha = 0.7f),
            Fmt.n(m?.liftedKg ?: d.liftKgTD, 1) + " kg" to kindColor(P))))
        if (m != null && m.liftedBirds > 0) add(PTile(GIcon.SCALE, "Lifted, a bird", v(m.liftedKg / m.liftedBirds, 3, P), "kg", null,
            refs = listOfNotNull(if (m.liftedToday > 0) "today ${m.liftedToday} · " + Fmt.n(m.liftAvgKg, 3) + " kg" to kindColor(P) else null,
                if (m.liftedToday > 0) "left " + Fmt.n(m.avgAfterKg * 1000, 0) + " g" to kindColor(PR) else null)))
        add(PTile(GIcon.HEART, "Livability", v(d.e.livability, 2, d.mk), "%", trendOf(d.e.livability, 100 - d.comCumPct, Better.HIGHER, 1.0),
            100 - d.comCumPct, 100 - d.ceilingPct, 2, actual = d.e.livability))
        add(PTile(GIcon.EPEF, "EPEF", v(d.epef, 1, d.fcrKind), "", trendOf(d.epef, d.epefCom, Better.HIGHER), d.epefCom, d.epefIdeal, 1, actual = d.epef))
        if (m != null) {
            add(PTile(GIcon.MEAT, "Produced", v(m.producedKg, 0, PR), "kg", null, refs = listOf(Fmt.n(m.cumBwKg, 3) + " kg a bird" to kindColor(PR),
                "dead " + Fmt.n(m.deadKg, 0) + " kg" to kindColor(PR))))
            add(PTile(GIcon.FCR, "FCR, dead counted", v(m.fcrAll, 3, PR), "", trendOf(m.fcrAll, d.fcrIdeal, Better.LOWER), null, d.fcrIdeal, 3, actual = m.fcrAll))
            add(PTile(GIcon.CFCR, "cFCR, breed", v(m.cFcrBreed, 3, d.fcrKind), "", trendOf(m.cFcrBreed, d.cfcrIdeal, Better.LOWER), null, d.cfcrIdeal, 3, actual = m.cFcrBreed))
        }
        add(PTile(GIcon.TARGET, "Target", v(d.targetG, 0, I), "g", null, refs = listOf("harvest day ${d.harvestAge}" to kindColor(I),
            Fmt.n(g.share * 100, 1) + " % of company" to kindColor(if (g.samples > 0) P else PR))))
        add(PTile(GIcon.CLOCK, "Reaches target", vt(g.targetDay?.let { "day $it" } ?: "—", PR), "", null,
            refs = listOf("earliest " + (g.targetDayEarly?.toString() ?: "—") to kindColor(MN), "latest " + (g.targetDayLate?.toString() ?: "—") to kindColor(MX))))
        g.atHarvest?.let { hv -> add(PTile(GIcon.WEIGHT, "At harvest", v(hv.mean, 0, PR), "g", null,
            refs = listOf(Fmt.n(hv.low, 0) + "–" + Fmt.n(hv.high, 0) to kindColor(PR), "chance " + Fmt.n((g.chanceTargetAtHarvest ?: 0.0) * 100, 0) + " %" to kindColor(PR)))) }
    }
    SectionCard(GIcon.TRUCK, "Lifting", "lifting") {
        if (m != null) MeatBar(m)
        TileGrid(tiles)
        if (onFlockPlan != null) androidx.compose.material3.OutlinedButton(onClick = { edit = true },
            border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.GlassLine), modifier = Modifier.fillMaxWidth()) {
            Text("Set target weight and harvest day", color = Color.White)
        }
    }
    if (edit && onFlockPlan != null) TargetDialog(d, onFlockPlan) { edit = false }
}

/** The flock's meat in one bar: in the house, lifted, dead — each part with its sign and kilos. */
@Composable
private fun MeatBar(m: com.example.flock.data.FlockCalc.Mass) {
    val parts = listOf(Triple(GIcon.HOUSE, m.liveKg, kindColor(PR)), Triple(GIcon.TRUCK, m.liftedKg, kindColor(P)), Triple(GIcon.MORTALITY, m.deadKg, Color.White.copy(alpha = 0.45f)))
    val total = parts.sumOf { it.second }.takeIf { it > 0 } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("meat_bar")) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            var x = 0f
            parts.forEach { (_, kg, c) ->
                val w = (kg / total).toFloat() * size.width
                if (w > 0.5f) drawRoundRect(c, Offset(x, 0f), Size(w - 2f, size.height), androidx.compose.ui.geometry.CornerRadius(4f, 4f))
                x += w
            }
        }
        Row(Modifier.fillMaxWidth()) {
            parts.forEach { (ic, kg, c) ->
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    GameIcon(ic, 18.dp); Spacer(Modifier.width(4.dp))
                    Text(Fmt.i(kg.roundToInt()) + " kg", style = MaterialTheme.typography.labelMedium.copy(fontFamily = com.example.ui.theme.NumberFont), color = c, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** The target weight and planned harvest day of the flock. */
@Composable
internal fun TargetDialog(d: OutputData, onFlockPlan: (Double, Int) -> Unit, onDone: () -> Unit) {
    var w by remember { mutableStateOf(Fmt.n(d.targetG, 0).replace(",", "")) }
    var h by remember { mutableStateOf(d.harvestAge.toString()) }
    val wv = w.replace(",", "").toDoubleOrNull(); val hv = h.toIntOrNull()
    val ok = wv != null && wv in 500.0..6000.0 && hv != null && hv in 14..PhysiologicalEngine.MAX_FLOCK_DAY
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Target for this flock") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.OutlinedTextField(value = w, onValueChange = { w = it.filter { c -> c.isDigit() || c == '.' } }, singleLine = true,
                    label = { Text("Target weight, g (500 – 6,000)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                androidx.compose.material3.OutlinedTextField(value = h, onValueChange = { h = it.filter { c -> c.isDigit() } }, singleLine = true,
                    label = { Text("Planned harvest day (14 – ${PhysiologicalEngine.MAX_FLOCK_DAY})") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(enabled = ok, onClick = { onFlockPlan(wv!!, hv!!); onDone() }) { Text("Save") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDone) { Text("Cancel") } }
    )
}

// =================================== 5. water ===================================

/** Water: the house with its drinker lines and the tank, the refills, and the water numbers with their ideals. */
@Composable
fun WaterSection(d: OutputData, needs: com.example.flock.domain.FlockNeeds.State, onFarmChange: ((com.example.flock.data.FarmEntity) -> Unit)?) {
    val e = d.e
    val iso = remember(d) { isoFarmOf(d, d.feedPattern, d.penStartFt, d.barricadeFtNow, false, d.hour) }
    val factor = if (d.refillF > 0) d.refillF else 1.0
    val lim = d.walkLimits
    val tiles = buildList {
        add(PTile(GIcon.TANK, "Water today", v(d.waterL, 0, PR), "L", trendOf(d.waterL, d.waterIdealL, Better.CLOSER),
            refs = listOf("${d.waterRefills} refills" to kindColor(PR), "tank " + d.tankL.roundToInt() + " L" to Color.White.copy(alpha = 0.7f),
                "id " + Fmt.n(d.waterIdealL, 0) to kindColor(I)), tag = "tile_water"))
        add(PTile(GIcon.WATER, "Water a bird", v(d.waterPerBird, 1, PR), "mL", trendOf(d.waterPerBird, d.waterIdealPerBird, Better.CLOSER), null,
            d.waterIdealPerBird, 1, actual = d.waterPerBird))
        add(PTile(GIcon.FLOW, "An hour, lit", v(d.waterL / max(0.5, e.lightHours), 1, PR), "L/h", null,
            refs = listOf(Fmt.n(e.drinkerFlowLHrLine.takeIf { it > 0 }, 1) + " L/h a line" to kindColor(I))))
        add(PTile(GIcon.WALK, "Walk to water", v(d.waterWalkM, 2, PR), "m", rangeTrend(d.waterWalkM, null, lim.safeM),
            refs = listOf("id ≤ " + Fmt.n(lim.safeM, 1) to kindColor(I), "max " + Fmt.n(lim.maxM, 1) to kindColor(MX))))
        add(PTile(GIcon.NIPPLE, "Birds a nipple", v(d.birdsPerNipple, 1, PR), "", d.birdsPerNipple?.let { rangeTrend(it, null, d.birdsPerNippleMax) },
            refs = listOf("max " + Fmt.n(d.birdsPerNippleMax, 0) to kindColor(MX))))
        add(PTile(GIcon.DRINKER, "Nipple height", v(d.drinkerHtIn, 1, I), "in", null,
            refs = listOf("pressure " + Fmt.n(e.drinkerPressureIn.takeIf { it > 0 }, 2) + " in" to kindColor(I))))
        add(PTile(GIcon.FLOW, "Nipple flow", vt(Fmt.n(d.nippleFlow.first, 0) + "–" + Fmt.n(d.nippleFlow.second, 0), I), "mL/min", null,
            refs = listOf("for the age" to Color.White.copy(alpha = 0.6f)), sensor = true))
        add(PTile(GIcon.WATER_TEMP, "Water temp", v(e.waterTempC, 1, P), "°C", rangeTrend(e.waterTempC, 18.0, 21.0), refs = listOf("id 18–21" to kindColor(I)), sensor = true))
        add(PTile(GIcon.PH, "Water pH", v(e.waterPh, 2, P), "", rangeTrend(e.waterPh, 6.0, 6.8), refs = listOf("id 6.0–6.8" to kindColor(I)), sensor = true))
        add(PTile(GIcon.THIRST, "Thirsty now", vt(pct(needs.thirsty), PR), "%", null, refs = listOf("likelihood" to Color.White.copy(alpha = 0.6f))))
    }
    SectionCard(GIcon.WATER, "Water", "water") {
        FarmIso(iso, IsoView.WATER)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            GameIcon(GIcon.TANK, 22.dp)
            Text("refill ×", style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1, softWrap = false)
            listOf(1.0, 2.0, 3.0, 4.0).forEach { mlt ->
                val on = abs(factor - mlt) < 0.01
                Text("${mlt.roundToInt()}×", style = MaterialTheme.typography.labelLarge.copy(fontFamily = com.example.ui.theme.NumberFont),
                    color = if (on) Color.Black else Color.White, maxLines = 1, softWrap = false,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f).background(if (on) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = if (on) 1f else 0.4f), RoundedCornerShape(8.dp))
                        .clickable(enabled = onFarmChange != null) { onFarmChange?.invoke(d.farm.copy(waterRefillFactor = mlt)) }.padding(vertical = 6.dp)
                        .testTag("refill_${mlt.toInt()}"))
            }
        }
        TileGrid(tiles)
    }
}

// =================================== 6. trends (with the graphs and the book) ===================================

/** The flock's trends as tiles: the start, daily gain, the last week's FCR, days ahead or behind, the first week's and the last week's deaths. */
@Composable
fun TrendsSection(d: OutputData) {
    val k = d.kpis
    val tiles = listOf(
        PTile(GIcon.LIVE, "Placed", vi(d.placed, P), "", null, refs = listOf("reception ${d.reception} · " + Fmt.n(d.receptionPct, 2) + " %" to kindColor(P),
            "started " + Fmt.i(d.entryBirds) to kindColor(P))),
        PTile(GIcon.MORTALITY, "Removed", vi(d.mortTD, d.mk), "dead", null, refs = listOf("culls ${d.lameTD}" to kindColor(P),
            "with reception " + Fmt.n(d.mortWithReceptionPct, 2) + " %" to kindColor(d.mk))),
        PTile(GIcon.GAIN_Y, "Daily gain, average", v(k.adg, 1, d.vk), "g", trendOf(k.adg, d.adgCom, Better.HIGHER), d.adgCom, d.adgIdeal, 1, actual = k.adg),
        PTile(GIcon.FCR, "FCR, last 7 days", v(k.fcr7, 3, d.fcr7Kind), "", trendOf(k.fcr7, d.fcr7Com, Better.LOWER), d.fcr7Com, d.fcr7Ideal, 3, actual = k.fcr7),
        PTile(GIcon.TREND, "Days ahead", vt(Fmt.signed(d.daysAheadCom, 1), d.vk), "d", null,
            refs = listOf("vs company" to kindColor(C), Fmt.signed(d.daysAheadIdeal, 1) + " vs ideal" to kindColor(I))),
        PTile(GIcon.MORTALITY, "First week", v(k.firstWeekMortPct, 2, if (k.firstWeekComplete) P else PR), "%", trendOf(k.firstWeekMortPct, d.firstWeekMortCom, Better.LOWER),
            d.firstWeekMortCom, d.firstWeekMortIdeal, 2, actual = k.firstWeekMortPct),
        PTile(GIcon.MORTALITY, "Deaths, last 7 days", v(k.mort7Pct, 2, d.mk), "%", trendOf(k.mort7Pct, d.mort7Com, Better.LOWER), d.mort7Com, null, 2, actual = k.mort7Pct)
    )
    SectionCard(GIcon.TREND, "Trends", "trends") {
        TileGrid(tiles)
        SuggestCard(d)
        AccuracyCard(d)
    }
}

/** The fans on the end wall, numbered, those running now lit — in the order the controller starts them. */
@Composable
private fun FanBank(f: IsoFarm) {
    if (f.fans <= 0) return
    val lit = f.fanOrder.take(f.fansRunning.roundToInt().coerceIn(if (f.fansRunning > 0.05) 1 else 0, f.fans)).toSet()
    Row(Modifier.fillMaxWidth().testTag("fan_bank"), horizontalArrangement = Arrangement.SpaceEvenly) {
        (1..f.fans).forEach { k ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GameIcon(GIcon.FAN, 24.dp, dim = k !in lit)
                Text("$k", style = MaterialTheme.typography.labelSmall.copy(fontFamily = com.example.ui.theme.NumberFont),
                    color = if (k in lit) Color.White else Color.White.copy(alpha = 0.45f))
            }
        }
    }
}
