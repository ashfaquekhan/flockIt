package com.example.flock.ui.screens

import android.graphics.Paint
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.domain.Radar
import com.example.flock.engine.PhysiologicalEngine
import com.example.flock.ui.Fmt
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val P = ValueKind.PRESENT
private val PR = ValueKind.PREDICTED
private val C = ValueKind.COMMERCIAL
private val I = ValueKind.IDEAL
private val NE = ValueKind.NEUTRAL

/**
 * The four radars for the day shown: the birds against the company (with the breed's ideal drawn beside), and feed
 * and water, the air, the floor and litter against their ideal bands.
 */
fun radarsOf(d: OutputData, view: OutputData.NowView, hour: Int, pat: OutputData.PanPattern = d.feedPattern): List<Radar.Group> {
    val m = d.mass
    val w7 = d.suggest.w7
    val birds = Radar.scores(Radar.In(
        weightG = if (d.vk == P) d.bwNow else view.weightG, weightCom = d.bwCom, fcr = m?.fcr ?: view.fcr, fcrCom = d.fcrCom,
        mortPct = d.mortTDPct, mortCom = d.comCumPct, cvPct = d.cvEst, eatenVsComPct = w7.feedPct,
        epef = d.epef, epefCom = d.epefCom, gainG = d.gain, gainCom = d.gainCom))
    val ideal = Radar.scores(Radar.In(
        weightG = d.bwIdeal, weightCom = d.bwCom, fcr = d.fcrIdeal, fcrCom = d.fcrCom, mortPct = d.ceilingPct, mortCom = d.comCumPct,
        cvPct = 8.0, eatenVsComPct = 0.0, epef = d.epefIdeal, epefCom = d.epefCom, gainG = d.gainIdeal, gainCom = d.gainCom))
    val feed = Radar.feedWater(Radar.FeedWater(w7.feedPct, pat.birdsPerPan.takeIf { it < 1e6 }, d.birdsPerPanMax,
        d.birdsPerNipple, d.birdsPerNippleMax, d.feedWalkM(pat), d.waterWalkM, d.walkLimits))
    val h = d.envAt(hour)
    val e = d.e
    val l = litterOf(d, hour)
    val air = Radar.air(Radar.Air(h?.air?.tempC ?: e.tempIdeal, e.tempIdeal, e.tempMin, e.tempMax,
        h?.air?.rhPct ?: e.rhIdeal, e.rhIdeal, e.rhMin, e.rhMax, h?.r?.feltC ?: d.plan.comfort, d.plan.comfort,
        d.airFpm(h?.fans ?: d.minLevel.avgFans), PhysiologicalEngine.maxAirSpeedFpm(d.day), l.nh3Ppm))
    val floor = Radar.floor(Radar.Floor(d.space.kgM2, d.space.targetKgM2, d.farm.densityCapDefault, d.space.roomRatio,
        l.depthCm, l.moisturePct, l.cakedPct))
    return listOf(
        Radar.Group("birds", "Birds", "vs company", birds, ideal, Radar.MAX),
        Radar.Group("feed", "Feed and water", "vs ideal", feed),
        Radar.Group("air", "Air", "vs ideal", air),
        Radar.Group("floor", "Floor and litter", "vs ideal", floor))
}

/** A score's grade: on target, a little off, far off — birds against the company, the rest against their ideal band. */
fun scoreGrade(score: Double, birds: Boolean): Grade = if (birds) when { score >= 97 -> Grade.GOOD; score >= 90 -> Grade.AVERAGE; else -> Grade.BAD }
    else when { score >= 95 -> Grade.GOOD; score >= 60 -> Grade.AVERAGE; else -> Grade.BAD }

/** One number of the glance: what, its value (coloured by what it is), the company's and the ideal, and the line. */
private class Tile(
    val label: String, val value: V, val trend: TrendMark?,
    val com: Double?, val ideal: Double?, val dec: Int,
    /** the ideal written as a band or a limit ("≤ 8.0"), when it is not one number */
    val idealText: String? = null,
    /** lines in place of company and ideal, for a number that has none */
    val subs: List<String>? = null,
    val actual: Double? = null
)

