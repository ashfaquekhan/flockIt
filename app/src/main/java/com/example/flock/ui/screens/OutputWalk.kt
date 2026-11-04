package com.example.flock.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.flock.domain.Travel
import com.example.flock.ui.Fmt
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/** The walk's three colours: the furthest bird's walk now (green), the ideal (blue), the most (red). */
private val WalkNow = TrendGood
private val WalkIdeal get() = kindColor(ValueKind.IDEAL)
private val WalkMost = TrendBad

private fun walkWord(g: Travel.Grade) = when (g) { Travel.Grade.SAFE -> "within the ideal"; Travel.Grade.MOST -> "past the ideal"; Travel.Grade.TOO_FAR -> "past the most" }

/**
 * What the floor looks like to a bird looking for feed or water, seen from above and to scale: lines [gapM] apart with
 * points on them [alongM] apart (open or shut), or — for trays and chick drinkers — points spread on a square grid.
 */
private class Layout(val gapM: Double, val alongM: Double, val open: (Int) -> Boolean, val grid: Boolean = false)

/**
 * The walk to feed and to water: for each, the furthest bird's walk against the ideal and the most for the age — as a
 * line like the other numbers' and as the floor seen from above, to scale, with three circles round the nearest open
 * pan or nipple: how far the furthest bird walks now (green), the ideal (blue) and the most (red).
 */
@Composable
fun WalkCard(d: OutputData, pat: OutputData.PanPattern) {
    val l = d.walkLimits
    val feed = d.feedWalkM(pat)
    val water = d.waterWalkM
    val floorM2 = d.areaInUseFt2 / 10.7639
    val trays = Travel.spread(floorM2, d.traysOnFloor)
    val drinkers = Travel.spread(floorM2, d.chickDrinkersOnFloor)
    OutputCard(title = "Walk to feed and water", info = "walk") {
        ColourKey(listOf(WalkNow to "now: the furthest bird", WalkIdeal to "ideal ≤ ${Fmt.n(l.safeM, 1)} m", WalkMost to "most ${Fmt.n(l.maxM, 1)} m"))
        val feedLayout = if (trays != null && trays < pat.travelM) sqrt(floorM2 / d.traysOnFloor).let { Layout(it, it, { true }, grid = true) }
            else Layout(d.feederGapFt * 0.3048, d.panSpacingFt * 0.3048, { pat.onInSeries(it) })
        WalkPart("To feed", if (feedLayout.grid) "trays" else "pans", feed, l, feedLayout, "walk_feed")
        val waterLayout = if (drinkers != null && drinkers < Travel.furthest(d.drinkerGapFt * 0.3048, d.nippleGapM))
            sqrt(floorM2 / d.chickDrinkersOnFloor).let { Layout(it, it, { true }, grid = true) }
            else Layout(d.drinkerGapFt * 0.3048, d.nippleGapM, { true })
        WalkPart("To water", if (waterLayout.grid) "chick drinkers" else "nipples", water, l, waterLayout, "walk_water")
    }
}

@Composable
private fun WalkPart(name: String, points: String, walkM: Double, l: Travel.Limits, lay: Layout, tag: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = Color.White)
        Spacer(Modifier.weight(1f))
        Text(Fmt.n(walkM, 2), style = MaterialTheme.typography.titleMedium.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Medium),
            color = WalkNow)
        Text(" m · " + walkWord(Travel.grade(walkM, l)), style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
    WalkLine(walkM, l)
    WalkFloor(lay, walkM, l, tag)
    Text(if (lay.grid) "$points one every ${Fmt.n(lay.gapM, 2)} m" else "lines ${Fmt.n(lay.gapM, 2)} m apart · $points ${Fmt.n(lay.alongM, 2)} m apart",
        style = MaterialTheme.typography.labelMedium.copy(fontFamily = com.example.ui.theme.NumberFont), color = Color.White.copy(alpha = 0.8f))
}

/** 0 m to past the most: the ideal stretch blue, the most a red mark (red past it), the furthest bird's walk a green dot. */
@Composable
private fun WalkLine(walkM: Double, l: Travel.Limits) {
    Canvas(Modifier.fillMaxWidth().height(30.dp)) {
        val px = density
        val top = 3f * px; val h = 8f * px
        val end = max(l.maxM + 1.0, walkM * 1.15)
        fun x(m: Double) = (m / end * size.width).toFloat()
        drawRoundRect(Color.White.copy(alpha = 0.14f), Offset(0f, top), Size(size.width, h), CornerRadius(4f * px))
        drawRoundRect(WalkIdeal.copy(alpha = 0.55f), Offset(0f, top), Size(x(l.safeM), h), CornerRadius(4f * px))
        drawRect(WalkMost.copy(alpha = 0.35f), Offset(x(l.maxM), top), Size(size.width - x(l.maxM), h))
        drawLine(WalkMost, Offset(x(l.maxM), top - 2f * px), Offset(x(l.maxM), top + h + 2f * px), 2f * px)
        val cx = x(walkM).coerceIn(6f * px, size.width - 6f * px)
        drawCircle(WalkNow, 6f * px, Offset(cx, top + h / 2))
        drawCircle(Color.Black, 2.2f * px, Offset(cx, top + h / 2))
        val p = Paint().apply { isAntiAlias = true; textSize = 10f * px; typeface = com.example.ui.theme.AppFonts.mono }
        p.color = Color.White.copy(alpha = 0.8f).toArgb(); p.textAlign = Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText("0", 0f, size.height - 2f * px, p)
        p.textAlign = Paint.Align.CENTER
        p.color = WalkIdeal.toArgb(); drawContext.canvas.nativeCanvas.drawText("ideal ${Fmt.n(l.safeM, 1)}", x(l.safeM), size.height - 2f * px, p)
        p.color = WalkMost.toArgb(); drawContext.canvas.nativeCanvas.drawText("most ${Fmt.n(l.maxM, 1)}", x(l.maxM), size.height - 2f * px, p)
        p.color = Color.White.copy(alpha = 0.8f).toArgb(); p.textAlign = Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText(Fmt.n(end, 1) + " m", size.width, size.height - 2f * px, p)
    }
}

