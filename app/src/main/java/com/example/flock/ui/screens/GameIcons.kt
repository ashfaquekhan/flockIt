package com.example.flock.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Small crafted symbols, one for each topic and number, drawn in code (the same on every phone): little objects with a
 * lit top, a shaded side and a soft shadow, like the items of a game. All in one place so the set can be changed
 * together; [GameIcon] draws one, [drawGameIcon] puts one inside another drawing (the farm views).
 */
enum class GIcon {
    // birds
    BIRDS, LIVE, WEIGHT, FCR, CFCR, MORTALITY, CV, DENSITY, HOUSE,
    // feed and floor
    BAGS, GRAM, GAIN_Y, GAIN_T, PANS, BARRICADE, WALK, HUNGRY, CLOCK, EATEN, TRAYS, PAPER,
    // air
    FAN, TEMP, HUMIDITY, FEELS, WIND, PRESSURE, GAS, AMMONIA, TIMER, AIRCHANGE, CONTROL, PAD_WET, PAD_DRY,
    LITTER, MOIST, LITTER_TEMP, DEPTH, LIGHT, BODY, PANT, OUTSIDE, SENSOR,
    // water
    WATER, TANK, NIPPLE, FLOW, PH, WATER_TEMP, THIRST, DRINKER,
    // lifting and records
    TRUCK, CRATE, EPEF, HEART, MEAT, GRAPH, TARGET, TREND, BOOK, CHECK, SCALE
}

@Composable
fun GameIcon(icon: GIcon, size: Dp = 28.dp, modifier: Modifier = Modifier, dim: Boolean = false) {
    Canvas(modifier.size(size)) { drawGameIcon(icon, Offset.Zero, this.size.minDimension, if (dim) 0.35f else 1f) }
}

// the palette: soft colours that sit on pure black
private val Chick = Color(0xFFF6D45C); private val ChickDk = Color(0xFFD9B23C); private val Beak = Color(0xFFF0A23A); private val Comb = Color(0xFFE5534B)
private val Feed = Color(0xFFE2BC6A); private val Sack = Color(0xFFD2B07A); private val SackDk = Color(0xFFA27F4C); private val Twine = Color(0xFF7A5A30)
private val Metal = Color(0xFFC4CBD3); private val MetalDk = Color(0xFF7D8792); private val Slate = Color(0xFF3C434C); private val SlateDk = Color(0xFF262B31)
private val Aqua = Color(0xFF5DB8EC); private val AquaLt = Color(0xFFA9DCF7); private val AquaDk = Color(0xFF2F7FB4)
private val Leaf = Color(0xFF46B98C); private val LeafDk = Color(0xFF2E8A66); private val Ruby = Color(0xFFE5534B); private val RubyDk = Color(0xFFB13A33)
private val Gold = Color(0xFFF2C94C); private val GoldDk = Color(0xFFC79A22); private val Wood = Color(0xFFB07D45); private val WoodDk = Color(0xFF7F5428)
private val Stone = Color(0xFF9AA1AA); private val StoneDk = Color(0xFF666D76); private val Snow = Color(0xFFF2F2F0); private val Ink = Color(0xFF15181C)
private val Litter = Color(0xFFA9824F); private val LitterDk = Color(0xFF7A5A35); private val Straw = Color(0xFFE8CE7E)
private val Violet = Color(0xFFB58BE0); private val Sun = Color(0xFFF7B544); private val Cloud = Color(0xFFD9DEE4); private val Gas = Color(0xFFB7D36B)

