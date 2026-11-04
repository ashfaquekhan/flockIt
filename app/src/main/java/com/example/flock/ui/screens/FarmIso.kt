package com.example.flock.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.flock.ui.Fmt
import com.example.ui.theme.ValueMin
import com.example.ui.theme.ValuePredicted
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** What a farm view shows: the pans and barricades (feed), the air, the fans, the pads, the litter, the water lines. */
enum class IsoView { FEED, AIR, FANS, PADS, LITTER, WATER }

/**
 * The whole house for the farm views, in feet: x along the house from the front wall (the hoppers' end, where the pads
 * are), z across it from the near side wall; the fans at the back wall.
 */
data class IsoFarm(
    val lenFt: Double, val widFt: Double, val wallFt: Double,
    val lineOrder: List<Char>, val lineGapFt: Double, val lineStartFt: Double, val panSpacingFt: Double,
    val pansPerLine: Int, val sensorPans: Int, val drinkerLenFt: Double, val nippleSpacingFt: Double,
    /** the pan series on every feeder line: [on] open, [off] shut, from the floor's first pan [firstPan], [pansInFloor] of them */
    val on: Int, val off: Int, val firstPan: Int, val pansInFloor: Int,
    /** the birds' floor, ft from the front wall, and whether these barricades are the plan's (not yet in the house) */
    val floorFromFt: Double, val floorToFt: Double, val planned: Boolean,
    /** fans on the back wall, how many run on average, their timer (0 / 0 = running on) and the order they start in */
    val fans: Int, val fansRunning: Double, val fanOnS: Int, val fanOffS: Int, val fanOrder: List<Int>,
    /** cooling pads: "side" (both side walls at the front end) or "c" (the front wall too), how long along a side, wet 0–1 */
    val padLayout: String, val padLenFt: Double, val padWet: Double,
    /** the air runs end to end (tunnel) rather than in from the sides (minimum ventilation) */
    val tunnel: Boolean,
    val map: com.example.flock.models.HouseMap.Field? = null
) {
    fun panOpen(i: Int) = i >= firstPan && i - firstPan < pansInFloor && ((i - firstPan) % max(1, on + off)) < on
    fun lineZ(i: Int) = (i + 0.5) * lineGapFt
}

/** The house as the day shows it, for the farm views: the pans of [pattern], the floor [fromFt]–[toFt], the air at [hour]. */
fun isoFarmOf(d: OutputData, pattern: OutputData.PanPattern, fromFt: Double, toFt: Double, planned: Boolean, hour: Int,
              map: com.example.flock.models.HouseMap.Field? = null): IsoFarm {
    val f = d.farm
    val h = d.envAt(hour)
    val running = h?.fans ?: d.minLevel.avgFans
    val padH = 5.0
    val padArea = if (f.padAreaFt2 > 0) f.padAreaFt2 else 0.0
    val padLen = when {
        padArea <= 0 -> 0.0
        f.padLayout == "c" -> ((padArea / padH - f.usableWidthFt) / 2).coerceAtLeast(0.0)
        else -> padArea / padH / 2
    }.coerceAtMost(f.usableLengthFt * 0.5)
    val out = d.outsideHours.getOrNull(hour.coerceIn(0, 23))
    val padWet = out?.let { d.sim(it.second, it.third, hour).padEff } ?: 0.0
    return IsoFarm(f.usableLengthFt.coerceAtLeast(20.0), f.usableWidthFt.coerceAtLeast(10.0), f.heightFt.takeIf { it > 0 } ?: 9.0,
        d.lineOrder, d.lineGapFt, d.lineStartFt, d.panSpacingFt, d.pansPerLine, d.sensorPans, d.drinkerLenFt, NIPPLE_SPACING_FT,
        pattern.on, pattern.off, pattern.first, pattern.count.coerceAtMost(d.pansPerLine), fromFt, toFt, planned,
        f.fanCount.coerceIn(0, 24), running, if (d.minLevel.isTimer) d.minLevel.on else 0, if (d.minLevel.isTimer) d.minLevel.off else 0,
        com.example.flock.engine.PhysiologicalEngine.fanSequence(f.fanCount.coerceIn(1, 24)),
        f.padLayout, padLen, padWet, running > d.minLevel.avgFans * 1.5 + 0.5, map)
}