/**
 * The day at a glance: what the colours mean, the four radars (100 = the target), and the twelve numbers that matter —
 * each with the company's and the ideal under it and the line that shows where it stands between them.
 */
@Composable
fun GlanceCard(d: OutputData, view: OutputData.NowView, entryHHmm: String?, live: Boolean, onNow: () -> Unit, hour: Int, pat: OutputData.PanPattern) {
    val m = d.mass
    val groups = remember(d, view, hour, pat) { radarsOf(d, view, hour, pat) }
    val beforeToday = (d.mortTD - d.mortToday).coerceAtLeast(0).toDouble()
    val mort = if (d.mk == P) d.mortTDPct else (beforeToday + view.deaths) * 100.0 / d.entryBirds
    val weight = if (d.vk == P) d.bwNow else view.weightG
    val fcr = m?.fcr ?: view.fcr
    val liveNow = if (d.mortEntered) d.live.toDouble() else view.live
    val lifted = m?.liftedBirds ?: d.liftTD
    val liftDays = d.rows.filter { it.birdsLifted > 0 && it.dayNumber <= d.day }.map { it.dayNumber }
    val tiles = listOf(
        Tile("Weight g", v(weight, 1, d.vk), trendOf(weight, d.bwCom, Better.HIGHER), d.bwCom, d.bwIdeal, 1, actual = weight),
        Tile("Gain g/day", v(d.gain, 1, d.gainKind), trendOf(d.gain, d.gainCom, Better.HIGHER), d.gainCom, d.gainIdeal, 1, actual = d.gain),
        Tile("CV %", v(d.cvEst, 1, if (d.cvMeasured) P else PR), d.cvEst?.let { rangeTrend(it, null, 10.0) }, null, 8.0, 1, idealText = "≤ 8.0", actual = d.cvEst),
        Tile("FCR", v(fcr, 3, d.fcrKind), trendOf(fcr, d.fcrCom, Better.LOWER), d.fcrCom, d.fcrIdeal, 3, actual = fcr),
        Tile("cFCR 2 kg", v(m?.cFcrCompany, 3, d.fcrKind), trendOf(m?.cFcrCompany, d.cfcrCom, Better.LOWER), d.cfcrCom, d.cfcrIdeal, 3, actual = m?.cFcrCompany),
        Tile("EPEF", v(d.epef, 1, d.fcrKind), trendOf(d.epef, d.epefCom, Better.HIGHER), d.epefCom, d.epefIdeal, 1, actual = d.epef),
        Tile("Mortality %", v(mort, 2, d.mk), trendOf(mort, d.comCumPct, Better.LOWER), d.comCumPct, d.ceilingPct, 2, actual = mort),
        Tile("Live birds", if (d.mortEntered) vi(d.live, P) else vi(Math.round(view.live).toInt(), PR), trendOf(liveNow, d.liveCom, Better.HIGHER, 1.0),
            d.liveCom, d.liveIdeal, 0, actual = liveNow),
        Tile("Lifted", vi(lifted, P), null, null, null, 0,
            subs = if (lifted > 0) listOf(Fmt.n(m?.liftedKg ?: d.liftKgTD, 1) + " kg", "day " + liftDays.joinToString(",")) else listOf("none yet", "")),
        Tile("Feed bags", v(d.dayBags, 1, PR), trendOf(d.dayBags, d.giveBags, Better.CLOSER, 10.0), d.giveBags, d.idealBags, 1, actual = d.dayBags),
        Tile("Water L", v(d.waterL, 1, PR), null, null, d.waterIdealL, 1, actual = d.waterL),
        Tile("kg/m²", v(d.space.kgM2, 1, d.vk), trendOf(d.space.kgM2, d.space.targetKgM2, Better.LOWER, 5.0), null, d.space.targetKgM2, 1,
            idealText = "≤ " + Fmt.n(d.space.targetKgM2, 1), actual = d.space.kgM2)
    )
    GlassBox(Modifier.fillMaxWidth().testTag("glance")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val sky = d.hourly.firstOrNull { it.date == d.e.date && it.hour == hour }
                Text("Day ${d.day}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.width(8.dp))
                Text(view.hhmm, style = MaterialTheme.typography.titleMedium.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Bold),
                    color = kindColor(PR), modifier = Modifier.testTag("time_shown"))
                if (sky?.emoji != null) Text(" " + sky.emoji, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                if (!live) Text("Now", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White,
                    modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).clickable { onNow() }
                        .padding(horizontal = 10.dp, vertical = 5.dp).testTag("time_now"))
                Spacer(Modifier.width(6.dp))
                InfoButton("glance")
            }
            // what the colours mean, before anything coloured
            ColourKey(listOf(kindColor(P) to "entered " + (entryHHmm ?: "—"), kindColor(PR) to "projected " + view.hhmm,
                kindColor(C) to "company", kindColor(I) to "ideal (Ross)"))
            GradeKey()
            RadarGrid(groups)
            RadarKey()
            tiles.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { t -> GlanceTile(t, Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Swatches and what each colour means; wraps as whole items. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ColourKey(items: List<Pair<Color, String>>, modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth().testTag("colour_key"), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { (c, t) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(c, CircleShape))
                Spacer(Modifier.width(4.dp))
                Text(t, style = MaterialTheme.typography.labelSmall.copy(fontFamily = com.example.ui.theme.NumberFont), color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1, softWrap = false)
            }
        }
    }
}

