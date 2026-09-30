package com.example.flock.ui.screens

import android.graphics.Paint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.engine.IbController
import com.example.ui.theme.ValueIdeal
import com.example.ui.theme.ValuePredicted
import com.example.ui.theme.ValuePresent
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Pictures used by the Output topics: the house airflow animation and feed sacks.

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
    val houseC = Color.Black
    val offC = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val labelArgb = Color.White.copy(alpha = 0.6f).toArgb()
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
            drawRoundRect(Color.White.copy(alpha = 0.45f), Offset(left, top), Size(right - left, bottom - top), CornerRadius(14f, 14f), style = Stroke(1.5f))
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
            val lbl = Paint().apply { color = labelArgb; textSize = 10.sp.toPx(); isAntiAlias = true; textAlign = Paint.Align.CENTER }
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
            Text("${running}.0 / $n.0 fans", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = ValuePresent)
            if (level.isTimer) Text("timer", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = ValuePredicted)
            if (hasPads) Text(if (padsOn) "pads wet" else "pads off", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = if (padsOn) ValueIdeal else MaterialTheme.colorScheme.onSurfaceVariant)
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


/** Sacks drawn in rows of 10: [full] filled sacks (last one part-filled), then [used] faded ones. */
@Composable
fun SackStrip(full: Double, used: Double, color: Color) {
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

