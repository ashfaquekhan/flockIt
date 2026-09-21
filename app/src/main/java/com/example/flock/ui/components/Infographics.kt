package com.example.flock.ui.components

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FeedStockSummary
import com.example.flock.data.FlockEntity
import com.example.ui.theme.ValueIdeal
import com.example.ui.theme.ValuePredicted
import com.example.ui.theme.ValuePresent
import kotlin.math.roundToInt
import kotlin.math.sin

private val ChickYellow = Color(0xFFF2C14E)
private val BroilerCream = Color(0xFFEBE3D2)
private val BeakOrange = Color(0xFFE0842E)

data class GrowthStage(val idx: Int, val label: String, val range: String)

fun stageForDay(day: Int): GrowthStage = when {
    day <= 7 -> GrowthStage(0, "Chick", "0–7 d")
    day <= 16 -> GrowthStage(1, "Grower", "8–16 d")
    day <= 30 -> GrowthStage(2, "Rearing", "17–30 d")
    else -> GrowthStage(3, "Finisher", "31–52 d")
}

private fun bodyColorForStage(stage: Int): Color = lerp(ChickYellow, BroilerCream, stage / 3f)

/**
 * Side-view broiler silhouette whose proportions + colour change by stage, drawn at radius [s] (px).
 * The same routine grows the bird for the stage illustration and the density blobs.
 */
private fun DrawScope.drawStageBird(cx: Float, cy: Float, s: Float, stage: Int, color: Color) {
    val bodyW = s * (1.2f + 0.30f * stage)
    val bodyH = s * (0.95f + 0.05f * stage)
    val headR = s * (0.62f - 0.10f * stage)
    val legLen = s * (0.10f + 0.40f * stage)
    val legColor = BeakOrange

    // Legs (older birds stand taller)
    if (legLen > 1f) {
        drawLine(legColor, Offset(cx - bodyW * 0.2f, cy + bodyH * 0.6f), Offset(cx - bodyW * 0.2f, cy + bodyH * 0.6f + legLen), strokeWidth = s * 0.12f)
        drawLine(legColor, Offset(cx + bodyW * 0.2f, cy + bodyH * 0.6f), Offset(cx + bodyW * 0.2f, cy + bodyH * 0.6f + legLen), strokeWidth = s * 0.12f)
    }
    // Body
    drawOval(color, topLeft = Offset(cx - bodyW, cy - bodyH), size = Size(bodyW * 2f, bodyH * 2f))
    // Head (front-top right)
    val hx = cx + bodyW * 0.75f
    val hy = cy - bodyH * 0.7f
    drawCircle(color, headR, Offset(hx, hy))
    // Beak
    val beak = Path().apply {
        moveTo(hx + headR * 0.7f, hy)
        lineTo(hx + headR * 1.6f, hy - headR * 0.25f)
        lineTo(hx + headR * 1.6f, hy + headR * 0.25f)
        close()
    }
    drawPath(beak, BeakOrange)
    // Eye
    drawCircle(Color(0xFF23201A), headR * 0.22f, Offset(hx + headR * 0.15f, hy - headR * 0.15f))
}

/**
 * Chick → broiler growth stages, sized by current weight, plus a 1 ft² density panel where the
 * bird sizes vary by CV% (uniformity).
 */
