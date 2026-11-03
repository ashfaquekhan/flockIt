package com.example.flock.ui.screens

import android.graphics.Paint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.domain.Litter
import com.example.flock.engine.CompanyStandard
import com.example.flock.engine.PhysiologicalEngine
import com.example.flock.ui.Fmt
import com.example.ui.theme.GlassLine
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val P = ValueKind.PRESENT
private val PR = ValueKind.PREDICTED
private val I = ValueKind.IDEAL
private val C = ValueKind.COMMERCIAL
private val MX = ValueKind.MAX
private val NE = ValueKind.NEUTRAL

fun hhmmOf(minutes: Int): String = String.format("%02d:%02d", (minutes / 60).coerceIn(0, 23), (minutes % 60).coerceIn(0, 59))

// =================================== litter ===================================

/** The litter for the hour shown: worked out (no sensors), against what it should be. */
fun litterOf(d: OutputData, hour: Int): Litter.Out {
    val h = d.envAt(hour)
    val fpm = h?.air?.airFpm?.takeIf { it > 0 } ?: d.airFpm(d.minLevel.avgFans)
    return Litter.state(Litter.In(day = d.day, kgM2 = d.space.kgM2, feedKgTD = d.usedKgTD, floorM2 = d.areaInUseFt2 / 10.7639,
        airC = h?.air?.tempC ?: d.e.tempIdeal, rhPct = h?.air?.rhPct ?: d.e.rhIdeal, airSpeedMs = fpm * 0.00508))
}

@Composable
fun LitterCard(d: OutputData, hour: Int) {
    val l = remember(d, hour) { litterOf(d, hour) }
    OutputCard(title = "Litter", info = "litter") {
        LitterSideView(l, d.litterTemp.second)
        Param("Depth", "cm", v(l.depthCm, 1, PR, "Projected"), vt("${Fmt.n(Litter.IDEAL_DEPTH_LO, 1)}–${Fmt.n(Litter.IDEAL_DEPTH_HI, 1)}", I, "Ideal bedding"),
            v(l.beddingCm, 1, NE, "Bedding, assumed"))
        Param("Moisture", "%", v(l.moisturePct, 1, PR, "House"), v(l.underDrinkersPct, 1, PR, "Under drinkers"),
            vt("${Fmt.n(Litter.IDEAL_MOIST_LO, 1)}–${Fmt.n(Litter.IDEAL_MOIST_HI, 1)}", I, "Ideal"), v(Litter.MOIST_MAX, 1, MX, "Most"))
        Param("Temperature", "°C", v(l.tempC, 1, PR, "Projected"), v(d.litterTemp.second, 1, I, "Ideal"), v(d.litterTemp.third, 1, MX, "Most"))
        Param("Ammonia", "ppm", v(l.nh3Ppm, 1, PR, "Projected"), v(Litter.NH3_IDEAL, 1, I, "Ideal under"), v(Litter.NH3_MAX, 1, MX, "Most"))
        Param("Caked floor", "%", v(l.cakedPct, 1, PR, "Projected"), v(0.0, 1, I, "Ideal"))
    }
}

/**
 * The litter cut open from the side: the concrete, the bed of litter (coloured by how wet it is, darker and caked
 * under the drinker line), the ideal bedding depth as dashed lines, a pan, a drop falling from a nipple, and ammonia
 * rising off the top — more dots the more there is.
 */
