package com.example.flock.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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

/** A value with its coloured tag inside a thin white outline — colour only on the tag and the number. */
@Composable
fun ValueChip(x: V, modifier: Modifier = Modifier, big: Boolean = false) {
    val k = if (x.text == "—") ValueKind.NEUTRAL else x.kind
    val col = kindColor(k)
    Column(modifier.border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 4.dp)) {
        Text(x.tag ?: kindTag(x.kind).ifEmpty { "—" }, maxLines = 1, softWrap = false,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.sp), color = col.copy(alpha = 0.9f))
        Text(x.text, maxLines = 1, softWrap = false,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                fontSize = if (big) 17.sp else 15.sp, letterSpacing = (-0.3).sp), color = col)
    }
}

/** Chips in rows that wrap to fit the screen (at least ~74 dp per chip). */
@Composable
fun ValueRow(vals: List<V>, lead: String? = null) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val leadW = if (lead != null) 62.dp else 0.dp
        val per = ((maxWidth - leadW) / 80.dp).toInt().coerceIn(2, 4).coerceAtMost(max(1, vals.size))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            vals.chunked(per).forEachIndexed { i, row ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (lead != null) Text(if (i == 0) lead else "", modifier = Modifier.width(leadW), maxLines = 2,
                        style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
                    row.forEach { ValueChip(it, Modifier.weight(1f)) }
                    repeat(per - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * One parameter: its name and unit, then one line per scope (e.g. "per bird", "whole farm") with
 * the values side by side — micro and macro together, never repeated elsewhere.
 */
@Composable
fun Param(label: String, unit: String, rows: List<Pair<String?, List<V>>>, note: String? = null, strong: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (strong) FontWeight.Black else FontWeight.Bold))
            if (unit.isNotEmpty()) Text("  $unit", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
        }
        rows.forEach { (scope, vals) -> ValueRow(vals, scope) }
    }
}

/** Single-scope shorthand. */
@Composable
fun Param(label: String, unit: String, vararg values: V, note: String? = null, strong: Boolean = false) =
    Param(label, unit, listOf(null to values.toList()), note, strong)

/** Explanatory text is kept out of the Output (numbers and pictures only). */
@Composable
@Suppress("UNUSED_PARAMETER")
fun Note(text: String) {}

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
    val idealTxt = when {
        idealBand != null && (idealBand.first != min || idealBand.second != max) -> "${Fmt.n(idealBand.first, dec)}–${Fmt.n(idealBand.second, dec)}"
        ideal != null -> Fmt.n(ideal, dec)
        else -> null
    }
    val refs = buildList {
        add("com NA" to kindColor(ValueKind.COMMERCIAL))
        add("ideal " + (idealTxt ?: "NA") to kindColor(ValueKind.IDEAL))
        if (min != null) add("min " + Fmt.n(min, dec) to kindColor(ValueKind.MIN))
        if (max != null) add("max " + Fmt.n(max, dec) to kindColor(ValueKind.MAX))
    }
    // no reading: the ideal is the headline value
    val main: V = when {
        present != null -> v(present, dec, presentKind)
        idealTxt != null -> vt(idealTxt, ValueKind.IDEAL)
        idealBand != null -> vt("${Fmt.n(idealBand.first, dec)}–${Fmt.n(idealBand.second, dec)}", ValueKind.IDEAL)
        else -> vt("—", ValueKind.NEUTRAL)
    }
    CompactLine(label, unit, null, main, rangeTrend(present, min, max), refs)
    RangeBar(min, ideal, max, present, presentKind, idealBand, slim = true)
}

@Composable
fun RangeBar(min: Double?, ideal: Double?, max: Double?, present: Double?, presentKind: ValueKind, idealBand: Pair<Double, Double>? = null, slim: Boolean = false) {
    val pts = listOfNotNull(min, ideal, max, present, idealBand?.first, idealBand?.second)
    if (pts.isEmpty()) return
    val lo0 = pts.min(); val hi0 = pts.max()
    val span = max(hi0 - lo0, max(abs(hi0) * 0.1, 1.0))
    val lo = lo0 - span * 0.25; val hi = hi0 + span * 0.25
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().height(if (slim) 12.dp else 18.dp)) {
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
        } ?: (ideal ?: idealBand?.let { (it.first + it.second) / 2 })?.let {
            // no reading: the dot sits on the ideal, drawn as a ring
            drawCircle(kindColor(ValueKind.IDEAL), h / 2.1f, Offset(x(it), h / 2))
            drawCircle(Color.Black, h / 4f, Offset(x(it), h / 2))
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
    return "$txt vs com" to col
}

