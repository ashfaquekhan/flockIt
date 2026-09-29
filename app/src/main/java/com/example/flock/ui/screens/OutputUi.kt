package com.example.flock.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.ui.Fmt
import com.example.ui.theme.StatusCrit
import com.example.ui.theme.StatusWarn
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// =====================================================================================
// One visual language for every Output topic. Every number sits in a ValueChip whose colour
// and tag say what it is: Present · Projected · Ideal · Commercial · Min · Max.
// =====================================================================================

/** A value and what it is. [tag] overrides the default tag word (e.g. "Ceiling", "Yours"). */
data class V(val text: String, val kind: ValueKind, val tag: String? = null)

fun v(value: Double?, dec: Int, kind: ValueKind, tag: String? = null) = V(Fmt.n(value, dec), if (value == null) ValueKind.NEUTRAL else kind, tag)
fun vt(text: String, kind: ValueKind, tag: String? = null) = V(text, kind, tag)
fun vi(value: Int?, kind: ValueKind, tag: String? = null) = V(Fmt.i(value), if (value == null) ValueKind.NEUTRAL else kind, tag)

/** A value with its coloured tag, on a tinted card with a coloured edge. */
@Composable
fun ValueChip(x: V, modifier: Modifier = Modifier, big: Boolean = false) {
    val k = if (x.text == "—") ValueKind.NEUTRAL else x.kind
    val col = kindColor(k)
    Row(modifier.height(IntrinsicSize.Min).clip(RoundedCornerShape(8.dp)).background(kindWash(k))) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(col))
        Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            Text(x.tag ?: kindTag(x.kind).ifEmpty { "—" }, maxLines = 1,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp), color = col.copy(alpha = 0.9f))
            Text(x.text, maxLines = 2,
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    fontSize = if (big) 16.sp else 14.sp, letterSpacing = (-0.3).sp), color = col)
        }
    }
}

/**
 * One parameter: its name and unit, then one line per scope (e.g. "per bird", "whole farm") with
 * the values side by side — micro and macro together, never repeated elsewhere.
 */
@Composable
fun Param(label: String, unit: String, rows: List<Pair<String?, List<V>>>, note: String? = null, strong: Boolean = false) {
    val cols = rows.maxOfOrNull { it.second.size } ?: 0
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (strong) FontWeight.Black else FontWeight.Bold))
            if (unit.isNotEmpty()) Text("  $unit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        rows.forEach { (scope, vals) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (scope != null) Text(scope, modifier = Modifier.width(58.dp), maxLines = 3,
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                vals.forEach { ValueChip(it, Modifier.weight(1f)) }
                repeat(cols - vals.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Single-scope shorthand. */
@Composable
fun Param(label: String, unit: String, vararg values: V, note: String? = null, strong: Boolean = false) =
    Param(label, unit, listOf(null to values.toList()), note, strong)

@Composable
fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun SubHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp))
}

// =================================== range bar ===================================

/**
 * A parameter with a safe range: Min (cyan) and Max (rose) ticks, the Ideal (blue) point or band,
 * and the Present / Projected reading as a dot. Chips above carry the exact numbers.
 */