@Composable
fun LitterSideView(l: Litter.Out, idealC: Double) {
    val t = rememberInfiniteTransition(label = "litter")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "gas")
    Canvas(Modifier.fillMaxWidth().height(190.dp).border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp)).testTag("litter_view")) {
        val px = density
        val padL = 34f * px; val padR = 10f * px
        val w = size.width - padL - padR
        val floorTop = size.height - 18f * px
        val maxCm = max(16.0, l.depthCm + 4)
        val cmPx = (floorTop - 70f * px) / maxCm.toFloat()
        fun y(cm: Double) = (floorTop - cm * cmPx).toFloat()
        val paint = Paint().apply { isAntiAlias = true; textSize = 10f * px; typeface = com.example.ui.theme.AppFonts.mono }
        fun text(s: String, x: Float, yy: Float, c: Color, align: Paint.Align = Paint.Align.LEFT, bold: Boolean = false) {
            paint.color = c.toArgb(); paint.textAlign = align; paint.isFakeBoldText = bold
            drawContext.canvas.nativeCanvas.drawText(s, x, yy, paint)
        }
        // concrete
        drawRect(Color(0xFF3A3A3C), Offset(padL, floorTop), Size(w, 10f * px))
        var hx = padL
        while (hx < padL + w) { drawLine(Color.White.copy(alpha = 0.12f), Offset(hx, floorTop + 10f * px), Offset(hx + 6f * px, floorTop), 1f); hx += 9f * px }
        // the bed: dry is pale straw, wet dark brown
        fun wetColor(m: Double): Color {
            val f = ((m - 12) / 28).coerceIn(0.0, 1.0).toFloat()
            return Color(0xFFD9B77A).copy(red = 0.85f - 0.5f * f, green = 0.72f - 0.47f * f, blue = 0.48f - 0.33f * f)
        }
        val top = y(l.depthCm)
        drawRect(wetColor(l.moisturePct), Offset(padL, top), Size(w, floorTop - top))
        // shavings
        val rnd = java.util.Random(5)
        repeat(220) {
            val sx = padL + rnd.nextFloat() * w; val sy = top + rnd.nextFloat() * (floorTop - top)
            drawLine(Color.White.copy(alpha = 0.10f + 0.08f * rnd.nextFloat()), Offset(sx, sy), Offset(sx + (2 + 4 * rnd.nextFloat()) * px, sy + (rnd.nextFloat() - 0.5f) * 2 * px), 1f)
        }
        // under the drinker line: wetter, a caked crust on top when over 30 %
        val dx = padL + w * 0.22f; val dw = w * 0.16f
        drawRect(wetColor(l.underDrinkersPct), Offset(dx - dw / 2, top), Size(dw, floorTop - top))
        if (l.underDrinkersPct > Litter.MOIST_MAX) drawRect(Color(0xFF2B1D10), Offset(dx - dw / 2, top - 2f * px), Size(dw, 5f * px))
        // the ideal bedding: dashed lines at 5 and 10 cm
        listOf(Litter.IDEAL_DEPTH_LO, Litter.IDEAL_DEPTH_HI).forEach { cm ->
            drawLine(kindColor(I), Offset(padL, y(cm)), Offset(padL + w, y(cm)), 1.5f * px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * px, 4f * px)))
        }
        text("ideal ${Fmt.n(Litter.IDEAL_DEPTH_LO, 0)}–${Fmt.n(Litter.IDEAL_DEPTH_HI, 0)} cm", padL + w - 4f * px, y(Litter.IDEAL_DEPTH_LO) - 4f * px, kindColor(I), Paint.Align.RIGHT, bold = true)
        // the cm scale
        var cm = 0.0
        while (cm <= maxCm + 1e-6) {
            drawLine(Color.White.copy(alpha = 0.4f), Offset(padL - 4f * px, y(cm)), Offset(padL, y(cm)), 1f)
            text(Fmt.n(cm, 0), padL - 6f * px, y(cm) + 3.5f * px, Color.White.copy(alpha = 0.55f), Paint.Align.RIGHT)
            cm += 4.0
        }
        text("cm", padL - 6f * px, 12f * px, Color.White.copy(alpha = 0.55f), Paint.Align.RIGHT)
        // the drinker line and a drop falling onto the wet patch
        val lineY = top - 52f * px
        drawLine(kindColor(ValueKind.MIN), Offset(padL, lineY), Offset(dx + dw, lineY), 3f * px)
        drawLine(kindColor(ValueKind.MIN), Offset(dx, lineY), Offset(dx, lineY + 8f * px), 2f * px)
        val dropY = lineY + 8f * px + (top - lineY - 8f * px) * ((phase * 2) % 1f)
        drawCircle(kindColor(ValueKind.MIN), 2.2f * px, Offset(dx, dropY))
        text("drinker", dx + 6f * px, lineY - 4f * px, kindColor(ValueKind.MIN))
        // a pan on the litter
        val panX = padL + w * 0.80f
        drawArc(Color.White.copy(alpha = 0.7f), 0f, 180f, false, Offset(panX - 16f * px, top - 8f * px), Size(32f * px, 14f * px), style = Stroke(2f * px))
        drawLine(Color.White.copy(alpha = 0.5f), Offset(panX, top - 1f * px), Offset(panX, top - 46f * px), 1.5f * px)
        // ammonia off the top: more dots the more there is; the colour of where it stands
        val gasCol = when { l.nh3Ppm > Litter.NH3_MAX -> kindColor(MX); l.nh3Ppm > Litter.NH3_IDEAL -> kindColor(PR); else -> kindColor(I) }
        val n = (l.nh3Ppm / 1.5).toInt().coerceIn(2, 32)
        val gr = java.util.Random(9)
        repeat(n) {
            val gx = padL + gr.nextFloat() * w
            val off = gr.nextFloat()
            val f = (phase + off) % 1f
            val gy = top - f * 60f * px
            drawCircle(gasCol.copy(alpha = (1 - f) * 0.8f), (1.6f + 1.2f * gr.nextFloat()) * px, Offset(gx + sin(f * 6.28f + off * 9) * 3f * px, gy))
        }
        // the numbers on the picture
        text("${Fmt.n(l.depthCm, 1)} cm", padL + w * 0.66f, top - 6f * px, kindColor(PR), bold = true)
        text("${Fmt.n(l.moisturePct, 1)} % wet · ${Fmt.n(l.tempC, 1)} °C (ideal ${Fmt.n(idealC, 1)})", padL + 4f * px, floorTop - 4f * px, Color.White.copy(alpha = 0.9f))
        text("NH₃ ${Fmt.n(l.nh3Ppm, 1)} ppm", padL + w - 2f * px, 14f * px, gasCol, Paint.Align.RIGHT, bold = true)
        if (l.underDrinkersPct > Litter.MOIST_MAX) text("caked", dx - dw / 2 + 4f * px, top + 14f * px, Color.White.copy(alpha = 0.9f), bold = true)
    }
}

