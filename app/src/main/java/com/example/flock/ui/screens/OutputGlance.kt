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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.flock.ui.Fmt
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val P = ValueKind.PRESENT
private val PR = ValueKind.PREDICTED
private val C = ValueKind.COMMERCIAL
private val NE = ValueKind.NEUTRAL

/** The radar's six scores for the day shown: the flock (entered where there is an entry, else projected) and the breed's ideal. */
fun radarOf(d: OutputData, view: OutputData.NowView): Pair<List<Radar.Axis>, List<Radar.Axis>> {
    val m = d.mass
    val flock = Radar.scores(Radar.In(
        weightG = if (d.vk == P) d.bwNow else view.weightG, weightCom = d.bwCom, fcr = m?.fcr ?: view.fcr, fcrCom = d.fcrCom,
        mortPct = d.mortTDPct, mortCom = d.comCumPct, cvPct = d.cvEst, eatenVsComPct = d.suggest.w7.feedPct,
        epef = d.epef, epefCom = d.epefCom))
    val ideal = Radar.scores(Radar.In(
        weightG = d.bwIdeal, weightCom = d.bwCom, fcr = d.fcrIdeal, fcrCom = d.fcrCom, mortPct = d.ceilingPct, mortCom = d.comCumPct,
        cvPct = 8.0, eatenVsComPct = 0.0, epef = d.epefIdeal, epefCom = d.epefCom))
    return flock to ideal
}

/** One number of the glance: what, its value (coloured by what it is) and the company's beside it. */
private class Tile(val label: String, val value: V, val ref: String?, val trend: TrendMark?)

/**
 * The day at a glance: the radar (100 = the company), the twelve numbers that matter — entered green, projected
 * amber — with the company's under each, and at the time shown the air and how likely the birds are hungry,
 * thirsty or panting. Each of these numbers lives only here.
 */
