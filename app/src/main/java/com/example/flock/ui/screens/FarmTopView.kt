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
import kotlin.math.roundToInt
import kotlin.math.abs
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
 * One stretch of a feeder line, two repeats of the pattern, drawn to scale. One open pan's floor is
 * highlighted with the birds on it (dots); its length along the line is marked on top, the line gap on
 * the right, the pan spacing under two pans, and the furthest walk to the pan against the 2 m ring.
 */
@Composable
fun PanCellView(d: OutputData, pattern: OutputData.PanPattern) {
    val period = pattern.on + pattern.off
    val shownPans = (period * 2).coerceIn(6, 12)
    val spacing = d.panSpacingFt
    val gap = d.feederGapFt
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wDp = maxWidth.value
        val padL = 6f; val padR = 74f; val padT = 30f; val padB = 34f
        val ftToDp = (wDp - padL - padR) / (shownPans * spacing).toFloat()
        val bandDp = (gap * ftToDp).toFloat().coerceIn(90f, 180f)
        val ftY = bandDp / gap.toFloat()
        Canvas(Modifier.fillMaxWidth().height((padT + bandDp + padB).dp).clipToBounds()) {
            val px = density
            fun X(ft: Double) = ((padL + ft * ftToDp) * px).toFloat()
            val midY = (padT + bandDp / 2) * px
            fun Y(ft: Double) = (midY + ft * ftY * px).toFloat()
            val paint = Paint().apply { isAntiAlias = true; textSize = 11.5f * px; typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER }
            fun label(t: String, x: Float, y: Float, c: Color, align: Paint.Align = Paint.Align.CENTER) {
                paint.textAlign = align; paint.color = c.toArgb()
                val w = paint.measureText(t); val h = paint.textSize
                val left = when (align) { Paint.Align.CENTER -> x - w / 2; Paint.Align.LEFT -> x; else -> x - w }
                drawRoundRect(Color.Black.copy(alpha = 0.85f), Offset(left - 3f * px, y - h * 0.85f), Size(w + 6f * px, h * 1.2f), androidx.compose.ui.geometry.CornerRadius(3f * px))
                drawContext.canvas.nativeCanvas.drawText(t, x, y, paint)
            }
            fun dimLine(a: Offset, b: Offset) {
                val c = Color.White.copy(alpha = 0.75f)
                drawLine(c, a, b, 1.2f * px)
                val horizontal = abs(a.y - b.y) < 1f
                val t = 4f * px
                for (p in listOf(a, b)) if (horizontal) drawLine(c, Offset(p.x, p.y - t), Offset(p.x, p.y + t), 1.2f * px) else drawLine(c, Offset(p.x - t, p.y), Offset(p.x + t, p.y), 1.2f * px)
            }
            val x0 = 0.5 * spacing
            fun panFt(i: Int) = x0 + i * spacing
            val opens = (-period until shownPans + period).filter { ((it % period) + period) % period < pattern.on }
            fun cellOf(k: Int): Pair<Double, Double> {
                val i = opens[k]
                val l = if (k > 0) (panFt(opens[k - 1]) + panFt(i)) / 2 else panFt(i) - spacing / 2
                val r = if (k < opens.size - 1) (panFt(i) + panFt(opens[k + 1])) / 2 else panFt(i) + spacing / 2
                return l to r
            }
            // the highlighted pan: the widest cell in the second repeat
            val hk = opens.indices.filter { opens[it] in period until 2 * period }.maxByOrNull { cellOf(it).let { (l, r) -> r - l } }
                ?: opens.indices.first { opens[it] >= 0 }
            val hi = opens[hk]; val (cl, cr) = cellOf(hk)
            val bandL = X(0.0); val bandR = X(shownPans * spacing)
            // the band between the two halfway lines (where the drinker lines run)
            drawRect(Color.White.copy(alpha = 0.03f), Offset(bandL, Y(-gap / 2)), Size(bandR - bandL, Y(gap / 2) - Y(-gap / 2)))
            opens.indices.forEach { k -> if (opens[k] in 0 until shownPans) { val r = cellOf(k).second; if (r < shownPans * spacing) drawLine(Color.White.copy(alpha = 0.16f), Offset(X(r), Y(-gap / 2)), Offset(X(r), Y(gap / 2)), 1f) } }
            val cell = androidx.compose.ui.geometry.Rect(X(cl), Y(-gap / 2), X(cr), Y(gap / 2))
            drawRect(ValuePredicted.copy(alpha = 0.14f), cell.topLeft, cell.size)
            drawRect(ValuePredicted.copy(alpha = 0.75f), cell.topLeft, cell.size, style = Stroke(1.5f * px))
            // birds on that floor, at today's density
            val n = pattern.cellBirds.roundToInt().coerceIn(0, 160)
            val rnd = java.util.Random(11)
            repeat(n) {
                var p: Offset
                var tries = 0
                do { p = Offset(cell.left + 3f * px + rnd.nextFloat() * (cell.width - 6f * px), cell.top + 3f * px + rnd.nextFloat() * (cell.height - 6f * px)); tries++ }
                while (tries < 6 && (p - Offset(X(panFt(hi)), Y(0.0))).getDistance() < 8f * px)
                drawCircle(Color.White.copy(alpha = 0.75f), 1.7f * px, p)
            }
            drawLine(ValueMin.copy(alpha = 0.7f), Offset(bandL, Y(-gap / 2)), Offset(bandR, Y(-gap / 2)), 1.2f * px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * px, 3f * px)))
            drawLine(ValueMin.copy(alpha = 0.7f), Offset(bandL, Y(gap / 2)), Offset(bandR, Y(gap / 2)), 1.2f * px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * px, 3f * px)))
            drawLine(Color.White.copy(alpha = 0.55f), Offset(bandL, Y(0.0)), Offset(bandR, Y(0.0)), 1.4f * px)
            // furthest walk: from the far top corner of the highlighted floor to its pan, and the 2 m ring
            val farX = if (cr - panFt(hi) >= panFt(hi) - cl) cr else cl
            val corner = Offset(X(farX), Y(-gap / 2)); val pan = Offset(X(panFt(hi)), Y(0.0))
            val ok = pattern.travelM <= ALLOWED_TRAVEL_M
            val rx = (ALLOWED_TRAVEL_M / 0.3048 * ftToDp * px).toFloat(); val ry = (ALLOWED_TRAVEL_M / 0.3048 * ftY * px).toFloat()
            drawOval(ValueIdeal.copy(alpha = 0.65f), Offset(pan.x - rx, pan.y - ry), Size(rx * 2, ry * 2), style = Stroke(1.2f * px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f * px, 5f * px))))
            drawLine(if (ok) ValuePresent else ValueMax, corner, pan, 2f * px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f * px, 3f * px)))
            // pans
            val r = (0.55 * ftToDp * px).toFloat().coerceIn(3.5f * px, 7f * px)
            for (i in 0 until shownPans) {
                val c = Offset(X(panFt(i)), Y(0.0))
                if (pattern.isOpen(i)) drawCircle(ValuePredicted, r, c) else { drawCircle(Color.Black, r, c); drawCircle(Color.White.copy(alpha = 0.6f), r, c, style = Stroke(1.3f * px)) }
            }
            label(Fmt.n(pattern.travelM, 2) + " m", (corner.x + pan.x) / 2, (corner.y + pan.y) / 2 - 4f * px, if (ok) ValuePresent else ValueMax)
            // dimensions: floor length along the line (top), line gap (right), pan spacing (under two pans)
            val yTop = (padT - 12f) * px
            dimLine(Offset(cell.left, yTop), Offset(cell.right, yTop))
            label(Fmt.n(cr - cl, 2) + " ft", cell.center.x, yTop - 6f * px, ValuePredicted)
            val xGap = bandR + 12f * px
            dimLine(Offset(xGap, Y(-gap / 2)), Offset(xGap, Y(gap / 2)))
            label(Fmt.n(gap, 2) + " ft", xGap + 6f * px, midY + 4f * px, Color.White, Paint.Align.LEFT)
            val sA = if (hi + 1 < shownPans) hi else hi - 1
            val ySp = Y(gap / 2) + 12f * px
            dimLine(Offset(X(panFt(sA)), ySp), Offset(X(panFt(sA + 1)), ySp))
            label(Fmt.n(spacing, 2) + " ft", (X(panFt(sA)) + X(panFt(sA + 1))) / 2, ySp + 17f * px, Color.White)
            // area of the highlighted floor
            label(Fmt.n(pattern.cellFt2, 1) + " ft²", cell.center.x, Y(gap / 2) - 8f * px, ValuePredicted)
        }
    }
}
