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

/** A birds score's grade: at or above the company (within 3 %) good, below it bad — green or red, nothing between. */
fun scoreGrade(score: Double): Grade = if (score >= 97) Grade.GOOD else Grade.BAD

/** One score for the birds: the average of the six (each as entered, else as projected at the time shown). */
fun overallScore(entered: List<Radar.Axis>, projected: List<Radar.Axis>): Double? =
    projected.indices.mapNotNull { i -> (entered.getOrNull(i)?.score ?: projected[i].score)?.coerceAtMost(Radar.MAX) }
        .takeIf { it.isNotEmpty() }?.average()

/** Swatches and what each colour means; wraps as whole items. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ColourKey(items: List<Pair<Color, String>>, modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth().testTag("colour_key"), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { (c, t) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(c, CircleShape))
                Spacer(Modifier.width(4.dp))
                Text(t, style = MaterialTheme.typography.labelMedium.copy(fontFamily = com.example.ui.theme.NumberFont), color = c,
                    maxLines = 1, softWrap = false)
            }
        }
    }
}

/** The birds' six scores for the day: as entered (null where nothing is entered) and as projected at the time shown. */
fun birdScores(d: OutputData, view: OutputData.NowView): Triple<List<Radar.Axis>, List<Radar.Axis>, List<Radar.Axis>> {
    val m = d.mass
    val beforeToday = (d.mortTD - d.mortToday).coerceAtLeast(0).toDouble()
    val entered = Radar.scores(Radar.In(
        weightG = d.bwNow.takeIf { d.vk == P }, weightCom = d.bwCom, fcr = m?.fcr?.takeIf { d.fcrKind == P }, fcrCom = d.fcrCom,
        mortPct = d.mortTDPct?.takeIf { d.mk == P }, mortCom = d.comCumPct, cvPct = d.cvEst?.takeIf { d.cvMeasured }, eatenVsComPct = null,
        epef = d.epef?.takeIf { d.fcrKind == P }, epefCom = d.epefCom, gainG = d.gain?.takeIf { d.gainKind == P }, gainCom = d.gainCom))
    val projected = Radar.scores(Radar.In(
        weightG = view.weightG, weightCom = d.bwCom, fcr = view.fcr ?: m?.fcr, fcrCom = d.fcrCom,
        mortPct = (beforeToday + view.deaths) * 100.0 / d.entryBirds, mortCom = d.comCumPct, cvPct = d.cvEst, eatenVsComPct = null,
        epef = d.epef, epefCom = d.epefCom, gainG = d.gain, gainCom = d.gainCom))
    val ideal = Radar.scores(Radar.In(
        weightG = d.bwIdeal, weightCom = d.bwCom, fcr = d.fcrIdeal, fcrCom = d.fcrCom, mortPct = d.ceilingPct, mortCom = d.comCumPct,
        cvPct = 8.0, eatenVsComPct = 0.0, epef = d.epefIdeal, epefCom = d.epefCom, gainG = d.gainIdeal, gainCom = d.gainCom))
    return Triple(entered, projected, ideal)
}

/**
 * The birds against the company, on six axes: every score is the flock's figure as a share of the company's for the
 * day (FCR and mortality the other way up, so further out is always better). Nothing is filled: the company is the
 * dashed ring (100), the ideal (Ross) the thin blue outline, the flock as entered green and as projected at the time
 * shown amber, so every outline shows through the others (an outline needs three scores; fewer are dots only).
 * Minimal (v52): the axis names and one overall score in the middle (green at or above the company, red below);
 * what the scores are lives behind the card's ⓘ.
 */