@Composable
fun GlanceCard(
    d: OutputData, view: OutputData.NowView, entryHHmm: String?, live: Boolean, onNow: () -> Unit,
    weatherOn: Boolean, onWeatherOn: (Boolean) -> Unit, needs: com.example.flock.domain.FlockNeeds.State, hour: Int
) {
    val m = d.mass
    val (flock, ideal) = remember(d, view) { radarOf(d, view) }
    fun com(x: Double?, dec: Int) = x?.let { "co " + Fmt.n(it, dec) }
    val beforeToday = (d.mortTD - d.mortToday).coerceAtLeast(0).toDouble()
    val mort = if (d.mk == P) d.mortTDPct else (beforeToday + view.deaths) * 100.0 / d.entryBirds
    val weight = if (d.vk == P) d.bwNow else view.weightG
    val fcr = m?.fcr ?: view.fcr
    val tiles = listOf(
        Tile("Weight g", v(weight, 1, d.vk), com(d.bwCom, 1), trendOf(weight, d.bwCom, Better.HIGHER)),
        Tile("Gain g/day", v(d.gain, 1, d.gainKind), com(d.gainCom, 1), trendOf(d.gain, d.gainCom, Better.HIGHER)),
        Tile("FCR", v(fcr, 3, d.fcrKind), com(d.fcrCom, 3), trendOf(fcr, d.fcrCom, Better.LOWER)),
        Tile("cFCR 2 kg", v(m?.cFcrCompany, 3, d.fcrKind), com(d.cfcrCom, 3), trendOf(m?.cFcrCompany, d.cfcrCom, Better.LOWER)),
        Tile("Mortality %", v(mort, 2, d.mk), com(d.comCumPct, 2), trendOf(mort, d.comCumPct, Better.LOWER)),
        Tile("Live birds", if (d.mortEntered) vi(d.live, P) else vi(Math.round(view.live).toInt(), PR),
            "dead " + (if (d.mortEntered) Fmt.i(d.mortToday) else Fmt.n(view.deaths, 1)), null),
        Tile("Lifted", vi(m?.liftedBirds ?: d.liftTD, P), m?.takeIf { it.liftedKg > 0 }?.let { Fmt.n(it.liftedKg, 1) + " kg" }, null),
        Tile("Feed bags", v(d.dayBags, 1, PR), "eaten " + Fmt.n(view.eatenBags, 1), null),
        Tile("Water L", v(d.waterL, 1, PR), "drunk " + Fmt.n(view.drunkL, 1), null),
        Tile("kg/m²", v(d.space.kgM2, 1, d.vk), "ok ≤ " + Fmt.n(d.space.targetKgM2, 1), trendOf(d.space.kgM2, d.space.targetKgM2, Better.LOWER, 5.0)),
        Tile("CV %", v(d.cvEst, 1, if (d.cvMeasured) P else PR), "even ≤ 10.0", d.cvEst?.let { rangeTrend(it, null, 10.0) }),
        Tile("EPEF", v(d.epef, 1, d.fcrKind), com(d.epefCom, 1), trendOf(d.epef, d.epefCom, Better.HIGHER))
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
            RadarChart(flock, ideal)
            tiles.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { t -> GlanceTile(t, Modifier.weight(1f)) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Dot(kindColor(P), "entered " + (entryHHmm ?: "—"))
                Dot(kindColor(PR), "projected " + view.hhmm)
                Dot(kindColor(C), "company")
            }
            // the air and the birds at the time shown
            val h = d.env?.at(hour)
            fun pct(x: Double) = vt(Fmt.n(x * 100, 1) + " %", PR)
            val air = listOf(
                Tile("Outside °C", v(h?.outC, 1, if (d.outsideSource == com.example.flock.domain.BirdEnvironment.Source.WEATHER) NE else PR), null, null),
                Tile("House °C", v(h?.air?.tempC, 1, PR), null, null),
                Tile("Birds feel °C", h?.let { v(it.r.feltC, 1, stateKind(it.r.state)) } ?: vt("—", NE), h?.r?.state?.short, null),
                Tile("Hungry", pct(needs.hungry), null, null), Tile("Thirsty", pct(needs.thirsty), null, null), Tile("Panting", pct(needs.panting), null, null))
            air.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { t -> GlanceTile(t, Modifier.weight(1f), small = true) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Weather acts on the house", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.weight(1f))
                Switch(checked = weatherOn, onCheckedChange = onWeatherOn, modifier = Modifier.testTag("weather_switch"),
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color.White,
                        uncheckedThumbColor = Color.White.copy(alpha = 0.7f), uncheckedTrackColor = Color.Black, uncheckedBorderColor = Color.White.copy(alpha = 0.5f)))
            }
        }
    }
}

@Composable
private fun Dot(c: Color, t: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(8.dp).height(8.dp).background(c, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(4.dp))
        Text(t, style = MaterialTheme.typography.labelSmall.copy(fontFamily = com.example.ui.theme.NumberFont), color = Color.White.copy(alpha = 0.7f), maxLines = 1, softWrap = false)
    }
}

@Composable
private fun GlanceTile(t: Tile, modifier: Modifier, small: Boolean = false) {
    val col = kindColor(if (t.value.text == "—") NE else t.value.kind)
    Column(modifier.border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = if (small) 5.dp else 7.dp)) {
        Text(t.label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.65f), maxLines = 1, softWrap = false)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.value.text, style = (if (small) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium)
                .copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
                color = col, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f, fill = false))
            if (t.trend != null) { Spacer(Modifier.width(3.dp)); TrendIcon(t.trend, 10.dp) }
        }
        if (t.ref != null) Text(t.ref, style = MaterialTheme.typography.labelSmall.copy(fontFamily = com.example.ui.theme.NumberFont, letterSpacing = (-0.3).sp),
            color = if (t.ref.startsWith("co ")) kindColor(C) else Color.White.copy(alpha = 0.6f), maxLines = 1, softWrap = false)
    }
}