/** Draws [icon] in the square at [at] of side [s] (px). */
fun DrawScope.drawGameIcon(icon: GIcon, at: Offset, s: Float, alpha: Float = 1f) {
    val p = Pen(this, at, s / 24f, alpha)
    with(p) {
        when (icon) {
            GIcon.BIRDS, GIcon.LIVE -> { shadow(12f, 21f, 8f, 2f); chick(12f, 13.5f, 1f) }
            GIcon.BODY -> { shadow(11f, 21f, 7f, 1.8f); chick(10f, 13.5f, 0.85f); thermo(19f, 4f, 0.75f) }
            GIcon.HOUSE -> { shadow(12f, 20f, 10.5f, 2f)
                // back roof slope, the gable end, the long wall with its windows, the front roof slope
                poly(RubyDk, 3f, 12.5f, 5.6f, 8.2f, 17.3f, 1.5f, 14.4f, 6f)
                poly(Snow, 3f, 17f, 8f, 20f, 8f, 14.6f, 5.6f, 9.8f, 3f, 12.5f)
                poly(Stone, 8f, 20f, 20.5f, 12.8f, 20.5f, 7.4f, 8f, 14.6f)
                listOf(10.5f, 14f, 17.5f).forEach { x -> val y = 18.2f - (x - 8f) * 0.577f; poly(Ink.copy(alpha = 0.55f), x, y, x + 1.8f, y - 1.04f, x + 1.8f, y - 3.64f, x, y - 2.6f) }
                poly(Ruby, 8f, 14.6f, 20.5f, 7.4f, 17.3f, 1.5f, 5.6f, 8.2f)
                rect(WoodDk, 4.6f, 14.6f, 1.8f, 3.6f, 0.3f) }
            GIcon.WEIGHT, GIcon.SCALE -> { shadow(12f, 21.5f, 8f, 1.6f)
                ring(MetalDk, 12f, 7f, 3.3f, 1.8f)
                poly(Metal, 6f, 10f, 18f, 10f, 20.5f, 20.5f, 3.5f, 20.5f); poly(MetalDk, 15f, 10f, 18f, 10f, 20.5f, 20.5f, 16.5f, 20.5f)
                text("kg", 11f, 18.2f, 6.4f, Ink) }
            GIcon.FCR -> { shadow(12f, 20.5f, 9f, 2f); pan(12f, 15f, 1f, true); grain(9f, 9f); grain(14f, 8f); grain(11.5f, 6.5f) }
            GIcon.CFCR -> { shadow(11f, 20.5f, 8f, 1.8f); pan(10.5f, 15f, 0.9f, true); medal(18.5f, 7f, 4.6f, Violet, "c") }
            GIcon.MORTALITY -> { shadow(12f, 21f, 7.5f, 1.8f)
                path(StoneDk) { moveTo(15.2f, 20f); lineTo(15.2f, 9f); cubicTo(15.2f, 5f, 17.5f, 4.2f, 17.5f, 4.2f); lineTo(17.5f, 19f); close() }
                path(Stone) { moveTo(6f, 20f); lineTo(6f, 10f); cubicTo(6f, 4.5f, 15.5f, 4.5f, 15.5f, 10f); lineTo(15.5f, 20f); close() }
                line(StoneDk, 10.75f, 8f, 10.75f, 15f, 1.6f); line(StoneDk, 8.2f, 10.5f, 13.3f, 10.5f, 1.6f)
                poly(Leaf, 4f, 20.5f, 6f, 18f, 7f, 20.5f); poly(Leaf, 15f, 20.5f, 17f, 17.6f, 18.5f, 20.5f) }
            GIcon.CV -> { shadow(12f, 21.5f, 9f, 1.6f); rect(SlateDk, 3.5f, 4.5f, 17f, 15f, 2f); rect(Slate, 3f, 4f, 17f, 14.5f, 2f)
                listOf(5.5f to 2.5f, 8f to 6f, 10.5f to 9f, 13f to 8f, 15.5f to 4.5f, 18f to 2f).forEach { (x, h) -> rect(Leaf.copy(alpha = 0.55f), x - 1f, 16.5f - h, 2f, h, 0.3f) }
                path(null, Snow, 1.4f) { moveTo(4.5f, 16f); cubicTo(9f, 16f, 8.5f, 6f, 11.5f, 6f); cubicTo(14.5f, 6f, 14f, 16f, 18.8f, 16f) } }
            GIcon.DENSITY -> { shadow(12f, 21f, 10f, 1.8f); poly(Litter, 12f, 9f, 22f, 14f, 12f, 19f, 2f, 14f); poly(LitterDk, 2f, 14f, 12f, 19f, 12f, 20.5f, 2f, 15.5f)
                poly(LitterDk.copy(alpha = 0.6f), 12f, 19f, 22f, 14f, 22f, 15.5f, 12f, 20.5f)
                listOf(8f to 13f, 12f to 11.3f, 16f to 13.2f, 10f to 15.8f, 14.2f to 15.9f).forEach { (x, y) -> chick(x, y, 0.3f) } }
            GIcon.BAGS -> sack(12f, 13f, 1f)
            GIcon.GRAM -> { shadow(12f, 21f, 8.5f, 1.8f); bowl(12f, 16f, 1f); grain(10f, 12.5f); grain(13f, 12f); grain(11.5f, 10.8f); medal(19f, 6f, 4.2f, Leaf, "g") }
            GIcon.EATEN -> { shadow(12f, 21f, 8.5f, 1.8f); bowl(12f, 16f, 1f); grain(12f, 13f); line(Metal, 17f, 4f, 13.5f, 12.5f, 1.6f); oval(Metal, 17.6f, 4.2f, 1.6f, 2.2f) }
            GIcon.GAIN_Y -> { shadow(12f, 21.5f, 9f, 1.5f); bars(Leaf, LeafDk); arrowUp(18f, 3f, Leaf) }
            GIcon.GAIN_T -> { shadow(12f, 21.5f, 9f, 1.5f); bars(Leaf.copy(alpha = 0.55f), LeafDk.copy(alpha = 0.6f))
                path(null, Aqua, 1.6f) { moveTo(3f, 15f); cubicTo(9f, 13.5f, 13f, 8f, 21f, 3.5f) }; circle(Snow, 21f, 3.5f, 1.3f) }
            GIcon.PANS -> { shadow(12f, 20.5f, 11f, 1.5f); pan(5.5f, 15.5f, 0.55f, true); pan(12f, 15.5f, 0.55f, true); pan(18.5f, 15.5f, 0.55f, false)
                line(Snow.copy(alpha = 0.7f), 2f, 9f, 22f, 9f, 1.3f); listOf(5.5f, 12f, 18.5f).forEach { line(Snow.copy(alpha = 0.5f), it, 9f, it, 12.5f, 1f) } }
            GIcon.BARRICADE -> { shadow(12f, 21f, 10f, 1.6f)
                listOf(4f, 12f, 20f).forEach { x -> rect(WoodDk, x - 1.4f, 7f, 2.8f, 13.5f, 0.6f); rect(Wood, x - 1.4f, 7f, 1.6f, 13.5f, 0.6f); poly(Wood, x - 1.4f, 7f, x, 5.5f, x + 1.4f, 7f) }
                rect(Snow, 2f, 9.5f, 20f, 2.4f, 0.8f); rect(Snow, 2f, 14.5f, 20f, 2.4f, 0.8f); rect(Stone, 2f, 11.1f, 20f, 0.8f, 0f); rect(Stone, 2f, 16.1f, 20f, 0.8f, 0f) }
            GIcon.WALK -> { foot(8f, 16f, -0.3f); foot(15f, 9f, 0.2f) }
            GIcon.HUNGRY -> { shadow(11f, 20.5f, 8f, 1.8f); pan(10.5f, 15.5f, 0.85f, false); bubble(18.5f, 6.5f, Ruby, "!") }
            GIcon.CLOCK, GIcon.TIMER -> {
                shadow(12f, 21.5f, 7f, 1.5f)
                if (icon == GIcon.TIMER) { rect(MetalDk, 10.5f, 2f, 3f, 2.6f, 0.6f); line(MetalDk, 18f, 5f, 19.5f, 3.5f, 1.6f) }
                circle(MetalDk, 12f, 12.8f, 8.2f); circle(Snow, 12f, 12.6f, 6.9f)
                if (icon == GIcon.TIMER) path(Leaf) { moveTo(12f, 12.6f); lineTo(12f, 5.7f); arcTo(androidx.compose.ui.geometry.Rect(5.1f * u + o.x, 5.7f * u + o.y, 18.9f * u + o.x, 19.5f * u + o.y), -90f, 110f, false); close() }
                line(Ink, 12f, 12.6f, 12f, 7.6f, 1.6f); line(Ink, 12f, 12.6f, 15.6f, 14.4f, 1.6f); circle(Ruby, 12f, 12.6f, 1.1f) }
            GIcon.TRAYS -> { shadow(12f, 20f, 10f, 1.6f); poly(RubyDk, 2f, 14f, 12f, 19f, 12f, 20.6f, 2f, 15.6f); poly(Ruby.copy(alpha = 0.75f), 12f, 19f, 22f, 14f, 22f, 15.6f, 12f, 20.6f)
                poly(Ruby, 12f, 9f, 22f, 14f, 12f, 19f, 2f, 14f); poly(Feed, 12f, 10.8f, 19f, 14f, 12f, 17.2f, 5f, 14f) }
            GIcon.PAPER -> { shadow(12f, 20f, 10f, 1.6f); poly(Snow, 12f, 9f, 22f, 14f, 12f, 19f, 2f, 14f); poly(Cloud, 2f, 14f, 12f, 19f, 12f, 19.8f, 2f, 14.8f)
                listOf(9f to 13f, 13f to 12f, 11f to 15f, 15f to 14.6f, 7f to 14.6f).forEach { (x, y) -> grain(x, y) } }
            // ---------------- air ----------------
            GIcon.FAN -> { shadow(12f, 21.5f, 9f, 1.5f); rect(SlateDk, 3.6f, 3.6f, 17.6f, 17.6f, 2.4f); rect(Slate, 3f, 3f, 17.6f, 17.6f, 2.4f)
                circle(SlateDk, 11.8f, 11.8f, 7.2f)
                for (k in 0 until 3) { val a = k * 2 * PI / 3 - 0.4; blade(11.8f, 11.8f, a.toFloat()) }
                circle(Metal, 11.8f, 11.8f, 1.6f) }
            GIcon.TEMP -> { shadow(12f, 21.5f, 5f, 1.4f); thermo(12f, 2.5f, 1f) }
            GIcon.FEELS -> { thermo(8.5f, 3f, 0.85f); listOf(5f, 9f, 13f).forEach { y -> wave(15f, y, Sun) } }
            GIcon.HUMIDITY -> { shadow(12f, 21.5f, 6f, 1.5f); drop(12f, 12.5f, 1f, Aqua, AquaDk) }
            GIcon.WIND -> { path(null, AquaLt, 1.8f) { moveTo(2.5f, 9f); lineTo(14f, 9f); cubicTo(18f, 9f, 18f, 4f, 15f, 4f); cubicTo(13f, 4f, 12.5f, 6f, 13.5f, 7f) }
                path(null, Snow, 1.8f) { moveTo(2.5f, 13f); lineTo(18f, 13f); cubicTo(22f, 13f, 22f, 18.5f, 18.5f, 18.5f); cubicTo(16.5f, 18.5f, 16f, 16.5f, 17f, 15.6f) }
                path(null, AquaLt.copy(alpha = 0.7f), 1.6f) { moveTo(5f, 17f); lineTo(12f, 17f) } }
            GIcon.PRESSURE -> { shadow(12f, 21.5f, 8f, 1.5f); circle(MetalDk, 12f, 12.5f, 8.6f); circle(Snow, 12f, 12.5f, 7.2f)
                arc(Leaf, 12f, 12.5f, 5.6f, 150f, 80f, 2f); arc(Gold, 12f, 12.5f, 5.6f, 230f, 60f, 2f); arc(Ruby, 12f, 12.5f, 5.6f, 290f, 100f, 2f)
                line(Ink, 12f, 12.5f, 15.6f, 8.4f, 1.6f); circle(Ink, 12f, 12.5f, 1.3f); rect(MetalDk, 10.5f, 19.8f, 3f, 2.4f, 0.4f) }
            GIcon.GAS, GIcon.AMMONIA -> { val c = if (icon == GIcon.GAS) Cloud else Gas
                cloud(12f, 12f, 1f, c, c.copy(alpha = 0.6f)); text(if (icon == GIcon.GAS) "CO₂" else "NH₃", 12f, 14f, 5.2f, Ink) }
            GIcon.AIRCHANGE -> { arc(Aqua, 12f, 12f, 7.5f, 200f, 220f, 2.4f); arc(Snow, 12f, 12f, 7.5f, 20f, 220f, 2.4f)
                poly(Aqua, 17.6f, 3.8f, 21.4f, 7.6f, 16f, 8.8f); poly(Snow, 6.4f, 20.2f, 2.6f, 16.4f, 8f, 15.2f) }
            GIcon.CONTROL -> { shadow(12f, 21.5f, 9f, 1.5f); isoBox(4f, 19f, 14f, 3f, 14f, Metal, Stone, StoneDk)
                poly(Ink, 6f, 15f, 15.6f, 9.5f, 15.6f, 5f, 6f, 10.5f); poly(Leaf.copy(alpha = 0.85f), 7f, 13.8f, 14.6f, 9.4f, 14.6f, 6.2f, 7f, 10.6f)
                circle(Ruby, 8f, 17f, 1.1f); circle(Gold, 11f, 15.3f, 1.1f) }
            GIcon.PAD_WET, GIcon.PAD_DRY -> { val wet = icon == GIcon.PAD_WET
                val face = if (wet) Aqua else Sack; val side = if (wet) AquaDk else SackDk
                shadow(12f, 21.5f, 9f, 1.5f); poly(side, 15f, 4f, 19f, 6f, 19f, 21f, 15f, 19f); poly(face, 4f, 4f, 15f, 4f, 15f, 19f, 4f, 19f)
                for (k in 0..6) line(side.copy(alpha = 0.7f), 4f, 5f + k * 2.4f, 15f, 3f + k * 2.4f + 2f, 0.9f)
                for (k in 0..6) line(side.copy(alpha = 0.7f), 4f, 3f + k * 2.4f + 2f, 15f, 5f + k * 2.4f, 0.9f)
                if (wet) { drop(9.5f, 21.5f, 0.28f, AquaLt, Aqua); drop(13f, 22.2f, 0.22f, AquaLt, Aqua) } }
            GIcon.LITTER -> { shadow(12f, 21f, 10f, 1.6f); heap(12f, 19.5f, 1f) }
            GIcon.MOIST -> { shadow(12f, 21f, 10f, 1.6f); heap(10f, 19.5f, 0.85f); drop(18.5f, 8f, 0.55f, Aqua, AquaDk) }
            GIcon.LITTER_TEMP -> { shadow(12f, 21f, 10f, 1.6f); heap(9.5f, 19.5f, 0.8f); thermo(18.5f, 3f, 0.7f) }
            GIcon.DEPTH -> { shadow(12f, 21f, 9f, 1.5f); isoBox(3f, 20f, 9f, 7f, 6f, Litter, LitterDk, LitterDk.copy(alpha = 0.8f))
                line(Snow, 20f, 7f, 20f, 19f, 1.2f); line(Snow, 18.5f, 7f, 21.5f, 7f, 1.2f); line(Snow, 18.5f, 19f, 21.5f, 19f, 1.2f) }
            GIcon.LIGHT -> { listOf(0, 1, 2, 3, 4).forEach { k -> val a = (PI + k * PI / 4).toFloat(); line(Sun, 12f + 8.5f * cos(a), 10f + 8.5f * sin(a), 12f + 10.5f * cos(a), 10f + 10.5f * sin(a), 1.4f) }
                circle(Gold, 12f, 10.5f, 6f); circle(Snow.copy(alpha = 0.5f), 10f, 8.5f, 1.8f); rect(MetalDk, 9.5f, 15.5f, 5f, 5f, 1f); line(Metal, 9.5f, 17.3f, 14.5f, 17.3f, 1f); line(Metal, 9.5f, 19f, 14.5f, 19f, 1f) }
            GIcon.PANT, GIcon.OUTSIDE -> { if (icon == GIcon.PANT) { sun(12f, 11f, 1f) } else { sun(9f, 9f, 0.75f); cloud(14f, 15f, 0.8f, Cloud, Stone) } }
            GIcon.SENSOR -> { shadow(12f, 21.5f, 6f, 1.4f); rect(StoneDk, 7f, 10f, 10f, 10f, 1.6f); rect(Stone, 7f, 10f, 8.5f, 10f, 1.6f); circle(Leaf, 11f, 14f, 1.2f)
                line(MetalDk, 12f, 10f, 12f, 4.5f, 1.3f); circle(Ruby, 12f, 4f, 1.2f); arc(Snow.copy(alpha = 0.7f), 12f, 4f, 3.6f, 300f, 60f, 1.2f); arc(Snow.copy(alpha = 0.7f), 12f, 4f, 3.6f, 120f, 60f, 1.2f) }
            // ---------------- water ----------------
            GIcon.WATER -> { shadow(12f, 21.5f, 6f, 1.5f); drop(12f, 12.5f, 1f, Aqua, AquaDk); circle(Snow.copy(alpha = 0.85f), 9.4f, 13f, 1.4f) }
            GIcon.TANK -> { shadow(12f, 21.5f, 8.5f, 1.8f); cyl(12f, 19f, 7.5f, 13f, AquaLt, Aqua, AquaDk); line(Snow.copy(alpha = 0.6f), 6.5f, 10f, 6.5f, 17f, 1f) }
            GIcon.NIPPLE, GIcon.DRINKER -> { rect(MetalDk, 2f, 5f, 20f, 3.2f, 1.6f); rect(Metal, 2f, 5f, 20f, 1.6f, 1.6f)
                rect(Ruby, 10.5f, 8f, 3f, 4f, 0.8f); line(MetalDk, 12f, 12f, 12f, 14f, 1f); drop(12f, 18.5f, 0.38f, Aqua, AquaDk)
                if (icon == GIcon.DRINKER) { shadow(12f, 22f, 6f, 1f) } }
            GIcon.FLOW -> { drop(9f, 13f, 0.85f, Aqua, AquaDk); arrowRight(14f, 13f, Snow) }
            GIcon.PH -> { shadow(12f, 21.5f, 7f, 1.5f); path(Cloud.copy(alpha = 0.35f)) { moveTo(9.5f, 3f); lineTo(14.5f, 3f); lineTo(14.5f, 9f); lineTo(20f, 20f); lineTo(4f, 20f); lineTo(9.5f, 9f); close() }
                path(Leaf) { moveTo(7.2f, 14f); lineTo(16.8f, 14f); lineTo(20f, 20f); lineTo(4f, 20f); close() }
                path(null, Snow, 1.2f) { moveTo(9.5f, 3f); lineTo(14.5f, 3f); lineTo(14.5f, 9f); lineTo(20f, 20f); lineTo(4f, 20f); lineTo(9.5f, 9f); close() }
                text("pH", 12f, 18.8f, 4.6f, Ink) }
            GIcon.WATER_TEMP -> { drop(8.5f, 13f, 0.75f, Aqua, AquaDk); thermo(17.5f, 3.5f, 0.75f) }
            GIcon.THIRST -> { path(null, Aqua, 1.6f) { dropPath(10.5f, 13f, 0.85f) }; bubble(18.5f, 6.5f, Ruby, "!") }
            // ---------------- lifting and records ----------------
            GIcon.TRUCK -> { shadow(12f, 21.5f, 10.5f, 1.6f); rect(Snow, 2f, 6f, 13f, 10f, 1.2f); rect(Cloud, 2f, 13.5f, 13f, 2.5f, 0f)
                path(Ruby) { moveTo(15.5f, 9f); lineTo(19.5f, 9f); lineTo(22f, 12.5f); lineTo(22f, 16f); lineTo(15.5f, 16f); close() }; poly(AquaLt, 16.5f, 10f, 19f, 10f, 20.8f, 12.6f, 16.5f, 12.6f)
                circle(Ink, 6f, 17.5f, 2.6f); circle(Stone, 6f, 17.5f, 1.2f); circle(Ink, 18.5f, 17.5f, 2.6f); circle(Stone, 18.5f, 17.5f, 1.2f)
                chick(8.5f, 11.2f, 0.32f) }
            GIcon.CRATE -> { shadow(12f, 21.5f, 9f, 1.6f); chick(9f, 9f, 0.38f); chick(14.5f, 8.6f, 0.38f)
                isoBox(4f, 20f, 9f, 7f, 7f, Wood, WoodDk, WoodDk.copy(alpha = 0.85f)); line(WoodDk, 4f, 16.5f, 11.8f, 12f, 0.9f) }
            GIcon.EPEF -> { shadow(12f, 21.5f, 7f, 1.5f); path(Gold) { moveTo(6f, 3.5f); lineTo(18f, 3.5f); cubicTo(18f, 11f, 15f, 13f, 12f, 13f); cubicTo(9f, 13f, 6f, 11f, 6f, 3.5f); close() }
                path(GoldDk) { moveTo(13.5f, 3.5f); lineTo(18f, 3.5f); cubicTo(18f, 11f, 15f, 13f, 12f, 13f); cubicTo(14f, 11f, 14f, 8f, 13.5f, 3.5f); close() }
                ring(Gold, 5f, 7f, 2.4f, 1.3f); ring(Gold, 19f, 7f, 2.4f, 1.3f); rect(GoldDk, 10.8f, 13f, 2.4f, 4f, 0f); rect(Gold, 7.5f, 17f, 9f, 3f, 0.8f); circle(Snow.copy(alpha = 0.6f), 9f, 6.5f, 1.2f) }
            GIcon.HEART -> { shadow(12f, 21.5f, 7f, 1.5f); path(Ruby) { moveTo(12f, 20f); cubicTo(2f, 13f, 3f, 4f, 8f, 4f); cubicTo(10.5f, 4f, 12f, 6.5f, 12f, 7.5f); cubicTo(12f, 6.5f, 13.5f, 4f, 16f, 4f); cubicTo(21f, 4f, 22f, 13f, 12f, 20f); close() }
                path(RubyDk) { moveTo(12f, 20f); cubicTo(17f, 16.5f, 20.5f, 12f, 20f, 8f); cubicTo(21.5f, 13f, 17f, 17f, 12f, 20f); close() }; circle(Snow.copy(alpha = 0.55f), 7.5f, 8f, 1.5f) }
            GIcon.MEAT -> { shadow(12f, 21.5f, 8f, 1.5f); line(Snow, 14f, 13f, 19.5f, 18.5f, 2.6f); circle(Snow, 20.2f, 17.6f, 1.6f); circle(Snow, 18.6f, 19.4f, 1.6f)
                oval(WoodDk, 9.6f, 9.6f, 7f, 6f); oval(Wood, 9f, 9f, 6.4f, 5.4f); circle(Snow.copy(alpha = 0.4f), 6.8f, 7f, 1.5f) }
            GIcon.GRAPH -> { shadow(12f, 21.5f, 9f, 1.5f); rect(SlateDk, 3.5f, 4.5f, 17f, 15f, 2f); rect(Slate, 3f, 4f, 17f, 14.5f, 2f)
                listOf(6f to 3f, 9.5f to 5f, 13f to 4f, 16.5f to 7f).forEach { (x, h) -> rect(Aqua.copy(alpha = 0.5f), x - 1.2f, 16.5f - h, 2.4f, h, 0.3f) }
                path(null, Leaf, 1.6f) { moveTo(5f, 13f); lineTo(9f, 10f); lineTo(12.5f, 12f); lineTo(18f, 6f) }; circle(Snow, 18f, 6f, 1.2f) }
            GIcon.TARGET -> { shadow(12f, 21.5f, 7.5f, 1.5f); circle(Ruby, 11f, 12f, 8f); circle(Snow, 11f, 12f, 5.8f); circle(Ruby, 11f, 12f, 3.6f); circle(Snow, 11f, 12f, 1.5f)
                line(WoodDk, 11f, 12f, 20.5f, 2.5f, 1.6f); poly(Leaf, 20.5f, 2.5f, 22.5f, 2.2f, 21f, 5f) }
            GIcon.TREND -> { path(null, Leaf, 2.4f) { moveTo(2.5f, 18f); lineTo(8.5f, 11f); lineTo(12.5f, 14.5f); lineTo(19f, 6f) }; poly(Leaf, 21.5f, 3f, 21.5f, 9.5f, 15.5f, 5f) }
            GIcon.BOOK -> { shadow(12f, 21.5f, 8.5f, 1.5f); path(RubyDk) { moveTo(5f, 4f); lineTo(18f, 4f); lineTo(18f, 20f); lineTo(5f, 20f); close() }
                path(Ruby) { moveTo(4f, 3f); lineTo(17f, 3f); lineTo(17f, 19f); lineTo(4f, 19f); close() }; rect(Snow, 17f, 4.5f, 1.4f, 15f, 0.3f)
                rect(Gold, 6.5f, 6f, 8f, 2.4f, 0.4f); line(Snow.copy(alpha = 0.6f), 6.5f, 11f, 14.5f, 11f, 1f); line(Snow.copy(alpha = 0.6f), 6.5f, 13.5f, 12.5f, 13.5f, 1f) }
            GIcon.CHECK -> { ring(Metal, 10f, 10f, 6f, 2.4f); circle(AquaLt.copy(alpha = 0.3f), 10f, 10f, 4.8f); line(MetalDk, 14.5f, 14.5f, 20.5f, 20.5f, 3.2f)
                path(null, Leaf, 1.6f) { moveTo(7.2f, 10f); lineTo(9.4f, 12.2f); lineTo(13f, 7.8f) } }
        }
    }
}