@Composable
fun BirdRadar(d: OutputData, view: OutputData.NowView) {
    val (entered, projected, ideal) = remember(d, view) { birdScores(d, view) }
    val overall = remember(entered, projected) { overallScore(entered, projected) }
    val present = kindColor(P); val proj = kindColor(PR); val company = kindColor(C); val idealC = kindColor(I)
    Column(Modifier.fillMaxWidth().testTag("radar_birds"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(230.dp).testTag("radar")) {
            val px = density
            val n = projected.size
            val max = Radar.MAX
            val cx = size.width / 2; val cy = size.height / 2
            val rMax = min(size.height / 2 - 22f * px, size.width / 2 - 64f * px)
            fun pt(i: Int, score: Double): Offset {
                val a = -Math.PI / 2 + i * Math.PI * 2 / n
                val r = (score.coerceIn(0.0, max) / max * rMax).toFloat()
                return Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
            }
            fun ring(score: Double) = Path().apply { (0 until n).forEach { i -> val p = pt(i, score); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }; close() }
            drawPath(ring(50.0), Color.White.copy(alpha = 0.12f), style = Stroke(1f))
            drawPath(ring(max), Color.White.copy(alpha = 0.12f), style = Stroke(1f))
            (0 until n).forEach { i -> drawLine(Color.White.copy(alpha = 0.10f), Offset(cx, cy), pt(i, max), 1f) }
            drawPath(ring(100.0), company, style = Stroke(1.4f * px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * px, 5f * px))))
            // an axis with no score is left out of the shape (not drawn as nought)
            fun shape(axes: List<Radar.Axis>) = Path().apply {
                var first = true
                axes.forEachIndexed { i, a -> val sc = a.score ?: return@forEachIndexed; val p = pt(i, sc); if (first) moveTo(p.x, p.y) else lineTo(p.x, p.y); first = false }
                close()
            }
            drawPath(shape(ideal), idealC, style = Stroke(1.2f * px))
            if (projected.count { it.score != null } >= 3) drawPath(shape(projected), proj, style = Stroke(1.4f * px))
            if (entered.count { it.score != null } >= 3) drawPath(shape(entered), present, style = Stroke(1.6f * px))
            entered.forEachIndexed { i, a -> a.score?.let { drawCircle(present, 3f * px, pt(i, it)) } }
            projected.forEachIndexed { i, a -> a.score?.let { drawCircle(proj, 2.4f * px, pt(i, it)) } }
            // the axis names only, just outside the outer ring
            val label = Paint().apply { isAntiAlias = true; textSize = 12.5f * px; typeface = com.example.ui.theme.AppFonts.text; color = Color.White.toArgb() }
            (0 until n).forEach { i ->
                val ang = -Math.PI / 2 + i * Math.PI * 2 / n
                val lr = rMax + 8f * px
                val lx = cx + lr * cos(ang).toFloat(); val ly0 = cy + lr * sin(ang).toFloat()
                val dx = lx - cx
                val align = when { abs(dx) < rMax * 0.3f -> Paint.Align.CENTER; dx > 0 -> Paint.Align.LEFT; else -> Paint.Align.RIGHT }
                val sy = sin(ang)
                val ly = when { sy < -0.5 -> ly0 - 4f * px; sy > 0.5 -> ly0 + 12f * px; else -> ly0 + 4f * px }
                label.textAlign = align
                drawContext.canvas.nativeCanvas.drawText(projected[i].label, lx, ly, label)
            }
            // one score in the middle: green at or above the company, red below it
            overall?.let { s ->
                val big = Paint().apply { isAntiAlias = true; textSize = 20f * px; typeface = com.example.ui.theme.AppFonts.mono; textAlign = Paint.Align.CENTER
                    color = gradeColor(scoreGrade(s)).toArgb() }
                drawCircle(Color.Black.copy(alpha = 0.6f), 26f * px, Offset(cx, cy))
                drawContext.canvas.nativeCanvas.drawText(Fmt.n(s, 1), cx, cy + 7f * px, big)
            }
        }
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
 * The page's sections as tabs that stay at the top: the one being read is lit, and a tap scrolls to another.
 */
@Composable
fun SectionTabs(items: List<Pair<String, String>>, current: String?, onJump: (String) -> Unit, modifier: Modifier = Modifier,
                icons: Map<String, GIcon> = emptyMap()) {
    val row = rememberScrollState()
    val xs = remember { HashMap<String, Int>() }
    LaunchedEffect(current) { current?.let { xs[it] }?.let { row.animateScrollTo((it - 40).coerceAtLeast(0)) } }
    Row(modifier.fillMaxWidth().background(Color.Black).horizontalScroll(row).padding(horizontal = 14.dp, vertical = 8.dp).testTag("section_tabs"),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (key, label) ->
            val on = key == current
            Row(Modifier.onGloballyPositioned { xs[key] = it.positionInParent().x.toInt() }
                .background(if (on) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                .border(1.dp, Color.White.copy(alpha = if (on) 1f else 0.4f), RoundedCornerShape(10.dp)).clickable { onJump(key) }
                .padding(start = if (icons[key] != null) 7.dp else 12.dp, end = 12.dp, top = 5.dp, bottom = 5.dp).testTag("jump_$key"),
                verticalAlignment = Alignment.CenterVertically) {
                icons[key]?.let { GameIcon(it, 20.dp); Spacer(Modifier.width(5.dp)) }
                Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal), color = if (on) Color.Black else Color.White,
                    maxLines = 1, softWrap = false)
            }
        }
    }
}