@Composable
fun RangeParam(
    label: String, unit: String, min: Double?, ideal: Double?, max: Double?, present: Double?,
    dec: Int = 1, presentKind: ValueKind = ValueKind.PRESENT, idealBand: Pair<Double, Double>? = null, note: String? = null
) {
    val vals = buildList {
        if (present != null) add(v(present, dec, presentKind))
        if (min != null) add(v(min, dec, ValueKind.MIN))
        when {
            idealBand != null && (idealBand.first != min || idealBand.second != max) ->
                add(vt("${Fmt.n(idealBand.first, dec)}–${Fmt.n(idealBand.second, dec)}", ValueKind.IDEAL))
            ideal != null -> add(v(ideal, dec, ValueKind.IDEAL))
        }
        if (max != null) add(v(max, dec, ValueKind.MAX))
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            if (unit.isNotEmpty()) Text("  $unit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { vals.forEach { ValueChip(it, Modifier.weight(1f)) } }
        RangeBar(min, ideal, max, present, presentKind, idealBand)
        if (note != null) Note(note)
    }
}

@Composable
fun RangeBar(min: Double?, ideal: Double?, max: Double?, present: Double?, presentKind: ValueKind, idealBand: Pair<Double, Double>? = null) {
    val pts = listOfNotNull(min, ideal, max, present, idealBand?.first, idealBand?.second)
    if (pts.isEmpty()) return
    val lo0 = pts.min(); val hi0 = pts.max()
    val span = max(hi0 - lo0, max(abs(hi0) * 0.1, 1.0))
    val lo = lo0 - span * 0.25; val hi = hi0 + span * 0.25
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().height(18.dp)) {
        fun x(v: Double) = ((v.coerceIn(lo, hi) - lo) / (hi - lo) * size.width).toFloat()
        val h = size.height
        drawRoundRect(track, size = Size(size.width, h), cornerRadius = CornerRadius(9f, 9f))
        // out-of-range washes
        if (min != null) drawRect(kindColor(ValueKind.MIN).copy(alpha = 0.22f), Offset(0f, 0f), Size(x(min), h))
        if (max != null) drawRect(kindColor(ValueKind.MAX).copy(alpha = 0.22f), Offset(x(max), 0f), Size(size.width - x(max), h))
        // acceptable band between the limits
        val a = min ?: lo; val b = max ?: hi
        drawRect(kindColor(ValueKind.IDEAL).copy(alpha = 0.16f), Offset(x(a), 0f), Size(x(b) - x(a), h))
        idealBand?.let { (i0, i1) -> drawRect(kindColor(ValueKind.IDEAL).copy(alpha = 0.45f), Offset(x(i0), h * 0.2f), Size(x(i1) - x(i0), h * 0.6f)) }
        min?.let { drawLine(kindColor(ValueKind.MIN), Offset(x(it), 0f), Offset(x(it), h), strokeWidth = 5f) }
        max?.let { drawLine(kindColor(ValueKind.MAX), Offset(x(it), 0f), Offset(x(it), h), strokeWidth = 5f) }
        ideal?.let { drawLine(kindColor(ValueKind.IDEAL), Offset(x(it), 0f), Offset(x(it), h), strokeWidth = 5f) }
        present?.let {
            val out = (min != null && it < min) || (max != null && it > max)
            drawCircle(if (out) StatusCrit else kindColor(presentKind), h / 2.1f, Offset(x(it), h / 2))
            drawCircle(Color.White, h / 5.5f, Offset(x(it), h / 2))
        }
    }
}

// =================================== comparison (KPI) ===================================

enum class Better { HIGHER, LOWER, CLOSER }

/**
 * A KPI: the flock's value against Commercial and Ideal, per bird and (optionally) for the whole
 * flock. [totalFactor] turns a per-bird value into the flock total.
 */
data class Kpi(
    val label: String, val unit: String, val actual: Double?, val actualKind: ValueKind,
    val company: Double?, val ideal: Double?, val better: Better, val decimals: Int,
    val settling: Boolean = false, val idealLabel: String = "Ideal", val points: Boolean = false,
    val totalFactor: Double? = null, val totalUnit: String = "", val totalDec: Int = 1, val scope: String = "per bird"
)

private val OkGreen = Color(0xFF46B98C)

private fun status(k: Kpi): Pair<String, Color> {
    val grey = Color(0xFF9AA0A6)
    val a = k.actual ?: return "not logged" to grey
    val c = k.company ?: return "—" to grey
    if (k.settling) return "settling" to grey
    val pct = if (c != 0.0) (a - c) / c * 100 else 0.0
    val txt = if (k.points) Fmt.signed(a - c, 2) + " pt" else Fmt.signed(pct, 1) + "%"
    val col = when (k.better) {
        Better.HIGHER -> if (pct >= -5) OkGreen else if (pct >= -10) StatusWarn else StatusCrit
        Better.LOWER -> if (pct <= 5) OkGreen else if (pct <= 10) StatusWarn else StatusCrit
        Better.CLOSER -> if (abs(pct) <= 10) OkGreen else if (abs(pct) <= 20) StatusWarn else StatusCrit
    }
    return "$txt vs commercial" to col
}

