package com.example.flock.ui.screens

import android.graphics.Paint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.engine.CompanyStandard
import com.example.flock.engine.IbController
import com.example.flock.engine.PhysiologicalEngine
import com.example.flock.ui.Fmt
import com.example.flock.ui.components.HouseFloorPlan
import com.example.ui.theme.StatusCrit
import com.example.ui.theme.StatusWarn
import com.example.ui.theme.ValueIdeal
import com.example.ui.theme.ValuePredicted
import com.example.ui.theme.ValuePresent
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val P = ValueKind.PRESENT
private val PR = ValueKind.PREDICTED
private val I = ValueKind.IDEAL
private val C = ValueKind.COMMERCIAL
private val N = ValueKind.NEUTRAL

// =================================== topic dispatcher ===================================

@Composable
fun TopicView(d: OutputData, t: Topic) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("${t.emoji}  ${t.title} · Day ${d.day}", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
        AlertPanel(d.alerts[t].orEmpty())
        when (t) {
            Topic.VENT -> VentTopic(d)
            Topic.ENV -> EnvTopic(d)
            Topic.BIRDS -> BirdsTopic(d)
            Topic.FEED -> FeedTopic(d)
            Topic.STOCK -> StockTopic(d)
        }
    }
}

@Composable
private fun AlertPanel(alerts: List<TopicAlert>) {
    val worst = alerts.maxOfOrNull { it.level } ?: 0
    Surface(color = alertColor(worst).copy(alpha = 0.14f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (alerts.isEmpty()) Text("✅  All in range", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = alertColor(0))
            alerts.sortedByDescending { it.level }.forEach { a ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(if (a.level == 2) "🔴" else "🟠", fontSize = 15.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(a.text, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

// =================================== value table ===================================

/** Column header: a tag chip (its kind colours it) or a plain scope title (per bird, whole farm …). */
data class VCol(val title: String, val kind: ValueKind = N)
data class VCell(val text: String, val kind: ValueKind)
data class VRow(val label: String, val unit: String, val cells: List<VCell?>, val strong: Boolean = false)

private fun cell(v: Double?, dec: Int, kind: ValueKind) = VCell(Fmt.n(v, dec), if (v == null) N else kind)
private fun txt(s: String, kind: ValueKind) = VCell(s, kind)
private fun count(v: Int?, kind: ValueKind) = VCell(Fmt.i(v), if (v == null) N else kind)

/**
 * Each row: the parameter name on its own line, then its values under the column headers —
 * so labels are never squeezed and values stay large.
 */
@Composable
fun VTable(cols: List<VCol>, rows: List<VRow>) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            cols.forEach { c ->
                if (c.kind == N) Text(c.title, modifier = Modifier.weight(1f), maxLines = 1,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = muted)
                else TagChip(c.kind, Modifier.weight(1f), c.title, compact = cols.size >= 4)
            }
        }
        rows.forEachIndexed { idx, r ->
            if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(r.label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (r.strong) FontWeight.Bold else FontWeight.SemiBold))
                    if (r.unit.isNotEmpty()) Text("  " + r.unit, style = MaterialTheme.typography.bodySmall, color = muted)
                }
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    cols.indices.forEach { i ->
                        val c = r.cells.getOrNull(i)
                        Text(
                            c?.text ?: "—", modifier = Modifier.weight(1f), maxLines = 2,
                            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace,
                                fontWeight = if (r.strong) FontWeight.Black else FontWeight.Bold, fontSize = 15.sp),
                            color = if (c == null || c.text == "—") kindColor(N) else kindColor(c.kind)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(a: GistItem, b: GistItem, c: GistItem) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GistTile(a, Modifier.weight(1f)); GistTile(b, Modifier.weight(1f)); GistTile(c, Modifier.weight(1f))
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// =================================== VENTILATION ===================================

@Composable
private fun VentTopic(d: OutputData) {
    val f = d.farm
    val heaterCap = f.heaterCount * f.heaterKw
    Hero(
        GistItem("Minimum", "${Fmt.n(d.minLevel.avgFans, 2)} fans", timerText(d.minLevel), I),
        GistItem("Hottest hour", "${Fmt.n(d.scenarios.last().state.fans, 2)} fans", "at ${Fmt.n(d.scenarios.last().outC, 1)} °C", PR),
        GistItem("Allowed", "${Fmt.n(d.plan.maxFans.toDouble(), 1)} fans", "age cap + 2", I)
    )

    d.now?.let { s ->
        OutputCard(title = "🟢 Right now · ${String.format("%02d:00", d.hour)}") {
            HouseAirflow(d.levelOf(s), f.fanCount, f.hasEC, s.padEff > 0, s.heaterKw > 0.05, d.airFpm(s.fans))
            Text(d.modeOf(s), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            VTable(
                listOf(VCol("Present", P), VCol("Projected", PR)),
                listOf(
                    VRow("Outside air", "°C · % RH", listOf(txt("${Fmt.n(d.weather!!.tempC, 1)} · ${Fmt.n(d.weather.rhPercent, 1)}", P), null)),
                    VRow("House air", "°C · % RH", listOf(null, txt("${Fmt.n(s.houseC, 1)} · ${Fmt.n(s.houseRh, 1)}", PR))),
                    VRow("Birds feel", "°C (comfort ${Fmt.n(d.plan.comfort, 1)})", listOf(null, cell(s.feltC, 1, PR)), strong = true),
                    VRow("Fans running", "time-averaged", listOf(cell(d.e.actualFans?.toDouble(), 1, P), cell(s.fans, 2, PR))),
                    VRow("Air speed at birds", "ft/min", listOf(cell(d.e.measuredAirspeed, 1, P), cell(d.airFpm(s.fans), 1, PR))),
                    VRow("Wind-chill on birds", "°C cooler", listOf(null, cell(s.chillC, 1, PR))),
                    VRow("Heaters", "kW", listOf(null, cell(s.heaterKw, 1, PR)))
                )
            )
            FeltGauge(comfort = d.plan.comfort, felt = s.feltC, air = s.houseC)
            val pts = d.hourly.let { hs ->
                val start = hs.indexOfFirst { it.hour == d.hour }.takeIf { it >= 0 } ?: 0
                hs.drop(start).take(24)
            }
            if (pts.size >= 6) {
                val states = pts.map { IbController.simulate(d.plan, it.tempC, it.rhPct, it.hour, d.live, d.bw, f) }
                Next24Chart(pts, states.map { it.feltC }, states.map { it.fans }, d.plan.comfort, f.fanCount)
            }
        }
    }

    OutputCard(title = "🔄 Minimum ventilation") {
        HouseAirflow(d.minLevel, f.fanCount, f.hasEC, false, false, d.airFpm(d.minLevel.avgFans))
        Text(
            if (d.minLevel.isTimer) "Fan ${d.minLevel.cyc.joinToString(", ")} on a timer: ${d.minLevel.on}.0 s ON / ${d.minLevel.off}.0 s OFF"
            else "${d.minLevel.fansOn}.0 fans non-stop" + if (d.minLevel.cyc.isNotEmpty()) " + fan ${d.minLevel.cyc.joinToString(", ")} on ${d.minLevel.on}/${d.minLevel.off} s" else "",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        val live = d.liveSafe.toDouble()
        val rossPb = PhysiologicalEngine.rossMinVentCfmPerBird(d.avgKg)
        VTable(
            listOf(VCol("Per bird"), VCol("Whole house")),
            listOf(
                VRow("Air needed (Ross × 1.30 × calibration)", "cfm", listOf(cell(d.plan.needCfm / live, 3, I), cell(d.plan.needCfm, 1, I)), strong = true),
                VRow("Ross air-quality floor", "cfm", listOf(cell(rossPb, 3, I), cell(rossPb * live, 1, I))),
                VRow("Air delivered at minimum", "cfm", listOf(cell(d.minAvgCfm / live, 3, PR), cell(d.minAvgCfm, 1, PR))),
                VRow("Fans running on average", "fans", listOf(null, cell(d.minLevel.avgFans, 2, PR))),
                VRow("Timer", "s ON · s OFF", listOf(null, txt(if (d.minLevel.isTimer) "${d.minLevel.on}.0 · ${d.minLevel.off}.0" else "non-stop", I))),
                VRow("Air changes", "per hour", listOf(null, cell(if (d.houseVolFt3 > 0) d.minAvgCfm * 60 / d.houseVolFt3 else null, 2, PR))),
                VRow("Air speed at birds", "ft/min", listOf(null, cell(d.airFpm(d.minLevel.avgFans), 1, PR)))
            )
        )
        Note("Minimum ventilation is for air quality (moisture, ammonia, CO₂), not cooling. The animation speeds the timer up; the real cycle is ${d.minLevel.on + d.minLevel.off}.0 s.")
    }

    OutputCard(title = "🌡️ Fans across the day") {
        val lo = d.scenarios.first(); val hi = d.scenarios.last()
        Text("Run ${Fmt.n(lo.state.fans, 2)} → ${Fmt.n(hi.state.fans, 2)} fans as outside goes ${Fmt.n(lo.outC, 1)} → ${Fmt.n(hi.outC, 1)} °C",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Note("Based on the ${d.scenarioSource}. Values are projected by the house model.")
        d.scenarios.forEach { s ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text("${s.emoji}  ${s.label} · ${String.format("%02d:00", s.hour)} · ${Fmt.n(s.outC, 1)} °C · ${Fmt.n(s.outRh, 1)}% RH",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            HouseAirflow(s.level, f.fanCount, f.hasEC, s.state.padEff > 0, s.state.heaterKw > 0.05, s.airFpm)
            Text(s.mode, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = ValuePredicted)
            VTable(
                listOf(VCol("Projected", PR), VCol("Ideal", I)),
                listOf(
                    VRow("Fans running", "time-averaged · fans on", listOf(txt("${Fmt.n(s.state.fans, 2)} · ${s.level.fansOn}.0", PR), txt("≤ ${d.plan.maxFans}.0", I)), strong = true),
                    VRow("Air speed at birds", "ft/min", listOf(cell(s.airFpm, 1, PR), cell(PhysiologicalEngine.maxAirSpeedFpm(d.day), 1, I))),
                    VRow("House air", "°C · % RH", listOf(txt("${Fmt.n(s.state.houseC, 1)} · ${Fmt.n(s.state.houseRh, 1)}", PR), txt("${Fmt.n(d.e.tempIdeal, 1)} · ${Fmt.n(d.e.rhIdeal, 1)}", I))),
                    VRow("Birds feel", "°C", listOf(cell(s.state.feltC, 1, PR), cell(d.plan.comfort, 1, I)), strong = true),
                    VRow("Heaters", "kW of ${Fmt.n(heaterCap, 1)}", listOf(cell(s.state.heaterKw, 1, PR), null)),
                    VRow("Cooling pads", "% efficiency used", listOf(cell(s.state.padEff * 100, 1, PR), null))
                )
            )
        }
    }

    OutputCard(title = "🎛️ Controller for day ${d.day}") {
        VTable(
            listOf(VCol("Ideal", I)),
            listOf(
                VRow("Bird comfort (still air, 65% RH)", "°C", listOf(cell(d.plan.comfort, 1, I)), strong = true),
                VRow("House target at minimum", "°C", listOf(cell(d.plan.target, 1, I))),
                VRow("SET temperature", "°C", listOf(cell(d.plan.set, 1, I))),
                VRow("Heaters on below", "°C", listOf(cell(d.plan.heat, 1, I))),
                VRow("Fans allowed at this age", "fans", listOf(cell(d.plan.maxFans.toDouble(), 1, I))),
                VRow("Air-speed limit for this age", "ft/min", listOf(cell(PhysiologicalEngine.maxAirSpeedFpm(d.day), 1, I)))
            )
        )
    }

    OutputCard(title = "🏭 Fan capacity") {
        VTable(
            listOf(VCol("One fan"), VCol("All ${f.fanCount}.0 fans")),
            listOf(
                VRow("Airflow", "cfm (rated × ${Fmt.n(1 - f.fanDerate, 2)})", listOf(cell(d.fanCfm, 1, I), cell(d.allFansCfm, 1, I))),
                VRow("Air speed at birds", "ft/min", listOf(cell(d.airFpm(1.0), 1, I), cell(d.airFpm(f.fanCount.toDouble()), 1, I))),
                VRow("Air changes", "per hour", listOf(cell(if (d.houseVolFt3 > 0) d.fanCfm * 60 / d.houseVolFt3 else null, 2, I), cell(if (d.houseVolFt3 > 0) d.allFansCfm * 60 / d.houseVolFt3 else null, 2, I)))
            )
        )
        Note("House cross-section ${Fmt.n(d.plan.crossFt2, 1)} ft². Birds and sensors sit ~1 ft above the litter, where air runs at about ${Fmt.n(IbController.FLOOR_AIR_FACTOR * 100, 1)}% of the average speed.")
    }
}

private fun timerText(l: IbController.Level) =
    if (l.isTimer && l.cont.isEmpty()) "${l.on}.0 s on / ${l.off}.0 s off" else "${l.cont.size}.0 non-stop"

/**
 * Top view of the house: pads on the left, fans on the right end wall. Running fans spin, the timer
 * fan blinks through a sped-up ON/OFF cycle (amber ring), and air streaks move at a speed
 * matching the air speed at bird height.
 */
@Composable
fun HouseAirflow(level: IbController.Level, fanCount: Int, hasPads: Boolean, padsOn: Boolean, heatersOn: Boolean, airFpm: Double) {
    val n = max(1, fanCount)
    val inf = rememberInfiniteTransition(label = "air")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "t")
    val cyc by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "cyc")
    val timerOn = level.cyc.isEmpty() || cyc < level.duty
    val houseC = MaterialTheme.colorScheme.surfaceVariant
    val offC = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val running = level.cont.size + if (timerOn) level.cyc.size else 0
    Column {
        Canvas(Modifier.fillMaxWidth().height(132.dp)) {
            val w = size.width; val h = size.height
            val rowsF = ceil(n / 2.0).toInt()
            val fanR = min((h - 16f) / rowsF / 2f - 2f, 26f)
            val fanColW = fanR * 4 + 46f
            val left = if (hasPads) 22f else 6f
            val right = w - fanColW - 8f
            val top = 6f; val bottom = h - 6f
            drawRoundRect(houseC, Offset(left, top), Size(right - left, bottom - top), CornerRadius(14f, 14f))
            if (hasPads) {
                val padC = if (padsOn) ValueIdeal else offC
                drawRoundRect(padC, Offset(4f, top + 8f), Size(14f, bottom - top - 16f), CornerRadius(4f, 4f))
                var y = top + 14f
                while (y < bottom - 12f) { drawLine(Color.White.copy(alpha = 0.35f), Offset(6f, y), Offset(16f, y + 6f), strokeWidth = 2f); y += 10f }
            }
            if (heatersOn) listOf(0.3f, 0.55f, 0.8f).forEach { fx ->
                val c = Offset(left + (right - left) * fx, (top + bottom) / 2)
                drawCircle(Color(0xFFE0703A).copy(alpha = 0.25f + 0.2f * t), 20f, c)
                drawCircle(Color(0xFFE0703A), 8f, c)
            }
            // air streaks, speed ∝ air speed at birds
            val k = (airFpm / 150.0).coerceIn(0.3, 5.0)
            val mult = max(1, (k * 2).roundToInt())
            val spacing = 64f
            val off = ((t * mult) % 1f) * spacing
            val frac = running.toFloat() / n
            val alpha = if (running == 0) 0.06f else 0.25f + 0.6f * frac
            val rows = 6
            for (r in 0 until rows) {
                val y = top + (r + 0.5f) * (bottom - top) / rows
                var x = left - spacing + off + (r % 2) * spacing / 2
                while (x < right - 4f) {
                    val x0 = max(x, left + 4f); val x1 = min(x + 26f, right - 4f)
                    if (x1 > x0) drawLine(ValueIdeal.copy(alpha = alpha), Offset(x0, y), Offset(x1, y), strokeWidth = 3f, cap = StrokeCap.Round)
                    x += spacing
                }
            }
            // fans: 2 columns on the end wall, numbered 1..n top-left to bottom-right
            val lbl = Paint().apply { color = labelArgb; textSize = 22f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
            for (i in 1..n) {
                val col = (i - 1) % 2; val row = (i - 1) / 2
                val cx = right + 22f + fanR + col * (fanR * 2 + 18f)
                val cy = top + (bottom - top) * (row + 0.5f) / rowsF
                val isCont = i in level.cont
                val isCyc = i in level.cyc
                val on = isCont || (isCyc && timerOn)
                drawCircle(houseC, fanR, Offset(cx, cy))
                fanBlades(Offset(cx, cy), fanR * 0.85f, if (on) t * 360f * 3 else 20f, if (on) ValuePresent else offC)
                if (isCyc) drawArc(ValuePredicted, -90f, 360f * cyc, false, Offset(cx - fanR, cy - fanR), Size(fanR * 2, fanR * 2), style = Stroke(width = 3.5f))
                drawContext.canvas.nativeCanvas.drawText("$i", cx - fanR - 8f, cy + 8f, lbl)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("● ${running}.0 of $n.0 fans on", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = ValuePresent)
            if (level.isTimer) Text("◔ timer fan", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = ValuePredicted)
            if (hasPads) Text(if (padsOn) "▮ pads wet" else "▮ pads off", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = if (padsOn) ValueIdeal else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun DrawScope.fanBlades(c: Offset, r: Float, rotation: Float, color: Color) {
    drawCircle(color, r * 0.18f, c)
    for (i in 0..2) {
        rotate(degrees = rotation + i * 120f, pivot = c) {
            val p = Path().apply {
                moveTo(c.x, c.y)
                quadraticBezierTo(c.x + r * 0.55f, c.y - r * 0.35f, c.x + r * 0.12f, c.y - r * 0.95f)
                quadraticBezierTo(c.x - r * 0.20f, c.y - r * 0.50f, c.x, c.y)
                close()
            }
            drawPath(p, color)
        }
    }
}

// =================================== ENVIRONMENT ===================================

@Composable
private fun EnvTopic(d: OutputData) {
    val e = d.e
    val w = d.weather
    Hero(
        GistItem("Outside", w?.let { "${Fmt.n(it.tempC, 1)} °C" } ?: "—", w?.let { "${Fmt.n(it.rhPercent, 1)}% RH" } ?: "no weather", P),
        GistItem("Comfort", "${Fmt.n(d.plan.comfort, 1)} °C", "birds, still air", I),
        GistItem("Birds feel", d.now?.let { "${Fmt.n(it.feltC, 1)} °C" } ?: "—", d.now?.let { "house ${Fmt.n(it.houseC, 1)} °C" } ?: "today only", PR)
    )
    OutputCard(title = "🌡️ Temperature & humidity") {
        VTable(
            listOf(VCol("Min", I), VCol("Ideal", I), VCol("Max", I)),
            listOf(
                VRow("House temperature", "°C", listOf(cell(e.tempMin, 1, I), cell(e.tempIdeal, 1, I), cell(e.tempMax, 1, I)), strong = true),
                VRow("Relative humidity", "%", listOf(cell(e.rhMin, 1, I), cell(e.rhIdeal, 1, I), cell(e.rhMax, 1, I)))
            )
        )
        VTable(
            listOf(VCol("Present", P), VCol("Projected", PR)),
            listOf(
                VRow("Outside temperature", "°C", listOf(cell(w?.tempC ?: e.outTemp, 1, P), null)),
                VRow("Outside humidity", "% RH", listOf(cell(w?.rhPercent ?: e.outRH, 1, P), null)),
                VRow("Wind", "km/h", listOf(cell(w?.windKmh, 1, P), null)),
                VRow("House temperature now", "°C", listOf(null, cell(d.now?.houseC, 1, PR))),
                VRow("House humidity now", "% RH", listOf(null, cell(d.now?.houseRh, 1, PR))),
                VRow("Birds feel now", "°C", listOf(null, cell(d.now?.feltC, 1, PR)), strong = true)
            )
        )
        Note(w?.let { "Weather: ${it.locationName}${if (it.isLive) "" else " (offline — last known)"}." } ?: "Weather not loaded — open the weather chip on top.")
    }
    OutputCard(title = "💨 Air quality") {
        VTable(
            listOf(VCol("Present", P), VCol("Ideal", I)),
            listOf(
                VRow("CO₂", "ppm", listOf(cell(e.measuredCo2, 1, P), txt("< ${Fmt.n(e.co2Max, 1)}", I))),
                VRow("Ammonia NH₃", "ppm", listOf(cell(e.measuredNh3, 1, P), txt("< ${Fmt.n(e.nh3Max, 1)}", I))),
                VRow("Carbon monoxide CO", "ppm", listOf(null, txt("< 10.0", I))),
                VRow("Oxygen O₂", "%", listOf(cell(e.measuredO2, 2, P), txt("≥ 19.60", I))),
                VRow("Dust", "mg/m³", listOf(null, txt("< 5.0", I))),
                VRow("Static pressure", "Pa", listOf(cell(e.measuredPressure, 1, P), txt("20.0–25.0 min · 30.0–37.0 tunnel", I))),
                VRow("Air speed at birds", "ft/min", listOf(cell(e.measuredAirspeed, 1, P), txt("≤ ${Fmt.n(PhysiologicalEngine.maxAirSpeedFpm(d.day), 1)}", I))),
                VRow("Pad wet time", "min", listOf(cell(e.padWetMin, 1, P), null)),
                VRow("Pad dry time", "min", listOf(cell(e.padDryMin, 1, P), null))
            )
        )
        Note("Present values are what you entered on the Entry tab; — means no reading today.")
    }
    OutputCard(title = "💡 Light & litter") {
        VTable(
            listOf(VCol("Present", P), VCol("Ideal", I)),
            listOf(
                VRow("Light", "hours per day", listOf(null, cell(e.lightHours, 1, I))),
                VRow("Dark", "hours per day", listOf(null, cell(24.0 - e.lightHours, 1, I))),
                VRow("Light intensity", "lux", listOf(cell(e.luxPerFt2, 1, P), txt(d.lightIdealLux, I))),
                VRow("Litter moisture", "%", listOf(null, txt("20.0–25.0", I)))
            )
        )
        Note("Litter over 30.0% moisture → ammonia and foot-pad burns; rake every 2 days.")
    }
    OutputCard(title = "🔥 Bird heat & moisture") {
        VTable(
            listOf(VCol("Per bird"), VCol("Whole house")),
            listOf(
                VRow("Total heat", "W per bird · kW house", listOf(cell(d.heatPerBirdW, 2, d.vk), cell(d.heatPerBirdW * d.live / 1000.0, 2, d.vk)), strong = true),
                VRow("Sensible heat (warms air)", "W · kW", listOf(cell(d.heatPerBirdW * d.sensibleFrac, 2, d.vk), cell(d.heatPerBirdW * d.sensibleFrac * d.live / 1000.0, 2, d.vk))),
                VRow("Latent heat (as moisture)", "W · kW", listOf(cell(d.heatPerBirdW * (1 - d.sensibleFrac), 2, d.vk), cell(d.heatPerBirdW * (1 - d.sensibleFrac) * d.live / 1000.0, 2, d.vk))),
                VRow("Moisture breathed out", "g/h · kg/h", listOf(cell(d.moistureGPerBirdHr, 2, d.vk), cell(d.moistureGPerBirdHr * d.live / 1000.0, 2, d.vk)))
            )
        )
        Note("Bird heat = 10.62 × kg^0.75 W (CIGR). This is the heat and water the ventilation has to remove.")
    }
}

// =================================== BIRDS ===================================

@Composable
private fun BirdsTopic(d: OutputData) {
    val e = d.e
    Hero(
        GistItem("Live birds", Fmt.i(d.live), "of ${Fmt.i(d.placed)} placed", P),
        GistItem("Avg weight", "${Fmt.n(d.bw, 1)} g", if (e.avgWeight != null) "sampled today" else "no sample today", d.vk),
        GistItem("Mortality", Fmt.pct(d.mortTDPct), "till date", P)
    )
    KpiCard(
        "🎯 How the flock compares",
        listOf(
            Kpi("Body weight", "g", d.bw, d.vk, d.bwCom, d.bwIdeal, Better.HIGHER, 1),
            Kpi("Daily gain", "g/day", d.gain, d.gainKind, d.gainCom, d.gainIdeal, Better.HIGHER, 1),
            Kpi("FCR", "", e.fcr, P, d.fcrCom, d.fcrIdeal, Better.LOWER, 3, settling = d.day < 7),
            Kpi("cFCR", "to 2 kg", e.cFcr, P, d.cfcrCom, d.cfcrIdeal, Better.LOWER, 3, settling = d.day < 7),
            Kpi("Mortality", "% till date", d.mortTDPct, P, d.comCumPct, d.ceilingPct, Better.LOWER, 2, points = true),
            Kpi("EPEF", "efficiency", d.epef, P, d.epefCom, d.epefIdeal, Better.HIGHER, 1, settling = d.day < 7)
        ),
        d.day
    )
    OutputCard(title = "🐣 Population") {
        val comCumBirds = d.comCumPct * d.placed / 100.0
        VTable(
            listOf(VCol("Today", P), VCol("Commercial", C), VCol("Till date", P), VCol("Commercial", C)),
            listOf(
                VRow("Birds placed", "head", listOf(null, null, count(d.placed, P), null)),
                VRow("Reception / transit deaths", "head", listOf(null, null, count(d.reception, P), null)),
                VRow("Deaths", "birds", listOf(count(d.mortToday, P), cell(d.comMortBirdsToday, 1, C), count(d.mortTD, P), cell(comCumBirds, 1, C)), strong = true),
                VRow("Deaths", "% of birds", listOf(cell(d.mortTodayPct, 3, P), cell(d.comDailyPct, 3, C), cell(d.mortTDPct, 2, P), cell(d.comCumPct, 2, C))),
                VRow("Culls / lame", "birds", listOf(count(e.lameSeparated, P), null, count(d.lameTD, P), null)),
                VRow("Lifted", "birds", listOf(count(e.birdsLifted, P), null, count(d.liftTD, P), null)),
                VRow("Lifted weight", "kg", listOf(cell(e.weightLifted, 2, P), null, cell(d.liftKgTD, 2, P), null)),
                VRow("Live birds", "head", listOf(count(d.live, P), null, null, cell(d.placed - comCumBirds, 1, C)), strong = true),
                VRow("Livability", "%", listOf(null, null, cell(e.livability, 2, P), cell(100 - d.comCumPct, 2, C)))
            )
        )
        Note("Ideal (industry benchmark) mortality at day ${d.day}: ${Fmt.pct(d.ceilingPct)}. Till-date deaths include reception deaths.")
    }
    OutputCard(title = "⚖️ Weight & growth") {
        val live = d.live.toDouble()
        VTable(
            listOf(VCol(kindTag(d.vk), d.vk), VCol("Commercial", C), VCol("Ideal", I)),
            listOf(
                VRow("Average weight", "g per bird", listOf(cell(d.bw, 1, d.vk), cell(d.bwCom, 1, C), cell(d.bwIdeal, 1, I)), strong = true),
                VRow("Whole flock live weight", "kg", listOf(cell(live * d.bw / 1000, 1, d.vk), cell(d.bwCom?.let { live * it / 1000 }, 1, C), cell(live * d.bwIdeal / 1000, 1, I))),
                VRow("vs commercial", "%", listOf(txt(d.bwCom?.let { Fmt.signed((d.bw - it) / it * 100, 2) + "%" } ?: "—", d.vk), null, txt(d.bwCom?.let { Fmt.signed((d.bwIdeal - it) / it * 100, 2) + "%" } ?: "—", I))),
                VRow("Weight-age", "days", listOf(cell(e.weightAge, 2, d.vk), null, cell(d.day.toDouble(), 1, I))),
                VRow("Daily gain", "g per bird per day", listOf(cell(d.gain, 1, d.gainKind), cell(d.gainCom, 1, C), cell(d.gainIdeal, 1, I))),
                VRow("Daily gain, whole flock", "kg per day", listOf(cell(d.gain * live / 1000, 1, d.gainKind), cell(d.gainCom?.let { it * live / 1000 }, 1, C), cell(d.gainIdeal * live / 1000, 1, I))),
                VRow("Uniformity CV", "%", listOf(cell(e.cv, 2, P), null, txt("< 10.00", I)))
            )
        )
    }
    OutputCard(title = "🔁 Conversion") {
        VTable(
            listOf(VCol("Present", P), VCol("Commercial", C), VCol("Ideal", I)),
            listOf(
                VRow("FCR", "kg feed per kg bird", listOf(cell(e.fcr, 3, P), cell(d.fcrCom, 3, C), cell(d.fcrIdeal, 3, I)), strong = true),
                VRow("cFCR to 2 kg", "(2 − kg) × 0.25 + FCR", listOf(cell(e.cFcr, 3, P), cell(d.cfcrCom, 3, C), cell(d.cfcrIdeal, 3, I))),
                VRow("EPEF", "livability × kg × 100 ÷ (age × FCR)", listOf(cell(d.epef, 1, P), cell(d.epefCom, 1, C), cell(d.epefIdeal, 1, I))),
                VRow("7-day weight multiple", "day-7 weight ÷ chick weight", listOf(cell(d.sevenDayMultiple, 2, P), null, txt("≥ 4.50", I)))
            )
        )
    }
    OutputCard(title = "🏠 Space") {
        VTable(
            listOf(VCol(kindTag(d.vk), d.vk), VCol("Ideal", I)),
            listOf(
                VRow("Stocking density", "kg per ft²", listOf(cell(e.densityKgM2?.let { kgPerFt2(it) }, 3, d.vk), txt("≤ ${Fmt.n(kgPerFt2(d.farm.densityCapDefault), 3)}", I)), strong = true),
                VRow("Floor space", "ft² per bird", listOf(cell(e.ftPerBird, 3, d.vk), txt("≥ ${Fmt.n(e.minFtPerBird, 3)}", I))),
                VRow("Birds per ft²", "birds", listOf(cell(if (e.ftPerBird > 0) 1 / e.ftPerBird else null, 2, d.vk), null)),
                VRow("Occupied floor", "ft²", listOf(cell(e.occupiedFt2, 1, d.vk), cell(d.farm.usableLengthFt * d.farm.usableWidthFt, 1, I))),
                VRow("Brooding barricade at", "ft from the front", listOf(cell(if (e.barricadeFt > 0) e.barricadeFt.toDouble() else null, 1, d.vk), cell(d.farm.usableLengthFt, 1, I)))
            )
        )
    }
    HouseFloorPlan(entry = e, farm = d.farm)
    PopulationDistributionCard(entry = e)
    BirdCharts(d)
}

// =================================== FEED & WATER ===================================

@Composable
private fun FeedTopic(d: OutputData) {
    val e = d.e
    val live = d.live.toDouble()
    val yLive = (d.byDay[d.day - 1]?.liveBirds ?: d.live).toDouble()
    Hero(
        GistItem("Feed to give", "${Fmt.n(d.giveBags, 2)} bags", "${Fmt.n(d.giveKg, 1)} kg · ${d.phase}", d.vk),
        GistItem("Per bird", "${Fmt.n(d.givePerBird, 1)} g", "commercial ${Fmt.n(d.comPerBird, 1)} g", d.vk),
        GistItem("Water", "${Fmt.n(e.totalWaterL, 1)} L", "${Fmt.n(e.totalWaterL / d.tankL * d.refillF, 2)} tank fills", PR)
    )
    KpiCard(
        "🎯 Feed vs standards",
        listOf(
            Kpi("Eaten yesterday", "g per bird", d.usedPerBirdY, P, d.comPerBirdY, d.idealPerBirdY, Better.CLOSER, 1),
            Kpi("Eaten till date", "g per bird", d.cumPerBird, P, d.cumPerBirdCom, d.cumPerBirdIdeal, Better.CLOSER, 1),
            Kpi("To give today", "g per bird", d.givePerBird, d.vk, d.comPerBird, d.idealPerBird, Better.CLOSER, 1)
        ),
        d.day
    )
    OutputCard(title = "🌾 Feed today") {
        Text(
            "Phase ${d.phase}" + (d.nextPhaseDay?.let { " until day ${it - 1} · ${CompanyStandard.feedPhase(it)} from day $it" } ?: " until lifting"),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        val bag = d.bagKg
        VTable(
            listOf(VCol("Per bird g"), VCol("Farm kg"), VCol("Bags")),
            listOf(
                VRow("To give today", "your flock, heat-adjusted", listOf(cell(d.givePerBird, 1, d.vk), cell(d.giveKg, 2, d.vk), cell(d.giveBags, 2, d.vk)), strong = true),
                VRow("Commercial ration", "company chart", listOf(cell(d.comPerBird, 1, C), cell(d.comPerBird?.let { it * live / 1000 }, 2, C), cell(d.comPerBird?.let { it * live / 1000 / bag }, 2, C))),
                VRow("Ideal ration", "Ross 308", listOf(cell(d.idealPerBird, 1, I), cell(d.idealPerBird * live / 1000, 2, I), cell(d.idealPerBird * live / 1000 / bag, 2, I))),
                VRow("Eaten yesterday", "entered today", listOf(cell(d.usedPerBirdY, 1, P), cell(d.usedKgToday, 2, P), cell(d.usedBagsToday, 2, P)), strong = true),
                VRow("Commercial yesterday", "", listOf(cell(d.comPerBirdY, 1, C), cell(d.comPerBirdY?.let { it * yLive / 1000 }, 2, C), cell(d.comPerBirdY?.let { it * yLive / 1000 / bag }, 2, C))),
                VRow("Ideal yesterday", "", listOf(cell(d.idealPerBirdY, 1, I), cell(d.idealPerBirdY * yLive / 1000, 2, I), cell(d.idealPerBirdY * yLive / 1000 / bag, 2, I)))
            )
        )
        Note("Bag = ${Fmt.n(bag, 1)} kg. Values are exact — round up only when issuing bags.")
    }
    OutputCard(title = "📊 Feed till date") {
        val bag = d.bagKg
        VTable(
            listOf(VCol("Per bird g"), VCol("Farm kg"), VCol("Bags")),
            listOf(
                VRow("Eaten till date", "", listOf(cell(d.cumPerBird, 1, P), cell(d.usedKgTD, 2, P), cell(d.usedBagsTD, 2, P)), strong = true),
                VRow("Commercial", "", listOf(cell(d.cumPerBirdCom, 1, C), cell(d.comKgTD, 2, C), cell(d.comKgTD / bag, 2, C))),
                VRow("Ideal", "", listOf(cell(d.cumPerBirdIdeal, 1, I), cell(d.idealKgTD, 2, I), cell(d.idealKgTD / bag, 2, I))),
                VRow("More (+) / less (−) than commercial", "", listOf(
                    txt(if (d.cumPerBird != null && d.cumPerBirdCom != null) Fmt.signed(d.cumPerBird - d.cumPerBirdCom, 1) else "—", P),
                    txt(Fmt.signed(d.usedKgTD - d.comKgTD, 2), P), txt(Fmt.signed((d.usedKgTD - d.comKgTD) / bag, 2), P))),
                VRow("Still needed to lifting", "day ${d.day}–${d.harvestAge}", listOf(cell(d.remainPlanKg * 1000 / d.liveSafe, 1, PR), cell(d.remainPlanKg, 2, PR), cell(d.remainPlanKg / bag, 2, PR)))
            )
        )
    }
    OutputCard(title = "💧 Water") {
        val tank = d.tankL; val fct = d.refillF
        VTable(
            listOf(VCol("Per bird mL"), VCol("Farm L"), VCol("Tank fills")),
            listOf(
                VRow("Today", "projected (water isn't logged)", listOf(cell(e.waterPerBird, 1, PR), cell(e.totalWaterL, 1, PR), cell(e.totalWaterL / tank * fct, 2, PR)), strong = true),
                VRow("Hot day (+3.0 °C)", "", listOf(cell(e.waterHighL * 1000 / d.liveSafe, 1, PR), cell(e.waterHighL, 1, PR), cell(e.waterHighL / tank * fct, 2, PR))),
                VRow("Cool day (−3.0 °C)", "", listOf(cell(e.waterLowL * 1000 / d.liveSafe, 1, PR), cell(e.waterLowL, 1, PR), cell(e.waterLowL / tank * fct, 2, PR))),
                VRow("Till date", "", listOf(cell(d.waterTD * 1000 / d.liveSafe, 1, PR), cell(d.waterTD, 1, PR), cell(d.waterTD / tank * fct, 2, PR)))
            )
        )
        VTable(
            listOf(VCol("Present", P), VCol("Projected", PR), VCol("Ideal", I)),
            listOf(
                VRow("Per drinker line", "L/hour over 16.0 h", listOf(null, cell(e.drinkerFlowLHrLine, 2, PR), null)),
                VRow("Water : feed", "ratio", listOf(null, cell(if (e.totalFeedKg > 0) e.totalWaterL / e.totalFeedKg else null, 2, PR), txt("1.80–2.00", I))),
                VRow("Drinker height", "inches", listOf(null, null, cell(d.drinkerHtIn, 1, I))),
                VRow("Line pressure", "inches of water", listOf(null, null, cell(e.drinkerPressureIn, 1, I))),
                VRow("Nipple flow", "mL/min", listOf(null, null, txt("60.0–90.0", I))),
                VRow("Water pH", "", listOf(cell(e.waterPh, 2, P), null, txt("6.00–6.80", I))),
                VRow("Water temperature", "°C", listOf(cell(e.waterTempC, 1, P), null, txt("10.0–25.0", I)))
            )
        )
        Note("Tank = ${Fmt.n(tank, 1)} L; fills include your calibration × ${Fmt.n(fct, 2)}.")
    }
    FeedCharts(d)
}

// =================================== STOCK ===================================

@Composable
private fun StockTopic(d: OutputData) {
    Hero(
        GistItem("In store", "${Fmt.n(d.stockBagsTotal, 2)} bags", "${Fmt.n(d.stockKgTotal, 1)} kg", P),
        GistItem("Lasts", d.lastsDays?.takeIf { d.stockKgTotal > 0 }?.let { "${Fmt.n(it, 1)} days" } ?: "—", "at today's ration", PR),
        GistItem("To order", "${Fmt.n(d.toOrderBags, 2)} bags", "to reach day ${d.harvestAge}", PR)
    )
    OutputCard(title = "📦 Feed store") {
        if (d.stockCodes.isEmpty()) {
            Note("No deliveries logged yet. Enter feed received on the Entry tab.")
        } else {
            val maxRec = d.stockCodes.maxOf { max(d.recByCode[it] ?: 0.0, d.stockBags(it)) }
            val unit = listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0).firstOrNull { maxRec / it <= 20 } ?: 200.0
            Text("Each sack = ${Fmt.n(unit, 1)} bags · filled = in store · faded = used",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            d.stockCodes.forEach { code -> FeedSackRow(d, code, unit) }
        }
        Note("Store below 25.0 °C and 60.0% RH, on pallets, first in – first out.")
    }
    OutputCard(title = "🧮 Store totals") {
        VTable(
            listOf(VCol("Bags"), VCol("kg")),
            listOf(
                VRow("Received till date", "", listOf(cell(d.recBagsTD, 2, P), cell(d.recKgTD, 2, P))),
                VRow("Used till date", "", listOf(cell(d.usedBagsTD, 2, P), cell(d.usedKgTD, 2, P))),
                VRow("In store now", "", listOf(cell(d.stockBagsTotal, 2, P), cell(d.stockKgTotal, 2, P)), strong = true),
                VRow("Needed today", "", listOf(cell(d.giveBags, 2, d.vk), cell(d.giveKg, 2, d.vk))),
                VRow("Needed to lifting", "day ${d.day}–${d.harvestAge}", listOf(cell(d.remainPlanKg / d.bagKg, 2, PR), cell(d.remainPlanKg, 2, PR))),
                VRow("Still to order", "", listOf(cell(d.toOrderBags, 2, PR), cell(d.toOrderBags * d.bagKg, 2, PR)), strong = true)
            )
        )
    }
    OutputCard(title = "⛽ Diesel") {
        val canL = d.farm.dieselCanL
        VTable(
            listOf(VCol("Today", P), VCol("Till date", P)),
            listOf(
                VRow("Cans", "", listOf(cell(d.e.dieselCansUsed, 2, P), cell(d.dieselTD, 2, P))),
                VRow("Litres", "${Fmt.n(canL, 1)} L per can", listOf(cell(d.e.dieselCansUsed * canL, 1, P), cell(d.dieselTD * canL, 1, P)), strong = true),
                VRow("Per 1,000 birds", "litres", listOf(cell(d.e.dieselCansUsed * canL * 1000 / d.liveSafe, 2, P), cell(d.dieselTD * canL * 1000 / d.liveSafe, 2, P)))
            )
        )
    }
}

/** One feed type: sacks in store (filled) and used (faded), with the numbers underneath. */
@Composable
private fun FeedSackRow(d: OutputData, code: String, unit: Double) {
    val ft = d.feedTypes.firstOrNull { it.code == code }
    val col = feedColor(code, d.feedTypes)
    val rec = d.recByCode[code] ?: 0.0
    val used = d.usedByCode[code] ?: 0.0
    val stock = d.stockBags(code)
    val kgBag = d.kgPerBag(code)
    val isPhase = code == d.phase
    Surface(color = col.copy(alpha = 0.10f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(col, RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("$code · ${ft?.name ?: "Feed"}" + if (isPhase) "  ◀ feeding now" else "",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = col)
                    Text("${Fmt.n(kgBag, 1)} kg per bag", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${Fmt.n(stock, 2)} bags", style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black),
                        color = if (stock < 0) StatusCrit else col)
                    Text("${Fmt.n(stock * kgBag, 1)} kg" + if (isPhase && d.giveKg > 0) " · ${Fmt.n(stock * kgBag / d.giveKg, 1)} days" else "",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SackStrip(max(0.0, stock) / unit, min(used, rec) / unit, col)
            Row(Modifier.fillMaxWidth()) {
                Text("Received ${Fmt.n(rec, 2)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                Text("Used ${Fmt.n(used, 2)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                if (stock < 0) Text("⚠ missing delivery", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = StatusCrit)
            }
        }
    }
}

/** Sacks drawn in rows of 10: [full] filled sacks (last one part-filled), then [used] faded ones. */
@Composable
private fun SackStrip(full: Double, used: Double, color: Color) {
    val total = ceil(full) + ceil(used)
    val rows = max(1, ceil(total / 10.0).toInt())
    val faded = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    Canvas(Modifier.fillMaxWidth().height((rows * 30).dp)) {
        val cw = size.width / 10f
        val sh = 30.dp.toPx()
        fun sack(i: Int, fill: Float, c: Color, outline: Boolean) {
            val x = (i % 10) * cw + cw * 0.12f
            val y = (i / 10) * sh + 4f
            val w = cw * 0.76f; val h = sh - 10f
            val body = Path().apply {
                moveTo(x + w * 0.2f, y + h * 0.18f)
                lineTo(x + w * 0.8f, y + h * 0.18f)
                quadraticBezierTo(x + w * 1.02f, y + h * 0.6f, x + w * 0.9f, y + h)
                lineTo(x + w * 0.1f, y + h)
                quadraticBezierTo(x - w * 0.02f, y + h * 0.6f, x + w * 0.2f, y + h * 0.18f)
                close()
            }
            if (outline) drawPath(body, c, style = Stroke(width = 2.5f))
            else {
                drawPath(body, c.copy(alpha = 0.18f))
                val fy = y + h - h * 0.82f * fill
                drawContext.canvas.save()
                drawContext.canvas.clipRect(x - 4f, fy, x + w + 4f, y + h + 2f)
                drawPath(body, c)
                drawContext.canvas.restore()
            }
            drawLine(if (outline) c else c.copy(alpha = 0.9f), Offset(x + w * 0.35f, y + h * 0.1f), Offset(x + w * 0.65f, y + h * 0.1f), strokeWidth = 4f, cap = StrokeCap.Round)
        }
        var i = 0
        val whole = floor(full).toInt()
        repeat(whole) { sack(i++, 1f, color, false) }
        val part = (full - whole).toFloat()
        if (part > 0.01f) sack(i++, part, color, false)
        repeat(ceil(used).toInt()) { if (i < rows * 10) sack(i++, 0f, faded, true) }
    }
}
