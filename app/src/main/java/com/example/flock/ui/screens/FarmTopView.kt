package com.example.flock.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.flock.ui.Fmt
import com.example.ui.theme.ValueIdeal
import com.example.ui.theme.ValuePresent
import com.example.ui.theme.ValueMax
import com.example.ui.theme.ValueMin
import com.example.ui.theme.ValuePredicted
import kotlin.math.max
import kotlin.math.min

/**
 * Top view of the house at its real aspect ratio: the front wall on the left, every feeder line
 * (hopper → pans → motor) and drinker line, the brooding barricade, and which pans are open today.
 * Long houses scroll sideways.
 */
@Composable
fun FarmTopView(d: OutputData, pattern: OutputData.PanPattern = d.feedPattern) {
    val f = d.farm
    val lenFt = max(10.0, f.usableLengthFt)
    val widFt = max(5.0, f.usableWidthFt)
    val heightDp = 190f
    val padDp = 26f
    val ftToDp = (heightDp - padDp * 2) / widFt.toFloat()
    val widthDp = (lenFt.toFloat() * ftToDp + padDp * 2).coerceAtLeast(320f)
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Canvas(Modifier.width(widthDp.dp).height(heightDp.dp)) {
            val px = density
            val s = ftToDp * px              // px per ft
            val ox = padDp * px; val oy = padDp * px
            fun X(ft: Double) = (ox + ft * s).toFloat()
            fun Y(ft: Double) = (oy + ft * s).toFloat()
            val line = Color.White.copy(alpha = 0.55f)
            val faint = Color.White.copy(alpha = 0.18f)
            val paint = Paint().apply { isAntiAlias = true; textSize = 10f * px; typeface = Typeface.MONOSPACE; color = Color.White.copy(alpha = 0.7f).toArgb() }
            // house
            drawRect(line, Offset(X(0.0), Y(0.0)), Size((lenFt * s).toFloat(), (widFt * s).toFloat()), style = Stroke(1.2f))
            // birds' area up to the barricade
            val bar = d.barricadeFtNow
            drawRect(ValueIdeal.copy(alpha = 0.07f), Offset(X(0.0), Y(0.0)), Size((bar * s).toFloat(), (widFt * s).toFloat()))
            if (bar < lenFt - 0.5) drawLine(ValuePredicted, Offset(X(bar), Y(0.0) - 6f), Offset(X(bar), Y(widFt) + 6f), 2.4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
            // lines, spread across the width
            val gap = d.lineGapFt
            var fIdx = 0
            d.lineOrder.forEachIndexed { i, kind ->
                val y = (i + 0.5) * gap
                if (kind == 'F') {
                    val start = d.lineStartFt
                    val end = start + d.lineLenFt
                    drawLine(line, Offset(X(start), Y(y)), Offset(X(end), Y(y)), 1.4f)
                    // hopper at the front end, motor at the far end
                    val hs = (1.6 * s).toFloat().coerceAtLeast(6f)
                    drawRect(ValuePredicted.copy(alpha = 0.85f), Offset(X(start) - hs, Y(y) - hs / 2), Size(hs, hs))
                    drawCircle(Color.White.copy(alpha = 0.8f), (0.9 * s).toFloat().coerceAtLeast(3f), Offset(X(end) + (0.9 * s).toFloat(), Y(y)))
                    val r = (0.55 * s).toFloat().coerceAtLeast(2.2f)
                    for (p in 0 until d.pansPerLine) {
                        val pxFt = start + (p + 0.5) * d.panSpacingFt
                        val c = Offset(X(pxFt), Y(y))
                        when {
                            p >= d.pansInArea -> drawCircle(faint, r, c, style = Stroke(1f))
                            pattern.isOpen(p) -> drawCircle(ValuePredicted, r, c)
                            else -> drawCircle(Color.White.copy(alpha = 0.45f), r, c, style = Stroke(1f))
                        }
                    }
                    // sensor (control) pans at the far end: always open, not counted
                    for (q in 0 until d.sensorPans) {
                        val c = Offset(X(start + (d.pansPerLine + q + 0.5) * d.panSpacingFt), Y(y))
                        drawRect(Color.White, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(1.2f))
                    }
                    fIdx++
                } else {
                    drawLine(ValueMin.copy(alpha = 0.75f), Offset(X(d.lineStartFt), Y(y)), Offset(X(d.lineStartFt + d.drinkerLenFt), Y(y)), 1.2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 4f)))
                }
            }
            // 2 m reach around the last open pan of the middle feeder line
            val midF = d.lineOrder.withIndex().filter { it.value == 'F' }.let { it.getOrNull(it.size / 2)?.index }
            if (midF != null && pattern.lastIdx >= 0) {
                val c = Offset(X(d.lineStartFt + (pattern.lastIdx + 0.5) * d.panSpacingFt), Y((midF + 0.5) * gap))
                drawCircle(ValueIdeal.copy(alpha = 0.7f), (ALLOWED_TRAVEL_M / 0.3048 * s).toFloat(), c, style = Stroke(1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
            }
            // labels
            drawContext.canvas.nativeCanvas.drawText("front", X(0.0), Y(0.0) - 8f, paint)
            paint.textAlign = Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText("${Fmt.n(lenFt, 1)} × ${Fmt.n(widFt, 1)} ft", X(lenFt), Y(0.0) - 8f, paint)
            paint.textAlign = Paint.Align.LEFT
            if (bar < lenFt - 0.5) { paint.color = ValuePredicted.toArgb(); drawContext.canvas.nativeCanvas.drawText("barricade ${Fmt.n(bar, 1)} ft", X(bar) + 6f, Y(widFt) + 18f, paint) }
            paint.color = Color.White.copy(alpha = 0.7f).toArgb()
            drawContext.canvas.nativeCanvas.drawText("lines ${Fmt.n(d.lineLenFt, 1)} ft · pans every ${Fmt.n(d.panSpacingFt, 1)} ft", X(0.0) + 60f * px, Y(0.0) - 8f, paint)
        }
    }
}