@Composable
fun KpiCard(title: String, kpis: List<Kpi>) {
    OutputCard(title = title) {
        kpis.forEach { KpiRow(it) }
        Note("Bars: violet band = commercial ±5.0% · blue band = ideal ±5.0% · dot = your flock.")
    }
}

@Composable
private fun KpiRow(k: Kpi) {
    val (stTxt, stCol) = status(k)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(k.label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            if (k.unit.isNotEmpty()) Text("  " + k.unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Surface(color = stCol.copy(alpha = 0.18f), shape = RoundedCornerShape(8.dp)) {
                Text(stTxt, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = stCol))
            }
        }
        val micro = listOf(v(k.actual, k.decimals, k.actualKind), v(k.company, k.decimals, ValueKind.COMMERCIAL), v(k.ideal, k.decimals, ValueKind.IDEAL, k.idealLabel))
        val rows = buildList {
            add((if (k.totalFactor != null) k.scope else null) to micro)
            k.totalFactor?.let { f ->
                add("whole flock\n${k.totalUnit}" to listOf(
                    v(k.actual?.times(f), k.totalDec, k.actualKind), v(k.company?.times(f), k.totalDec, ValueKind.COMMERCIAL),
                    v(k.ideal?.times(f), k.totalDec, ValueKind.IDEAL, k.idealLabel)))
            }
        }
        rows.forEach { (scope, vals) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (scope != null) Text(scope, modifier = Modifier.width(58.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                vals.forEachIndexed { i, x -> ValueChip(x, Modifier.weight(1f), big = i == 0 && scope == rows.first().first) }
            }
        }
        CompareBar(k)
    }
}

/** ±15 % scale around Commercial: commercial ±5 % band (upper lane), ideal ±5 % band (lower lane), flock dot. */
@Composable
private fun CompareBar(k: Kpi) {
    val c = k.company ?: k.ideal ?: return
    val track = MaterialTheme.colorScheme.surfaceVariant
    val dotC = kindColor(k.actualKind)
    Canvas(Modifier.fillMaxWidth().height(20.dp)) {
        val span = max(abs(c) * 0.15, 0.05)
        val lo = min(c - span, (k.ideal ?: c) - span * 0.4); val hi = max(c + span, (k.ideal ?: c) + span * 0.4)
        fun x(v: Double) = ((v.coerceIn(lo, hi) - lo) / (hi - lo) * size.width).toFloat()
        val h = size.height; val mid = h / 2
        drawRoundRect(track, size = Size(size.width, h), cornerRadius = CornerRadius(10f, 10f))
        k.company?.let { co ->
            drawRect(kindColor(ValueKind.COMMERCIAL).copy(alpha = 0.35f), Offset(x(co * 0.95), 0f), Size(x(co * 1.05) - x(co * 0.95), mid))
            drawLine(kindColor(ValueKind.COMMERCIAL), Offset(x(co), 0f), Offset(x(co), mid), strokeWidth = 5f)
        }
        k.ideal?.let { id ->
            drawRect(kindColor(ValueKind.IDEAL).copy(alpha = 0.35f), Offset(x(id * 0.95), mid), Size(x(id * 1.05) - x(id * 0.95), mid))
            drawLine(kindColor(ValueKind.IDEAL), Offset(x(id), mid), Offset(x(id), h), strokeWidth = 5f)
        }
        k.actual?.let {
            drawCircle(dotC, h / 2.4f, Offset(x(it), mid))
            if (it < lo || it > hi) drawCircle(Color.White, h / 6f, Offset(x(it), mid))
        }
    }
}

// =================================== rotary knob ===================================

/**
 * A rotary dial: drag around it (or tap − / +) to set a value between [lo] and [hi].
 * 270° sweep, value in the middle.
 */
