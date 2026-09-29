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
import com.example.ui.theme.ValueMin
import com.example.ui.theme.ValuePredicted
import kotlin.math.max

/**
 * Top view of the house at its real aspect ratio: the front wall on the left, every feeder line
 * (hopper → pans → motor) and drinker line, the brooding barricade, and which pans are open today.
 * Long houses scroll sideways.
 */
@Composable
fun FarmTopView(d: OutputData) {
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
                            d.feedPattern.isOpen(p) -> drawCircle(ValuePredicted, r, c)
                            else -> drawCircle(Color.White.copy(alpha = 0.45f), r, c, style = Stroke(1f))
                        }
                    }
                    fIdx++
                } else {
                    drawLine(ValueMin.copy(alpha = 0.75f), Offset(X(d.lineStartFt), Y(y)), Offset(X(d.lineStartFt + d.drinkerLenFt), Y(y)), 1.2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 4f)))
                }
            }
            // 2 m reach around the last open pan of the middle feeder line
            val midF = d.lineOrder.withIndex().filter { it.value == 'F' }.let { it.getOrNull(it.size / 2)?.index }
            if (midF != null && d.feedPattern.lastIdx >= 0) {
                val c = Offset(X(d.lineStartFt + (d.feedPattern.lastIdx + 0.5) * d.panSpacingFt), Y((midF + 0.5) * gap))
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
