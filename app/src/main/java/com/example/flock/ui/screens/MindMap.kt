package com.example.flock.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.flock.data.FlockEventEntity
import com.example.flock.graph.EventKind
import com.example.flock.graph.FlockEvents
import com.example.flock.graph.FlockGraph
import com.example.flock.graph.FlockGraph.EdgeKind
import com.example.flock.graph.FlockGraph.Group
import com.example.flock.graph.FlockGraph.Source
import com.example.flock.ui.Fmt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The flock as a map you walk through (v59), in three dimensions and one thing at a time — so it stays simple however
 * much is wired together:
 *  - **the flock**: its groups stand round it on a ring;
 *  - **a group**: its numbers, parts or events on rings round it;
 *  - **a number**: its wiring — what it comes from on the left, what it moves on the right, the events that set it above,
 *    the parts it hangs with below; stronger links nearer and thicker, green raises, red lowers, dashed acts later.
 * Every point is a button: tap it and it becomes the middle. A finger sideways turns the scene; the trail above goes back.
 * Under it the point's figures in words, and the event log, newest first, with both times.
 */
@Composable
fun MindMapSection(d: OutputData, events: List<FlockEventEntity>, initial: String? = null, view: OutputData.NowView? = null, hour: Int = d.hour) {
    // the trail walked: the flock, then what was tapped (kept short; a point already on it goes back to it)
    var trail by rememberSaveable { mutableStateOf(listOfNotNull("center", initial?.let { k -> FlockGraph.prop(k)?.let { "group:" + it.group.name } }, initial).joinToString("|")) }
    val path = trail.split("|").filter { it.isNotBlank() }.ifEmpty { listOf("center") }
    val focus = path.last()
    fun go(key: String) {
        val at = path.indexOf(key)
        val next = if (at >= 0) path.take(at + 1) else {
            val base = if (key.startsWith("group:")) listOf("center") else path
            (base + key).let { if (it.size > 6) listOf("center") + it.takeLast(5) else it }
        }
        trail = next.joinToString("|")
    }
    val values = remember(d, view, hour) { FlockGraph.props.associate { it.key to nodeValue(it.key, d, view, hour) } }
    val byKind = remember(events) { events.groupBy { it.kind } }
    SectionCard(GIcon.GRAPH, "Map", "map") {
        Trail(path, d.flock?.name ?: "Flock") { i -> trail = path.take(i + 1).joinToString("|") }
        Stage3D(focus, values, byKind, d.flock?.name ?: "Flock", d.day, ::go)
        Text("Tap a point to step to it · a finger sideways turns it", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
        if (!focus.startsWith("group:") && focus != "center") KeyLine(RAISES to "raises", LOWERS to "lowers", Color.White.copy(alpha = 0.7f) to "shapes", EVENT_COLOR to "event")
        else KeyLine(kindColor(ValueKind.PRESENT) to "entered", kindColor(ValueKind.PREDICTED) to "worked out", Color.White to "setting", EVENT_COLOR to "event")
        NodePanel(focus, values, byKind, d, ::go)
        EventLog(events, d)
    }
}

private val EVENT_COLOR = Color(0xFFB58BE0)
private val RAISES = Color(0xFF46B98C)
private val LOWERS = Color(0xFFE5737A)

/** A point's value now, as text, with the kind of value it is (null kind: a setting or a part of the house). */
data class NodeValue(val text: String, val kind: ValueKind?)

/** The trail walked: Flock › Birds › Weight now — each step a button back to it. */
@Composable
private fun Trail(path: List<String>, flockName: String, onStep: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("map_trail"), verticalAlignment = Alignment.CenterVertically) {
        path.forEachIndexed { i, key ->
            val last = i == path.size - 1
            if (i > 0) Text(" › ", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.4f))
            Text(nodeName(key, flockName), style = MaterialTheme.typography.labelLarge, color = if (last) Color.White else Color.White.copy(alpha = 0.65f), maxLines = 1,
                modifier = Modifier.clickable(enabled = !last) { onStep(i) }.padding(vertical = 8.dp, horizontal = 2.dp).testTag("map_crumb_$i"))
        }
    }
}

private fun nodeName(key: String, flockName: String = "Flock"): String = when {
    key == "center" -> flockName
    key.startsWith("group:") -> Group.valueOf(key.removePrefix("group:")).label
    else -> FlockGraph.prop(key)?.label ?: key
}

/** One point of the scene: where it stands (x right, y down, z toward the eye; about −1…1) and how it links to the middle. */
internal data class StagePoint(val key: String, val x: Float, val y: Float, val z: Float, val edge: FlockGraph.Edge? = null,
                               /** the wire runs from this point to the middle (false: from the middle to it) */
                               val inward: Boolean = true, val big: Boolean = false, val more: Int = 0)