/**
 * The floor from above, to scale: two lines with the floor halfway to the next ones on either side (or a grid of trays),
 * the open and shut points, the furthest bird (✕) and its walk to the nearest open point, and round that point the
 * walk now (green), the ideal (blue) and the most (red).
 */
@Composable
private fun WalkFloor(lay: Layout, walkM: Double, l: Travel.Limits, tag: String) {
    Canvas(Modifier.fillMaxWidth().height(130.dp).border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp)).testTag(tag)) {
        val px = density
        // two gaps tall: lines at a quarter and three quarters, the furthest bird halfway between them
        val hM = lay.gapM * 2
        val s = (size.height / hM).toFloat()           // px a metre
        val wM = size.width / s
        fun X(m: Double) = (m * s).toFloat()
        val rows = listOf(lay.gapM * 0.5, lay.gapM * 1.5)
        val n = ceil(wM / lay.alongM).toInt() + 1
        val pts = buildList { rows.forEach { y -> for (i in 0 until n) add(Triple(lay.alongM * (i + if (lay.grid) 0.5 else 0.25), y, lay.open(i))) } }
        val open = pts.filter { it.third }
        clipRect {
            if (!lay.grid) rows.forEach { y -> drawLine(Color.White.copy(alpha = 0.45f), Offset(0f, X(y)), Offset(size.width, X(y)), 1.5f * px) }
            val dotR = (if (lay.alongM * s < 9f * px) 1.8f else 3.2f) * px
            pts.forEach { (x, y, on) ->
                val c = Offset(X(x), X(y))
                if (on) drawCircle(Color.White, dotR, c)
                else {
                    drawCircle(Color.White.copy(alpha = 0.4f), dotR, c, style = Stroke(1f * px))
                    drawLine(Color.White.copy(alpha = 0.4f), Offset(c.x - dotR, c.y - dotR), Offset(c.x + dotR, c.y + dotR), 1f * px)
                }
            }
            // the furthest bird: halfway between the lines, halfway between two open points (the middle of a square on a grid)
            val firstOpen = open.minByOrNull { it.first } ?: return@clipRect
            val sameRow = open.filter { it.second == firstOpen.second }.map { it.first }.sorted()
            val gapAlong = if (sameRow.size >= 2) sameRow[1] - sameRow[0] else lay.alongM
            val fx = (sameRow.firstOrNull { X(it + gapAlong / 2) > size.width * 0.35f } ?: firstOpen.first) + gapAlong / 2
            val fy = lay.gapM
            val near = open.minByOrNull { hypot(it.first - fx, it.second - fy) }!!
            val f = Offset(X(fx), X(fy)); val t = Offset(X(near.first), X(near.second))
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f * px, 4f * px))
            drawCircle(WalkMost, (l.maxM * s).toFloat(), t, style = Stroke(1.6f * px, pathEffect = dash))
            drawCircle(WalkIdeal, (l.safeM * s).toFloat(), t, style = Stroke(1.6f * px, pathEffect = dash))
            drawCircle(WalkNow, (walkM * s).toFloat(), t, style = Stroke(1.8f * px))
            drawLine(WalkNow, f, t, 1.6f * px)
            val k = 5f * px
            drawLine(Color.White, Offset(f.x - k, f.y - k), Offset(f.x + k, f.y + k), 2.2f * px)
            drawLine(Color.White, Offset(f.x - k, f.y + k), Offset(f.x + k, f.y - k), 2.2f * px)
            val p = Paint().apply { isAntiAlias = true; textSize = 11f * px; typeface = com.example.ui.theme.AppFonts.mono; color = WalkNow.toArgb() }
            val label = Fmt.n(walkM, 2) + " m"
            val tw = p.measureText(label)
            val lx = (f.x + 9f * px).coerceAtMost(size.width - tw - 8f * px); val ly = f.y - 8f * px
            drawRoundRect(Color.Black.copy(alpha = 0.85f), Offset(lx - 4f * px, ly - 12f * px), Size(tw + 8f * px, 16f * px), CornerRadius(4f * px))
            drawContext.canvas.nativeCanvas.drawText(label, lx, ly, p)
            // a metre for scale
            val bar = X(1.0)
            drawLine(Color.White.copy(alpha = 0.8f), Offset(size.width - bar - 8f * px, size.height - 8f * px), Offset(size.width - 8f * px, size.height - 8f * px), 2f * px)
            val p2 = Paint().apply { isAntiAlias = true; textSize = 9.5f * px; typeface = com.example.ui.theme.AppFonts.mono; color = Color.White.copy(alpha = 0.8f).toArgb(); textAlign = Paint.Align.CENTER }
            drawContext.canvas.nativeCanvas.drawText("1 m", size.width - bar / 2 - 8f * px, size.height - 12f * px, p2)
        }
    }
}