/**
 * One stretch of a feeder line at true scale: the on/off series, the floor each open pan serves (to
 * halfway to the next line across, halfway to the next open pan along) with its ft² and birds, the pan
 * spacing, the line gap, and the furthest walk to an open pan against the 2 m limit.
 */
@Composable
fun PanCellView(d: OutputData, pattern: OutputData.PanPattern) {
    val period = pattern.on + pattern.off
    val shownPans = max(period * 2, 8).coerceAtMost(16)
    val spacing = d.panSpacingFt
    val gap = d.feederGapFt
    val birdsPerFt2 = d.live.toDouble() / d.areaInUseFt2      // birds per ft² today (same as the cell numbers)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wDp = maxWidth.value
        val padL = 8f; val padR = 8f
        val ftToDp = (wDp - padL - padR) / (shownPans * spacing).toFloat()
        val bandDp = (gap * ftToDp).toFloat().coerceIn(70f, 190f)
        val ftY = bandDp / gap.toFloat()                        // vertical scale (kept close to true)
        val hDp = bandDp + 34f
        Canvas(Modifier.fillMaxWidth().height(hDp.dp).clipToBounds()) {
            val px = density
            fun X(ft: Double) = ((padL + ft * ftToDp) * px).toFloat()
            val top = 4f * px
            val midY = top + bandDp / 2 * px
            fun Y(ftFromLine: Double) = (midY + ftFromLine * ftY * px).toFloat()
            val paint = Paint().apply { isAntiAlias = true; textSize = 10f * px; typeface = Typeface.MONOSPACE }
            fun text(t: String, x: Float, y: Float, c: Color, align: Paint.Align = Paint.Align.CENTER) { paint.color = c.toArgb(); paint.textAlign = align; drawContext.canvas.nativeCanvas.drawText(t, x, y, paint) }
            // open pans (a little beyond both ends so every shown cell has its neighbours)
            val opens = (-period until shownPans + period).filter { ((it % period) + period) % period < pattern.on }
            val x0 = 0.5 * spacing
            fun panFt(i: Int) = x0 + i * spacing
            // cells: halfway to the neighbouring open pans, halfway to the next line on each side
            val cellCols = listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.11f))
            var worst: Triple<Double, Double, Double>? = null   // (x of the far corner, x of its pan, walk m)
            opens.forEachIndexed { k, i ->
                if (i < 0 || i >= shownPans) return@forEachIndexed
                val left = if (k > 0) (panFt(opens[k - 1]) + panFt(i)) / 2 else panFt(i) - spacing / 2
                val right = if (k < opens.size - 1) (panFt(i) + panFt(opens[k + 1])) / 2 else panFt(i) + spacing / 2
                val l = max(0.0, left); val r = min(shownPans * spacing, right)
                drawRect(cellCols[k % 2], Offset(X(l), Y(-gap / 2)), Size(X(r) - X(l), Y(gap / 2) - Y(-gap / 2)))
                drawLine(Color.White.copy(alpha = 0.25f), Offset(X(r), Y(-gap / 2)), Offset(X(r), Y(gap / 2)), 1f)
                val cellFt2 = (right - left) * gap
                // ft² and birds on the first repeat, where the cell is wide enough for the numbers
                val t1 = Fmt.n(cellFt2, 1); val t2 = Fmt.n(cellFt2 * birdsPerFt2, 1)
                if (i < period && X(r) - X(l) > max(paint.measureText(t1), paint.measureText(t2)) + 4f * px) {
                    text(t1, X((l + r) / 2), Y(-gap / 2) + 13f * px, ValuePredicted)
                    text(t2, X((l + r) / 2), Y(-gap / 2) + 25f * px, ValuePresent)
                }
                // furthest walk, checked on the second repeat so the 2 m ring sits inside the picture
                val farX = if (right - panFt(i) >= panFt(i) - left) right else left
                val walk = Math.hypot(farX - panFt(i), gap / 2) * 0.3048
                if (i >= period && i < 2 * period && (worst == null || walk > worst!!.third + 1e-9)) worst = Triple(farX, panFt(i), walk)
            }
            // halfway lines (where the drinker lines run) and the feeder line
            drawLine(ValueMin.copy(alpha = 0.7f), Offset(X(0.0), Y(-gap / 2)), Offset(X(shownPans * spacing), Y(-gap / 2)), 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 4f)))
            drawLine(ValueMin.copy(alpha = 0.7f), Offset(X(0.0), Y(gap / 2)), Offset(X(shownPans * spacing), Y(gap / 2)), 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 4f)))
            drawLine(Color.White.copy(alpha = 0.55f), Offset(X(0.0), Y(0.0)), Offset(X(shownPans * spacing), Y(0.0)), 1.4f)
            // furthest walk and the 2 m limit around that pan
            worst?.let { (fx, panX, walk) ->
                val corner = Offset(X(fx), Y(gap / 2)); val pan = Offset(X(panX), Y(0.0))
                drawLine(if (walk <= ALLOWED_TRAVEL_M) ValuePresent else ValueMax, corner, pan, 1.6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
                val rx = (ALLOWED_TRAVEL_M / 0.3048 * ftToDp * px).toFloat(); val ry = (ALLOWED_TRAVEL_M / 0.3048 * ftY * px).toFloat()
                drawOval(ValueIdeal.copy(alpha = 0.6f), Offset(pan.x - rx, pan.y - ry), Size(rx * 2, ry * 2), style = Stroke(1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
                text(Fmt.n(walk, 2) + " m", (corner.x + pan.x) / 2 + 4f * px, (corner.y + pan.y) / 2 + 4f * px, if (walk <= ALLOWED_TRAVEL_M) ValuePresent else ValueMax, Paint.Align.LEFT)
            }
            // pans
            val r = (0.6 * ftToDp * px).toFloat().coerceIn(3f * px, 7f * px)
            for (i in 0 until shownPans) {
                val c = Offset(X(panFt(i)), Y(0.0))
                if (pattern.isOpen(i)) drawCircle(ValuePredicted, r, c) else { drawCircle(Color.Black, r, c); drawCircle(Color.White.copy(alpha = 0.55f), r, c, style = Stroke(1.2f)) }
            }
            // pan spacing and line gap
            val yb = Y(gap / 2) + 14f * px
            drawLine(Color.White.copy(alpha = 0.6f), Offset(X(panFt(0)), yb - 4f * px), Offset(X(panFt(1)), yb - 4f * px), 1f)
            text(Fmt.n(spacing, 1) + " ft", X(panFt(1)) + 4f * px, yb, Color.White.copy(alpha = 0.7f), Paint.Align.LEFT)
            text("gap " + Fmt.n(gap, 1) + " ft", X(shownPans * spacing), yb, Color.White.copy(alpha = 0.7f), Paint.Align.RIGHT)
        }
    }
}