/** The scene for [focus]: the middle and the points round it (see [MindMapSection]). */
internal object StageLayout {
    const val MAX_SIDE = 6
    const val PER_RING = 6

    fun of(focus: String): List<StagePoint> = when {
        focus == "center" -> ring(Group.values().map { "group:" + it.name }, Group.values().size) + StagePoint("center", 0f, 0f, 0f, big = true)
        focus.startsWith("group:") -> {
            val g = Group.valueOf(focus.removePrefix("group:"))
            ring(FlockGraph.props.filter { it.group == g }.map { it.key }) + StagePoint(focus, 0f, 0f, 0f, big = true)
        }
        else -> wiring(focus)
    }

    /** Points on rings round the middle, [PER_RING] to a ring, the rings one above the other and half a step apart. */
    private fun ring(keys: List<String>, per: Int = PER_RING): List<StagePoint> {
        val rows = keys.chunked(per)
        return rows.flatMapIndexed { r, row ->
            val y = (r - (rows.size - 1) / 2f) * 0.62f
            row.mapIndexed { i, k ->
                val a = (2 * Math.PI * (i + (if (r % 2 == 1) 0.5 else 0.0)) / row.size).toFloat()
                StagePoint(k, kotlin.math.sin(a), y, kotlin.math.cos(a))
            }
        }
    }

    /** A number's wiring: causes left, effects right (stronger nearer), events above, parts below. */
    private fun wiring(key: String): List<StagePoint> {
        fun side(edges: List<FlockGraph.Edge>, x: Float, inward: Boolean): List<StagePoint> {
            val shown = edges.take(MAX_SIDE)
            return shown.mapIndexed { i, e ->
                val y = (i - (shown.size - 1) / 2f) * minOf(0.5f, 2.75f / maxOf(1, shown.size))
                val other = if (inward) e.from else e.to
                StagePoint(other, x, y, (e.strength - 2) * 0.28f + (if (i % 2 == 0) 0.08f else -0.08f), e, inward, more = if (i == shown.size - 1) edges.size - shown.size else 0)
            }
        }
        fun row(edges: List<Pair<String, FlockGraph.Edge>>, y: Float, inward: Boolean): List<StagePoint> {
            val shown = edges.take(4)
            return shown.mapIndexed { i, (other, e) -> StagePoint(other, (i - (shown.size - 1) / 2f) * 0.62f, y, -0.35f, e, inward) }
        }
        val events = FlockGraph.changes.filter { it.to == key }.map { it.from to it }
        val changed = FlockGraph.changes.filter { it.from == key }.map { it.to to it }          // an event in the middle: what it changes
        val parts = FlockGraph.relations.filter { it.from == key || it.to == key }.map { (if (it.from == key) it.to else it.from) to it }
        return side(FlockGraph.inputsOf(key), -1f, true) + side(FlockGraph.outputsOf(key), 1f, false) +
            row(events, -1.05f, true) + (if (changed.isNotEmpty()) side(changed.map { it.second }, 1f, false) else emptyList()) +
            row(parts, 1.05f, false) + StagePoint(key, 0f, 0f, 0f, big = true)
    }
}

/**
 * The scene, drawn in perspective and turned by a finger sideways. Every point is a real button (so a tap always lands);
 * the wires are drawn behind them.
 */
