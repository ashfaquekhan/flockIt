package com.example.flock.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.flock.domain.HouseField
import com.example.flock.ui.Fmt
import kotlin.math.max

private val Cool = Color(0xFF6FA8DC)
private val GoodLow = Color(0xFF6CCFC4)
private val GoodHigh = Color(0xFFB9D46F)
private val Warm = Color(0xFFE5A15A)
private val Hot = Color(0xFFE5736B)

private fun mix(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

/**
 * The colour of a value on its layer's scale, continuous like a map app's: blue below the good band, teal → yellow-green
 * across it (so a small change inside it still shows), amber to red above it.
 */
fun layerColorOf(s: HouseField.Scale, v: Double): Color {
    fun t(a: Double, b: Double) = ((v - a) / kotlin.math.max(1e-6, b - a)).toFloat().coerceIn(0f, 1f)
    return when {
        v <= s.goodLo -> mix(Cool, GoodLow, t(s.lo, s.goodLo))
        v <= s.goodHi -> mix(GoodLow, GoodHigh, t(s.goodLo, s.goodHi))
        v <= (s.goodHi + s.hi) / 2 -> mix(GoodHigh, Warm, t(s.goodHi, (s.goodHi + s.hi) / 2))
        else -> mix(Warm, Hot, t((s.goodHi + s.hi) / 2, s.hi))
    }
}

/** The colour stops of a layer's legend, at their place on the scale. */
private fun legendStops(s: HouseField.Scale): Array<Pair<Float, Color>> {
    val span = kotlin.math.max(1e-6, s.hi - s.lo)
    fun at(v: Double) = ((v - s.lo) / span).toFloat().coerceIn(0f, 1f)
    return arrayOf(0f to Cool, at(s.goodLo) to GoodLow, at(s.goodHi) to GoodHigh, at((s.goodHi + s.hi) / 2) to Warm, 1f to Hot)
}

/**
 * The map's layer, like the layers of a map app: none, or one of the [HouseField] layers; Projected (today's model) or
 * Ideal (what it should be); seen from the top or from the side (the floor to the roof).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LayerBar(layer: HouseField.Layer?, ideal: Boolean, side: Boolean,
             onLayer: (HouseField.Layer?) -> Unit, onIdeal: (Boolean) -> Unit, onSide: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("layer_bar"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoButton("layers")
            Pill("Plain", layer == null, "layer_none") { onLayer(null) }
            HouseField.Layer.values().forEach { l -> Pill(l.label, layer == l, "layer_${l.name.lowercase()}") { onLayer(l) } }
        }
        // each chip as wide as its word; a second line when the text is large
        if (layer != null) androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("Projected", !ideal, "layer_projected") { onIdeal(false) }
            Pill("Ideal", ideal, "layer_ideal") { onIdeal(true) }
            Spacer(Modifier.width(10.dp))
            Pill("Top", !side, "layer_top") { onSide(false) }
            Pill("Side", side, "layer_side") { onSide(true) }
        }
    }
}

@Composable
private fun Pill(text: String, on: Boolean, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(text, modifier.clip(RoundedCornerShape(16.dp)).border(if (on) 2.dp else 1.dp, if (on) Color.White else Color.White.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
        .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 6.dp).testTag(tag),
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
        color = if (on) Color.White else Color.White.copy(alpha = 0.75f), maxLines = 1, softWrap = false,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

/**
 * The whole house with the chosen layer on it. Top: the floor from above, front (pads) on the left, the feeder (gold)
 * and drinker (blue) lines, the barricade. Side: the house cut along its length, the floor to the roof, the air in
 * see-through bands (temperature, or humidity for that layer) and the chosen floor layer as the strip at the bottom.
 */
@Composable
fun HouseMap(f: HouseField.In, layer: HouseField.Layer, ideal: Boolean, side: Boolean) {
    val air = layer == HouseField.Layer.AIR_T || layer == HouseField.Layer.RH
    val sc = HouseField.scale(layer, f)
    fun value(x: Double, y: Double, z: Double) = if (ideal) HouseField.ideal(layer, f, x, y, z) else HouseField.projected(layer, f, x, y, z)
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).testTag("house_map"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val hDp = if (side) 130.dp else (maxWidth * (f.widthFt / f.lengthFt).toFloat() * 2.2f).coerceIn(70.dp, 150.dp)
            Canvas(Modifier.fillMaxWidth().height(hDp)) {
                val w = size.width; val h = size.height
                if (!side) {
                    // the floor from above: 3 ft × 1 ft cells
                    val cx = 3.0; val cy = 1.0
                    val px = w / f.lengthFt.toFloat(); val py = h / f.widthFt.toFloat()
                    var x = 0.0
                    while (x < f.lengthFt) {
                        var y = 0.0
                        while (y < f.widthFt) {
                            val v = value(x + cx / 2, y + cy / 2, 0.12)
                            val c = if (x > f.barricadeFt && !air) Color.Black else mix(Color.Black, layerColorOf(sc, v), 0.85f)
                            drawRect(c, Offset((x * px).toFloat(), (y * py).toFloat()), Size((cx * px).toFloat() + 0.6f, (cy * py).toFloat() + 0.6f))
                            y += cy
                        }
                        x += cx
                    }
                    f.feederYs.forEach { y -> drawLine(Color(0xFFE2BC6A), Offset(0f, (y * py).toFloat()), Offset(w, (y * py).toFloat()), 1.2f) }
                    f.drinkerYs.forEach { y -> drawLine(Color(0xFF8FC1F0), Offset(0f, (y * py).toFloat()), Offset(w, (y * py).toFloat()), 1f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 3f))) }
                    if (f.barricadeFt < f.lengthFt - 0.5) drawLine(Color.White, Offset((f.barricadeFt * px).toFloat(), 0f), Offset((f.barricadeFt * px).toFloat(), h), 2.5f)
                } else {
                    // the house cut along its length: the air from the floor to the roof in see-through bands, the floor layer below
                    val floorH = h * 0.16f
                    val bands = 8
                    val px = w / f.lengthFt.toFloat()
                    val airLayer = if (layer == HouseField.Layer.RH) HouseField.Layer.RH else HouseField.Layer.AIR_T
                    val asc = HouseField.scale(airLayer, f)
                    for (b in 0 until bands) {
                        val z = (b + 0.5) / bands
                        var x = 0.0
                        while (x < f.lengthFt) {
                            val v = if (ideal) HouseField.ideal(airLayer, f, x + 3.0, f.widthFt / 2, z) else HouseField.projected(airLayer, f, x + 3.0, f.widthFt / 2, z)
                            val top = (h - floorH) * (1 - (b + 1).toFloat() / bands)
                            // see-through bands: the colour over black (no doubled seams), alternate bands a shade apart
                            drawRect(mix(Color.Black, layerColorOf(asc, v), 0.62f + 0.08f * (b % 2)), Offset((x * px).toFloat(), top),
                                Size((6.0 * px).toFloat() + 0.6f, (h - floorH) / bands + 0.6f))
                            x += 6.0
                        }
                        drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, (h - floorH) * (1 - (b + 1).toFloat() / bands)), Offset(w, (h - floorH) * (1 - (b + 1).toFloat() / bands)), 1f)
                    }
                    // the floor strip: the chosen layer along the house, the width averaged
                    var x = 0.0
                    while (x < f.lengthFt) {
                        val v = if (air) value(x + 1.5, f.widthFt / 2, 0.02) else (0 until 6).map { k -> value(x + 1.5, (k + 0.5) * f.widthFt / 6, 0.02) }.average()
                        val c = if (x > f.barricadeFt && !air) Color.Black else layerColorOf(sc, v)
                        drawRect(c, Offset((x * px).toFloat(), h - floorH), Size((3.0 * px).toFloat() + 0.6f, floorH))
                        x += 3.0
                    }
                    drawLine(Color.White.copy(alpha = 0.6f), Offset(0f, h - floorH), Offset(w, h - floorH), 1.2f)
                    // the readings at the front, the middle and the back, by the floor and under the roof
                    val paint = android.graphics.Paint().apply { isAntiAlias = true; textSize = 10.5f * density; color = android.graphics.Color.WHITE
                        typeface = com.example.ui.theme.AppFonts.mono; textAlign = android.graphics.Paint.Align.CENTER }
                    listOf(0.08, 0.5, 0.92).forEach { fx ->
                        val xf = f.lengthFt * fx
                        listOf(0.06 to (h - floorH) * 0.94f, 0.94 to (h - floorH) * 0.16f).forEach { (z, yPx) ->
                            val v = if (ideal) HouseField.ideal(airLayer, f, xf, f.widthFt / 2, z) else HouseField.projected(airLayer, f, xf, f.widthFt / 2, z)
                            drawContext.canvas.nativeCanvas.drawText(Fmt.n(v, 1), (xf * px).toFloat(), yPx, paint)
                        }
                    }
                }
                drawRect(Color.White.copy(alpha = 0.6f), Offset.Zero, size, style = Stroke(1.2f))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text("front · pads", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
            if (side) Text("floor → roof", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("back · fans", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
        }
        // the scale: low · good · high, and what the layer reads over the birds' floor
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            drawRect(Brush.horizontalGradient(*legendStops(sc)), Offset.Zero, size)
        }
        Row(Modifier.fillMaxWidth()) {
            Text(Fmt.n(sc.lo, 1), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
            Text("good ${Fmt.n(sc.goodLo, 1)}–${Fmt.n(sc.goodHi, 1)} ${layer.unit}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.weight(2f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(Fmt.n(sc.hi, 1), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
        }
        val (lo, mid, hi) = HouseField.summary(layer, f, ideal)
        val zs = if (air && !ideal) " · floor ${Fmt.n(value(f.barricadeFt / 2, f.widthFt / 2, 0.05), 1)}, roof ${Fmt.n(value(f.barricadeFt / 2, f.widthFt / 2, 0.95), 1)}" else ""
        Text((if (ideal) "Ideal: " else "Projected (a model — no sensors): ") + "${layer.label} ${Fmt.n(lo, 1)} to ${Fmt.n(hi, 1)} ${layer.unit}, mostly ${Fmt.n(mid, 1)}$zs",
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = com.example.ui.theme.NumberFont),
            color = kindColor(if (ideal) ValueKind.IDEAL else ValueKind.PREDICTED))
    }
}