@Composable
fun Knob(label: String, unit: String, value: Double, lo: Double, hi: Double, step: Double, color: Color, onChange: (Double) -> Unit, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    fun snap(x: Double) = ((x / step).roundToInt() * step).coerceIn(lo, hi)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = color)
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.size(112.dp)
                    .pointerInput(lo, hi) {
                        fun set(o: Offset) {
                            val cx = size.width / 2f; val cy = size.height / 2f
                            var a = Math.toDegrees(atan2((o.y - cy).toDouble(), (o.x - cx).toDouble()))
                            // 0° at 135° (bottom-left), sweep 270° clockwise
                            var t = a - 135.0
                            while (t < 0) t += 360.0
                            if (t > 270.0) t = if (t > 315.0) 0.0 else 270.0
                            onChange(snap(lo + (hi - lo) * t / 270.0))
                        }
                        detectDragGestures(onDragStart = { set(it) }) { ch, _ -> set(ch.position) }
                    }
                    .pointerInput(lo, hi) {
                        detectTapGestures { o ->
                            val cx = size.width / 2f; val cy = size.height / 2f
                            var t = Math.toDegrees(atan2((o.y - cy).toDouble(), (o.x - cx).toDouble())) - 135.0
                            while (t < 0) t += 360.0
                            if (t <= 270.0) onChange(snap(lo + (hi - lo) * t / 270.0))
                        }
                    }
            ) {
                val r = size.minDimension / 2f - 10f
                val tl = Offset(center.x - r, center.y - r)
                drawArc(track, 135f, 270f, false, tl, Size(r * 2, r * 2), style = Stroke(width = 14f, cap = StrokeCap.Round))
                val frac = ((value - lo) / (hi - lo)).coerceIn(0.0, 1.0).toFloat()
                drawArc(color, 135f, 270f * frac, false, tl, Size(r * 2, r * 2), style = Stroke(width = 14f, cap = StrokeCap.Round))
                val ang = (135f + 270f * frac) * PI.toFloat() / 180f
                val knob = Offset(center.x + r * cos(ang), center.y + r * sin(ang))
                drawCircle(Color.White, 11f, knob); drawCircle(color, 7f, knob)
                // tick marks
                for (i in 0..10) {
                    val a2 = (135f + 27f * i) * PI.toFloat() / 180f
                    drawLine(ink.copy(alpha = 0.25f), Offset(center.x + (r - 16f) * cos(a2), center.y + (r - 16f) * sin(a2)),
                        Offset(center.x + (r - 24f) * cos(a2), center.y + (r - 24f) * sin(a2)), strokeWidth = 2f)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Fmt.n(value, 1), style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black), color = color)
                Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { onChange(snap(value - step)) }, modifier = Modifier.size(34.dp)) { Text("−", fontWeight = FontWeight.Black) }
            FilledTonalIconButton(onClick = { onChange(snap(value + step)) }, modifier = Modifier.size(34.dp)) { Text("+", fontWeight = FontWeight.Black) }
        }
    }
}

/** Simple horizontal fill bar with a label, for capacity-type values (godown, line reach). */
@Composable
fun FillBar(frac: Double, color: Color, modifier: Modifier = Modifier, marker: Double? = null) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.fillMaxWidth().height(16.dp)) {
        drawRoundRect(track, size = size, cornerRadius = CornerRadius(8f, 8f))
        val f = frac.coerceIn(0.0, 1.0).toFloat()
        drawRoundRect(if (frac > 1.0) StatusCrit else color, size = Size(size.width * f, size.height), cornerRadius = CornerRadius(8f, 8f))
        marker?.let { m -> val mx = (m.coerceIn(0.0, 1.0) * size.width).toFloat(); drawLine(Color.White, Offset(mx, 0f), Offset(mx, size.height), strokeWidth = 3f) }
    }
}

/** Small legend line under a picture. */
@Composable
fun KeyLine(vararg items: Pair<Color, String>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { (c, t) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(c, CircleShape))
                Spacer(Modifier.width(4.dp))
                Text(t, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = c)
            }
        }
    }
}