@Composable
fun KpiCard(title: String, kpis: List<Kpi>, info: String? = null) {
    OutputCard(title = title, info = info) {
        kpis.forEach { KpiLine(it) }
    }
}

/**
 * A KPI on one line: name (and the whole-flock figure), the trend marker, the value, then commercial
 * and ideal in small type, with the comparison bar under it.
 */
@Composable
fun KpiLine(k: Kpi) {
    val t = if (k.settling) null else trendOf(k.actual, k.company ?: k.ideal, k.better)
    fun f(x: Double, dec: Int) = if (dec == 0) Fmt.i(x.roundToInt()) else Fmt.n(x, dec)
    val refs = buildList {
        add("com " + (k.company?.let { f(it, k.decimals) } ?: "NA") to kindColor(ValueKind.COMMERCIAL))
        add(k.idealLabel.lowercase() + " " + (k.ideal?.let { f(it, k.decimals) } ?: "NA") to kindColor(ValueKind.IDEAL))
    }
    val total = k.totalFactor?.let { fac -> k.actual?.let { "flock " + f(it * fac, k.totalDec) + " " + k.totalUnit } }
    val value = if (k.decimals == 0) vi(k.actual?.roundToInt(), k.actualKind) else v(k.actual, k.decimals, k.actualKind)
    CompactLine(k.label, k.unit, total, value, t, refs)
    SlimCompareBar(k)
}

/** Name and unit on the left (with an optional second line), trend marker and value on the right, references under the value. */
@Composable
fun CompactLine(label: String, unit: String, sub: String?, value: V, trend: TrendMark?, refs: List<Pair<String, Color>>) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), maxLines = 2)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = Color.White.copy(alpha = 0.6f), maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (trend != null) { TrendIcon(trend); Spacer(Modifier.width(5.dp)) }
                Text(value.text, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                    color = kindColor(if (value.text == "—") ValueKind.NEUTRAL else value.kind), maxLines = 1, softWrap = false)
                if (unit.isNotEmpty()) Text(" $unit", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f), maxLines = 1, softWrap = false)
            }
            if (refs.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                refs.forEach { (t, c) -> Text(t, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = c, maxLines = 1, softWrap = false) }
            }
        }
    }
}

/** The comparison bar, full width and slim: commercial ±5 % band and tick, ideal tick, the flock's dot. */
@Composable
fun SlimCompareBar(k: Kpi) {
    val c = k.company ?: k.ideal ?: return
    val dotC = kindColor(k.actualKind)
    Canvas(Modifier.fillMaxWidth().height(16.dp)) {
        val pad = 7.dp.toPx()
        val pts = listOfNotNull(c * 0.95, c * 1.05, k.ideal?.times(0.97), k.ideal?.times(1.03), k.actual)
        val span = max(pts.max() - pts.min(), abs(c) * 0.1)
        val lo = pts.min() - span * 0.08; val hi = pts.max() + span * 0.08
        val w = size.width - pad * 2
        fun x(v: Double) = pad + ((v.coerceIn(lo, hi) - lo) / (hi - lo) * w).toFloat()
        val mid = size.height / 2
        drawLine(Color.White.copy(alpha = 0.3f), Offset(pad, mid), Offset(pad + w, mid), strokeWidth = 2f)
        k.company?.let { co ->
            drawRect(kindColor(ValueKind.COMMERCIAL).copy(alpha = 0.32f), Offset(x(co * 0.95), mid + 1.dp.toPx()), Size(x(co * 1.05) - x(co * 0.95), 5.5.dp.toPx()))
            drawLine(kindColor(ValueKind.COMMERCIAL), Offset(x(co), mid), Offset(x(co), mid + 7.dp.toPx()), strokeWidth = 2.dp.toPx())
        }
        k.ideal?.let { id ->
            drawRect(kindColor(ValueKind.IDEAL).copy(alpha = 0.32f), Offset(x(id * 0.97), mid - 6.5.dp.toPx()), Size(x(id * 1.03) - x(id * 0.97), 5.5.dp.toPx()))
            drawLine(kindColor(ValueKind.IDEAL), Offset(x(id), mid - 7.dp.toPx()), Offset(x(id), mid), strokeWidth = 2.dp.toPx())
        }
        k.actual?.let { drawCircle(dotC, 5.dp.toPx(), Offset(x(it), mid)); drawCircle(Color.Black, 1.8.dp.toPx(), Offset(x(it), mid)) }
    }
}