// =================================== nipple line ===================================

/**
 * A nipple drinker line from the side: the regulator at the head of the line with its sight tube (the water column
 * is the line's pressure, inches of water) and a dial reading the same, the line at its height over the litter, a
 * drop at a nipple.
 */
@Composable
fun NippleLineView(heightIn: Double, pressureIn: Double?) {
    val t = rememberInfiniteTransition(label = "nipple")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "drop")
    Canvas(Modifier.fillMaxWidth().height(200.dp).border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp)).testTag("nipple_view")) {
        val px = density
        val litterTop = size.height - 16f * px
        val scaleIn = (litterTop - 34f * px) / 24f          // px per inch, 24 in from the litter to the top
        fun yIn(inch: Double) = (litterTop - inch * scaleIn).toFloat()
        val paint = Paint().apply { isAntiAlias = true; textSize = 10f * px; typeface = com.example.ui.theme.AppFonts.mono }
        fun text(s: String, x: Float, yy: Float, c: Color, align: Paint.Align = Paint.Align.LEFT, bold: Boolean = false) {
            paint.color = c.toArgb(); paint.textAlign = align; paint.isFakeBoldText = bold
            drawContext.canvas.nativeCanvas.drawText(s, x, yy, paint)
        }
        val water = kindColor(ValueKind.MIN)
        // litter
        drawRect(Color(0xFFB08A55).copy(alpha = 0.55f), Offset(0f, litterTop), Size(size.width, size.height - litterTop))
        // the regulator and its sight tube: the water stands as high as the pressure
        val regX = 30f * px
        val tubeTop = yIn(22.0); val tubeBottom = yIn(2.0)
        val pin = (pressureIn ?: 0.0).coerceIn(0.0, 20.0)
        drawRect(Color.White.copy(alpha = 0.10f), Offset(regX - 5f * px, tubeTop), Size(10f * px, tubeBottom - tubeTop))
        drawRect(water.copy(alpha = 0.75f), Offset(regX - 5f * px, tubeBottom - (pin * scaleIn).toFloat()), Size(10f * px, (pin * scaleIn).toFloat()))
        drawRect(Color.White.copy(alpha = 0.7f), Offset(regX - 5f * px, tubeTop), Size(10f * px, tubeBottom - tubeTop), style = Stroke(1.2f * px))
        var tick = 0.0
        while (tick <= 20.0 + 1e-6) {
            val ty = (tubeBottom - tick * scaleIn).toFloat()
            drawLine(Color.White.copy(alpha = 0.5f), Offset(regX + 5f * px, ty), Offset(regX + (if (tick % 4.0 == 0.0) 11f else 8f) * px, ty), 1f)
            if (tick % 4.0 == 0.0) text(Fmt.n(tick, 0), regX + 13f * px, ty + 3.5f * px, Color.White.copy(alpha = 0.55f))
            tick += 2.0
        }
        text("sight tube", regX - 6f * px, tubeTop - 5f * px, Color.White.copy(alpha = 0.6f))
        // the line at its height, from the regulator along the house
        val lineY = yIn(heightIn)
        drawRect(Color.White.copy(alpha = 0.8f), Offset(regX - 9f * px, lineY - 6f * px), Size(18f * px, 12f * px), style = Stroke(1.4f * px))
        drawLine(water, Offset(regX - 9f * px, lineY), Offset(size.width, lineY), 3.5f * px)
        drawLine(Color.White.copy(alpha = 0.5f), Offset(regX, lineY - 6f * px), Offset(regX, tubeBottom), 1.2f * px)
        // the dial: the same pressure on a gauge, 0 to 20 in
        val gc = Offset(size.width * 0.46f, 34f * px); val gr = 22f * px
        drawCircle(Color.Black, gr, gc); drawCircle(Color.White.copy(alpha = 0.75f), gr, gc, style = Stroke(1.5f * px))
        for (k in 0..10) {
            val a = Math.toRadians(135.0 + 27.0 * k)
            drawLine(Color.White.copy(alpha = 0.5f), Offset(gc.x + (gr - 5f * px) * cos(a).toFloat(), gc.y + (gr - 5f * px) * sin(a).toFloat()),
                Offset(gc.x + (gr - 1.5f * px) * cos(a).toFloat(), gc.y + (gr - 1.5f * px) * sin(a).toFloat()), 1f)
        }
        val na = Math.toRadians(135.0 + 270.0 * pin / 20.0)
        drawLine(water, gc, Offset(gc.x + (gr - 6f * px) * cos(na).toFloat(), gc.y + (gr - 6f * px) * sin(na).toFloat()), 2.2f * px, cap = StrokeCap.Round)
        drawCircle(Color.White, 2.5f * px, gc)
        text(if (pressureIn != null && pressureIn > 0) "${Fmt.n(pressureIn, 1)} in water" else "—", gc.x + gr + 6f * px, gc.y + 4f * px, water, bold = true)
        text("line pressure", gc.x + gr + 6f * px, gc.y + 16f * px, Color.White.copy(alpha = 0.6f))
        // nipples along the line; the bird drinks at one of them
        var nx = regX + 40f * px
        while (nx < size.width - 6f * px) {
            drawLine(Color.White.copy(alpha = 0.75f), Offset(nx, lineY), Offset(nx, lineY + 7f * px), 2f * px)
            drawArc(Color.White.copy(alpha = 0.4f), 0f, 180f, false, Offset(nx - 5f * px, lineY + 3f * px), Size(10f * px, 6f * px), style = Stroke(1f))
            nx += 34f * px
        }
        // a drop at one nipple
        val tip = Offset(regX + 40f * px + 4 * 34f * px, lineY + 7f * px)
        drawCircle(water.copy(alpha = 1 - phase), 2.4f * px, Offset(tip.x, tip.y + phase * 6f * px))
        // the height, litter to the nipple line
        val hx = size.width - 22f * px
        drawLine(Color.White.copy(alpha = 0.8f), Offset(hx, litterTop), Offset(hx, lineY), 1.2f * px)
        drawLine(Color.White.copy(alpha = 0.8f), Offset(hx - 4f * px, litterTop), Offset(hx + 4f * px, litterTop), 1.2f * px)
        drawLine(Color.White.copy(alpha = 0.8f), Offset(hx - 4f * px, lineY), Offset(hx + 4f * px, lineY), 1.2f * px)
        text("${Fmt.n(heightIn, 1)} in", hx - 6f * px, (litterTop + lineY) / 2, kindColor(I), Paint.Align.RIGHT, bold = true)
        text("${Fmt.n(heightIn * 2.54, 1)} cm", hx - 6f * px, (litterTop + lineY) / 2 + 12f * px, Color.White.copy(alpha = 0.6f), Paint.Align.RIGHT)
    }
}