private const val YAW = 0.21            // 12°: the house runs left to right, rising a little
private const val ELEV = 0.96           // 55°: seen from above and the side
private const val VZ = 1.5              // walls drawn a little taller than they are, to read at this size

/**
 * The whole house from above and the side at a fixed angle (no turning): every pan of every line, the barricades, the
 * fans and pads, the air or the litter. It fits the width; [zoomable] views pinch to zoom and slide sideways (one finger
 * left or right; up and down still scrolls the page); a double tap fits it again.
 */
@Composable
fun FarmIso(f: IsoFarm, view: IsoView, modifier: Modifier = Modifier, zoomable: Boolean = view == IsoView.FEED || view == IsoView.WATER,
            layer: com.example.flock.models.HouseMap.Layer? = null) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    // the fans turn and the air moves in the air views; the feed and water views stand still
    val moving = view == IsoView.AIR || view == IsoView.FANS || view == IsoView.PADS
    var t by remember { mutableFloatStateOf(0f) }
    if (moving) LaunchedEffect(Unit) { while (true) withFrameNanos { t = (it / 1_000_000L % 3_600_000L) / 1000f } }
    val grid = remember(f.map, layer) { if (f.map != null && layer != null) f.map.grid(layer) else null }
    val lim = remember(f.map, layer) { if (f.map != null && layer != null) f.map.limits(layer) to f.map.range(layer).first else null }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wPx = constraints.maxWidth.toFloat()
        val dens = androidx.compose.ui.platform.LocalDensity.current.density
        val geo = remember(f, wPx, dens) { IsoGeo(f, wPx, view == IsoView.WATER, dens) }
        val hDp = with(androidx.compose.ui.platform.LocalDensity.current) { geo.hPx.toDp() }
        fun clamp() {
            val maxX = max(0f, (geo.spanPx * zoom - wPx) / 2 + 12f)
            val maxY = max(0f, (geo.hPx * zoom - geo.hPx) / 2 + 12f)
            panX = panX.coerceIn(-maxX, maxX); panY = panY.coerceIn(-maxY, maxY)
        }
        var gesture = Modifier.fillMaxWidth().height(hDp).clipToBounds().testTag("farm_iso_${view.name.lowercase()}")
        if (zoomable) gesture = gesture
            // two fingers: zoom about the fingers and move the view
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val ev = awaitPointerEvent()
                        if (ev.changes.count { it.pressed } >= 2) {
                            val z0 = zoom
                            val z1 = (zoom * ev.calculateZoom()).coerceIn(1f, 10f)
                            val c = ev.calculateCentroid()
                            val cx = size.width / 2f; val cy = size.height / 2f
                            panX = c.x - cx - (c.x - cx - panX) * z1 / z0 + ev.calculatePan().x
                            panY = c.y - cy - (c.y - cy - panY) * z1 / z0 + ev.calculatePan().y
                            zoom = z1; clamp()
                            ev.changes.forEach { it.consume() }
                        }
                    } while (ev.changes.any { it.pressed })
                }
            }
            // one finger sideways slides along the house once zoomed in
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dx -> if (zoom > 1.01f) { change.consume(); panX += dx; clamp() } }
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { zoom = 1f; panX = 0f; panY = 0f }) }
        Canvas(gesture) {
            @Suppress("UNUSED_VARIABLE") val frame = t
            drawIso(f, view, geo, zoom, panX, panY, t, grid, lim)
        }
        if (zoomable && zoom > 1.05f) Text("× " + Fmt.n(zoom.toDouble(), 1), style = MaterialTheme.typography.labelMedium.copy(fontFamily = com.example.ui.theme.NumberFont),
            color = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp)).border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable { zoom = 1f; panX = 0f; panY = 0f }.padding(horizontal = 8.dp, vertical = 3.dp).testTag("iso_zoom_reset"))
    }
}