/** The arrows' and the scores' colours. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun GradeKey(modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(Grade.GOOD to "good", Grade.AVERAGE to "a little off", Grade.BAD to "far off").forEach { (g, t) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrendIcon(TrendMark(1, g), 10.dp)
                Spacer(Modifier.width(4.dp))
                Text(t, style = MaterialTheme.typography.labelSmall, color = gradeColor(g), maxLines = 1, softWrap = false)
            }
        }
    }
}

/** What the radar's shapes are. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RadarKey() {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        @Composable fun item(t: String, draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(width = 18.dp, height = 10.dp)) { draw() }
            Spacer(Modifier.width(4.dp))
            Text(t, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f), maxLines = 1, softWrap = false)
        }
        val present = kindColor(P); val company = kindColor(C); val ideal = kindColor(I)
        item("flock") { drawLine(present, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.4f * density) }
        item("100 = target") { drawLine(company, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.6f * density,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * density, 3f * density))) }
        item("ideal (birds)") { drawLine(ideal, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.2f * density) }
    }
}

@Composable
private fun RadarGrid(groups: List<Radar.Group>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        groups.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { g ->
                    Column(Modifier.weight(1f).border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp)).padding(horizontal = 4.dp, vertical = 6.dp)
                        .testTag("radar_${g.key}")) {
                        Text(g.title, Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(g.target, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f), maxLines = 1, modifier = Modifier.weight(1f))
                            val mean = g.mean
                            Text(mean?.let { Fmt.n(it, 0) } ?: "—", style = MaterialTheme.typography.labelLarge.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Bold),
                                color = mean?.let { gradeColor(scoreGrade(it, g.key == "birds")) } ?: Color.White.copy(alpha = 0.5f), maxLines = 1)
                        }
                        RadarChart(g.flock, g.ideal, g.max, g.key == "birds", compact = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun GlanceTile(t: Tile, modifier: Modifier) {
    val col = kindColor(if (t.value.text == "—") NE else t.value.kind)
    val mono = MaterialTheme.typography.labelSmall.copy(fontFamily = com.example.ui.theme.NumberFont, letterSpacing = (-0.3).sp)
    fun f(x: Double) = if (t.dec == 0) Fmt.i(Math.round(x).toInt()) else Fmt.n(x, t.dec)
    Column(modifier.border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(10.dp)).padding(horizontal = 7.dp, vertical = 6.dp)) {
        Text(t.label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.65f), maxLines = 1, softWrap = false)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.value.text, style = MaterialTheme.typography.titleMedium.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
                color = col, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f, fill = false))
            if (t.trend != null) { Spacer(Modifier.width(3.dp)); TrendIcon(t.trend, 10.dp) }
        }
        if (t.subs != null) t.subs.forEach { Text(it, style = mono, color = Color.White.copy(alpha = 0.6f), maxLines = 1, softWrap = false) }
        else {
            Text("co " + (t.com?.let { f(it) } ?: "NA"), style = mono, color = kindColor(C).copy(alpha = if (t.com == null) 0.55f else 1f), maxLines = 1, softWrap = false)
            Text("id " + (t.idealText ?: t.ideal?.let { f(it) } ?: "NA"), style = mono, color = kindColor(I).copy(alpha = if (t.ideal == null) 0.55f else 1f), maxLines = 1, softWrap = false)
        }
        // the line: the company's tick (violet), the ideal's (blue) and the flock's dot
        if (t.subs == null) MiniCompare(t.actual, t.value.kind, t.com, t.ideal)
        else Spacer(Modifier.height(10.dp))
    }
}

/** The comparison line of a glance number, small: company tick below the line, ideal tick above, the flock's dot. */
@Composable
fun MiniCompare(actual: Double?, kind: ValueKind, com: Double?, ideal: Double?) {
    val pts = listOfNotNull(actual, com, ideal)
    Canvas(Modifier.fillMaxWidth().height(10.dp).padding(top = 2.dp)) {
        val mid = size.height / 2
        drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, mid), Offset(size.width, mid), 1.5f)
        if (pts.isEmpty()) return@Canvas
        val lo0 = pts.min(); val hi0 = pts.max()
        val span = max(hi0 - lo0, max(abs(hi0) * 0.06, 1e-6))
        val lo = lo0 - span * 0.25; val hi = hi0 + span * 0.25
        val pad = 4.dp.toPx()
        fun x(v: Double) = pad + ((v - lo) / (hi - lo) * (size.width - 2 * pad)).toFloat()
        com?.let { drawLine(kindColor(C), Offset(x(it), mid), Offset(x(it), size.height), 2.dp.toPx()) }
        ideal?.let { drawLine(kindColor(I), Offset(x(it), 0f), Offset(x(it), mid), 2.dp.toPx()) }
        actual?.let { drawCircle(kindColor(kind), 3.6f * density, Offset(x(it), mid)); drawCircle(Color.Black, 1.3f * density, Offset(x(it), mid)) }
    }
}