/**
 * The time of the day, as a rail down the right edge of the page (00:00 at the top, 24:00 at the bottom). At rest it
 * is a thin line with the time on its handle; touched it opens out — the hours, the dark hours shaded, the entries'
 * time (green) and now (white, tap it to go back to the clock) — and the handle follows the finger.
 */
/** A drag on the rail let go this close to now (minutes) goes back to the clock. */
const val NOW_SNAP_MIN = 15

/**
 * Back to the time now, from anywhere on the page: a button at the bottom of the rail, shown only while another time
 * is picked (v52: the white dot on the rail was too small a target to find the present).
 */
@Composable
fun NowButton(onNow: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.background(Color.Black, RoundedCornerShape(22.dp)).border(1.dp, Color.White, RoundedCornerShape(22.dp))
        .clickable { onNow() }.padding(horizontal = 14.dp, vertical = 10.dp).testTag("rail_now"),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(Color.White, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text("Now", style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, softWrap = false)
    }
}

@Composable
fun TimeRail(minutes: Int, nowMinutes: Int?, entryMinutes: Int?, light: LightProgram, onMinutes: (Int) -> Unit, onNow: () -> Unit, modifier: Modifier = Modifier,
             /** a day that is not today: −1 all of it has gone by, 1 all of it is to come */
             side: Int = 0) {
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
                var last = minAt(down.position.y)
                if (!onNowMark) setMin(last)
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull() ?: break
                    if (!ch.pressed) break
                    if (abs(ch.position.y - down.position.y) > 6.dp.toPx()) moved = true
                    if (moved || !onNowMark) { last = minAt(ch.position.y); setMin(last) }
                    ch.consume(); touch++
                }
                // back to the clock: a tap on the now mark, or a drag let go within a quarter of an hour of now
                if ((onNowMark && !moved) || (moved && n != null && abs(last - n) <= NOW_SNAP_MIN)) goNow()
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
            // (v61) the time gone by in the entered green, the time to come in the projected amber; the white dot is now
            val split = nowMinutes?.let { y(it) } ?: if (side < 0) h else 0f
            val lw = (if (engaged) 3f else 2f) * px; val la = if (engaged) 0.95f else 0.7f
            if (split > 0f) drawLine(kindColor(P).copy(alpha = la), Offset(x, 0f), Offset(x, split), lw)
            if (split < h) drawLine(kindColor(PR).copy(alpha = la), Offset(x, split), Offset(x, h), lw)
            entryMinutes?.let { drawLine(Color.White, Offset(x - 6f * px, y(it)), Offset(x + 6f * px, y(it)), 2f * px) }
            nowMinutes?.let { drawCircle(Color.White, (if (engaged) 6f else 3.5f) * px, Offset(x, y(it))); if (engaged) drawCircle(Color.Black, 2.5f * px, Offset(x, y(it))) }
            // the handle: the time shown
            val hy = y(minutes)
            drawRoundRect(Color.White, Offset(x - 4f * px, hy - 12f * px), Size(8f * px, 24f * px), CornerRadius(4f * px))
        }
        // the time on a bubble beside the handle
        val label = hhmmOf(minutes)
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxHeight()) {
            val top = (maxHeight * (minutes / 1440f) - 12.dp).coerceIn(0.dp, maxHeight - 24.dp)
            if (engaged) Text(label, Modifier.padding(top = top).background(Color.White, RoundedCornerShape(6.dp)).padding(horizontal = 4.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Normal), color = Color.Black,
                textAlign = TextAlign.Center)
        }
    }
}