// =================================== flock book ===================================

private data class BookCol(val title: String, val width: Int, val cell: (OutputData, com.example.flock.data.DailyDataEntity) -> V)

private fun bookCols(): List<BookCol> = listOf(
    BookCol("Date", 58) { _, r -> vt(try { java.time.LocalDate.parse(r.date).format(java.time.format.DateTimeFormatter.ofPattern("dd MMM", java.util.Locale.US)) } catch (e: Exception) { r.date }, NE) },
    BookCol("Live", 62) { d, r -> vi(r.liveBirds, if (r.mortality > 0 || "M" in r.savedFields) P else PR) },
    BookCol("Dead", 46) { _, r -> vi(r.mortality, P) },
    BookCol("Mort %", 54) { _, r -> v(r.cumMortPct, 2, P) },
    BookCol("Lifted", 56) { _, r -> if (r.birdsLifted > 0) vi(r.birdsLifted, P) else vt("", NE) },
    BookCol("Lift kg", 64) { _, r -> if (r.weightLifted > 0) v(r.weightLifted, 1, P) else vt("", NE) },
    BookCol("Bags", 50) { d, r -> v(d.bagsUsedOnDay(r.dayNumber), 2, P) },
    BookCol("g/bird", 56) { d, r -> v(d.eatenGOn(r.dayNumber), 1, P) },
    BookCol("Weight", 64) { d, r -> if (r.avgWeight != null) v(r.avgWeight, 1, P) else v(d.massByDay[r.dayNumber]?.avgAfterKg?.times(1000), 1, PR) },
    BookCol("Company", 64) { _, r -> v(CompanyStandard.bw(r.dayNumber), 1, C) },
    BookCol("CV %", 48) { _, r -> v(r.cv, 1, P) },
    BookCol("FCR", 54) { d, r -> v(d.massByDay[r.dayNumber]?.fcr, 3, if (r.avgWeight != null) P else PR) },
    BookCol("cFCR", 54) { d, r -> v(d.massByDay[r.dayNumber]?.cFcrCompany, 3, if (r.avgWeight != null) P else PR) },
    BookCol("kg/m²", 52) { _, r -> v(r.densityKgM2, 1, if (r.avgWeight != null) P else PR) }
)