/**
 * A radar: the target is the dashed ring (100), the breed's ideal the thin blue shape (birds only), the flock the
 * filled green one; each score sits by its axis in its grade's colour. [max] is the outer ring (150 for the birds,
 * who can beat the company; 100 for the rest).
 */
@Composable
fun RadarChart(flock: List<Radar.Axis>, ideal: List<Radar.Axis>?, max: Double = Radar.MAX, birds: Boolean = true, compact: Boolean = false) {
    val present = kindColor(P); val company = kindColor(C); val idealC = kindColor(I)
    Canvas(Modifier.fillMaxWidth().height(if (compact) 150.dp else 220.dp)) {
        val px = density
        val labelPx = (if (compact) 10f else 11.5f) * px
        val numPx = (if (compact) 11f else 12.5f) * px
        val cx = size.width / 2; val cy = size.height / 2 + (if (compact) 2f else 4f) * px
        val rMax = min(size.height / 2 - (if (compact) 30f else 34f) * px, size.width / 2 - (if (compact) 34f else 70f) * px)
        fun pt(i: Int, score: Double): Offset {
            val a = -Math.PI / 2 + i * Math.PI * 2 / flock.size
            val r = (score / max * rMax).toFloat()
            return Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
        }
        fun ring(score: Double) = Path().apply { flock.indices.forEach { i -> val p = pt(i, score); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }; close() }
        drawPath(ring(50.0), Color.White.copy(alpha = 0.14f), style = Stroke(1f))
        if (max > 100) drawPath(ring(max), Color.White.copy(alpha = 0.14f), style = Stroke(1f))
        flock.indices.forEach { i -> drawLine(Color.White.copy(alpha = 0.12f), Offset(cx, cy), pt(i, max), 1f) }
        drawPath(ring(100.0), company.copy(alpha = 0.08f))
        drawPath(ring(100.0), company, style = Stroke(1.6f * px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * px, 5f * px))))
        // an axis with no score (nothing to judge it by) is left out of the shape, not drawn as nought
        fun shape(axes: List<Radar.Axis>) = Path().apply {
            var first = true
            axes.forEachIndexed { i, a -> val sc = a.score ?: return@forEachIndexed; val p = pt(i, sc.coerceAtMost(max)); if (first) moveTo(p.x, p.y) else lineTo(p.x, p.y); first = false }
            close()
        }
        if (ideal != null) drawPath(shape(ideal), idealC.copy(alpha = 0.8f), style = Stroke(1.2f * px))
        drawPath(shape(flock), present.copy(alpha = 0.22f))
        drawPath(shape(flock), present, style = Stroke((if (compact) 2f else 2.4f) * px))
        val paint = Paint().apply { isAntiAlias = true; textSize = labelPx; typeface = com.example.ui.theme.AppFonts.text }
        val num = Paint().apply { isAntiAlias = true; textSize = numPx; typeface = com.example.ui.theme.AppFonts.mono; isFakeBoldText = true }
        flock.forEachIndexed { i, a ->
            val s = a.score
            val dot = pt(i, (s ?: 0.0).coerceAtMost(max))
            val sc = if (s == null) Color.White.copy(alpha = 0.5f) else gradeColor(scoreGrade(s, birds))
            if (s != null) drawCircle(sc, (if (compact) 3f else 3.5f) * px, dot)
            // the name and the score just outside the outer ring: centred near the top and bottom, beside it at the sides
            val ang = -Math.PI / 2 + i * Math.PI * 2 / flock.size
            val lr = rMax + (if (compact) 6f else 10f) * px
            val lx = cx + lr * cos(ang).toFloat(); val ly0 = cy + lr * sin(ang).toFloat()
            val dx = lx - cx
            val align = when { abs(dx) < rMax * 0.65f -> Paint.Align.CENTER; dx > 0 -> Paint.Align.LEFT; else -> Paint.Align.RIGHT }
            paint.textAlign = align; num.textAlign = align
            paint.color = Color.White.copy(alpha = 0.75f).toArgb(); num.color = sc.toArgb()
            val sy = sin(ang)
            val ly = when { sy < -0.5 -> ly0 - numPx * 1.05f - 2f * px; sy > 0.5 -> ly0 + labelPx; else -> ly0 }
            drawContext.canvas.nativeCanvas.drawText(a.label, lx, ly, paint)
            drawContext.canvas.nativeCanvas.drawText(s?.let { if (it >= Radar.MAX) "150+" else Fmt.n(it, 0) } ?: "—", lx, ly + numPx * 1.05f, num)
        }
    }
}