@Composable
fun ChickGrowthInfographic(entry: DailyDataEntity?, flock: FlockEntity?, modifier: Modifier = Modifier) {
    if (entry == null) return
    val day = entry.dayNumber
    val stage = stageForDay(day)
    val avgG = entry.avgWeight ?: entry.idealWeight
    val measured = entry.avgWeight != null
    val cv = entry.cv ?: 0.0
    val birdsPerFt2 = if (entry.ftPerBird > 0) (1.0 / entry.ftPerBird) else 0.0

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(width = 4.dp, height = 18.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(8.dp))
                Text("Bird growth & density", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }

            // Current stage — big bird sized by weight
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Canvas(modifier = Modifier.size(96.dp)) {
                    val s = (14f + (avgG.toFloat() / 3200f).coerceIn(0f, 1.2f) * 30f)
                    drawStageBird(size.width / 2f, size.height / 2f - s * 0.3f, s, stage.idx, bodyColorForStage(stage.idx))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("${stage.label} · ${stage.range}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                    Text(
                        "${String.format("%.0f", avgG)} g " + (if (measured) "measured" else "predicted"),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                            color = if (measured) ValuePresent else ValuePredicted
                        )
                    )
                    Text("Day $day of ${flock?.harvestAge ?: 42}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Stage strip — 4 thumbnails, current highlighted
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (st in 0..3) {
                    val g = stageForDay(intArrayOf(4, 12, 24, 40)[st])
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Surface(
                            color = if (st == stage.idx) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Canvas(modifier = Modifier.size(52.dp).padding(4.dp)) {
                                val s = 7f + st * 5f
                                drawStageBird(size.width / 2f, size.height / 2f - s * 0.3f, s, st, bodyColorForStage(st))
                            }
                        }
                        Text(g.label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = if (st == stage.idx) FontWeight.Bold else FontWeight.Normal))
                        Text(g.range, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Density panel — one square foot, birds sized by CV%
            Text(
                "Density: ${String.format("%.1f", birdsPerFt2)} birds per ft²   ·   spread by CV ${if (cv > 0) String.format("%.0f", cv) + "%" else "—"}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier.fillMaxWidth().height(120.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                    val boxSide = size.height - 8f
                    val left = 8f
                    val top = 4f
                    // 1 ft² frame
                    drawRect(color = Color(0x22FFFFFF), topLeft = Offset(left, top), size = Size(boxSide, boxSide))
                    drawRect(color = Color(0x55FFFFFF), topLeft = Offset(left, top), size = Size(boxSide, boxSide), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))

                    val n = birdsPerFt2.roundToInt().coerceIn(0, 16)
                    if (n > 0) {
                        val cols = kotlin.math.ceil(kotlin.math.sqrt(n.toDouble())).toInt().coerceAtLeast(1)
                        val cell = boxSide / cols
                        val baseR = (cell * 0.32f)
                        var i = 0
                        for (r in 0 until cols) {
                            for (c in 0 until cols) {
                                if (i >= n) break
                                // size varies by CV% around the mean (uniformity)
                                val varFrac = (cv / 100.0) * sin(i * 1.7)
                                val rad = (baseR * (1f + varFrac.toFloat())).coerceIn(baseR * 0.5f, baseR * 1.5f)
                                val cx = left + c * cell + cell / 2f
                                val cy = top + r * cell + cell / 2f
                                // top-view blob (body oval)
                                drawOval(
                                    bodyColorForStage(stage.idx),
                                    topLeft = Offset(cx - rad, cy - rad * 0.8f),
                                    size = Size(rad * 2f, rad * 1.6f)
                                )
                                i++
                            }
                        }
                    }
                }
                Text(
                    "1 ft²",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 6.dp)
                )
                if (cv >= 10.0) {
                    Text(
                        "uneven sizes",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ValuePredicted),
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Feed store (godown): bags of each type in stock as stacked icons, plus ideal storage conditions.
 */
@Composable
fun GodownStockInfographic(stock: FeedStockSummary, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(width = 4.dp, height = 18.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(8.dp))
                    Text("Feed godown (store)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                Text(
                    "${String.format("%.0f", stock.totalOnHandBags)} bags",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                        color = if (stock.totalOnHandBags < 20) ValuePredicted else ValuePresent)
                )
            }

            if (stock.perTypeOnHand.isEmpty() || stock.totalOnHandBags <= 0) {
                Text("No feed in store yet — log deliveries in the ENTRY tab.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val palette = listOf(ValuePresent, ValueIdeal, ValuePredicted, Color(0xFFB57EDC), Color(0xFF6FBF73))
                var ci = 0
                for ((code, qty) in stock.perTypeOnHand) {
                    if (qty <= 0.0) continue
                    val color = palette[ci % palette.size]; ci++
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("$code", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = color))
                            Text("${String.format("%.1f", qty)} bags", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                        }
                        // Bag row (each icon = 1 bag, capped at 24 with an overflow note)
                        val bags = qty.roundToInt().coerceIn(0, 24)
                        Canvas(modifier = Modifier.fillMaxWidth().height(22.dp)) {
                            val gap = 3f
                            val bw = ((size.width + gap) / 24f) - gap
                            val bh = size.height
                            for (b in 0 until bags) {
                                val x = b * (bw + gap)
                                drawRoundRect(
                                    color = color.copy(alpha = 0.85f),
                                    topLeft = Offset(x, 0f),
                                    size = Size(bw.coerceAtLeast(2f), bh),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
                                )
                            }
                        }
                    }
                }
            }

            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("IDEAL STORE TEMP", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant))
                        Text("< 25 °C, cool & shaded", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = ValueIdeal))
                    }
                    Column {
                        Text("IDEAL HUMIDITY", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant))
                        Text("< 60 % RH, dry, off floor", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = ValueIdeal))
                    }
                }
            }
        }
    }
}