/** The view's fixed projection fitted to the width: px per ft and where the house sits. */
private class IsoGeo(val f: IsoFarm, w: Float, tank: Boolean, dens: Float) {
    val cs = cos(YAW); val sn = sin(YAW); val se = sin(ELEV); val ce = cos(ELEV)
    private fun u(x: Double, z: Double) = x * cs - z * sn
    private fun v(x: Double, y: Double, z: Double) = (x * sn + z * cs) * se + y * VZ * ce
    val pad = 10f * dens
    /** room under the house for the ruler and the barricades' places */
    private val bottom = 30f * dens
    val minU: Double; val maxU: Double; val minV: Double; val maxV: Double
    init {
        val xs = listOf(if (tank) -12.0 else 0.0, f.lenFt); val zs = listOf(0.0, f.widFt); val ys = listOf(0.0, f.wallFt)
        val us = xs.flatMap { x -> zs.map { z -> u(x, z) } }; val vs = xs.flatMap { x -> zs.flatMap { z -> ys.map { y -> v(x, y, z) } } }
        minU = us.min(); maxU = us.max(); minV = vs.min(); maxV = vs.max()
    }
    /** px per ft at zoom 1 */
    val S: Float = ((w - 2 * pad) / (maxU - minU)).toFloat()
    val spanPx = ((maxU - minU) * S).toFloat() + 2 * pad
    val hPx = ((maxV - minV) * S).toFloat() + 2 * pad + bottom
    /** world (ft) to screen at zoom 1 */
    fun base(x: Double, y: Double, z: Double) = Offset(pad + ((u(x, z) - minU) * S).toFloat(), hPx - pad - bottom - ((v(x, y, z) - minV) * S).toFloat())
}