/**
 * The page's sections as tabs that stay at the top: the one being read is lit, and a tap scrolls to another.
 */
@Composable
fun SectionTabs(items: List<Pair<String, String>>, current: String?, onJump: (String) -> Unit, modifier: Modifier = Modifier) {
    val row = rememberScrollState()
    val xs = remember { HashMap<String, Int>() }
    LaunchedEffect(current) { current?.let { xs[it] }?.let { row.animateScrollTo((it - 40).coerceAtLeast(0)) } }
    Row(modifier.fillMaxWidth().background(Color.Black).horizontalScroll(row).padding(horizontal = 14.dp, vertical = 8.dp).testTag("section_tabs"),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (key, label) ->
            val on = key == current
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = if (on) Color.Black else Color.White,
                maxLines = 1, softWrap = false,
                modifier = Modifier.onGloballyPositioned { xs[key] = it.positionInParent().x.toInt() }
                    .background(if (on) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                    .border(1.dp, Color.White.copy(alpha = if (on) 1f else 0.4f), RoundedCornerShape(10.dp)).clickable { onJump(key) }
                    .padding(horizontal = 12.dp, vertical = 7.dp).testTag("jump_$key"))
        }
    }
}

/**
 * The time of the day, as a rail down the right edge of the page (00:00 at the top, 24:00 at the bottom). At rest it
 * is a thin line with the time on its handle; touched it opens out — the hours, the dark hours shaded, the entries'
 * time (green) and now (white, tap it to go back to the clock) — and the handle follows the finger.
 */