/**
 * The flock book: one line a day, the numbers that matter side by side like a small sheet — entered ones as entered,
 * projected ones marked; lifting days stand out. Tap a day to open it. Day stays put while the rest scrolls sideways.
 */
@Composable
fun FlockBookCard(d: OutputData, onSelectDay: ((Int) -> Unit)?) {
    var full by remember { mutableStateOf(false) }
    OutputCard(title = "Flock book", info = "book") {
        FlockBookGrid(d, Modifier.fillMaxWidth().heightIn(max = 380.dp), onSelectDay)
        OutlinedButton(onClick = { full = true }, border = androidx.compose.foundation.BorderStroke(1.dp, GlassLine), modifier = Modifier.fillMaxWidth().testTag("book_full")) {
            Text("Full screen", color = Color.White)
        }
    }
    if (full) androidx.compose.ui.window.Dialog(onDismissRequest = { full = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Flock book · ${d.flock?.name ?: ""}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                Text("Close", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White,
                    modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).clickable { full = false }.padding(horizontal = 12.dp, vertical = 6.dp))
            }
            FlockBookGrid(d, Modifier.fillMaxWidth().weight(1f)) { day -> onSelectDay?.invoke(day); full = false }
        }
    }
}

@Composable
private fun FlockBookGrid(d: OutputData, modifier: Modifier, onSelectDay: ((Int) -> Unit)?) {
    val cols = remember { bookCols() }
    val days = remember(d) { d.rows.filter { it.dayNumber <= max(d.day, d.lastEnteredDay) }.sortedByDescending { it.dayNumber } }
    val h = rememberScrollState()
    val mono = MaterialTheme.typography.labelMedium.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
    val head = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
    Column(modifier.border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp)).testTag("flock_book")) {
        // the titles
        Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.06f)).padding(vertical = 6.dp)) {
            Text("Day", Modifier.width(40.dp).padding(start = 6.dp), style = head, color = Color.White.copy(alpha = 0.8f))
            Row(Modifier.horizontalScroll(h)) { cols.forEach { c -> Text(c.title, Modifier.width(c.width.dp), style = head, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.End, maxLines = 1) } }
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            days.forEach { r ->
                val lifting = r.birdsLifted > 0
                val sel = r.dayNumber == d.day
                Row(Modifier.fillMaxWidth()
                    .background(if (lifting) kindColor(PR).copy(alpha = 0.10f) else Color.Transparent)
                    .border(if (sel) 1.5.dp else 0.dp, if (sel) Color.White else Color.Transparent)
                    .clickable(enabled = onSelectDay != null) { onSelectDay?.invoke(r.dayNumber) }
                    .padding(vertical = 5.dp).testTag("book_day_${r.dayNumber}"), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.dayNumber.toString() + if (lifting) " L" else "", Modifier.width(40.dp).padding(start = 6.dp), style = mono,
                        color = if (lifting) kindColor(PR) else Color.White)
                    Row(Modifier.horizontalScroll(h)) {
                        cols.forEach { c ->
                            val x = c.cell(d, r)
                            Text(x.text, Modifier.width(c.width.dp).padding(start = 2.dp), style = mono, color = kindColor(if (x.text == "—" || x.text.isEmpty()) NE else x.kind),
                                textAlign = TextAlign.End, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
    Text("green entered · yellow projected · L lifting", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
}