// =================================== trend markers ===================================

enum class Grade { GOOD, AVERAGE, BAD }
/** [dir] 1 above the reference, −1 below, 0 on par; [grade] says whether that is good. */
data class TrendMark(val dir: Int, val grade: Grade)

val TrendGood = Color(0xFF46B98C)
val TrendAverage = Color(0xFFF0A23A)
val TrendBad = Color(0xFFE5534B)
fun gradeColor(g: Grade) = when (g) { Grade.GOOD -> TrendGood; Grade.AVERAGE -> TrendAverage; Grade.BAD -> TrendBad }

/**
 * Against a standard: within ±[tolPct] % is on par (an orange dash where higher or lower is better, a
 * green one where being on target is the aim). Beyond it the arrow shows the direction and its colour
 * whether that is good: green good, orange a little off, red well off (3 × the tolerance).
 */
fun trendOf(actual: Double?, ref: Double?, better: Better, tolPct: Double = 3.0): TrendMark? {
    if (actual == null || ref == null || ref == 0.0) return null
    val d = (actual - ref) / abs(ref) * 100
    val dir = if (abs(d) < tolPct) 0 else if (d > 0) 1 else -1
    val grade = when (better) {
        Better.HIGHER -> when { d >= tolPct -> Grade.GOOD; d > -3 * tolPct -> Grade.AVERAGE; else -> Grade.BAD }
        Better.LOWER -> when { d <= -tolPct -> Grade.GOOD; d < 3 * tolPct -> Grade.AVERAGE; else -> Grade.BAD }
        Better.CLOSER -> when { abs(d) < tolPct -> Grade.GOOD; abs(d) < 3 * tolPct -> Grade.AVERAGE; else -> Grade.BAD }
    }
    return TrendMark(dir, grade)
}

/** A reading against its safe range: inside is good (green dash); just outside orange, well outside red. */
fun rangeTrend(actual: Double?, min: Double?, max: Double?): TrendMark? {
    if (actual == null || (min == null && max == null)) return null
    val span = if (min != null && max != null) max - min else abs(min ?: max!!) * 0.2
    return when {
        max != null && actual > max -> TrendMark(1, if (actual - max <= span * 0.15) Grade.AVERAGE else Grade.BAD)
        min != null && actual < min -> TrendMark(-1, if (min - actual <= span * 0.15) Grade.AVERAGE else Grade.BAD)
        else -> TrendMark(0, Grade.GOOD)
    }
}

/** ▲ / ▼ / – in the grade's colour. */
@Composable
fun TrendIcon(t: TrendMark, size: androidx.compose.ui.unit.Dp = 12.dp) {
    val c = gradeColor(t.grade)
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        when (t.dir) {
            1 -> drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w / 2, h * 0.12f); lineTo(w * 0.95f, h * 0.88f); lineTo(w * 0.05f, h * 0.88f); close() }, c)
            -1 -> drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.05f, h * 0.12f); lineTo(w * 0.95f, h * 0.12f); lineTo(w / 2, h * 0.88f); close() }, c)
            else -> {
                val pc = if (t.grade == Grade.GOOD) c else Color.White.copy(alpha = 0.6f)
                drawLine(pc, Offset(w * 0.15f, h * 0.36f), Offset(w * 0.85f, h * 0.36f), strokeWidth = h * 0.14f, cap = StrokeCap.Round)
                drawLine(pc, Offset(w * 0.15f, h * 0.64f), Offset(w * 0.85f, h * 0.64f), strokeWidth = h * 0.14f, cap = StrokeCap.Round)
            }
        }
    }
}

// =================================== tabs and tables ===================================