/** A little pen on a 24-unit grid inside the icon's square. */
private class Pen(val ds: DrawScope, val o: Offset, val u: Float, val a: Float) {
    fun c(col: Color) = col.copy(alpha = col.alpha * a)
    fun P(x: Float, y: Float) = Offset(o.x + x * u, o.y + y * u)
    fun circle(col: Color, x: Float, y: Float, r: Float) = ds.drawCircle(c(col), r * u, P(x, y))
    fun ring(col: Color, x: Float, y: Float, r: Float, w: Float) = ds.drawCircle(c(col), r * u, P(x, y), style = Stroke(w * u))
    fun oval(col: Color, x: Float, y: Float, rx: Float, ry: Float) = ds.drawOval(c(col), P(x - rx, y - ry), Size(rx * 2 * u, ry * 2 * u))
    fun shadow(x: Float, y: Float, rx: Float, ry: Float) { oval(Color.White.copy(alpha = 0.08f), x, y, rx, ry); oval(Color.Black.copy(alpha = 0.5f), x, y, rx * 0.92f, ry * 0.85f) }
    fun rect(col: Color, x: Float, y: Float, w: Float, h: Float, r: Float) =
        ds.drawRoundRect(c(col), P(x, y), Size(w * u, h * u), androidx.compose.ui.geometry.CornerRadius(r * u, r * u))
    fun line(col: Color, x1: Float, y1: Float, x2: Float, y2: Float, w: Float) = ds.drawLine(c(col), P(x1, y1), P(x2, y2), w * u, cap = StrokeCap.Round)
    fun poly(col: Color, vararg xy: Float) {
        val path = Path(); var i = 0
        while (i < xy.size - 1) { val q = P(xy[i], xy[i + 1]); if (i == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y); i += 2 }
        path.close(); ds.drawPath(path, c(col))
    }
    /** a path on the grid: filled with [fill], or stroked with [stroke] */
    fun path(fill: Color?, stroke: Color? = null, w: Float = 1f, build: GridPath.() -> Unit) {
        val gp = GridPath(this); gp.build()
        if (fill != null) ds.drawPath(gp.path, c(fill))
        if (stroke != null) ds.drawPath(gp.path, c(stroke), style = Stroke(w * u, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
    fun arc(col: Color, x: Float, y: Float, r: Float, start: Float, sweep: Float, w: Float) =
        ds.drawArc(c(col), start, sweep, false, P(x - r, y - r), Size(r * 2 * u, r * 2 * u), style = Stroke(w * u, cap = StrokeCap.Round))
    fun text(t: String, x: Float, y: Float, size: Float, col: Color) {
        val paint = android.graphics.Paint().apply { isAntiAlias = true; textSize = size * u; textAlign = android.graphics.Paint.Align.CENTER
            typeface = com.example.ui.theme.AppFonts.text; color = c(col).toArgb() }
        ds.drawContext.canvas.nativeCanvas.drawText(t, P(x, y).x, P(x, y).y, paint)
    }

    // ---- the pieces several icons share ----
    /** a 2:1 box standing on (x, y), its right edge [w] long, its left [d], [h] tall */
    fun isoBox(x: Float, y: Float, w: Float, d: Float, h: Float, top: Color, left: Color, right: Color) {
        val rx = 0.866f; val ry = 0.5f
        val f = x to y; val r = (x + w * rx) to (y - w * ry); val l = (x - d * rx) to (y - d * ry); val b = (x + w * rx - d * rx) to (y - w * ry - d * ry)
        poly(left, l.first, l.second, f.first, f.second, f.first, f.second - h, l.first, l.second - h)
        poly(right, f.first, f.second, r.first, r.second, r.first, r.second - h, f.first, f.second - h)
        poly(top, f.first, f.second - h, r.first, r.second - h, b.first, b.second - h, l.first, l.second - h)
    }
    fun cyl(x: Float, y: Float, r: Float, h: Float, top: Color, side: Color, dark: Color) {
        val ry = r * 0.42f
        oval(side, x, y, r, ry); rect(side, x - r, y - h, r * 2, h, 0f); rect(dark, x + r * 0.35f, y - h, r * 0.65f, h, 0f)
        oval(dark.copy(alpha = 0.6f), x, y, r, ry); rect(side, x - r, y - h, r * 1.35f, h, 0f); oval(top, x, y - h, r, ry)
    }
    fun chick(x: Float, y: Float, k: Float) {
        // body, a darker wing, the head with its beak, eye and a little comb
        oval(Chick, x - 0.6f * k, y + 0.6f * k, 6.2f * k, 5.6f * k)
        oval(ChickDk, x - 1.6f * k, y + 1.4f * k, 3.4f * k, 2.4f * k)
        circle(Chick, x + 3.4f * k, y - 4.4f * k, 3.6f * k)
        if (k >= 0.6f) poly(Comb, x + 2.2f * k, y - 7.6f * k, x + 3.4f * k, y - 9.2f * k, x + 4.6f * k, y - 7.8f * k)
        poly(Beak, x + 6.6f * k, y - 5.2f * k, x + 9.2f * k, y - 4.1f * k, x + 6.6f * k, y - 3.2f * k)
        circle(Ink, x + 4.6f * k, y - 5.1f * k, 0.8f * k.coerceAtLeast(0.45f))
        if (k >= 0.6f) { circle(Snow, x + 4.85f * k, y - 5.4f * k, 0.28f * k); circle(Snow.copy(alpha = 0.35f), x - 2.6f * k, y - 2.2f * k, 1.6f * k)
            line(Beak, x - 1.8f * k, y + 5.8f * k, x - 2.4f * k, y + 7.4f * k, 0.9f * k); line(Beak, x + 1.2f * k, y + 5.8f * k, x + 1.6f * k, y + 7.4f * k, 0.9f * k) }
    }
    fun pan(x: Float, y: Float, k: Float, full: Boolean) {
        oval(RubyDk, x, y + 1.2f * k, 8f * k, 3.4f * k); oval(Ruby, x, y, 8f * k, 3.2f * k)
        oval(if (full) Feed else SlateDk, x, y - 0.2f * k, 6.2f * k, 2.2f * k)
        if (full) oval(Straw, x - 1.5f * k, y - 0.8f * k, 2.2f * k, 0.8f * k)
        rect(MetalDk, x - 0.8f * k, y - 6.5f * k, 1.6f * k, 4.5f * k, 0.4f * k); circle(Metal, x, y - 1.2f * k, 1.4f * k)
    }
    fun bowl(x: Float, y: Float, k: Float) {
        path(WoodDk) { moveTo(x - 8f * k, y - 2f * k); cubicTo(x - 8f * k, y + 4.5f * k, x + 8f * k, y + 4.5f * k, x + 8f * k, y - 2f * k); close() }
        oval(Wood, x, y - 2f * k, 8f * k, 2.4f * k); oval(Feed, x, y - 2.2f * k, 6.4f * k, 1.6f * k)
    }
    fun grain(x: Float, y: Float) { oval(Feed, x, y, 1.1f, 0.7f); oval(Straw, x - 0.3f, y - 0.2f, 0.4f, 0.25f) }
    fun sack(x: Float, y: Float, k: Float) {
        shadow(x, y + 8.4f * k, 8f * k, 1.8f * k)
        path(SackDk) { moveTo(x - 6.5f * k, y - 4f * k); cubicTo(x - 9f * k, y + 3f * k, x - 8f * k, y + 8f * k, x, y + 8f * k)
            cubicTo(x + 8f * k, y + 8f * k, x + 9f * k, y + 3f * k, x + 6.5f * k, y - 4f * k); close() }
        path(Sack) { moveTo(x - 6.5f * k, y - 4f * k); cubicTo(x - 9f * k, y + 3f * k, x - 7f * k, y + 7.6f * k, x - 0.5f * k, y + 7.6f * k)
            cubicTo(x + 4f * k, y + 7.6f * k, x + 5f * k, y + 3f * k, x + 4f * k, y - 4f * k); close() }
        path(Sack) { moveTo(x - 4.5f * k, y - 4.2f * k); lineTo(x - 3f * k, y - 8.5f * k); lineTo(x + 3f * k, y - 8.5f * k); lineTo(x + 4.5f * k, y - 4.2f * k); close() }
        line(Twine, x - 5f * k, y - 4.4f * k, x + 5f * k, y - 4.4f * k, 1.4f * k)
        rect(Leaf.copy(alpha = 0.85f), x - 5.4f * k, y - 0.2f * k, 10.8f * k, 3.2f * k, 0.6f * k)
        circle(Snow.copy(alpha = 0.3f), x - 3.6f * k, y - 1.8f * k, 1.2f * k)
    }
    fun thermo(x: Float, y: Float, k: Float) {
        rect(Snow, x - 2.1f * k, y, 4.2f * k, 13.5f * k, 2.1f * k); circle(Snow, x, y + 15.5f * k, 3.6f * k)
        rect(Ruby, x - 0.9f * k, y + 5f * k, 1.8f * k, 10f * k, 0.9f * k); circle(Ruby, x, y + 15.5f * k, 2.5f * k)
        for (t in 0..3) line(MetalDk, x + 2.1f * k, y + (2.2f + t * 2.6f) * k, x + 3.4f * k, y + (2.2f + t * 2.6f) * k, 0.7f * k)
        circle(Snow.copy(alpha = 0.6f), x - 0.9f * k, y + 14.6f * k, 0.8f * k)
    }
    fun dropPath(x: Float, y: Float, k: Float): Path = Path().apply {
        moveTo(P(x, y - 9f * k).x, P(x, y - 9f * k).y)
        cubicTo(P(x + 2f * k, y - 5f * k).x, P(x + 2f * k, y - 5f * k).y, P(x + 7f * k, y).x, P(x + 7f * k, y).y, P(x + 7f * k, y + 2.5f * k).x, P(x + 7f * k, y + 2.5f * k).y)
        cubicTo(P(x + 7f * k, y + 6.5f * k).x, P(x + 7f * k, y + 6.5f * k).y, P(x + 3.8f * k, y + 9.2f * k).x, P(x + 3.8f * k, y + 9.2f * k).y, P(x, y + 9.2f * k).x, P(x, y + 9.2f * k).y)
        cubicTo(P(x - 3.8f * k, y + 9.2f * k).x, P(x - 3.8f * k, y + 9.2f * k).y, P(x - 7f * k, y + 6.5f * k).x, P(x - 7f * k, y + 6.5f * k).y, P(x - 7f * k, y + 2.5f * k).x, P(x - 7f * k, y + 2.5f * k).y)
        cubicTo(P(x - 7f * k, y).x, P(x - 7f * k, y).y, P(x - 2f * k, y - 5f * k).x, P(x - 2f * k, y - 5f * k).y, P(x, y - 9f * k).x, P(x, y - 9f * k).y)
        close()
    }
    fun drop(x: Float, y: Float, k: Float, lit: Color, dark: Color) {
        ds.drawPath(dropPath(x, y, k), c(lit))
        ds.drawPath(dropPath(x + 1.6f * k, y + 0.8f * k, k * 0.78f), c(dark.copy(alpha = 0.55f)))
        circle(Snow.copy(alpha = 0.7f), x - 2.8f * k, y + 2.4f * k, 1.4f * k)
    }
    fun medal(x: Float, y: Float, r: Float, col: Color, t: String) { circle(Ink, x, y, r + 0.8f); circle(col, x, y, r); text(t, x, y + r * 0.42f, r * 1.35f, Ink) }
    fun bubble(x: Float, y: Float, col: Color, t: String) { circle(col, x, y, 4.6f); poly(col, x - 3f, y + 2.8f, x - 5.6f, y + 6.4f, x - 0.6f, y + 4.2f); text(t, x, y + 2.4f, 7f, Snow) }
    fun bars(lit: Color, dark: Color) {
        isoBox(3f, 20.5f, 3.2f, 2.2f, 4f, lit, dark, dark.copy(alpha = 0.8f))
        isoBox(9f, 20.5f, 3.2f, 2.2f, 8f, lit, dark, dark.copy(alpha = 0.8f))
        isoBox(15f, 20.5f, 3.2f, 2.2f, 12f, lit, dark, dark.copy(alpha = 0.8f))
    }
    fun arrowUp(x: Float, y: Float, col: Color) { poly(col, x, y, x + 3.6f, y + 4f, x + 1.4f, y + 4f, x + 1.4f, y + 7f, x - 1.4f, y + 7f, x - 1.4f, y + 4f, x - 3.6f, y + 4f) }
    fun arrowRight(x: Float, y: Float, col: Color) { poly(col, x + 8f, y, x + 4f, y - 3.6f, x + 4f, y - 1.4f, x, y - 1.4f, x, y + 1.4f, x + 4f, y + 1.4f, x + 4f, y + 3.6f) }
    fun foot(x: Float, y: Float, rot: Float) {
        val col = Snow.copy(alpha = 0.85f)
        fun r(dx: Float, dy: Float) = (x + dx * cos(rot) - dy * sin(rot)) to (y + dx * sin(rot) + dy * cos(rot))
        val heel = r(0f, 3f)
        listOf(r(0f, -4f), r(-3.2f, -2.6f), r(3.2f, -2.6f), r(0f, 5.4f)).forEach { t -> line(col, heel.first, heel.second, t.first, t.second, 1.5f) }
    }
    fun wave(x: Float, y: Float, col: Color) = path(null, col, 1.5f) { moveTo(x, y); cubicTo(x + 1.5f, y - 1.6f, x + 3f, y + 1.6f, x + 4.5f, y); cubicTo(x + 6f, y - 1.6f, x + 7.5f, y + 1.6f, x + 8f, y) }
    fun cloud(x: Float, y: Float, k: Float, col: Color, dark: Color) {
        oval(dark, x + 0.6f * k, y + 3.8f * k, 9f * k, 2.6f * k)
        circle(col, x - 4.5f * k, y + 1.5f * k, 4f * k); circle(col, x + 4.6f * k, y + 1.6f * k, 3.8f * k); circle(col, x, y - 1.6f * k, 5.4f * k)
        rect(col, x - 4.5f * k, y + 1.5f * k, 9.2f * k, 3.9f * k, 1f * k)
    }
    fun sun(x: Float, y: Float, k: Float) {
        for (i in 0 until 8) { val an = (i * PI / 4).toFloat(); line(Sun, x + 7f * k * cos(an), y + 7f * k * sin(an), x + 9.6f * k * cos(an), y + 9.6f * k * sin(an), 1.6f * k) }
        circle(Sun, x, y, 5.4f * k); circle(Gold, x + 0.8f * k, y + 0.8f * k, 4.4f * k); circle(Snow.copy(alpha = 0.45f), x - 1.8f * k, y - 1.8f * k, 1.4f * k)
    }
    fun heap(x: Float, y: Float, k: Float) {
        path(LitterDk) { moveTo(x - 10f * k, y); cubicTo(x - 8f * k, y - 9f * k, x + 8f * k, y - 9f * k, x + 10f * k, y); close() }
        path(Litter) { moveTo(x - 10f * k, y); cubicTo(x - 8f * k, y - 9f * k, x + 4f * k, y - 9.5f * k, x + 5f * k, y); close() }
        listOf(-5.5f to -3f, -2f to -5f, 2f to -4.2f, -6.5f to -1f, 4.5f to -1.5f).forEach { (dx, dy) -> line(Straw, x + dx * k, y + dy * k, x + (dx + 2.2f) * k, y + (dy - 0.8f) * k, 0.9f * k) }
    }
    fun blade(x: Float, y: Float, a: Float) {
        val r = 6.2f
        path(Metal) {
            moveTo(x, y)
            cubicTo(x + r * cos(a - 0.5f), y + r * sin(a - 0.5f), x + r * cos(a + 0.2f), y + r * sin(a + 0.2f), x + r * cos(a + 0.35f), y + r * sin(a + 0.35f))
            cubicTo(x + 3f * cos(a + 0.9f), y + 3f * sin(a + 0.9f), x + 1f * cos(a + 1f), y + 1f * sin(a + 1f), x, y)
            close()
        }
    }
}

/** A path built on the pen's 24-unit grid. */
private class GridPath(val pen: Pen) {
    val path = Path()
    fun moveTo(x: Float, y: Float) { val q = pen.P(x, y); path.moveTo(q.x, q.y) }
    fun lineTo(x: Float, y: Float) { val q = pen.P(x, y); path.lineTo(q.x, q.y) }
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        val a = pen.P(x1, y1); val b = pen.P(x2, y2); val c = pen.P(x3, y3); path.cubicTo(a.x, a.y, b.x, b.y, c.x, c.y)
    }
    fun arcTo(rect: androidx.compose.ui.geometry.Rect, start: Float, sweep: Float, force: Boolean) = path.arcTo(rect, start, sweep, force)
    fun close() = path.close()
    /** the drop's outline, for a stroke */
    fun dropPath(x: Float, y: Float, k: Float) { path.addPath(pen.dropPath(x, y, k)) }
}