@Composable
fun TimeRail(minutes: Int, nowMinutes: Int?, entryMinutes: Int?, light: LightProgram, onMinutes: (Int) -> Unit, onNow: () -> Unit, modifier: Modifier = Modifier) {
    var engaged by remember { mutableStateOf(false) }
    var touch by remember { mutableIntStateOf(0) }
    LaunchedEffect(touch, engaged) { if (engaged) { kotlinx.coroutines.delay(1600); engaged = false } }
    val width by animateDpAsState(if (engaged) 72.dp else 26.dp, label = "rail")
    val setMin by rememberUpdatedState(onMinutes)
    val goNow by rememberUpdatedState(onNow)
    val nowAt by rememberUpdatedState(nowMinutes)
    Box(modifier.width(width).fillMaxHeight().padding(vertical = 10.dp)
        .semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(minutes.toFloat(), 0f..1435f)
            setProgress("Time of the day") { v -> setMin((v / 5).toInt().coerceIn(0, 287) * 5); true }
        }
        .testTag("time_rail")
        .pointerInput(Unit) {
            fun minAt(y: Float) = ((y / size.height) * 1440f).toInt().coerceIn(0, 1435) / 5 * 5
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                engaged = true; touch++
                down.consume()
                val n = nowAt
                // a tap on the "now" mark goes back to the clock
                val onNowMark = n != null && abs(down.position.y - n / 1440f * size.height) < 18.dp.toPx()
                var moved = false
                if (!onNowMark) setMin(minAt(down.position.y))
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull() ?: break
                    if (!ch.pressed) break
                    if (abs(ch.position.y - down.position.y) > 6.dp.toPx()) moved = true
                    if (moved || !onNowMark) setMin(minAt(ch.position.y))
                    ch.consume(); touch++
                }
                if (onNowMark && !moved) goNow()
            }
        }) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val px = density
            val h = size.height; val w = size.width
            val x = w - 8f * px
            fun y(min: Int) = min / 1440f * h
            if (engaged) {
                drawRoundRect(Color.Black.copy(alpha = 0.85f), Offset(x - 22f * px, 0f), Size(30f * px, h), CornerRadius(12f * px))
                drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(x - 22f * px, 0f), Size(30f * px, h), CornerRadius(12f * px), style = Stroke(1f))
                // the dark hours
                for (q in 0 until 96) if (!light.isLight(q / 4.0)) drawRect(Color.White.copy(alpha = 0.10f), Offset(x - 21f * px, q / 96f * h), Size(28f * px, h / 96f + 0.5f))
                val p = Paint().apply { isAntiAlias = true; textSize = 10f * px; typeface = com.example.ui.theme.AppFonts.mono; color = Color.White.copy(alpha = 0.6f).toArgb(); textAlign = Paint.Align.CENTER }
                listOf(0, 6, 12, 18, 24).forEach { hh -> drawContext.canvas.nativeCanvas.drawText(String.format("%02d", hh), x - 7f * px, (hh / 24f * h).coerceIn(10f * px, h - 3f * px), p) }
            }
            // the line, the entries' time and now
            drawLine(Color.White.copy(alpha = if (engaged) 0.6f else 0.35f), Offset(x, 0f), Offset(x, h), (if (engaged) 2f else 1.5f) * px)
            entryMinutes?.let { drawLine(kindColor(P), Offset(x - 6f * px, y(it)), Offset(x + 6f * px, y(it)), 3f * px) }
            nowMinutes?.let { drawCircle(Color.White, (if (engaged) 6f else 3.5f) * px, Offset(x, y(it))); if (engaged) drawCircle(Color.Black, 2.5f * px, Offset(x, y(it))) }
            // the handle: the time shown
            val hy = y(minutes)
            drawRoundRect(kindColor(PR), Offset(x - 4f * px, hy - 12f * px), Size(8f * px, 24f * px), CornerRadius(4f * px))
        }
        // the time on a bubble beside the handle
        val label = hhmmOf(minutes)
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxHeight()) {
            val top = (maxHeight * (minutes / 1440f) - 12.dp).coerceIn(0.dp, maxHeight - 24.dp)
            if (engaged) Text(label, Modifier.padding(top = top).background(kindColor(PR), RoundedCornerShape(6.dp)).padding(horizontal = 4.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Bold), color = Color.Black,
                textAlign = TextAlign.Center)
        }
    }
}