/** Equal-width tabs with a white outline; the chosen one is outlined brighter and bold. */
@Composable
fun OutputTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                .border(if (on) 2.dp else 1.dp, if (on) Color.White else Color.White.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .background(if (on) Color.White.copy(alpha = 0.10f) else Color.Transparent)
                .clickable { onSelect(i) }
                .padding(vertical = 10.dp, horizontal = 4.dp)
                .testTagCompat("tab_out_$i"), contentAlignment = Alignment.Center) {
                Text(l, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
                    color = Color.White.copy(alpha = if (on) 1f else 0.7f), maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

private fun Modifier.testTagCompat(tag: String) = this.then(androidx.compose.ui.Modifier.testTag(tag))

/** One row of the house / line / pan table (null = not shown for that level). */
data class MMRow(val label: String, val house: V?, val line: V?, val pan: V?)

/** Whole house (macro) next to one line and one pan (micro), same numbers side by side. */
@Composable
fun MacroMicroTable(rows: List<MMRow>, heads: List<String> = listOf("House", "Line", "Pan")) {
    val mono = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
    val grey = Color.White.copy(alpha = 0.6f)
    val wts = listOf(1.3f, 1.1f, 0.85f)
    Column(Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1.05f))
            heads.forEachIndexed { i, h -> Text(h, Modifier.weight(wts[i]).padding(start = 4.dp), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = grey, textAlign = androidx.compose.ui.text.style.TextAlign.End) }
        }
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(r.label, Modifier.weight(1.05f), style = MaterialTheme.typography.labelMedium, color = grey, maxLines = 2)
                listOf(r.house, r.line, r.pan).forEachIndexed { i, c ->
                    Text(c?.text ?: "", Modifier.weight(wts[i]).padding(start = 4.dp), style = mono, color = c?.let { kindColor(if (it.text == "—") ValueKind.NEUTRAL else it.kind) } ?: grey,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun KpiRow(k: Kpi) {
    val (stTxt, stCol) = status(k)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(k.label + if (k.unit.isNotEmpty()) "  " + k.unit else "", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            Text(stTxt, modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = stCol), maxLines = 1)
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
        rows.forEach { (scope, vals) -> ValueRow(vals, scope ?: "") }
        CompareBar(k)
    }
}

/** One track: commercial ±5 % band, commercial (violet) and ideal (blue) ticks, the flock's dot. */
@Composable
private fun CompareBar(k: Kpi) {
    val c = k.company ?: k.ideal ?: return
    val dotC = kindColor(k.actualKind)
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(62.dp + 6.dp))
        Canvas(Modifier.weight(1f).height(22.dp)) {
            val pad = 9.dp.toPx()
            val pts = listOfNotNull(c * 0.95, c * 1.05, k.ideal, k.actual)
            val span = max(pts.max() - pts.min(), abs(c) * 0.1)
            val lo = pts.min() - span * 0.08; val hi = pts.max() + span * 0.08
            val w = size.width - pad * 2
            fun x(v: Double) = pad + ((v.coerceIn(lo, hi) - lo) / (hi - lo) * w).toFloat()
            val mid = size.height / 2
            drawLine(Color.White.copy(alpha = 0.25f), Offset(pad, mid), Offset(pad + w, mid), strokeWidth = 2f)
            k.company?.let { co ->
                drawRect(kindColor(ValueKind.COMMERCIAL).copy(alpha = 0.28f), Offset(x(co * 0.95), mid - 5.dp.toPx()), Size(x(co * 1.05) - x(co * 0.95), 10.dp.toPx()))
                drawLine(kindColor(ValueKind.COMMERCIAL), Offset(x(co), mid - 8.dp.toPx()), Offset(x(co), mid + 8.dp.toPx()), strokeWidth = 2.5.dp.toPx())
            }
            k.ideal?.let { id -> drawLine(kindColor(ValueKind.IDEAL), Offset(x(id), mid - 8.dp.toPx()), Offset(x(id), mid + 8.dp.toPx()), strokeWidth = 2.5.dp.toPx()) }
            k.actual?.let { drawCircle(dotC, 6.dp.toPx(), Offset(x(it), mid)); drawCircle(Color.Black, 2.dp.toPx(), Offset(x(it), mid)) }
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
            androidx.compose.material3.OutlinedIconButton(onClick = { onChange(snap(value - step)) }, modifier = Modifier.size(34.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))) { Text("−", fontWeight = FontWeight.Black) }
            androidx.compose.material3.OutlinedIconButton(onClick = { onChange(snap(value + step)) }, modifier = Modifier.size(34.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))) { Text("+", fontWeight = FontWeight.Black) }
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
                Text(t, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}