private fun DrawScope.drawIso(f: IsoFarm, view: IsoView, g: IsoGeo, zoom: Float, panX: Float, panY: Float, t: Float,
                              grid: Array<DoubleArray>?, lim: Pair<com.example.flock.models.HouseMap.Limits, Double>?) {
    val cx = size.width / 2f; val cy = size.height / 2f
    fun P(x: Double, y: Double, z: Double): Offset { val b = g.base(x, y, z); return Offset(cx + (b.x - cx) * zoom + panX, cy + (b.y - cy) * zoom + panY) }
    val S = g.S * zoom          // px per ft now
    val L = f.lenFt; val W = f.widFt; val H = f.wallFt
    fun quad(a: Offset, b: Offset, c: Offset, d: Offset) = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close() }
    fun floorQuad(x1: Double, z1: Double, x2: Double, z2: Double) = quad(P(x1, 0.0, z1), P(x2, 0.0, z1), P(x2, 0.0, z2), P(x1, 0.0, z2))
    val line = Color.White.copy(alpha = 0.45f)
    val feedView = view == IsoView.FEED
    val waterView = view == IsoView.WATER
    val airish = view == IsoView.AIR || view == IsoView.FANS || view == IsoView.PADS

    // ---- the floor: litter; the birds' floor brighter, the rest dark ----
    drawPath(floorQuad(0.0, 0.0, L, W), Color(0xFF2A2218).copy(alpha = 0.55f))
    val from = f.floorFromFt.coerceIn(0.0, L); val to = f.floorToFt.coerceIn(from, L)
    drawPath(floorQuad(from, 0.0, to, W), Color(0xFF4A3A26).copy(alpha = 0.55f))
    // the house map's layer, cell by cell
    if (grid != null && lim != null) {
        val nx = grid.size; val ny = grid[0].size
        for (a in 0 until nx) for (b in 0 until ny) {
            val x1 = L * a / nx; val x2 = L * (a + 1) / nx; val z1 = W * b / ny; val z2 = W * (b + 1) / ny
            drawPath(floorQuad(x1, z1, x2 + 0.4, z2 + 0.4), heatColor(grid[a][b], lim.first, lim.second).copy(alpha = 0.6f))
        }
    }
    if (from > 0.5) drawPath(floorQuad(0.0, 0.0, from, W), Color.Black.copy(alpha = 0.5f))
    if (to < L - 0.5) drawPath(floorQuad(to, 0.0, L, W), Color.Black.copy(alpha = 0.5f))

    // ---- the far walls (their inside faces): back wall with the fans, far side wall ----
    val wallFill = Color.White.copy(alpha = 0.05f)
    val back = quad(P(L, 0.0, 0.0), P(L, 0.0, W), P(L, H, W), P(L, H, 0.0))
    val far = quad(P(0.0, 0.0, W), P(L, 0.0, W), P(L, H, W), P(0.0, H, W))
    drawPath(far, wallFill); drawPath(far, line, style = Stroke(1f))
    drawPath(back, wallFill.copy(alpha = 0.08f)); drawPath(back, line, style = Stroke(1f))

    // ---- fans on the back wall: in rows, those running lit (in the order the controller starts them) ----
    if (f.fans > 0) {
        val perRow = max(1, min(f.fans, (W / 5.0).toInt()))
        val rows = ceil(f.fans / perRow.toDouble()).toInt().coerceAtMost(3)
        val r = min(W / perRow * 0.4, H / rows * 0.4)
        val lit = f.fanOrder.take(f.fansRunning.roundToInt().coerceIn(if (f.fansRunning > 0.05) 1 else 0, f.fans)).toSet()
        // on a timer the running fan is on for its share of the cycle
        val cycle = (f.fanOnS + f.fanOffS).takeIf { it > 0 }
        val onNow = cycle == null || ((t * 20f) % cycle) < f.fanOnS
        for (k in 0 until f.fans) {
            val row = k / perRow; val col = k % perRow
            val zc = W * (col + 0.5) / perRow; val yc = H * (row + 0.5) / rows
            val running = (k + 1) in lit && onNow
            val pts = (0..16).map { i -> val a = i / 16.0 * 2 * PI; P(L, yc + r * sin(a), zc + r * cos(a)) }
            val ring = Path().apply { pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }; close() }
            drawPath(ring, if (running) Color.White.copy(alpha = if (airish) 0.32f else 0.14f) else Color.Black.copy(alpha = 0.6f))
            drawPath(ring, if (running) Color.White else Color.White.copy(alpha = 0.35f), style = Stroke(if (running) 1.4f else 1f))
            if (airish && S > 1.2f) {
                val spin = if (running) t * 9f else 0f
                for (b in 0 until 3) { val a = spin + b * 2 * PI / 3; drawLine(if (running) Color.White else Color.White.copy(alpha = 0.3f), P(L, yc, zc), P(L, yc + r * 0.85 * sin(a), zc + r * 0.85 * cos(a)), 1.4f) }
            }
        }
    }

    // ---- cooling pads at the front end: both side walls, and the front wall too in a C ----
    if (f.padLenFt > 0.5 && (airish || view == IsoView.LITTER)) {
        val wet = f.padWet > 0.05
        val col = (if (wet) Color(0xFF5DB8EC) else Color(0xFFD2B07A)).copy(alpha = if (view == IsoView.PADS) 0.75f else 0.45f)
        val y1 = 0.6; val y2 = min(H * 0.8, 6.0)
        fun padPanel(a: Offset, b: Offset, c: Offset, d: Offset) {
            drawPath(quad(a, b, c, d), col); drawPath(quad(a, b, c, d), Color.White.copy(alpha = 0.6f), style = Stroke(1f))
            // the honeycomb's slant
            for (k in 1 until 6) { val s = k / 6f; drawLine(Color.Black.copy(alpha = 0.25f), lerpO(a, b, s), lerpO(d, c, s), 1f) }
        }
        padPanel(P(0.0, y1, W), P(f.padLenFt, y1, W), P(f.padLenFt, y2, W), P(0.0, y2, W))
        if (f.padLayout == "c") padPanel(P(0.0, y1, 0.0), P(0.0, y1, W), P(0.0, y2, W), P(0.0, y2, 0.0))
        padPanel(P(0.0, y1, 0.0), P(f.padLenFt, y1, 0.0), P(f.padLenFt, y2, 0.0), P(0.0, y2, 0.0))
    }

    // ---- the lines: drinkers, then feeders with every pan ----
    val panR = 0.75
    val panDetail = S > 3.2f
    f.lineOrder.forEachIndexed { i, kind ->
        val z = f.lineZ(i)
        if (kind == 'D') {
            val c = ValueMin.copy(alpha = if (waterView) 1f else 0.35f)
            drawLine(c, P(f.lineStartFt, 1.1, z), P(f.lineStartFt + f.drinkerLenFt, 1.1, z), if (waterView) 1.8f else 1f)
            if (waterView && S > 2.2f) {
                var x = f.lineStartFt + f.nippleSpacingFt / 2
                while (x < f.lineStartFt + f.drinkerLenFt) { drawCircle(ValueMin, max(1f, 0.18f * S), P(x, 0.9, z)); x += f.nippleSpacingFt }
            }
        }
    }
    f.lineOrder.forEachIndexed { i, kind ->
        if (kind != 'F') return@forEachIndexed
        val z = f.lineZ(i)
        val total = f.pansPerLine + f.sensorPans
        val endX = f.lineStartFt + total * f.panSpacingFt
        drawLine(Color.White.copy(alpha = if (feedView) 0.7f else 0.25f), P(f.lineStartFt, 1.7, z), P(endX, 1.7, z), if (feedView) 1.4f else 1f)
        // the hopper at the front end
        val hs = 1.4
        val top = quad(P(f.lineStartFt - hs * 2, 3.2, z - hs), P(f.lineStartFt, 3.2, z - hs), P(f.lineStartFt, 3.2, z + hs), P(f.lineStartFt - hs * 2, 3.2, z + hs))
        drawPath(quad(P(f.lineStartFt - hs * 2, 0.8, z - hs), P(f.lineStartFt, 0.8, z - hs), P(f.lineStartFt, 3.2, z - hs), P(f.lineStartFt - hs * 2, 3.2, z - hs)),
            Color.White.copy(alpha = if (feedView) 0.55f else 0.2f))
        drawPath(top, Color.White.copy(alpha = if (feedView) 0.9f else 0.3f))
        for (p in 0 until total) {
            val x = f.lineStartFt + (p + 0.5) * f.panSpacingFt
            val c = P(x, 0.15, z)
            val rx = max(0.9f, (panR * S).toFloat()); val ry = rx * g.se.toFloat()
            val sensor = p >= f.pansPerLine
            val inFloor = x >= from && x <= to
            when {
                !feedView -> drawCircle(Color.White.copy(alpha = 0.22f), max(0.7f, rx * 0.6f), c)
                sensor -> drawRect(Color.White, Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2), style = Stroke(1f))
                !inFloor -> {
                    drawOval(Color.White.copy(alpha = 0.18f), Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2), style = Stroke(1f))
                    if (panDetail) { drawLine(Color.White.copy(alpha = 0.4f), Offset(c.x - rx * 0.7f, c.y - ry * 0.7f), Offset(c.x + rx * 0.7f, c.y + ry * 0.7f), 1f)
                        drawLine(Color.White.copy(alpha = 0.4f), Offset(c.x - rx * 0.7f, c.y + ry * 0.7f), Offset(c.x + rx * 0.7f, c.y - ry * 0.7f), 1f) }
                }
                f.panOpen(p) -> {
                    if (panDetail) drawLine(Color.White.copy(alpha = 0.5f), P(x, 0.3, z), P(x, 1.7, z), 1f)
                    drawOval(ValuePredicted, Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2))
                    if (panDetail) drawOval(Color(0xFFE5534B), Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2), style = Stroke(1.2f))
                }
                else -> {
                    if (panDetail) drawLine(Color.White.copy(alpha = 0.3f), P(x, 0.3, z), P(x, 1.7, z), 1f)
                    drawOval(Color.Black, Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2))
                    drawOval(Color.White.copy(alpha = 0.55f), Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2), style = Stroke(1f))
                }
            }
        }
    }

    // ---- the barricades: white fences across the house (dashed when they are the plan's, not yet put up) ----
    fun fence(x: Double) {
        val bh = 2.4
        val eff = if (f.planned) PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null
        drawPath(quad(P(x, 0.0, 0.0), P(x, 0.0, W), P(x, bh, W), P(x, bh, 0.0)), Color.White.copy(alpha = 0.16f))
        drawLine(Color.White, P(x, bh, 0.0), P(x, bh, W), 2.2f, pathEffect = eff)
        drawLine(Color.White.copy(alpha = 0.8f), P(x, bh * 0.45, 0.0), P(x, bh * 0.45, W), 1.2f, pathEffect = eff)
        var z = 0.0
        while (z <= W + 1e-6) { drawLine(Color.White.copy(alpha = 0.85f), P(x, 0.0, z), P(x, bh, z), 1.3f); z += 6.5 }
    }
    if (from > 0.5) fence(from)
    if (to < L - 0.5) fence(to)

    // ---- the air: in from the pads, out through the fans (tunnel), or a gentle draw (minimum ventilation) ----
    if (view == IsoView.AIR || view == IsoView.PADS || view == IsoView.FANS) {
        val speed = if (f.tunnel) 18f else 6f
        val n = if (f.tunnel) 9 else 5
        listOf(0.25, 0.5, 0.75).forEach { zf ->
            for (k in 0 until n) {
                val x = ((k.toDouble() / n + (t * speed / L.toFloat()) % 1.0) % 1.0) * L * 0.92 + L * 0.04
                val a = P(x, 4.0, W * zf); val b = P(x + 6.0, 4.0, W * zf)
                val c = Color.White.copy(alpha = if (f.tunnel) 0.7f else 0.4f)
                drawLine(c, a, b, 1.4f)
                val dir = Offset(b.x - a.x, b.y - a.y); val len = max(1f, kotlin.math.hypot(dir.x, dir.y)); val ux = dir.x / len; val uy = dir.y / len
                drawLine(c, b, Offset(b.x - ux * 5f - uy * 3f, b.y - uy * 5f + ux * 3f), 1.4f)
                drawLine(c, b, Offset(b.x - ux * 5f + uy * 3f, b.y - uy * 5f - ux * 3f), 1.4f)
            }
        }
    }

    // ---- where sensors would stand (none yet): dim posts with the sensor sign ----
    if (view == IsoView.AIR || view == IsoView.LITTER) listOf(0.2, 0.5, 0.8).forEach { xf ->
        val p = P(L * xf, 3.5, W * 0.5)
        drawLine(Color.White.copy(alpha = 0.3f), P(L * xf, 0.0, W * 0.5), p, 1f)
        drawGameIcon(GIcon.SENSOR, Offset(p.x - 8.dp.toPx(), p.y - 16.dp.toPx()), 16.dp.toPx(), 0.45f)
    }

    // ---- the water tank outside the front wall ----
    if (waterView) {
        val p = P(-7.0, 0.0, W * 0.5)
        drawLine(ValueMin, P(-4.0, 1.1, W * 0.5), P(f.lineStartFt, 1.1, W * 0.5), 1.6f)
        drawGameIcon(GIcon.TANK, Offset(p.x - 13.dp.toPx(), p.y - 24.dp.toPx()), 26.dp.toPx())
    }

    // ---- the near walls last, as outlines only (so the floor shows through) ----
    drawPath(quad(P(0.0, 0.0, 0.0), P(L, 0.0, 0.0), P(L, H, 0.0), P(0.0, H, 0.0)), line, style = Stroke(1f))
    drawPath(quad(P(0.0, 0.0, 0.0), P(0.0, 0.0, W), P(0.0, H, W), P(0.0, H, 0.0)), line, style = Stroke(1f))
    drawPath(floorQuad(0.0, 0.0, L, W), Color.White.copy(alpha = 0.7f), style = Stroke(1.2f))

    // ---- a ruler along the near wall: every 50 ft ----
    val paint = Paint().apply { isAntiAlias = true; textSize = 9.5f * density; typeface = com.example.ui.theme.AppFonts.mono; color = Color.White.copy(alpha = 0.6f).toArgb(); textAlign = Paint.Align.CENTER }
    val step = if (S > 6f) 10.0 else if (S > 2.5f) 25.0 else 50.0
    var x = 0.0
    while (x <= L + 1e-6) {
        val a = P(x, 0.0, 0.0)
        drawLine(Color.White.copy(alpha = 0.5f), a, Offset(a.x, a.y + 4f), 1f)
        drawContext.canvas.nativeCanvas.drawText(x.roundToInt().toString(), a.x, a.y + 14f * density, paint)
        x += step
    }
    // the barricades' places in ft, on the ruler
    paint.color = Color.White.toArgb()
    if (from > 0.5) { val a = P(from, 0.0, 0.0); drawContext.canvas.nativeCanvas.drawText("▲" + from.roundToInt(), a.x, a.y + 26f * density, paint) }
    if (to < L - 0.5) { val a = P(to, 0.0, 0.0); drawContext.canvas.nativeCanvas.drawText("▲" + to.roundToInt(), a.x, a.y + 26f * density, paint) }
}

private fun lerpO(a: Offset, b: Offset, s: Float) = Offset(a.x + (b.x - a.x) * s, a.y + (b.y - a.y) * s)