/** The house's field for today from the Output's numbers: the air the model makes of the weather (or the ideal house). */
fun houseFieldOf(d: OutputData, hour: Int): HouseField.In {
    val h = d.envAt(hour)
    val ys = d.lineOrder.indices.map { (it + 0.5) * d.lineGapFt }
    val fans = h?.fans ?: (d.minLevel.avgCfm(d.fanCfm) / max(1.0, d.fanCfm))
    val outC = d.outsideHours.getOrNull(hour)?.second ?: d.e.tempIdeal
    return HouseField.In(
        lengthFt = d.farm.usableLengthFt, widthFt = d.farm.usableWidthFt, heightFt = d.farm.heightFt,
        barricadeFt = d.barricadeFtNow,
        feederYs = ys.filterIndexed { i, _ -> d.lineOrder[i] == 'F' }, drinkerYs = ys.filterIndexed { i, _ -> d.lineOrder[i] == 'D' },
        live = d.live, avgKg = d.bwNow / 1000.0, day = d.day,
        airC = h?.air?.tempC ?: d.e.tempIdeal, rhPct = h?.air?.rhPct ?: d.e.rhIdeal,
        idealC = d.e.tempIdeal, idealRh = d.e.rhIdeal,
        fanCfm = fans * d.fanCfm, airSpeedMs = (h?.air?.airFpm ?: (fans * d.fanCfm / max(1.0, d.plan.crossFt2))) * 0.00508,
        heatLoadC = h?.r?.heatLoadC ?: 0.0, outRh = d.outsideHours.getOrNull(hour)?.third ?: 70.0,
        body = d.bodyTemp, heatersOn = outC < d.e.tempIdeal - 4 && d.day < 21
    )
}