/**
 * The flock's shape on six axes: the company is the dashed ring (100), the breed's ideal the thin blue shape, the flock
 * the filled green one; each score sits by its axis — green at or above the company, amber a little under, rose well
 * under.
 */
@Composable
fun RadarChart(flock: List<Radar.Axis>, ideal: List<Radar.Axis>) {
    val present = kindColor(P); val company = kindColor(C); val idealC = kindColor(ValueKind.IDEAL)
    Canvas(Modifier.fillMaxWidth().height(220.dp).testTag("radar")) {
        val px = density
        val cx = size.width / 2; val cy = size.height / 2 + 4f * px
        val rMax = (size.height / 2 - 26f * px)
        fun pt(i: Int, score: Double): Offset {
            val a = -Math.PI / 2 + i * Math.PI * 2 / flock.size
            val r = (score / Radar.MAX * rMax).toFloat()
            return Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
        }
        fun ring(score: Double) = Path().apply { flock.indices.forEach { i -> val p = pt(i, score); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }; close() }
        drawPath(ring(50.0), Color.White.copy(alpha = 0.14f), style = Stroke(1f))
        drawPath(ring(Radar.MAX), Color.White.copy(alpha = 0.14f), style = Stroke(1f))
        flock.indices.forEach { i -> drawLine(Color.White.copy(alpha = 0.12f), Offset(cx, cy), pt(i, Radar.MAX), 1f) }
        drawPath(ring(100.0), company.copy(alpha = 0.08f))
        drawPath(ring(100.0), company, style = Stroke(1.6f * px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * px, 5f * px))))
        fun shape(axes: List<Radar.Axis>) = Path().apply { axes.forEachIndexed { i, a -> val p = pt(i, a.score ?: 0.0); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }; close() }
        drawPath(shape(ideal), idealC.copy(alpha = 0.8f), style = Stroke(1.2f * px))
        drawPath(shape(flock), present.copy(alpha = 0.22f))
        drawPath(shape(flock), present, style = Stroke(2.4f * px))
        val paint = Paint().apply { isAntiAlias = true; textSize = 11.5f * px; typeface = com.example.ui.theme.AppFonts.text }
        val num = Paint().apply { isAntiAlias = true; textSize = 12.5f * px; typeface = com.example.ui.theme.AppFonts.mono; isFakeBoldText = true }
        flock.forEachIndexed { i, a ->
            val s = a.score
            val dot = pt(i, s ?: 0.0)
            val sc = when { s == null -> Color.White.copy(alpha = 0.5f); s >= 100 -> present; s >= 90 -> kindColor(PR); else -> kindColor(ValueKind.MAX) }
            if (s != null) drawCircle(sc, 3.5f * px, dot)
            val lab = pt(i, Radar.MAX + 14)
            val align = when { abs(lab.x - cx) < 4f * px -> Paint.Align.CENTER; lab.x > cx -> Paint.Align.LEFT; else -> Paint.Align.RIGHT }
            paint.textAlign = align; num.textAlign = align
            paint.color = Color.White.copy(alpha = 0.75f).toArgb(); num.color = sc.toArgb()
            val top = lab.y < cy - 4f * px; val bottom = lab.y > cy + rMax * 0.9f
            val ly = if (top && align == Paint.Align.CENTER) lab.y - 8f * px else if (bottom) lab.y + 4f * px else lab.y
            drawContext.canvas.nativeCanvas.drawText(a.label, lab.x, ly, paint)
            drawContext.canvas.nativeCanvas.drawText(s?.let { if (it >= Radar.MAX) "150+" else Fmt.n(it, 0) } ?: "—", lab.x, ly + 13f * px, num)
        }
    }
}

/**
 * The page's sections, one tap away: tapping a name scrolls the page to it.
 */
@Composable
fun JumpBar(items: List<Pair<String, String>>, onJump: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("jump_bar"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (key, label) ->
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = Color.White,
                modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(10.dp)).clickable { onJump(key) }
                    .padding(horizontal = 14.dp, vertical = 9.dp).testTag("jump_$key"))
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