@Composable
private fun Stage3D(focus: String, values: Map<String, NodeValue?>, byKind: Map<String, List<FlockEventEntity>>, flockName: String, day: Int, onTap: (String) -> Unit) {
    val points = remember(focus) { StageLayout.of(focus) }
    val wired = !focus.startsWith("group:") && focus != "center"
    // a ring turns all the way round; a wiring only swings a little, so left stays "comes from" and right "moves"
    val home = focus == "center"
    // (a ring starts a little turned, so no point stands straight in front of the middle)
    var yaw by remember(focus) { mutableFloatStateOf(if (wired) 0.30f else 0.45f) }
    val pitch = if (wired) 0.16f else if (home) 0.62f else 0.24f
    val rings = if (wired) 1 else kotlin.math.ceil((points.size - 1) / StageLayout.PER_RING.toFloat()).toInt().coerceAtLeast(1)
    val height = if (wired) 440.dp else if (home) 330.dp else (150 + rings * 84).dp
    BoxWithConstraints(Modifier.fillMaxWidth().height(height).testTag("mind_map")
        .pointerInput(focus) {
            detectHorizontalDragGestures { ch, dx -> ch.consume()
                yaw = (yaw + dx / size.width * 3.2f).let { if (wired) it.coerceIn(-0.75f, 0.75f) else it } }
        }) {
        val wPx = constraints.maxWidth.toFloat(); val hPx = constraints.maxHeight.toFloat()
        val unit = if (wired) minOf(wPx * 0.30f, hPx * 0.31f) else if (home) wPx * 0.36f else wPx * 0.37f
        val cam = 3.4f
        data class Shown(val p: StagePoint, val sx: Float, val sy: Float, val depth: Float, val scale: Float)
        val cy = kotlin.math.cos(yaw); val sn = kotlin.math.sin(yaw); val cp = kotlin.math.cos(pitch); val sp = kotlin.math.sin(pitch)
        val shown = points.map { p ->
            val x1 = p.x * cy + p.z * sn; val z1 = -p.x * sn + p.z * cy
            val y2 = p.y * cp - z1 * sp; val z2 = p.y * sp + z1 * cp
            val s = cam / (cam - z2)
            Shown(p, wPx / 2 + x1 * unit * s, hPx / 2 + y2 * unit * s, z2, s)
        }
        val mid = shown.first { it.p.big }
        // ---- the ground rings and the wires, behind the buttons
        Canvas(Modifier.fillMaxSize()) {
            val px = density
            if (!wired) {
                // each ring of points as the ellipse it stands on
                shown.filter { !it.p.big }.map { it.p.y }.distinct().forEach { ry ->
                    val path = Path()
                    for (i in 0..48) {
                        val a = (2 * Math.PI * i / 48).toFloat()
                        val x = kotlin.math.sin(a); val z = kotlin.math.cos(a)
                        val z1 = -x * sn + z * cy; val x1 = x * cy + z * sn
                        val y2 = ry * cp - z1 * sp; val z2 = ry * sp + z1 * cp
                        val s = cam / (cam - z2)
                        val o = Offset(wPx / 2 + x1 * unit * s, hPx / 2 + y2 * unit * s)
                        if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                    }
                    drawPath(path, Color.White.copy(alpha = 0.14f), style = Stroke(1f * px))
                }
                shown.filter { !it.p.big }.forEach { s ->
                    drawLine(dotColor(s.p.key, values[s.p.key]).copy(alpha = 0.10f + 0.16f * ((s.depth + 1.2f) / 2.4f).coerceIn(0f, 1f)),
                        Offset(mid.sx, mid.sy), Offset(s.sx, s.sy), 1f * px)
                }
            } else shown.filter { it.p.edge != null }.sortedBy { it.depth }.forEach { s ->
                val e = s.p.edge!!
                val col = when (e.kind) { EdgeKind.CHANGES -> EVENT_COLOR; EdgeKind.RELATION -> Color.White.copy(alpha = 0.6f); EdgeKind.DEPENDS -> signColor(e.sign) }
                val a = if (s.p.inward) Offset(s.sx, s.sy) else Offset(mid.sx, mid.sy)
                val b = if (s.p.inward) Offset(mid.sx, mid.sy) else Offset(s.sx, s.sy)
                val ctl = Offset((a.x + b.x) / 2, if (e.kind == EdgeKind.DEPENDS) (a.y + b.y) / 2 + (b.x - a.x) * 0.06f else (a.y + b.y) / 2)
                val path = Path().apply { moveTo(a.x, a.y); quadraticBezierTo(ctl.x, ctl.y, b.x, b.y) }
                val w = (if (e.kind == EdgeKind.DEPENDS) 0.9f + 0.9f * e.strength else 1.2f) * px * s.scale
                val dash = if (e.lag || e.kind != EdgeKind.DEPENDS) PathEffect.dashPathEffect(floatArrayOf(6f * px, 4f * px)) else null
                drawPath(path, col.copy(alpha = 0.85f), style = Stroke(w, pathEffect = dash, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                // an arrow halfway, the way the effect runs
                val t = 0.55f
                val m = Offset((1 - t) * (1 - t) * a.x + 2 * (1 - t) * t * ctl.x + t * t * b.x, (1 - t) * (1 - t) * a.y + 2 * (1 - t) * t * ctl.y + t * t * b.y)
                val dx = b.x - a.x; val dy = b.y - a.y; val len = hypot(dx, dy).coerceAtLeast(1f); val ux = dx / len; val uy = dy / len
                val h = 5.5f * px
                drawPath(Path().apply { moveTo(m.x + ux * h, m.y + uy * h); lineTo(m.x - ux * h - uy * h * 0.7f, m.y - uy * h + ux * h * 0.7f)
                    lineTo(m.x - ux * h + uy * h * 0.7f, m.y - uy * h - ux * h * 0.7f); close() }, col)
            }
        }
        if (wired) {
            // which side is which
            val lab = MaterialTheme.typography.labelSmall
            if (shown.any { it.p.edge?.kind == EdgeKind.DEPENDS && it.p.inward }) Text("comes from", style = lab, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.align(Alignment.TopStart))
            if (shown.any { it.p.edge != null && !it.p.inward && it.p.edge.kind != EdgeKind.RELATION }) Text("moves", style = lab, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.align(Alignment.TopEnd))
        }
        // ---- the points: buttons, far ones first so near ones lie on top
        shown.sortedBy { it.depth }.forEach { s ->
            val near = ((s.depth + 1.2f) / 2.4f).coerceIn(0f, 1f)
            Box(Modifier
                .layout { measurable, c ->
                    val pl = measurable.measure(c.copy(minWidth = 0, minHeight = 0))
                    layout(0, 0) { pl.place((s.sx - pl.width / 2f).roundToInt(), (s.sy - pl.height / 2f).roundToInt(), zIndex = s.depth + 10f) }   // always in front of the wires, nearer ones on top
                }
                // a wiring's points stay solid (the wires pass behind them); a ring's far side fades and shrinks
                .graphicsLayer { val k = if (s.p.big) 1f else if (wired) s.scale.coerceIn(0.88f, 1.08f) else s.scale.coerceIn(0.7f, 1.05f)
                    scaleX = k; scaleY = k; alpha = if (s.p.big || wired) 1f else 0.25f + 0.75f * near * near }) {
                StageButton(s.p, values[s.p.key], byKind, flockName, day, focus, onTap)
            }
        }
    }
}

/** One point as a button: a group's mark, the flock, or a number with its value. */
@Composable
private fun StageButton(p: StagePoint, v: NodeValue?, byKind: Map<String, List<FlockEventEntity>>, flockName: String, day: Int, focus: String, onTap: (String) -> Unit) {
    val key = p.key
    val num = MaterialTheme.typography.labelMedium.copy(fontFamily = com.example.ui.theme.NumberFont)
    val name = MaterialTheme.typography.labelMedium
    val tap = Modifier.clickable(enabled = key != focus) { onTap(key) }.testTag("map_node_$key")
    when {
        key == "center" -> Column(Modifier.background(Color.Black, CircleShape).border(1.4.dp, Color.White.copy(alpha = 0.8f), CircleShape).then(tap).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(flockName.take(14), style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
            Text("day $day", style = name, color = Color.White.copy(alpha = 0.7f))
        }
        key.startsWith("group:") -> {
            val g = Group.valueOf(key.removePrefix("group:"))
            Column(tap.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(if (p.big) 58.dp else 48.dp).background(Color.Black, CircleShape).border(if (p.big) 1.6.dp else 1.dp, groupColor(g).copy(alpha = if (p.big) 1f else 0.7f), CircleShape),
                    contentAlignment = Alignment.Center) { GameIcon(groupIcon(g), if (p.big) 34.dp else 28.dp) }
                Text(g.label, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
                if (!p.big) Text(FlockGraph.props.count { it.group == g }.toString(), style = num, color = Color.White.copy(alpha = 0.55f))
            }
        }
        else -> {
            val c = dotColor(key, v)
            val prop = FlockGraph.prop(key)
            val label = if (p.big) (prop?.label ?: key) else (prop?.let { shortLabel(it) } ?: key)
            val events = if (key.startsWith("ev:")) byKind[key.removePrefix("ev:")]?.size ?: 0 else null
            Column(Modifier.widthIn(max = if (p.big) 150.dp else 96.dp).background(Color.Black, RoundedCornerShape(10.dp))
                .border(if (p.big) 1.6.dp else 1.dp, if (p.big) Color.White else c.copy(alpha = 0.75f), RoundedCornerShape(10.dp)).then(tap)
                .padding(horizontal = if (p.big) 12.dp else 7.dp, vertical = if (p.big) 9.dp else 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = if (p.big) MaterialTheme.typography.labelLarge else name, color = Color.White, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                val text = events?.let { "×$it" } ?: v?.text
                if (text != null) Text(text, style = if (p.big) MaterialTheme.typography.titleSmall.copy(fontFamily = com.example.ui.theme.NumberFont) else num,
                    color = if (events != null) EVENT_COLOR else v?.kind?.let { kindColor(it) } ?: Color.White.copy(alpha = 0.8f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                if (p.more > 0) Text("+${p.more} more below", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f), maxLines = 1)
            }
        }
    }
}

/** Names short enough for a button. */
private fun shortLabel(p: FlockGraph.Prop): String = SHORT[p.key] ?: p.label
private val SHORT = mapOf(
    "singles" to "Single weights", "cumBw" to "Avg produced", "birdHeat" to "Bird heat", "appetite" to "Appetite",
    "gainToday" to "Gain today", "density" to "Density", "gainY" to "Gain yesterday", "minVent" to "Min vent",
    "outTemp" to "Outside °C", "outRh" to "Outside RH", "houseTemp" to "House °C", "houseRh" to "House RH",
    "deadKg" to "Kg dead", "kgInHouse" to "Kg in house", "liftAvg" to "Lift kg/bird", "feedTD" to "Feed to date",
    "correction" to "Correction", "birdsPerNipple" to "Birds/nipple", "litterMoist" to "Litter wet", "heatLoad" to "Heat load",
    "weightNow" to "Weight now", "samples" to "Weighings", "ft2Bird" to "ft² per bird", "pansFilled" to "Pans filled",
    "ev:placed" to "Placed", "ev:deaths" to "Deaths", "ev:feed_used" to "Feed used", "ev:feed_received" to "Feed in",
    "ev:feeding" to "Feeding", "ev:barricades" to "Barricades", "ev:weather" to "Weather", "ev:reading" to "Reading",
    "ev:settings" to "Settings", "ev:diesel" to "Diesel", "ev:notes" to "Notes", "ev:culls" to "Culls", "ev:lifting" to "Lifting",
    "ev:weighing" to "Weighing",
)

private fun groupIcon(g: Group): GIcon = when (g) {
    Group.BIRDS -> GIcon.BIRDS; Group.FEED -> GIcon.BAGS; Group.LIFTING -> GIcon.TRUCK; Group.AIR -> GIcon.FAN
    Group.WATER -> GIcon.WATER; Group.HOUSE -> GIcon.HOUSE; Group.EVENTS -> GIcon.CLOCK
}

private fun groupColor(g: Group?): Color = when (g) {
    Group.BIRDS -> Color(0xFFF6D45C); Group.FEED -> Color(0xFFE2BC6A); Group.LIFTING -> Color(0xFFB07D45); Group.AIR -> Color(0xFFA9DCF7)
    Group.WATER -> Color(0xFF5DB8EC); Group.HOUSE -> Color(0xFF9AA1AA); Group.EVENTS -> EVENT_COLOR; null -> Color.White
}

private fun signColor(sign: Int): Color = when { sign > 0 -> RAISES; sign < 0 -> LOWERS; else -> Color.White }

/** The dot's colour: the value's kind (entered green, worked out amber …), white for settings and parts, violet for events. */
private fun dotColor(key: String, v: NodeValue?): Color {
    if (key.startsWith("ev:")) return EVENT_COLOR
    val p = FlockGraph.prop(key)
    return when {
        v?.kind != null -> kindColor(v.kind)
        p?.source == Source.SETTING || p?.source == Source.THING || p?.source == Source.CLOCK -> Color.White
        p?.source == Source.SENSOR -> Color.White.copy(alpha = 0.35f)
        else -> kindColor(ValueKind.PREDICTED).copy(alpha = 0.6f)
    }
}

/** The point tapped: its value, what it comes from, what it moves, the events that set it (and why it is not entered). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NodePanel(selected: String?, values: Map<String, NodeValue?>, byKind: Map<String, List<FlockEventEntity>>, d: OutputData, onSelect: (String) -> Unit) {
    val dimText = Color.White.copy(alpha = 0.6f)
    @Composable fun linkRow(title: String, items: List<Triple<String, String, Color>>) {
        if (items.isEmpty()) return
        Text(title, style = MaterialTheme.typography.labelMedium, color = dimText)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEach { (key, text, c) ->
                Text(text, style = MaterialTheme.typography.labelLarge, color = c, modifier = Modifier.clickable { onSelect(key) }.testTag("map_link_$key"))
            }
        }
    }
    fun name(key: String) = FlockGraph.prop(key)?.label ?: key
    fun arrow(sign: Int) = when { sign > 0 -> "↑ "; sign < 0 -> "↓ "; else -> "~ " }
    fun dots(n: Int) = " " + "•".repeat(n)
    Column(Modifier.fillMaxWidth().testTag("map_panel"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            selected == null -> Text("Tap a point: its value, what it comes from and what it moves.", style = MaterialTheme.typography.labelLarge, color = dimText)
            selected == "center" -> {
                Text(d.flock?.name ?: "Flock", style = MaterialTheme.typography.titleSmall, color = Color.White)
                Text("${FlockGraph.depends.size} links of what is worked out from what · ${FlockGraph.relations.size} between parts of the house · " +
                    "${FlockGraph.changes.size} from events", style = MaterialTheme.typography.labelLarge, color = dimText)
                linkRow("Groups", Group.values().map { Triple("group:" + it.name, it.label, groupColor(it)) })
            }
            selected.startsWith("group:") -> {
                val g = Group.valueOf(selected.removePrefix("group:"))
                Row(verticalAlignment = Alignment.CenterVertically) { GameIcon(groupIcon(g), 22.dp); Spacer(Modifier.width(8.dp)); Text(g.label, style = MaterialTheme.typography.titleSmall, color = Color.White) }
                linkRow("In it", FlockGraph.props.filter { it.group == g }.map { Triple(it.key, it.label + (values[it.key]?.let { v -> " " + v.text } ?: ""), dotColor(it.key, values[it.key])) })
            }
            selected.startsWith("ev:") -> {
                val kind = EventKind.values().firstOrNull { it.nodeKey == selected } ?: return@Column
                val list = byKind[kind.key].orEmpty()
                Text(kind.label, style = MaterialTheme.typography.titleSmall, color = EVENT_COLOR)
                Text(if (list.isEmpty()) "none kept yet" else "${list.size} kept · last ${whenText(list.first().happenedAt, d)}" +
                    list.first().enteredBy.substringBefore("@").takeIf { it.isNotBlank() }?.let { " by $it" }.orEmpty(),
                    style = MaterialTheme.typography.labelLarge, color = Color.White)
                linkRow("Changes", kind.changes.map { Triple(it, name(it), Color.White) })
                list.take(4).forEach { e -> Text(whenText(e.happenedAt, d) + " · " + FlockEvents.summary(e), style = MaterialTheme.typography.labelMedium, color = dimText) }
            }
            else -> {
                val p = FlockGraph.prop(selected) ?: return@Column
                val v = values[selected]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GameIcon(groupIcon(p.group), 22.dp); Spacer(Modifier.width(8.dp))
                    Text(p.label, style = MaterialTheme.typography.titleSmall, color = Color.White, modifier = Modifier.weight(1f))
                    Text(v?.text ?: "—", style = MaterialTheme.typography.titleSmall.copy(fontFamily = com.example.ui.theme.NumberFont),
                        color = v?.kind?.let { kindColor(it) } ?: Color.White, modifier = Modifier.testTag("map_value"))
                }
                Text(p.group.label + " · " + p.source.label, style = MaterialTheme.typography.labelMedium, color = dimText)
                linkRow("Comes from", FlockGraph.inputsOf(selected).map { Triple(it.from, arrow(it.sign) + name(it.from) + dots(it.strength) + (if (it.lag) " later" else ""), signColor(it.sign)) })
                linkRow("Moves", FlockGraph.outputsOf(selected).map { Triple(it.to, arrow(it.sign) + name(it.to) + dots(it.strength) + (if (it.lag) " later" else ""), signColor(it.sign)) })
                val rel = FlockGraph.relationProps(d.farm, d.flock, d.rows.count { it.birdsLifted > 0 }, barText(d))
                linkRow("Parts", FlockGraph.relations.filter { it.from == selected || it.to == selected }.map { r ->
                    val other = if (r.from == selected) r.to else r.from
                    Triple(other, (if (r.from == selected) r.label + " " + name(other) else name(other) + " " + r.label) + (rel[r.from to r.to]?.let { " · $it" } ?: ""), Color.White)
                })
                linkRow("Set by", FlockGraph.eventsOf(selected).map { k -> Triple(k.nodeKey, k.label + " ×" + (byKind[k.key]?.size ?: 0), EVENT_COLOR) })
                // not entered: which of its inputs are worked out or modelled themselves
                if (v?.kind == ValueKind.PREDICTED) {
                    // an input with no figure (nothing lifted yet …) is not part of it; settings, parts and the clock are known
                    val from = FlockGraph.why(selected) { key ->
                        val src = FlockGraph.prop(key)?.source
                        if (src == Source.SETTING || src == Source.THING || src == Source.CLOCK) true
                        else values[key]?.let { v -> v.kind == null || v.kind == ValueKind.PRESENT }
                    }
                    if (from.isNotEmpty()) Text("Worked out, not entered: " + from.joinToString(", ") { name(it) } + " " + (if (from.size == 1) "is" else "are") + " not entered either",
                        style = MaterialTheme.typography.labelMedium, color = kindColor(ValueKind.PREDICTED))
                }
            }
        }
    }
}

/** The event log, newest first: when it happened, the kind, what it set; when it was entered later, that too. */
@Composable
private fun EventLog(events: List<FlockEventEntity>, d: OutputData) {
    Text("Events", style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.padding(top = 6.dp))
    if (events.isEmpty()) { Text("Every save, lifting, feeding and barricade move is kept here from now on.", style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = 0.6f)); return }
    Column(Modifier.fillMaxWidth().testTag("event_log"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        events.take(15).forEach { e ->
            val kind = EventKind.of(e.kind)
            Row(verticalAlignment = Alignment.Top) {
                Text(whenText(e.happenedAt, d), style = MaterialTheme.typography.labelMedium.copy(fontFamily = com.example.ui.theme.NumberFont),
                    color = Color.White.copy(alpha = 0.7f), modifier = Modifier.width(92.dp))
                Column(Modifier.weight(1f)) {
                    Text((kind?.label ?: e.kind) + (if (e.day >= 0) " · day ${e.day}" else " · farm"), style = MaterialTheme.typography.labelLarge, color = EVENT_COLOR)
                    val s = FlockEvents.summary(e)
                    if (s.isNotBlank()) Text(s, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                    val who = e.enteredBy.substringBefore("@").takeIf { it.isNotBlank() }
                    val late = e.enteredAt - e.happenedAt > 30 * 60_000L
                    if (late || who != null) Text(listOfNotNull(if (late) "entered " + whenText(e.enteredAt, d) else null, who?.let { "by $it" }).joinToString(" "),
                        style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                }
            }
        }
        if (events.size > 15) Text("${events.size - 15} more in the sheet's Events tab", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
    }
}

private fun whenText(ms: Long, d: OutputData): String = try {
    java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.of(d.farm.timeZone)).let {
        "${it.dayOfMonth} ${it.month.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() }} " + String.format("%02d:%02d", it.hour, it.minute)
    }
} catch (e: Exception) { "—" }

private fun barText(d: OutputData): String {
    val L = d.farm.usableLengthFt
    return if (d.penStartFt < 0.5 && d.barricadeFtNow > L - 0.5) "none" else "${Math.round(d.penStartFt)}–${Math.round(d.barricadeFtNow)} ft"
}

/** A point's value on the day shown, with its kind (entered, worked out …); null where the app has no figure for it. */
internal fun nodeValue(key: String, d: OutputData, view: OutputData.NowView? = null, hour: Int = d.hour): NodeValue? {
    val P = ValueKind.PRESENT; val PR = ValueKind.PREDICTED
    fun n(x: Double?, dec: Int, k: ValueKind?, unit: String = "") = x?.takeIf { !it.isNaN() && !it.isInfinite() }?.let { NodeValue(Fmt.n(it, dec) + (if (unit.isEmpty()) "" else " $unit"), k) }
    fun i(x: Int?, k: ValueKind?, unit: String = "") = x?.let { NodeValue(Fmt.i(it) + (if (unit.isEmpty()) "" else " $unit"), k) }
    val m = d.mass
    val f = d.farm
    return try { when (key) {
        "placed" -> i(d.placed, P)
        "deaths" -> i(d.mortTD, P)
        "culls" -> i(d.lameTD, P)
        "live" -> i(view?.live?.let { Math.round(it).toInt() } ?: d.live, if (d.mortEntered) P else PR)
        "mortality" -> n(d.mortTDPct, 2, d.mk, "%")
        "livability" -> n(d.e.livability, 2, d.mk, "%")
        "samples" -> d.e.avgWeight?.let { n(it, 1, P, "g") } ?: NodeValue("not today", null)
        "singles" -> i(com.example.flock.engine.PhysiologicalEngine.parseWeights(d.e.indivWeights).size, P, "birds")
        "weight" -> n(d.bw, 1, d.vk, "g")
        "weightNow" -> n(view?.weightG ?: d.bwNow, 1, d.vk, "g")
        "cv" -> n(d.cvEst, 1, if (d.cvMeasured) P else PR, "%")
        "gainY" -> n(d.gainYesterday, 1, d.gainYesterdayKind, "g")
        "density" -> n(d.space.kgM2, 1, d.vk, "kg/m²")
        "ft2Bird" -> n(d.space.ft2PerBird, 2, d.vk, "ft²")
        "fcr" -> n(m?.fcr, 3, d.fcrKind)
        "cfcr" -> n(m?.cFcrCompany, 3, d.fcrKind)
        "cumBw" -> n(m?.cumBwKg, 3, d.fcrKind, "kg")
        "epef" -> n(d.epef, 0, d.fcrKind)
        "heatLoad" -> n(d.envAt(hour)?.r?.heatLoadC, 1, PR, "°C")
        "birdHeat" -> n(d.heatPerBirdW, 1, PR, "W a bird")
        "feedUsed" -> n(d.e.feedBagsUsed, 1, P, "bags")
        "feedTD" -> n(d.usedKgTD, 0, P, "kg")
        "received" -> n(d.rows.filter { it.dayNumber <= d.day }.sumOf { it.feedRecB1 + it.feedRecB2 + it.feedRecB3 }, 0, P, "bags")
        "store" -> n(d.stockBagsTotal, 1, P, "bags")
        "appetite" -> n(d.likelyEatKg, 0, PR, "kg")
        "bags" -> n(d.dayBags, 1, PR, "bags")
        "perBird" -> n(d.dayBags * d.bagKg * 1000 / d.liveSafe, 1, PR, "g")
        "eatenY" -> n(d.usedPerBirdY, 1, P, "g")
        "gainToday" -> n(d.gainToday, 1, PR, "g")
        "pansOpen" -> i(d.feedPattern.openPerLine, PR, "a line")
        "birdsPerPan" -> n(d.feedPattern.birdsPerPan.takeIf { it < 1e6 }, 1, PR)
        "feedings" -> i(d.feedOptions.getOrNull(d.recommendedOption)?.feedings, PR)
        "hungry" -> null
        "pansFilled" -> i(com.example.flock.data.parseFeedings(d.e.feedings).size, P, "today")
        "walkFeed" -> n(d.feedWalkM(d.feedPattern), 2, PR, "m")
        "correction" -> n(d.correction.pct, 1, PR, "%")
        "lifted" -> i(d.liftTD, P)
        "liftedKg" -> n(d.liftKgTD, 0, P, "kg")
        "liftAvg" -> if (d.liftTD > 0) n(d.liftKgTD / d.liftTD, 3, P, "kg") else null
        "liftTime" -> d.liftHour?.let { h -> NodeValue(String.format("%02d:%02d", h.toInt(), Math.round((h % 1) * 60).toInt()), P) }
        "kgInHouse" -> n(m?.liveKg, 0, d.vk, "kg")
        "deadKg" -> n(m?.deadKg, 0, PR, "kg")
        "produced" -> n(m?.producedKg, 0, d.vk, "kg")
        "outTemp" -> n(d.outsideHours.firstOrNull { it.first == hour }?.second, 1, P, "°C")
        "outRh" -> n(d.outsideHours.firstOrNull { it.first == hour }?.third, 0, P, "%")
        "houseTemp" -> n(d.envAt(hour)?.air?.tempC, 1, PR, "°C")
        "houseRh" -> n(d.envAt(hour)?.air?.rhPct, 0, PR, "%")
        "minVent" -> n(d.minVentCfmBird * d.live, 0, PR, "cfm")
        "lightHours" -> n(d.e.lightHours, 1, PR, "h")
        "sensors" -> NodeValue("none yet", null)
        "water" -> n(d.waterL, 0, PR, "L")
        "waterPerBird" -> n(d.waterL * 1000 / d.liveSafe, 0, PR, "mL")
        "refills" -> i(d.e.tankRefills, PR)
        "birdsPerNipple" -> n(d.birdsPerNipple, 1, PR)
        "walkWater" -> n(d.waterWalkM, 2, PR, "m")
        "nippleHeight" -> n(d.drinkerHtIn, 1, PR, "in")
        "age" -> NodeValue("day ${d.day}", null)
        "clock" -> NodeValue(view?.hhmm ?: String.format("%02d:00", hour), null)
        "house" -> NodeValue(Fmt.n(f.usableLengthFt, 0) + " × " + Fmt.n(f.usableWidthFt, 0) + " ft", null)
        "floor" -> n(d.areaInUseFt2, 0, null, "ft²")
        "barricades" -> NodeValue(barText(d), null)
        "feederLines" -> i(f.feederLines, null)
        "pans" -> i(f.pansPerFeederLine, null, "a line")
        "drinkerLines" -> i(f.drinkerLines, null)
        "nipples" -> i(d.nipples, null)
        "fans" -> i(f.fanCount, null)
        "pads" -> NodeValue(f.padLayout, null)
        "tank" -> n(f.drinkTankL, 0, null, "L")
        "trucks" -> i(d.rows.count { it.birdsLifted > 0 && it.dayNumber <= d.day }, P, "liftings")
        "godown" -> n(f.godownBags.takeIf { it > 0 }, 0, null, "bags room")
        else -> null
    } } catch (e: Exception) { null }
}
