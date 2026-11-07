package com.example.flock.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
 * The flock as one map (v58): every property, part of the house and kind of event round the flock, in their groups, with
 * every link between them — what each number is worked out from (green raises, red lowers, white shapes; thicker =
 * stronger; dashed = acts later), how the parts hang together, and which events move what (violet). Two fingers zoom,
 * one finger moves a zoomed map, a double tap zooms in or back; a tap on a point shows its value, what it comes from,
 * what it moves and the events that set it. Under it the event log, newest first, with both times.
 */
@Composable
fun MindMapSection(d: OutputData, events: List<FlockEventEntity>, initial: String? = null) {
    var selected by rememberSaveable { mutableStateOf(initial) }
    val layout = remember { MindLayout.build() }
    val values = remember(d) { FlockGraph.props.associate { it.key to nodeValue(it.key, d) } }
    val byKind = remember(events) { events.groupBy { it.kind } }
    val links = FlockGraph.edges.size
    SectionCard(GIcon.GRAPH, "Map", "map") {
        Text("${FlockGraph.props.size} properties, parts and events · $links links · ${events.size} events kept",
            style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
        MindMapCanvas(layout, values, byKind, selected, d.flock?.name ?: "Flock", d.day) { selected = if (selected == it) null else it }
        KeyLine(kindColor(ValueKind.PRESENT) to "entered", kindColor(ValueKind.PREDICTED) to "worked out", Color.White to "setting", EVENT_COLOR to "event")
        KeyLine(RAISES to "raises", LOWERS to "lowers", Color.White.copy(alpha = 0.6f) to "shapes")
        NodePanel(selected, values, byKind, d) { selected = it }
        EventLog(events, d)
    }
}

private val EVENT_COLOR = Color(0xFFB58BE0)
private val RAISES = Color(0xFF46B98C)
private val LOWERS = Color(0xFFE5737A)

/** A point's value now, as text, with the kind of value it is (null kind: a setting or a part of the house). */
data class NodeValue(val text: String, val kind: ValueKind?)

/** Where each point of the map sits: in a square of side [S], the flock in the middle, the groups round it, their points outside. */
internal class MindLayout(val nodes: List<Node>) {
    data class Node(val key: String, val label: String, val group: Group?, val level: Int, val x: Float, val y: Float, val angle: Float)

    val byKey = nodes.associateBy { it.key }

    companion object {
        const val S = 1000f
        private const val R1 = 165f
        private const val R2 = 330f

        fun build(): MindLayout {
            val groups = Group.values().toList()
            val leaves = groups.associateWith { g -> FlockGraph.props.filter { it.group == g } }
            val gap = 1.6f
            val slots = leaves.values.sumOf { it.size } + gap * groups.size
            val step = (2 * PI / slots).toFloat()
            var a = (-PI / 2).toFloat() - step * gap / 2
            val out = mutableListOf(Node("center", "", null, 0, S / 2, S / 2, 0f))
            groups.forEach { g ->
                a += step * gap
                val ps = leaves[g].orEmpty()
                val start = a
                ps.forEach { p ->
                    out += Node(p.key, shortLabel(p), g, 2, S / 2 + R2 * cos(a), S / 2 + R2 * sin(a), a)
                    a += step
                }
                val mid = if (ps.isEmpty()) start else (start + a - step) / 2
                out += Node("group:" + g.name, g.label, g, 1, S / 2 + R1 * cos(mid), S / 2 + R1 * sin(mid), mid)
            }
            return MindLayout(out)
        }

        /** Names short enough to sit round the map. */
        fun shortLabel(p: FlockGraph.Prop): String = SHORT[p.key] ?: p.label
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
    }
}

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

@Composable
private fun MindMapCanvas(layout: MindLayout, values: Map<String, NodeValue?>, byKind: Map<String, List<FlockEventEntity>>, selected: String?,
                          flockName: String, day: Int, onTap: (String) -> Unit) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var ox by remember { mutableFloatStateOf(0f) }
    var oy by remember { mutableFloatStateOf(0f) }
    val tap by androidx.compose.runtime.rememberUpdatedState(onTap)
    // the points the selection reaches: what it comes from and what it moves (and, for an event, what it changes)
    val near = remember(selected) {
        if (selected == null) emptySet() else FlockGraph.edges.filter { it.from == selected || it.to == selected }.flatMap { listOf(it.from, it.to) }.toSet() +
            (if (selected.startsWith("group:")) FlockGraph.props.filter { "group:" + it.group.name == selected }.map { it.key } else emptyList())
    }
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f).clipToBounds().testTag("mind_map")
        .pointerInput(Unit) {
            // two fingers zoom (about the point between them) and move; one finger moves the map only once zoomed in,
            // so the page still scrolls over a map seen whole
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val ev = awaitPointerEvent()
                    val fingers = ev.changes.count { it.pressed }
                    if (fingers >= 2 || zoom > 1.01f) {
                        val z = if (fingers >= 2) ev.calculateZoom() else 1f
                        val c = ev.calculateCentroid(useCurrent = false)
                        val pan = ev.calculatePan()
                        val nz = (zoom * z).coerceIn(1f, 6f)
                        if (c != Offset.Unspecified) {
                            ox = (c.x - (c.x - ox) * nz / zoom + pan.x).coerceIn(size.width - size.width * nz, 0f)
                            oy = (c.y - (c.y - oy) * nz / zoom + pan.y).coerceIn(size.height - size.height * nz, 0f)
                        }
                        zoom = nz
                        ev.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (ev.changes.any { it.pressed })
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = { o ->
                    val nz = if (zoom > 1.5f) 1f else 2.6f
                    ox = (o.x - (o.x - ox) * nz / zoom).coerceIn(size.width - size.width * nz, 0f)
                    oy = (o.y - (o.y - oy) * nz / zoom).coerceIn(size.height - size.height * nz, 0f)
                    zoom = nz
                },
                onTap = { o ->
                    val k = size.width * zoom / MindLayout.S
                    val hit = layout.nodes.minByOrNull { n -> hypot(n.x * k + ox - o.x, n.y * k + oy - o.y) }
                    if (hit != null && hypot(hit.x * k + ox - o.x, hit.y * k + oy - o.y) < 22.dp.toPx()) tap(hit.key)
                })
        }) {
        val k = size.width * zoom / MindLayout.S
        fun pt(n: MindLayout.Node) = Offset(n.x * k + ox, n.y * k + oy)
        val center = pt(layout.byKey.getValue("center"))
        val px = density
        val dim = selected != null
        // ---- the tree: flock → groups → points
        layout.nodes.filter { it.level == 1 }.forEach { g ->
            drawLine(groupColor(g.group).copy(alpha = if (dim) 0.25f else 0.55f), center, pt(g), 1.6f * px)
        }
        layout.nodes.filter { it.level == 2 }.forEach { n ->
            val g = layout.byKey["group:" + n.group?.name] ?: return@forEach
            val a = pt(g); val b = pt(n)
            val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
            val ctl = Offset(mid.x + (a.x - center.x) * 0.25f, mid.y + (a.y - center.y) * 0.25f)
            val path = Path().apply { moveTo(a.x, a.y); quadraticBezierTo(ctl.x, ctl.y, b.x, b.y) }
            val on = !dim || n.key in near || n.key == selected
            drawPath(path, groupColor(n.group).copy(alpha = if (on) 0.30f else 0.08f), style = Stroke(0.8f * px))
        }
        // ---- the links between points, bundled toward the middle
        FlockGraph.edges.forEach { e ->
            val a = layout.byKey[e.from] ?: return@forEach; val b = layout.byKey[e.to] ?: return@forEach
            val pa = pt(a); val pb = pt(b)
            val mid = Offset((pa.x + pb.x) / 2, (pa.y + pb.y) / 2)
            val ctl = Offset(center.x + (mid.x - center.x) * 0.3f, center.y + (mid.y - center.y) * 0.3f)
            val path = Path().apply { moveTo(pa.x, pa.y); quadraticBezierTo(ctl.x, ctl.y, pb.x, pb.y) }
            val hot = selected != null && (e.from == selected || e.to == selected)
            val col = when (e.kind) { EdgeKind.CHANGES -> EVENT_COLOR; EdgeKind.RELATION -> Color.White; EdgeKind.DEPENDS -> signColor(e.sign) }
            val alpha = when { hot -> 0.95f; dim -> 0.04f; e.kind == EdgeKind.DEPENDS -> 0.10f + 0.05f * e.strength; else -> 0.18f }
            val w = (if (hot) 1.0f + 0.5f * e.strength else 0.35f + 0.3f * e.strength) * px
            val dash = if (e.lag || e.kind == EdgeKind.CHANGES || e.kind == EdgeKind.RELATION) PathEffect.dashPathEffect(floatArrayOf(4f * px, 3f * px)) else null
            drawPath(path, col.copy(alpha = alpha), style = Stroke(w, pathEffect = dash))
        }
        // ---- the points and their names, turned to read outward
        val paint = android.graphics.Paint().apply { isAntiAlias = true; typeface = com.example.ui.theme.AppFonts.text }
        val leafSp = (19f * k / px).coerceIn(7.5f, 13f)
        layout.nodes.filter { it.level == 2 }.forEach { n ->
            val p = pt(n)
            val on = !dim || n.key in near || n.key == selected
            val c = dotColor(n.key, values[n.key])
            val r = (if (n.key.startsWith("ev:")) 3.2f + 0.6f * minOf(4, (byKind[n.key.removePrefix("ev:")]?.size ?: 0).let { if (it > 0) 1 + it / 10 else 0 }) else 3.2f) * px
            drawCircle(c.copy(alpha = if (on) c.alpha else 0.25f), r, p)
            if (n.key == selected) drawCircle(Color.White, r + 3.5f * px, p, style = Stroke(1.4f * px))
            paint.textSize = leafSp * px
            paint.color = Color.White.copy(alpha = if (n.key == selected) 1f else if (on) 0.82f else 0.22f).toArgb()
            val deg = Math.toDegrees(n.angle.toDouble()).toFloat()
            val flip = cos(n.angle) < 0
            drawContext.canvas.nativeCanvas.apply {
                save(); translate(p.x, p.y); rotate(if (flip) deg + 180f else deg)
                paint.textAlign = if (flip) android.graphics.Paint.Align.RIGHT else android.graphics.Paint.Align.LEFT
                drawText(n.label, if (flip) -(r + 4f * px) else r + 4f * px, paint.textSize * 0.35f, paint)
                restore()
            }
        }
        // ---- the groups: their mark and name
        layout.nodes.filter { it.level == 1 }.forEach { g ->
            val p = pt(g)
            val s = (34f * k).coerceIn(18f * px, 40f * px)
            drawCircle(Color.Black, s * 0.62f, p)
            drawCircle(groupColor(g.group).copy(alpha = if (g.key == selected) 1f else 0.5f), s * 0.62f, p, style = Stroke((if (g.key == selected) 1.8f else 1f) * px))
            drawGameIcon(groupIcon(g.group!!), Offset(p.x - s / 2, p.y - s / 2), s)
            paint.textSize = (leafSp + 1.5f) * px; paint.color = Color.White.toArgb(); paint.textAlign = android.graphics.Paint.Align.CENTER
            drawContext.canvas.nativeCanvas.drawText(g.label, p.x, p.y + s * 0.62f + paint.textSize * 1.05f, paint)
        }
        // ---- the flock in the middle
        val rc = (48f * k).coerceIn(26f * px, 70f * px)
        drawCircle(Color.Black, rc, center)
        drawCircle(Color.White.copy(alpha = if (selected == "center") 1f else 0.6f), rc, center, style = Stroke(1.4f * px))
        paint.textAlign = android.graphics.Paint.Align.CENTER; paint.color = Color.White.toArgb()
        paint.textSize = (leafSp + 2f) * px
        drawContext.canvas.nativeCanvas.drawText(flockName.take(14), center.x, center.y - paint.textSize * 0.15f, paint)
        paint.textSize = (leafSp + 0.5f) * px; paint.color = Color.White.copy(alpha = 0.7f).toArgb()
        drawContext.canvas.nativeCanvas.drawText("day $day", center.x, center.y + paint.textSize * 1.1f, paint)
    }
    Text(if (zoom > 1.01f) "Double tap to see it whole · one finger moves it" else "Two fingers or a double tap to zoom · tap a point",
        style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
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
internal fun nodeValue(key: String, d: OutputData): NodeValue? {
    val P = ValueKind.PRESENT; val PR = ValueKind.PREDICTED
    fun n(x: Double?, dec: Int, k: ValueKind?, unit: String = "") = x?.takeIf { !it.isNaN() && !it.isInfinite() }?.let { NodeValue(Fmt.n(it, dec) + (if (unit.isEmpty()) "" else " $unit"), k) }
    fun i(x: Int?, k: ValueKind?, unit: String = "") = x?.let { NodeValue(Fmt.i(it) + (if (unit.isEmpty()) "" else " $unit"), k) }
    val m = d.mass
    val f = d.farm
    return try { when (key) {
        "placed" -> i(d.placed, P)
        "deaths" -> i(d.mortTD, P)
        "culls" -> i(d.lameTD, P)
        "live" -> i(d.live, if (d.mortEntered) P else PR)
        "mortality" -> n(d.mortTDPct, 2, d.mk, "%")
        "livability" -> n(d.e.livability, 2, d.mk, "%")
        "samples" -> d.e.avgWeight?.let { n(it, 1, P, "g") } ?: NodeValue("not today", null)
        "singles" -> i(com.example.flock.engine.PhysiologicalEngine.parseWeights(d.e.indivWeights).size, P, "birds")
        "weight" -> n(d.bw, 1, d.vk, "g")
        "weightNow" -> n(d.bwNow, 1, d.vk, "g")
        "cv" -> n(d.cvEst, 1, if (d.cvMeasured) P else PR, "%")
        "gainY" -> n(d.gainYesterday, 1, d.gainYesterdayKind, "g")
        "density" -> n(d.space.kgM2, 1, d.vk, "kg/m²")
        "ft2Bird" -> n(d.space.ft2PerBird, 2, d.vk, "ft²")
        "fcr" -> n(m?.fcr, 3, d.fcrKind)
        "cfcr" -> n(m?.cFcrCompany, 3, d.fcrKind)
        "cumBw" -> n(m?.cumBwKg, 3, d.fcrKind, "kg")
        "epef" -> n(d.epef, 0, d.fcrKind)
        "heatLoad" -> n(d.envAt(d.hour)?.r?.heatLoadC, 1, PR, "°C")
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
        "outTemp" -> n(d.outsideHours.firstOrNull { it.first == d.hour }?.second, 1, P, "°C")
        "outRh" -> n(d.outsideHours.firstOrNull { it.first == d.hour }?.third, 0, P, "%")
        "houseTemp" -> n(d.envAt(d.hour)?.air?.tempC, 1, PR, "°C")
        "houseRh" -> n(d.envAt(d.hour)?.air?.rhPct, 0, PR, "%")
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
        "clock" -> NodeValue(String.format("%02d:00", d.hour), null)
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
