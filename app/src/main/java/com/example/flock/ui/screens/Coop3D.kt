package com.example.flock.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.flock.ui.Fmt
import com.example.ui.theme.ValueIdeal
import com.example.ui.theme.ValueMax
import com.example.ui.theme.ValueMin
import com.example.ui.theme.ValuePredicted
import com.example.ui.theme.ValuePresent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Everything the farm window needs from the live flock for one frame. Environment values are ideals (no sensors). */
data class CoopInput(
    val age: Int, val meanG: Double, val cvPct: Double?, val live: Int, val entry: Int, val stage: String,
    val light: LightProgram, val feeder: FeederState, val zoneId: java.time.ZoneId,
    val airC: Double, val rhPct: Double, val feelsC: Double, val chillC: Double, val pressurePa: Double,
    val litterC: Double, val litterMoist: Double, val bodyC: Double,
    val waterC: Pair<Double, Double>, val waterPh: Pair<Double, Double>, val travelM: Double,
    val layout: FarmLayout = FarmLayout.DEMO,
    /** shown in the grid under the window: minimum air per bird and for the house, gas limits, vent and feet temperatures */
    val minVentCfmBird: Double = 0.0, val minVentCfm: Double = 0.0, val idealC: Double = airC,
    val nh3Max: Double = 10.0, val co2Max: Double = 3000.0,
    val ventC: Triple<Double, Double, Double>? = null, val feetC: Double? = null,
    /** breaths a minute at the house's ideal (resting birds) and where panting starts */
    val breaths: Pair<Double, Double> = 20.0 to 40.0, val pantAbove: Double = 60.0
)

/**
 * The house as the window sees it, in feet: x from the front wall along the length, y across the width.
 * Lines run along x at [lineY]; feeder pans every [panSpacingFt] from [lineStartFt], sensor pans after the
 * feed pans; nipples every [nippleSpacingFt]. Birds live up to the barricade at [birdsPerFt2].
 */
data class FarmLayout(
    val lengthFt: Double, val widthFt: Double, val lineOrder: List<Char>, val lineGapFt: Double,
    val lineStartFt: Double, val panSpacingFt: Double, val pansPerLine: Int, val sensorPans: Int,
    val patternOn: Int, val patternOff: Int, val pansInArea: Int,
    val nippleSpacingFt: Double, val drinkerLenFt: Double, val drinkerHtM: Double,
    val barricadeFt: Double, val birdsPerFt2: Double
) {
    fun panOpen(i: Int) = i < pansInArea && (i % max(1, patternOn + patternOff)) < patternOn
    fun lineY(i: Int) = (i + 0.5) * lineGapFt
    companion object {
        val DEMO = FarmLayout(299.0, 39.0, listOf('D', 'F', 'D', 'F', 'D', 'F', 'D', 'F', 'D'), 39.0 / 9, 5.0, 2.5, 112, 2,
            2, 1, 112, 0.82, 290 * 0.82, 0.16, 299.0, 1.31)
    }
}

internal const val FT = 0.3048
/** Most birds drawn at once (brooding densities can exceed it in a full-width view; the half-width view is offered then). */
internal const val MAX_BIRDS = 1800
/** The window is always this long along the house (ft); across it is the full, half or quarter house width. */
internal const val VIEW_LEN_FT = 10.0

private val NAMES = listOf("Pip", "Dot", "Hazel", "Tiko")
internal enum class Trait(val label: String) { CURIOUS("curious"), GLUTTON("big eater"), LAZY("lazy"), CHATTY("chatty"), PLAIN("") }
private val ZS = listOf(-1.1, 0.4, -0.35, 1.1)   // size spread (z-scores) of the four named birds

internal class Bird(val name: String, val trait: Trait, val z: Double, seed: Int) {
    val rnd = Random(seed)
    /** the crowd is drawn simply; the four named birds in full */
    val lite get() = trait == Trait.PLAIN
    var x = 0.0; var zz = 0.0; var yaw = rnd.nextDouble(0.0, 2 * PI)
    var state = "idle"; var t = rnd.nextDouble(0.0, 2.0); var dur = rnd.nextDouble(1.0, 4.0)
    var tx = 0.0; var tz = 0.0
    var step = 0.0; var fullness = rnd.nextDouble(0.45, 1.0)   // birds are at different points between meals
    var panI = -1; var nipI = -1; var slotA = rnd.nextDouble(0.0, 2 * PI)
    // pose (smoothed)
    var sit = 0.0; var peck = 0.0; var headYaw = 0.0; var flap = 0.0; var jump = 0.0; var swing = 0.0; var lift = 0.0
    var blink = 0.0; var nextBlink = rnd.nextDouble(1.0, 4.0); var chirp = 0.0
    // targets
    var tSit = 0.0; var tPeck = 0.0; var tHeadYaw = 0.0
    var weightG = 42.0
}

private fun smooth(a: Double, b: Double, x: Double): Double { val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0); return t * t * (3 - 2 * t) }
private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
private fun damp(c: Double, target: Double, rate: Double, dt: Double) = c + (target - c) * (1 - kotlin.math.exp(-rate * dt))

/** Body proportions (base units ≈ 10 cm for a day-old chick) by age. */
private data class Shape(val L: Double, val rx: Double, val ry: Double, val rz: Double, val hr: Double, val neckY: Double, val neckZ: Double,
                         val comb: Double, val wattle: Double, val tail: Double, val body: Color, val legs: Color)
private fun shapeFor(age: Int): Shape {
    val k = Math.pow(smooth(0.0, 42.0, age.toDouble()), 0.8)
    val plume = smooth(5.0, 20.0, age.toDouble())
    val body = lerpColor(Color(0xFFF6D45C), Color(0xFFF2EEE6), plume)
    val legs = lerpColor(Color(0xFFF2A278), Color(0xFFF2C046), smooth(0.0, 20.0, age.toDouble()))
    return Shape(lerp(0.26, 0.19, k), lerp(0.30, 0.34, k), lerp(0.29, 0.27, k), lerp(0.34, 0.42, k), lerp(0.21, 0.115, k),
        lerp(0.26, 0.30, k), lerp(0.18, 0.30, k), smooth(12.0, 42.0, age.toDouble()), smooth(16.0, 44.0, age.toDouble()),
        smooth(6.0, 30.0, age.toDouble()), body, legs)
}
private fun lerpColor(a: Color, b: Color, t: Double) = Color(
    (a.red + (b.red - a.red) * t.toFloat()), (a.green + (b.green - a.green) * t.toFloat()), (a.blue + (b.blue - a.blue) * t.toFloat()), 1f)

/** Birds within a window of the house (metres, window corner = 0,0; x along the house, z across it). */
internal class CoopSim {
    var birds: List<Bird> = emptyList()
    var layout: FarmLayout = FarmLayout.DEMO
    /** window: [lenFt] along the house × [widFt] across it, corner at ([x0Ft], [y0Ft]) in the farm, ft */
    var lenFt = VIEW_LEN_FT
    var widFt = 39.0
    var x0Ft = 0.0; var y0Ft = 0.0
    /** window size in metres: x along the house, z across it */
    val sx get() = lenFt * FT
    val sz get() = widFt * FT
    val panR = 0.165
    val tubeH = 0.42
    var nippleH = 0.16

    class Pan(val idx: Int, val x: Double, val z: Double, val open: Boolean, val sensor: Boolean) { var fill = -1.0; var arrive = 0.0; var shake = 0.0 }
    class Nipple(val x: Double, val z: Double) { var ripple = 0.0 }
    class Line(val z: Double, val x1: Double, val x2: Double, val feeder: Boolean)
    var pans: List<Pan> = emptyList()
    var nipples: List<Nipple> = emptyList()
    var lines: List<Line> = emptyList()
    /** birds stay short of the barricade (window x, m) */
    var birdMaxX = 0.0
    /** house walls inside the window: front (x = 0), back (x = sx), near side (z = 0), far side (z = sz) */
    var walls = BooleanArray(4)

    // ---- interaction and effects ----
    class Grain(val x: Double, val z: Double, var life: Double)
    /** kind 0 feed crumbs, 1 dust */
    class Particle(var x: Double, var y: Double, var z: Double, var vx: Double, var vy: Double, var vz: Double, var life: Double, val kind: Int)
    val grains = mutableListOf<Grain>()
    val particles = mutableListOf<Particle>()
    var clock = 0.0
    var selected: Bird? = null
    var selectedT = 0.0
    private val fxRnd = Random(99)
    private var builtKey: Any? = null
    private var shape = shapeFor(11)

    /** The window after a move or resize: rebuild what it holds and keep the birds that are still inside. */
    fun setup(inp: CoopInput) {
        val L = inp.layout
        layout = L
        lenFt = lenFt.coerceIn(2.0, max(2.0, L.lengthFt)); widFt = widFt.coerceIn(2.0, max(2.0, L.widthFt))
        x0Ft = x0Ft.coerceIn(0.0, max(0.0, L.lengthFt - lenFt)); y0Ft = y0Ft.coerceIn(0.0, max(0.0, L.widthFt - widFt))
        nippleH = L.drinkerHtM.coerceIn(0.10, 0.55)
        shape = shapeFor(inp.age)
        val key = listOf(L, lenFt, widFt, x0Ft, y0Ft, inp.age)
        if (key != builtKey) { builtKey = key; build(inp) }
        val cv = (inp.cvPct ?: 8.0) / 100.0
        birds.forEach { it.weightG = max(30.0, inp.meanG * (1 + cv * it.z)) }
    }

    /** Birds the window would hold at the flock's density (before the cap). */
    fun birdsFor(L: FarmLayout, lenFt: Double, widFt: Double, x0: Double): Int {
        val reach = (min(x0 + lenFt, L.barricadeFt) - x0).coerceIn(0.0, lenFt)
        return (L.birdsPerFt2 * reach * widFt).roundToInt()
    }

    private fun build(inp: CoopInput) {
        val L = inp.layout
        val sx = sx; val sz = sz
        fun lx(ft: Double) = (ft - x0Ft) * FT
        fun lz(ft: Double) = (ft - y0Ft) * FT
        val ls = mutableListOf<Line>(); val ps = mutableListOf<Pan>(); val ns = mutableListOf<Nipple>()
        L.lineOrder.forEachIndexed { i, k ->
            val z = lz(L.lineY(i))
            if (z < -0.05 || z > sz + 0.05) return@forEachIndexed
            val a = L.lineStartFt
            if (k == 'F') {
                val total = L.pansPerLine + L.sensorPans
                val x1 = lx(a); val x2 = lx(a + total * L.panSpacingFt)
                if (x2 > 0 && x1 < sx) ls += Line(z, max(x1, 0.0), min(x2, sx), true)
                val first = max(0, floor((x0Ft - a) / L.panSpacingFt).toInt() - 1)
                val last = min(total - 1, ceil((x0Ft + lenFt - a) / L.panSpacingFt).toInt() + 1)
                for (p in first..last) {
                    val x = lx(a + (p + 0.5) * L.panSpacingFt)
                    if (x < panR * 0.5 || x > sx - panR * 0.5) continue
                    val sensor = p >= L.pansPerLine
                    ps += Pan(p, x, z, sensor || L.panOpen(p), sensor)
                }
            } else {
                val x1 = lx(a); val x2 = lx(a + L.drinkerLenFt)
                if (x2 > 0 && x1 < sx) {
                    ls += Line(z, max(x1, 0.0), min(x2, sx), false)
                    val first = max(0, floor((x0Ft - a) / L.nippleSpacingFt).toInt())
                    val last = ceil((x0Ft + lenFt - a) / L.nippleSpacingFt).toInt()
                    for (q in first..last) {
                        val ft = a + (q + 0.5) * L.nippleSpacingFt
                        val x = lx(ft)
                        if (ft <= a + L.drinkerLenFt && x in 0.03..(sx - 0.03)) ns += Nipple(x, z)
                    }
                }
            }
        }
        lines = ls; pans = ps; nipples = ns
        birdMaxX = lx(L.barricadeFt).coerceIn(0.0, sx)
        walls = booleanArrayOf(x0Ft <= 0.01, x0Ft + lenFt >= L.lengthFt - 0.01, y0Ft <= 0.01, y0Ft + widFt >= L.widthFt - 0.01)
        // birds at the flock's real density on the floor they can reach in this window
        val n = birdsFor(L, lenFt, widFt, x0Ft).coerceIn(0, MAX_BIRDS)
        val rnd = Random(1234)
        val list = birds.toMutableList()
        while (list.size > n) list.removeAt(list.size - 1)
        while (list.size < n) {
            val i = list.size
            list += if (i < 4) Bird(NAMES[i], Trait.values()[i], ZS[i], 31 * i + 7)
            else Bird("", Trait.PLAIN, gauss(rnd).coerceIn(-2.5, 2.5), 1000 + i).also { it.x = -1.0 }
        }
        list.forEach { b ->
            if (b.x < 0.06 || b.x > birdMaxX - 0.06 || b.zz < 0.06 || b.zz > sz - 0.06) place(b)
            b.panI = -1; b.nipI = -1
            if (b.state.startsWith("go") || b.state == "eat" || b.state == "drink" || b.state == "peckGrain") { b.state = "idle"; b.t = 0.0; b.dur = b.rnd.nextDouble(0.5, 2.0) }
        }
        birds = list
        grains.clear()
        if (selected != null && selected !in list) selected = null
    }

    private fun gauss(r: Random): Double { val u = max(1e-9, r.nextDouble()); val v = r.nextDouble(); return sqrt(-2 * kotlin.math.ln(u)) * cos(2 * PI * v) }

    private fun place(b: Bird) {
        if (birdMaxX < 0.12) { b.x = 0.06; b.zz = sz / 2; return }
        repeat(10) {
            b.x = b.rnd.nextDouble(0.06, birdMaxX - 0.06); b.zz = b.rnd.nextDouble(0.06, sz - 0.06)
            if (pans.none { hypot(it.x - b.x, it.z - b.zz) < panR + 0.04 }) return
        }
    }

    /** Tap on a bird: it flaps and calls; its numbers show for a few seconds. */
    fun poke(b: Bird) {
        selected = b; selectedT = 6.0
        b.chirp = 1.0
        if (b.state != "sleep") { b.state = "flap"; b.t = 0.0; b.dur = 0.9 }
    }
    /** Tap on the floor: a few grains land there; birds close enough (the curious one from further) come to peck. */
    fun dropGrains(x: Double, z: Double) {
        repeat(6) { grains += Grain((x + fxRnd.nextDouble(-0.05, 0.05)).coerceIn(0.05, sx - 0.05), (z + fxRnd.nextDouble(-0.05, 0.05)).coerceIn(0.05, sz - 0.05), 12.0) }
        repeat(8) { particles += Particle(x, 0.25, z, fxRnd.nextDouble(-0.25, 0.25), 0.0, fxRnd.nextDouble(-0.25, 0.25), 0.6, 0) }
        birds.forEach { b ->
            val reach = when (b.trait) { Trait.CURIOUS -> 2.0; Trait.GLUTTON -> 1.2; Trait.LAZY -> 0.45; else -> 0.6 }
            if (b.state != "sleep" && b.state != "eat" && hypot(b.x - x, b.zz - z) <= reach) { b.state = "goGrain"; b.t = 0.0; b.dur = 12.0; b.tx = x; b.tz = z }
        }
    }
    /** Tap on a pan: it rattles; birds nearby that aren't full come over. */
    fun tapPan(i: Int) {
        val p = pans.getOrNull(i) ?: return
        p.shake = 1.0
        repeat(10) { particles += Particle(p.x, 0.08, p.z, fxRnd.nextDouble(-0.3, 0.3), fxRnd.nextDouble(0.4, 0.9), fxRnd.nextDouble(-0.3, 0.3), 0.8, 0) }
        if (!p.open || p.sensor) return
        birds.forEach { b -> if (b.state != "sleep" && b.fullness < 0.9 && hypot(b.x - p.x, b.zz - p.z) < 1.2) { b.state = "goEat"; b.panI = i; b.t = 0.0; b.dur = 20.0 } }
    }
    /** Tap on a nipple: a drop ripples and the nearest awake bird goes to drink there. */
    fun tapNipple(i: Int) {
        val n = nipples.getOrNull(i) ?: return
        n.ripple = 1.0
        birds.filter { it.state != "sleep" }.minByOrNull { hypot(it.x - n.x, it.zz - n.z) }?.let { it.state = "goDrink"; it.nipI = i; it.t = 0.0; it.dur = 20.0 }
    }
    val feederAt: Pair<Double, Double> get() = pans.firstOrNull { it.open && !it.sensor }?.let { it.x to it.z } ?: (sx / 2 to sz / 2)

    private var lastFedSeen: Long? = null
    private var fedSeenInit = false

    fun update(dt: Double, inp: CoopInput, lightLevel: Double) {
        val hunger = inp.feeder.hunger
        val feedIn = inp.feeder.levelKg > 0
        // a new feeding logged: feed runs down the line from the hopper (near pans first) and awake birds head for it
        if (!fedSeenInit) { lastFedSeen = inp.feeder.lastFedAt; fedSeenInit = true }
        else if (inp.feeder.lastFedAt != lastFedSeen) {
            lastFedSeen = inp.feeder.lastFedAt
            val lineFt = max(1.0, (layout.pansPerLine + layout.sensorPans) * layout.panSpacingFt)
            pans.forEach { p -> p.arrive = clock + 0.3 + 3.0 * ((p.idx + 0.5) * layout.panSpacingFt / lineFt); p.fill = 0.0 }
            birds.forEach { b -> b.panI = -1 }
            birds.sortedWith(compareBy<Bird>({ it.lite }, { it.fullness })).forEach { b -> if (b.state != "sleep") { b.fullness = min(b.fullness, 0.5); val i = pickPan(b); if (i >= 0) { b.state = "goEat"; b.panI = i; b.t = 0.0; b.dur = 20.0 } } }
        }
        stepEffects(dt, inp)
        val sh = shape
        val heavy = smooth(18.0, 45.0, inp.age.toDouble())
        birds.forEach { b ->
            b.t += dt
            if (!b.lite) { b.nextBlink -= dt; if (b.nextBlink < 0) { b.blink = 1.0; b.nextBlink = b.rnd.nextDouble(1.8, 4.5) }; b.blink = max(0.0, b.blink - dt * 7) }
            b.chirp = max(0.0, b.chirp - dt)
            val s = cbrt(b.weightG / 42.0) * 0.1
            b.tSit = 0.0; b.tPeck = 0.0; b.tHeadYaw = 0.0; b.swing = 0.0; b.lift = 0.0
            // fullness only rises by eating; it drops as they digest, and an empty feeder caps it
            b.fullness -= dt * when { b.trait == Trait.GLUTTON -> 0.011; b.lite -> 0.004; else -> 0.008 }
            if (b.state == "eat" && feedIn) b.fullness += dt * 0.09
            if (!feedIn) b.fullness = min(b.fullness, 1 - hunger * 0.9)
            b.fullness = b.fullness.coerceIn(0.0, 1.0)

            if (lightLevel < 0.5 && b.state != "sleep") { b.state = "sleep"; b.t = 0.0; b.dur = 9999.0 }
            if (lightLevel >= 0.5 && b.state == "sleep") { b.state = "idle"; b.t = 0.0; b.dur = b.rnd.nextDouble(0.5, 2.0) }

            fun walkTo(x: Double, z: Double, stop: Double, speedMul: Double = 1.0): Boolean {
                val dx = x - b.x; val dz = z - b.zz; val d = hypot(dx, dz)
                if (d <= stop + 1e-4) return true   // arrived (the slack stops a bird hanging a hair short of its spot)
                val want = atan2(dx, dz)
                val dy = ((want - b.yaw + PI * 3) % (2 * PI)) - PI
                b.yaw += dy.coerceIn(-4 * dt, 4 * dt)
                val speed = (0.25 + 0.2 * sqrt(s * 10)) * speedMul * (1 - heavy * 0.45)
                if (abs(dy) < 1.1) { val st = min(d, speed * dt); b.x += sin(b.yaw) * st; b.zz += cos(b.yaw) * st; b.step += dt * speed * 9 / (s * 10) }
                b.swing = sin(b.step * 2 * PI) * 0.6
                return false
            }
            val rim = panR + sh.rz * s * 0.9
            when (b.state) {
                "idle" -> { b.tHeadYaw = sin(b.t * 1.3 + b.z * 3) * 0.7 }
                "walk" -> if (walkTo(b.tx, b.tz, 0.03)) b.t = b.dur
                "peck" -> { val ph = (b.t * 2.6) % 1; b.tPeck = if (ph < 0.35) sin(ph / 0.35 * PI) else 0.15; b.tSit = 0.25 }
                "scratch" -> { val ph = (b.t * 2.4) % 1; b.lift = sin(ph * PI); b.swing = -sin(ph * 2 * PI) * 0.8; if (b.t > 1.6) { b.tPeck = 0.7 } }
                "preen" -> { b.tHeadYaw = 2.0; b.tPeck = 0.3 }
                "rest" -> { b.tSit = 1.0; b.tPeck = 0.1 }
                "flap" -> { b.flap = 1.0; b.jump = sin((b.t / 0.8).coerceIn(0.0, 1.0) * PI) * 0.03 * (1 - heavy) }
                "chirp" -> { if (b.chirp <= 0) b.chirp = 0.8; b.tHeadYaw = 0.2 }
                "goEat" -> {
                    if (b.panI < 0) b.panI = pickPan(b)
                    val p = pans.getOrNull(b.panI)
                    if (p == null) b.t = b.dur
                    else if (walkTo(p.x + sin(b.slotA) * rim, p.z + cos(b.slotA) * rim, 0.02, 1.4)) { b.state = "eat"; b.t = 0.0; b.dur = if (feedIn) b.rnd.nextDouble(6.0, 12.0) else b.rnd.nextDouble(3.0, 6.0) }
                }
                "eat" -> {
                    pans.getOrNull(b.panI)?.let { p -> b.yaw = atan2(p.x - b.x, p.z - b.zz) }
                    val ph = (b.t * 3.0) % 1; b.tPeck = if (feedIn) (if (ph < 0.4) 0.6 + 0.4 * sin(ph / 0.4 * PI) else 0.55) else 0.45 + 0.3 * sin(b.t * 1.5)
                    if (!feedIn && b.t > 2 && b.chirp <= 0 && hunger > 0.4 && !b.lite) b.chirp = 0.9
                    if (feedIn && b.fullness >= (if (b.trait == Trait.GLUTTON) 0.99 else 0.95)) b.t = b.dur
                }
                "goGrain" -> if (walkTo(b.tx, b.tz, 0.06, 1.3)) { b.state = "peckGrain"; b.t = 0.0; b.dur = b.rnd.nextDouble(2.0, 3.5) }
                "peckGrain" -> {
                    b.yaw = atan2(b.tx - b.x, b.tz - b.zz)
                    val ph = (b.t * 2.8) % 1; b.tPeck = if (ph < 0.35) 0.4 + 0.6 * sin(ph / 0.35 * PI) else 0.35; b.tSit = 0.2
                    if (ph < 0.05) grains.filter { hypot(it.x - b.x, it.z - b.zz) < 0.2 }.minByOrNull { hypot(it.x - b.tx, it.z - b.tz) }?.let { grains.remove(it); b.fullness = min(1.0, b.fullness + 0.01) }
                }
                "goDrink" -> {
                    if (b.nipI < 0) b.nipI = pickNipple(b)
                    val n = nipples.getOrNull(b.nipI)
                    if (n == null) b.t = b.dur
                    else if (walkTo(n.x, n.z + (if (b.slotA > PI) 0.05 else -0.05), 0.02)) { b.state = "drink"; b.t = 0.0; b.dur = b.rnd.nextDouble(2.5, 4.0) }
                }
                "drink" -> {
                    nipples.getOrNull(b.nipI)?.let { n -> b.yaw = atan2(n.x - b.x, n.z - b.zz); if (b.rnd.nextDouble() < dt * 1.5) n.ripple = max(n.ripple, 0.6) }
                    val ph = (b.t * 0.9) % 1; b.tPeck = if (ph < 0.4) -0.9 else -0.6
                }
                "sleep" -> { b.tSit = 1.0; b.tPeck = 0.35 }
                "slump" -> { b.tSit = 1.0; b.tPeck = 0.5; if (!b.lite && b.chirp <= 0 && b.rnd.nextDouble() < dt * 0.3) b.chirp = 0.7 }
            }
            if (!b.lite || b.rnd.nextDouble() < 0.15) {
                if (b.state == "scratch" && b.lift > 0.6 && b.rnd.nextDouble() < dt * 8) particles += Particle(b.x, 0.01, b.zz, b.rnd.nextDouble(-0.15, 0.15), b.rnd.nextDouble(0.05, 0.2), b.rnd.nextDouble(-0.15, 0.15), 0.9, 1)
                if (b.state == "eat" && feedIn && b.rnd.nextDouble() < dt * 3) particles += Particle(b.x + sin(b.yaw) * 0.12, 0.05, b.zz + cos(b.yaw) * 0.12, b.rnd.nextDouble(-0.2, 0.2), b.rnd.nextDouble(0.3, 0.6), b.rnd.nextDouble(-0.2, 0.2), 0.5, 0)
            }
            if (b.state != "flap") { b.flap = max(0.0, b.flap - dt * 3); b.jump = damp(b.jump, 0.0, 10.0, dt) }
            if (b.t >= b.dur) choose(b, inp, hunger, feedIn, heavy)
            b.sit = damp(b.sit, b.tSit, 6.0, dt); b.peck = damp(b.peck, b.tPeck, 14.0, dt); b.headYaw = damp(b.headYaw, b.tHeadYaw, 6.0, dt)
            // stay on the floor of the window, short of the barricade, and out of the pans
            b.x = b.x.coerceIn(0.06, max(0.06, birdMaxX - 0.06)); b.zz = b.zz.coerceIn(0.06, sz - 0.06)
            for (p in pans) {
                val dx = b.x - p.x; val dz = b.zz - p.z; val d = hypot(dx, dz)
                if (d < panR * 0.85 && d > 1e-6) { b.x = p.x + dx / d * panR * 0.85; b.zz = p.z + dz / d * panR * 0.85 }
            }
        }
        separate(dt)
    }

    /** Birds that fit round one pan's rim, shoulder to shoulder, at today's size. */
    private fun panCap(): Int {
        val s = cbrt(max(40.0, birds.firstOrNull()?.weightG ?: 42.0) / 42.0) * 0.1
        val rim = panR + shape.rz * s * 0.9
        return (2 * PI * rim / (2 * shape.rx * s * 1.05)).toInt().coerceIn(3, 14)
    }
    /** Nearest open feed pan with room at the rim (sometimes the next one, so the crowd spreads); -1 if all are full. */
    private fun pickPan(b: Bird): Int {
        val c = pans.indices.filter { pans[it].open && !pans[it].sensor && pans[it].x <= birdMaxX + panR }.sortedBy { hypot(pans[it].x - b.x, pans[it].z - b.zz) }
        if (c.isEmpty()) return -1
        val cap = panCap()
        val free = c.filter { i -> birds.count { o -> o !== b && o.panI == i && (o.state == "eat" || o.state == "goEat") } < cap }
        if (free.isEmpty()) return -1
        b.slotA = b.rnd.nextDouble(0.0, 2 * PI)
        return if (free.size > 1 && b.rnd.nextDouble() < 0.3) free[1] else free[0]
    }
    private fun pickNipple(b: Bird): Int {
        val c = nipples.indices.filter { nipples[it].x <= birdMaxX }.sortedBy { hypot(nipples[it].x - b.x, nipples[it].z - b.zz) }
        if (c.isEmpty()) return -1
        return c[min(c.size - 1, b.rnd.nextInt(3))]
    }

    /** Birds don't walk through each other (sweep along x so hundreds stay cheap). */
    private fun separate(dt: Double) {
        if (birds.size < 2) return
        val idx = birds.indices.sortedBy { birds[it].x }
        val k = min(1.0, dt * 12)
        for (ii in idx.indices) {
            val a = birds[idx[ii]]
            val ra = cbrt(a.weightG / 42.0) * 0.034
            var jj = ii + 1
            while (jj < idx.size) {
                val c = birds[idx[jj]]
                val dx = c.x - a.x
                if (dx > 0.3) break
                val minD = (ra + cbrt(c.weightG / 42.0) * 0.034) * 0.9
                val dz = c.zz - a.zz
                if (abs(dz) < minD) {
                    val d = hypot(dx, dz)
                    if (d in 1e-6..minD) {
                        val push = (minD - d) * 0.5 * k
                        val wa = if (a.state == "eat" || a.state == "sleep" || a.state == "drink") 0.2 else 1.0
                        val wc = if (c.state == "eat" || c.state == "sleep" || c.state == "drink") 0.2 else 1.0
                        a.x -= dx / d * push * wa; a.zz -= dz / d * push * wa; c.x += dx / d * push * wc; c.zz += dz / d * push * wc
                    }
                }
                jj++
            }
        }
    }

    private fun stepEffects(dt: Double, inp: CoopInput) {
        clock += dt
        selectedT = max(0.0, selectedT - dt); if (selectedT <= 0) selected = null
        grains.forEach { it.life -= dt }; grains.removeAll { it.life <= 0 }
        val target = inp.feeder.fillFrac.coerceIn(0.0, 1.0)
        pans.forEach { p ->
            val want = if (p.open) target else 0.0
            if (p.fill < 0) p.fill = want else if (clock >= p.arrive) p.fill = damp(p.fill, want, 1.6, dt)
            p.shake = max(0.0, p.shake - dt * 1.6)
        }
        nipples.forEach { it.ripple = max(0.0, it.ripple - dt * 0.8) }
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val q = iter.next()
            q.life -= dt
            if (q.kind == 1) q.vy -= dt * 0.1 else q.vy -= dt * 3.2
            q.x += q.vx * dt; q.y += q.vy * dt; q.z += q.vz * dt
            if (q.y < 0.0) { q.y = 0.0; q.vx *= 0.3; q.vz *= 0.3; q.vy = 0.0 }
            if (q.life <= 0) iter.remove()
        }
        if (particles.size > 220) particles.subList(0, particles.size - 220).clear()
    }

    private fun choose(b: Bird, inp: CoopInput, hunger: Double, feedIn: Boolean, heavy: Double) {
        b.t = 0.0
        val r = b.rnd.nextDouble()
        // hunger comes first: go to a pan, and once very hungry, slump with the head low
        if (feedIn && b.fullness < when (b.trait) { Trait.GLUTTON -> 0.95; else -> 0.8 }) { b.panI = pickPan(b); if (b.panI >= 0) { b.state = "goEat"; b.dur = 20.0; return } }
        if (!feedIn && hunger > 0.75 && r < 0.6) { b.state = "slump"; b.dur = b.rnd.nextDouble(5.0, 10.0); return }
        if (!feedIn && hunger > 0.3 && r < 0.55 + hunger * 0.3) { b.panI = pickPan(b); if (b.panI >= 0) { b.state = "goEat"; b.dur = 20.0; return } }
        val w = mutableListOf(
            "idle" to 2.0, "walk" to (1.5 + (if (b.trait == Trait.CURIOUS) 3.0 else 0.0)) * (1 - heavy * 0.6),
            "peck" to 2.0, "scratch" to 1.5 * (1 - heavy * 0.5), "preen" to 1.0 + heavy,
            "rest" to (0.4 + heavy * 3.0) * (if (b.trait == Trait.LAZY) 3.0 else 1.0) * (1 + hunger),
            "flap" to 0.6 * (1 - heavy), "chirp" to (0.6 + (if (b.trait == Trait.CHATTY) 2.5 else 0.0)) * (1 + hunger * 2) * (if (b.lite) 0.3 else 1.0),
            "goDrink" to 0.6
        )
        val sum = w.sumOf { it.second }; var pick = b.rnd.nextDouble() * sum
        for ((s, wt) in w) { pick -= wt; if (pick <= 0) { b.state = s; break } }
        when (b.state) {
            "walk" -> {
                // a short wander, within the allowed travel radius and inside the window
                val rad = min(inp.travelM, min(sx, sz) * 0.5) * (if (b.trait == Trait.CURIOUS) 1.0 else 0.5)
                val a = b.rnd.nextDouble(0.0, 2 * PI); val d = b.rnd.nextDouble(0.1, 1.0) * rad
                b.tx = (b.x + sin(a) * d).coerceIn(0.1, max(0.1, birdMaxX - 0.1)); b.tz = (b.zz + cos(a) * d).coerceIn(0.1, sz - 0.1); b.dur = 15.0
            }
            "rest" -> b.dur = b.rnd.nextDouble(4.0, 8.0) + heavy * 8
            "goDrink" -> { b.nipI = pickNipple(b); if (b.nipI < 0) { b.state = "idle"; b.dur = 2.0 } else b.dur = 20.0 }
            "flap" -> b.dur = 1.0
            else -> b.dur = b.rnd.nextDouble(1.5, 3.5)
        }
    }
}

/** View of the window: turned by [yaw], looked at from [elev] above the floor, [zoom] × the fitted size. */
internal class CoopCam {
    /** home view: the window's long side (across the house) runs left to right, seen from fairly high */
    val homeYaw = PI / 4; val homeElev = 0.95
    var yaw = homeYaw; var elev = homeElev; var zoom = 1.0
    var yawVel = 0.0
    var resetting = false
    fun step(dt: Double) {
        if (resetting) {
            yaw = damp(yaw, homeYaw, 6.0, dt); elev = damp(elev, homeElev, 6.0, dt); zoom = damp(zoom, 1.0, 6.0, dt)
            if (abs(yaw - homeYaw) < 1e-3 && abs(elev - homeElev) < 1e-3 && abs(zoom - 1.0) < 1e-3) { yaw = homeYaw; elev = homeElev; zoom = 1.0; resetting = false }
        } else if (abs(yawVel) > 1e-3) { yaw += yawVel * dt; yawVel *= kotlin.math.exp(-3.0 * dt) }
    }
}
internal const val ISO_ELEV = 0.6154797      // atan(1/√2): true isometric
private const val SQRT2 = 1.4142135623730951

/** World (x, y, z in metres, floor = y 0) to screen, for one frame's size and camera; and back onto the floor. */
internal class CoopProj(val sx: Double, val sz: Double, val wallH: Double, w: Float, h: Float, cam: CoopCam) {
    private val cy = cos(cam.yaw); private val sy = sin(cam.yaw)
    private val se = sin(cam.elev); private val ce = cos(cam.elev)
    private val c = sx / 2
    private val cz = sz / 2
    /** px per metre: the window fits the canvas in the home view (turning keeps the scale; the canvas clips) */
    val S: Double = run {
        val hy = cam.homeYaw; val hc = cos(hy); val hs = sin(hy)
        val corners = listOf(-c to -cz, c to -cz, c to cz, -c to cz).map { (dx, dz) -> val xr = dx * hc - dz * hs; val zr = dx * hs + dz * hc; (xr - zr) / SQRT2 to (xr + zr) / SQRT2 }
        val wSpan = corners.maxOf { it.first } - corners.minOf { it.first }
        val vSpan = (corners.maxOf { it.second } - corners.minOf { it.second }) * se + wallH * ce
        cam.zoom * min(w * 0.92 / wSpan, h * 0.6 / vSpan)
    }
    private val ox = w / 2.0
    private val oy = h * 0.52 + S * wallH * ce * 0.45
    /** vertical squash of circles lying on the floor */
    val flat = se
    /** world direction toward the viewer (x, z) */
    val camX = (cy + sy) / SQRT2; val camZ = (cy - sy) / SQRT2
    private fun rot(x: Double, z: Double): Pair<Double, Double> { val dx = x - c; val dz = z - cz; return Pair(dx * cy - dz * sy, dx * sy + dz * cy) }
    fun P(x: Double, y: Double, z: Double): Offset {
        val (xr, zr) = rot(x, z)
        val u = (xr - zr) / SQRT2; val v = (xr + zr) / SQRT2
        return Offset((ox + S * u).toFloat(), (oy + S * (v * se - y * ce)).toFloat())
    }
    /** larger = nearer the viewer */
    fun depth(x: Double, z: Double): Double { val (xr, zr) = rot(x, z); return (xr + zr) / SQRT2 }
    fun floorAt(p: Offset): Pair<Double, Double> {
        val u = (p.x - ox) / S; val v = (p.y - oy) / S / se
        val xr = (u + v) / SQRT2; val zr = (v - u) / SQRT2
        return Pair(c + xr * cy + zr * sy, cz - xr * sy + zr * cy)
    }
}

private fun stateWord(s: String) = when (s) {
    "eat" -> "eating"; "goEat" -> "to feeder"; "drink" -> "drinking"; "goDrink" -> "to drinker"; "rest" -> "resting"
    "sleep" -> "asleep"; "walk" -> "walking"; "peck", "peckGrain" -> "pecking"; "goGrain" -> "to grain"; "scratch" -> "scratching"
    "preen" -> "preening"; "flap" -> "flapping"; "chirp" -> "calling"; "slump" -> "hungry"; else -> "standing"
}

/**
 * A window onto the house — 10 ft along it, the full or half width across — with the real feeder and drinker lines, the
 * pans on and off, feed running down the line after a feeding, and the birds at the flock's real
 * density; four of them are named and followed. Drag sideways to turn it, two fingers to tilt and
 * zoom, double-tap to square it up; tap a bird, a pan, a nipple or the floor. The map under it is the
 * whole house at its real shape: drag or tap it to move the window.
 */
@Composable
fun Coop3D(inp: CoopInput, modifier: Modifier = Modifier) {
    val sim = remember { CoopSim() }
    val L = inp.layout
    // the window: 10 ft along the house; the full, half or quarter width across; starts a quarter into the birds' area
    var mode by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }   // 0 full · 1 half · 2 quarter
    fun widthOf(m: Int) = L.widthFt / (1 shl m)
    val midF = L.lineOrder.withIndex().filter { it.value == 'F' }.let { it.getOrNull(it.size / 2)?.index ?: 0 }
    var x0 by rememberSaveable { mutableDoubleStateOf(-1.0) }
    var y0 by rememberSaveable { mutableDoubleStateOf(-1.0) }
    // a view that would hold too many birds to draw (dense brooding) steps down to a narrower one
    while (mode < 2 && sim.birdsFor(L, VIEW_LEN_FT, widthOf(mode), max(0.0, x0)) > MAX_BIRDS) mode++
    val full = mode == 0
    val lenFt = min(VIEW_LEN_FT, L.lengthFt)
    val widFt = widthOf(mode)
    if (x0 < 0) x0 = (min(L.barricadeFt, L.lengthFt) * 0.25 - lenFt / 2).coerceIn(0.0, max(0.0, L.lengthFt - lenFt))
    if (full) y0 = 0.0 else if (y0 < 0) y0 = (L.lineY(midF) - widFt / 2).coerceIn(0.0, max(0.0, L.widthFt - widFt))
    y0 = y0.coerceIn(0.0, max(0.0, L.widthFt - widFt))
    sim.lenFt = lenFt; sim.widFt = widFt; sim.x0Ft = x0; sim.y0Ft = y0
    sim.setup(inp)
    // the frame loop starts once: read the latest input (new feedings, lights) through this, not the first one
    val cur = androidx.compose.runtime.rememberUpdatedState(inp)
    val cam = remember { CoopCam() }
    var tick by remember { mutableLongStateOf(0L) }
    val slow by remember { derivedStateOf { tick / 400_000_000L } }
    // lights follow the programme; "Wake" turns them on in the animation until tapped again
    var awake by remember { androidx.compose.runtime.mutableStateOf(false) }
    fun lightNow(): Double {
        val i = cur.value
        val z = java.time.ZonedDateTime.now(i.zoneId)
        return if (awake) 1.0 else i.light.level(z.hour + z.minute / 60.0 + z.second / 3600.0)
    }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016 else ((now - last) / 1e9).coerceAtMost(0.05)
                last = now
                sim.update(dt, cur.value, lightNow())
                cam.step(dt)
                tick = now
            }
        }
    }
    val scheduledDark = run { val z = java.time.ZonedDateTime.now(inp.zoneId); !inp.light.isLight(z.hour + z.minute / 60.0) }
    Column(modifier) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val h = (maxWidth * (when (mode) { 0 -> 0.72f; 1 -> 0.82f; else -> 0.9f })).coerceIn(250.dp, 460.dp)
            Canvas(
                Modifier.fillMaxWidth().height(h).clipToBounds().testTag("coop")
                    // two fingers: tilt (up/down), zoom (pinch) and turn; one finger up/down still scrolls the page
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val ev = awaitPointerEvent()
                                if (ev.changes.count { it.pressed } >= 2) {
                                    val pan = ev.calculatePan()
                                    cam.resetting = false
                                    cam.zoom = (cam.zoom * ev.calculateZoom()).coerceIn(0.7, 4.0)
                                    cam.elev = (cam.elev + pan.y * 0.004).coerceIn(0.22, 1.35)
                                    cam.yaw += pan.x * 0.008
                                    ev.changes.forEach { it.consume() }
                                }
                            } while (ev.changes.any { it.pressed })
                        }
                    }
                    .pointerInput(Unit) {
                        var lastDx = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { cam.resetting = false; cam.yawVel = 0.0 },
                            onDragEnd = { cam.yawVel = (lastDx * 0.01 * 60).toDouble().coerceIn(-6.0, 6.0) },
                        ) { change, dx -> change.consume(); lastDx = dx; cam.yaw += dx * 0.01 }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { cam.yawVel = 0.0; cam.resetting = true },
                            onTap = { at ->
                                val pr = CoopProj(sim.sx, sim.sz, 0.5, size.width.toFloat(), size.height.toFloat(), cam)
                                val sh = shapeFor(cur.value.age)
                                val bird = sim.birds.map { b ->
                                    val sc = cbrt(b.weightG / 42.0) * 0.1
                                    b to (pr.P(b.x, (sh.L + sh.ry) * sc, b.zz) - at).getDistance()
                                }.filter { it.second < max(16.dp.toPx(), (0.12 * pr.S).toFloat()) }.minByOrNull { it.second }?.first
                                val pan = sim.pans.indices.map { it to (pr.P(sim.pans[it].x, 0.05, sim.pans[it].z) - at).getDistance() }
                                    .filter { it.second < (sim.panR * pr.S).toFloat() + 8.dp.toPx() }.minByOrNull { it.second }?.first
                                val nip = sim.nipples.indices.map { it to (pr.P(sim.nipples[it].x, sim.nippleH, sim.nipples[it].z) - at).getDistance() }
                                    .filter { it.second < 12.dp.toPx() }.minByOrNull { it.second }?.first
                                when {
                                    bird != null -> sim.poke(bird)
                                    pan != null -> sim.tapPan(pan)
                                    nip != null -> sim.tapNipple(nip)
                                    else -> {
                                        val (x, z) = pr.floorAt(at)
                                        if (x in 0.05..sim.sx - 0.05 && z in 0.05..sim.sz - 0.05) sim.dropGrains(x, z)
                                    }
                                }
                            }
                        )
                    }
            ) {
                @Suppress("UNUSED_VARIABLE") val frame = tick
                val pr = CoopProj(sim.sx, sim.sz, 0.5, size.width, size.height, cam)
                drawCoop(sim, inp, lightNow().toFloat(), java.time.ZonedDateTime.now(inp.zoneId), awake, pr)
            }
            if (scheduledDark || awake) {
                androidx.compose.material3.OutlinedButton(
                    onClick = { awake = !awake },
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                    modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter).padding(top = 6.dp).height(32.dp)
                ) { Text(if (awake) "Sleep" else "Wake", color = Color.White) }
            }
        }
        // window size, and the whole house with the window on it
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..2).forEach { m ->
                val w = widthOf(m)
                val n = sim.birdsFor(L, lenFt, w, x0)
                val fits = n <= MAX_BIRDS
                val on = m == mode
                ValueChip(vt("${Fmt.n(w, 1)} ft", if (!fits) ValueKind.MAX else if (on) ValueKind.PRESENT else ValueKind.PREDICTED,
                    listOf("Full", "Half", "Quarter")[m] + " · " + (if (fits) Fmt.i(n) else "too many")),
                    Modifier.weight(1f).testTag("coopWidth_$m").border(if (on) 2.dp else 0.dp, if (on) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable(enabled = fits) {
                            mode = m
                            y0 = if (m == 0) 0.0 else (L.lineY(midF) - w / 2).coerceIn(0.0, max(0.0, L.widthFt - w))
                        })
            }
        }
        FarmMiniMap(L, x0, y0, lenFt, widFt, Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) { nx, ny -> x0 = nx; if (!full) y0 = ny }
        // the birds' and the house's numbers for today, under the window's size and map
        CoopStats(inp)
        // the four followed birds, in one table
        @Suppress("UNUSED_VARIABLE") val refresh = slow
        val named = sim.birds.filter { !it.lite }
        if (named.isNotEmpty()) Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val lab = MaterialTheme.typography.labelMedium
            val num = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            named.forEach { b ->
                Row(Modifier.fillMaxWidth()) {
                    Text(b.name, Modifier.width(52.dp), style = lab, color = Color.White)
                    Text(b.trait.label, Modifier.weight(1.2f), style = lab, color = Color.White.copy(alpha = 0.55f), maxLines = 1)
                    Text(Fmt.n(b.weightG, 1) + " g", Modifier.weight(1f), style = num, color = ValuePresent, maxLines = 1)
                    Text(Fmt.n(b.fullness * 100, 1) + "%", Modifier.weight(0.8f), style = num, color = ValuePredicted, maxLines = 1)
                    Text(stateWord(b.state), Modifier.weight(1.1f), style = lab, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
                }
            }
        }
    }
}

/**
 * Today's numbers for the birds in the window, all at the house's ideal (there are no sensors): weight,
 * the temperature to hold, minimum air, gas limits, vent and feet temperatures and breathing.
 */
@Composable
private fun CoopStats(inp: CoopInput) {
    data class Cell(val label: String, val value: String, val unit: String, val col: Color)
    val v = inp.ventC
    val cells = listOf(
        Cell("Weight", Fmt.n(inp.meanG, 1), "g", ValuePresent),
        Cell("Ideal temp", Fmt.n(inp.idealC, 1), "°C", ValueIdeal),
        Cell("Min vent / bird", Fmt.n(inp.minVentCfmBird, 3), "cfm", ValuePredicted),
        Cell("Min vent, house", Fmt.n(inp.minVentCfm, 1), "cfm", ValuePredicted),
        Cell("NH₃ under", Fmt.n(inp.nh3Max, 1), "ppm", ValueMax),
        Cell("CO₂ under", Fmt.n(inp.co2Max, 1), "ppm", ValueMax),
        Cell("Vent temp", if (v == null) "NA" else "${Fmt.n(v.first, 1)}–${Fmt.n(v.third, 1)}", "°C", ValueIdeal),
        Cell("Feet", inp.feetC?.let { Fmt.n(it, 1) } ?: "NA", "°C", ValueIdeal),
        Cell("Breaths", "${Fmt.n(inp.breaths.first, 1)}–${Fmt.n(inp.breaths.second, 1)}", "/min", ValueIdeal),
        Cell("Panting over", Fmt.n(inp.pantAbove, 1), "/min", ValueMax)
    )
    val lab = MaterialTheme.typography.labelSmall
    val num = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp).testTag("coopStats"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Day ${inp.age} · targets (weight is measured)", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
            InfoButton("window")
        }
        cells.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { c ->
                    Column(Modifier.weight(1f).border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 4.dp)) {
                        Text(c.label, style = lab, color = Color.White.copy(alpha = 0.55f), maxLines = 1)
                        Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                            Text(c.value, style = num, color = c.col, maxLines = 1, softWrap = false)
                            Text(" " + c.unit, style = lab, color = Color.White.copy(alpha = 0.55f), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** The whole house at its real shape: feeder and drinker lines, the barricade, and the window (drag or tap to move it). */
@Composable
private fun FarmMiniMap(L: FarmLayout, x0: Double, y0: Double, lenFt: Double, widFt: Double, modifier: Modifier = Modifier, onMove: (Double, Double) -> Unit) {
    val cx by androidx.compose.runtime.rememberUpdatedState(x0)
    val cy by androidx.compose.runtime.rememberUpdatedState(y0)
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth()) {
        val wDp = maxWidth.value
        val hDp = (wDp * L.widthFt / L.lengthFt).toFloat().coerceIn(40f, 120f)
        fun clampMove(nx: Double, ny: Double) = onMove(nx.coerceIn(0.0, max(0.0, L.lengthFt - lenFt)), ny.coerceIn(0.0, max(0.0, L.widthFt - widFt)))
        Canvas(
            Modifier.fillMaxWidth().height(hDp.dp).testTag("farmMap")
                .pointerInput(lenFt, widFt, L) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val ftPerPx = L.lengthFt / size.width
                        clampMove(cx + drag.x * ftPerPx, cy + drag.y * L.widthFt / size.height)
                    }
                }
                .pointerInput(lenFt, widFt, L) {
                    detectTapGestures { at -> clampMove(at.x / size.width * L.lengthFt - lenFt / 2, at.y / size.height * L.widthFt - widFt / 2) }
                }
        ) {
            val sx = size.width / L.lengthFt.toFloat(); val sy = size.height / L.widthFt.toFloat()
            drawRect(Color.White.copy(alpha = 0.5f), Offset.Zero, size, style = Stroke(1.2f))
            drawRect(ValueIdeal.copy(alpha = 0.07f), Offset.Zero, Size((L.barricadeFt * sx).toFloat(), size.height))
            if (L.barricadeFt < L.lengthFt - 0.5) drawLine(ValuePredicted, Offset((L.barricadeFt * sx).toFloat(), 0f), Offset((L.barricadeFt * sx).toFloat(), size.height), 2f)
            L.lineOrder.forEachIndexed { i, k ->
                val y = (L.lineY(i) * sy).toFloat()
                if (k == 'F') drawLine(ValuePredicted.copy(alpha = 0.8f), Offset((L.lineStartFt * sx).toFloat(), y), Offset(((L.lineStartFt + (L.pansPerLine + L.sensorPans) * L.panSpacingFt) * sx).toFloat(), y), 1.6f)
                else drawLine(ValueMin.copy(alpha = 0.6f), Offset((L.lineStartFt * sx).toFloat(), y), Offset(((L.lineStartFt + L.drinkerLenFt) * sx).toFloat(), y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f)))
            }
            val wx = (x0 * sx).toFloat(); val wy = (y0 * sy).toFloat()
            val ww = max(6f, (lenFt * sx).toFloat()); val wh = max(6f, (widFt * sy).toFloat())
            drawRect(Color.White.copy(alpha = 0.18f), Offset(wx, wy), Size(ww, wh))
            drawRect(Color.White, Offset(wx, wy), Size(ww, wh), style = Stroke(2f))
        }
    }
}

private fun DrawScope.drawCoop(sim: CoopSim, inp: CoopInput, light: Float, now: java.time.ZonedDateTime, awake: Boolean, pr: CoopProj) {
    val sx = sim.sx; val sz = sim.sz
    fun P(x: Double, y: Double, z: Double) = pr.P(x, y, z)
    val S = pr.S
    val line = Color.White.copy(alpha = 0.55f)
    val faint = Color.White.copy(alpha = 0.14f)
    fun quad(a: Offset, b: Offset, c: Offset, d: Offset) = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close() }
    // house walls that fall inside the window
    val wallH = 0.55
    listOf(
        Triple(0.0 to 0.0, 0.0 to sz, sim.walls[0]), Triple(sx to 0.0, sx to sz, sim.walls[1]),
        Triple(0.0 to 0.0, sx to 0.0, sim.walls[2]), Triple(0.0 to sz, sx to sz, sim.walls[3])
    ).filter { it.third }.forEach { (p0, p1, _) ->
        drawPath(quad(P(p0.first, 0.0, p0.second), P(p1.first, 0.0, p1.second), P(p1.first, wallH, p1.second), P(p0.first, wallH, p0.second)), line, style = Stroke(1.2f))
    }
    // floor: litter tone follows the lights; past the barricade it's darker (no birds there)
    val floor = quad(P(0.0, 0.0, 0.0), P(sx, 0.0, 0.0), P(sx, 0.0, sz), P(0.0, 0.0, sz))
    drawPath(floor, Color(0xFF2A2218).copy(alpha = 0.20f + 0.45f * light))
    clipPath(floor) {
        if (sim.birdMaxX < sx) drawPath(quad(P(sim.birdMaxX, 0.0, 0.0), P(sx, 0.0, 0.0), P(sx, 0.0, sz), P(sim.birdMaxX, 0.0, sz)), Color.Black.copy(alpha = 0.55f))
        // a line every foot for scale
        var g = FT
        while (g < sx - 1e-6) { drawLine(faint, P(g, 0.0, 0.0), P(g, 0.0, sz), 1f); g += FT }
        g = FT
        while (g < sz - 1e-6) { drawLine(faint, P(0.0, 0.0, g), P(sx, 0.0, g), 1f); g += FT }
        val rnd = Random(7)
        repeat(160) { val p = P(rnd.nextDouble(0.02, sx - 0.02), 0.0, rnd.nextDouble(0.02, sz - 0.02)); drawCircle(Color(0xFFC9A36B).copy(alpha = 0.10f + 0.22f * light), 1.3f, p) }
        sim.grains.forEach { gr -> drawCircle(Color(0xFFE2BC6A).copy(alpha = (0.4f + 0.6f * light) * min(1.0, gr.life / 2).toFloat()), max(1.8f, (0.012 * S).toFloat()), P(gr.x, 0.0, gr.z)) }
        // allowed walk around the pan nearest the middle of the window
        sim.pans.filter { it.open && !it.sensor }.minByOrNull { hypot(it.x - sx / 2, it.z - sz / 2) }?.let { p ->
            val ring = Path(); val R = inp.travelM
            for (i in 0..72) { val a = i / 72.0 * 2 * PI; val q = P(p.x + sin(a) * R, 0.0, p.z + cos(a) * R); if (i == 0) ring.moveTo(q.x, q.y) else ring.lineTo(q.x, q.y) }
            drawPath(ring, ValueIdeal.copy(alpha = 0.45f), style = Stroke(1.3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
        }
    }
    drawPath(floor, line, style = Stroke(1.2f))
    // barricade: a low fence
    if (sim.birdMaxX < sx - 0.01) {
        val bx = sim.birdMaxX
        drawLine(ValuePredicted.copy(alpha = 0.8f), P(bx, 0.25, 0.0), P(bx, 0.25, sz), 2f)
        var zf = 0.0
        while (zf <= sz + 1e-6) { drawLine(ValuePredicted.copy(alpha = 0.6f), P(bx, 0.0, zf), P(bx, 0.25, zf), 1.2f); zf += 0.3 }
    }

    fun ellipseAt(x: Double, y: Double, z: Double, r: Double, col: Color, stroke: Boolean = false, w: Float = 1.2f) {
        val c = P(x, y, z); val rx = (r * S).toFloat(); val ry = (r * S * pr.flat).toFloat()
        if (stroke) drawOval(col, Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2), style = Stroke(w)) else drawOval(col, Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2))
    }
    // lines: feeder tubes above the pans, drinker pipes at head height with nipples
    sim.lines.forEach { l ->
        if (l.feeder) drawLine(Color.White.copy(alpha = 0.6f), P(l.x1, sim.tubeH, l.z), P(l.x2, sim.tubeH, l.z), max(1.5f, (0.02 * S).toFloat()))
        else drawLine(ValueMin.copy(alpha = 0.8f), P(l.x1, sim.nippleH + 0.03, l.z), P(l.x2, sim.nippleH + 0.03, l.z), max(1.2f, (0.012 * S).toFloat()))
    }
    sim.nipples.forEach { n ->
        drawLine(ValueMin.copy(alpha = 0.8f), P(n.x, sim.nippleH + 0.03, n.z), P(n.x, sim.nippleH, n.z), 1.2f)
        drawCircle(ValueMin, max(1.2f, (0.008 * S).toFloat()), P(n.x, sim.nippleH, n.z))
        if (n.ripple > 0) ellipseAt(n.x, 0.003, n.z, 0.02 + 0.05 * (1 - n.ripple), ValueMin.copy(alpha = n.ripple.toFloat()), stroke = true, w = 1f)
    }
    // pans: open ones hold feed (filling along the line after a feeding), closed ones are shut, sensor pans marked
    sim.pans.sortedBy { pr.depth(it.x, it.z) }.forEach { p ->
        val wob = sin(sim.clock * 38) * p.shake * 0.012
        val px = p.x + wob
        drawLine(Color.White.copy(alpha = 0.45f), P(px, 0.06, p.z), P(px, sim.tubeH, p.z), max(1.2f, (0.012 * S).toFloat()))
        if (p.open && p.fill > 0.01) ellipseAt(px, 0.02 + 0.03 * p.fill, p.z, sim.panR * 0.86, Color(0xFFD8B064).copy(alpha = (0.2 + 0.8 * p.fill).toFloat()))
        ellipseAt(px, 0.06, p.z, sim.panR, if (p.open) line else Color.White.copy(alpha = 0.25f), stroke = true, w = if (p.open) 1.4f else 1f)
        if (!p.open) ellipseAt(px, 0.045, p.z, sim.panR * 0.55, Color.White.copy(alpha = 0.18f), stroke = true, w = 1f)
        if (p.sensor) { val c = P(px, 0.12, p.z); val r = max(3f, (0.03 * S).toFloat()); drawRect(Color.White, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(1.2f)) }
    }
    // crumbs and dust
    sim.particles.forEach { q ->
        val c = P(q.x, q.y, q.z)
        if (q.kind == 1) drawCircle(Color(0xFFB59A74).copy(alpha = (0.35 * min(1.0, q.life) * light).toFloat()), (0.015 * S * (1.8 - q.life)).toFloat().coerceAtLeast(1.5f), c)
        else drawCircle(Color(0xFFE2BC6A).copy(alpha = (0.5f + 0.5f * light) * min(1.0, q.life * 3).toFloat()), max(1.3f, (0.008 * S).toFloat()), c)
    }

    // birds, far to near: the crowd simply, the named four in full
    val sh = shapeFor(inp.age)
    val k = (S / 1.2247).toFloat()
    val sel = sim.selected
    if (sel != null) ellipseAt(sel.x, 0.0, sel.zz, 0.07 * cbrt(sel.weightG / 42.0), ValuePresent.copy(alpha = 0.9f), stroke = true, w = 1.8f)
    val nameAt = mutableListOf<Pair<Bird, Offset>>()
    // big crowds are drawn more simply so the view stays smooth
    val lod = when { sim.birds.size <= 350 -> 2; sim.birds.size <= 900 -> 1; else -> 0 }
    sim.birds.sortedBy { pr.depth(it.x, it.zz) }.forEach { b ->
        val at = drawBird(b, sh, light, ::P, k, pr.camX, pr.camZ, pr.flat, lod)
        if (!b.lite) nameAt += b to at
    }
    // names above the named heads, nudged up so two never overlap
    val np = Paint().apply { isAntiAlias = true; textSize = 11f * density; color = Color.White.copy(alpha = 0.85f).toArgb(); textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    val placed = mutableListOf<androidx.compose.ui.geometry.Rect>()
    nameAt.sortedByDescending { it.second.y }.forEach { (b, at) ->
        val w = np.measureText(b.name) + 6f; val hh = np.textSize + 3f
        var r = androidx.compose.ui.geometry.Rect(at.x - w / 2, at.y - hh, at.x + w / 2, at.y)
        var guard = 0
        while (placed.any { it.overlaps(r) } && guard++ < 6) r = r.translate(0f, -hh)
        placed += r
        drawRoundRect(Color.Black.copy(alpha = 0.55f), r.topLeft, r.size, androidx.compose.ui.geometry.CornerRadius(4f, 4f))
        drawContext.canvas.nativeCanvas.drawText(b.name, r.center.x, r.bottom - 4f, np)
    }
    // numbers of a tapped bird, next to it
    if (sel != null) {
        val lp = Paint().apply { isAntiAlias = true; textSize = (size.width / 30f).coerceIn(11f * density, 15f * density); typeface = Typeface.MONOSPACE }
        val sc = cbrt(sel.weightG / 42.0) * 0.1
        val anchor = P(sel.x, (sh.L + sh.ry * 2 + sh.neckY + sh.hr * 3) * sc, sel.zz)
        val mean = inp.meanG
        val grey = Color.White.copy(alpha = 0.6f)
        val rows = listOf(
            listOf((if (sel.lite) "Bird" else sel.name) + " " to Color.White, sel.trait.label to grey),
            listOf(Fmt.n(sel.weightG, 1) to ValuePresent, " g  " to grey, (if (sel.weightG >= mean) "+" else "") + Fmt.n((sel.weightG / mean - 1) * 100, 1) to ValuePresent, "%" to grey),
            listOf("full " to grey, Fmt.n(sel.fullness * 100, 1) to ValuePredicted, "%  " to grey, stateWord(sel.state) to grey)
        )
        val lh2 = lp.textSize * 1.3f
        val wBox = rows.maxOf { r -> r.sumOf { lp.measureText(it.first).toDouble() } }.toFloat() + 16f
        val hBox = lh2 * rows.size + 10f
        val bx = (anchor.x - wBox / 2).coerceIn(4f, size.width - wBox - 4f)
        val by = (anchor.y - hBox - 6f).coerceIn(4f, size.height - hBox - 4f)
        drawRoundRect(Color.Black.copy(alpha = 0.8f), Offset(bx, by), Size(wBox, hBox), androidx.compose.ui.geometry.CornerRadius(8f, 8f))
        drawRoundRect(Color.White.copy(alpha = 0.6f), Offset(bx, by), Size(wBox, hBox), androidx.compose.ui.geometry.CornerRadius(8f, 8f), style = Stroke(1f))
        rows.forEachIndexed { i, r ->
            var x = bx + 8f
            r.forEach { (t, col) -> lp.color = col.toArgb(); drawContext.canvas.nativeCanvas.drawText(t, x, by + 5f + lh2 * (i + 0.8f), lp); x += lp.measureText(t) }
        }
    }

    // text: grey labels, coloured numbers (ideal values where there is no sensor)
    val paint = Paint().apply { isAntiAlias = true; textSize = (size.width / 31f).coerceIn(10.5f * density, 14f * density); typeface = Typeface.MONOSPACE }
    val grey = Color.White.copy(alpha = 0.6f)
    fun seg(parts: List<Pair<String, Color>>, x: Float, y: Float, right: Boolean) {
        val widths = parts.map { paint.measureText(it.first) }
        var cx0 = if (right) x - widths.sum() else x
        parts.forEachIndexed { i, (t, c) -> paint.color = c.toArgb(); paint.textAlign = Paint.Align.LEFT; drawContext.canvas.nativeCanvas.drawText(t, cx0, y, paint); cx0 += widths[i] }
    }
    val lh = paint.textSize * 1.35f; val pad = 9.dp.toPx()
    val ideal = ValueIdeal
    val rx = size.width - pad
    seg(listOf("View " to grey, "${Fmt.n(sim.widFt, 1)} × ${Fmt.n(sim.lenFt, 1)}" to Color.White, " ft" to grey), pad, pad + lh, false)
    seg(listOf(Fmt.i(sim.birds.size) to ValuePresent, " birds" to grey), pad, pad + lh * 2, false)
    seg(listOf(Fmt.n(inp.layout.birdsPerFt2, 2) to ValuePresent, " /ft²" to grey), pad, pad + lh * 3, false)
    seg(listOf("Air " to grey, Fmt.n(inp.airC, 1) to ideal, "°  " to grey, Fmt.n(inp.rhPct, 1) to ideal, "%" to grey), rx, pad + lh, true)
    seg(listOf("Feels " to grey, Fmt.n(inp.feelsC, 1) to ideal, "°" to grey), rx, pad + lh * 2, true)
    seg(listOf("Static " to grey, Fmt.n(inp.pressurePa, 1) to ideal, " Pa" to grey), rx, pad + lh * 3, true)
    val by = size.height - pad
    seg(listOf("Litter " to grey, Fmt.n(inp.litterC, 1) to ideal, "°  " to grey, Fmt.n(inp.litterMoist, 1) to ideal, "%" to grey), pad, by - lh * 2, false)
    seg(listOf("Body " to grey, Fmt.n(inp.bodyC, 1) to ideal, "°" to grey), pad, by - lh, false)
    seg(listOf(if (awake) "Lights on (woken)" to grey else ("Dark " to grey), if (awake) "" to grey else "${hhmm(inp.light.darkStartHour)}–${hhmm(inp.light.darkEndHour)}" to ideal), pad, by, false)
    val f = inp.feeder
    val feederCol = if (f.levelKg <= 0 && f.hunger > 0.6) ValueMax else ValuePredicted
    when {
        f.lastFedAt == null -> seg(listOf("Feeder " to grey, "—" to grey), rx, by - lh * 2, true)
        f.levelKg > 0 -> seg(listOf("Feeder " to grey, Fmt.n(f.fillFrac * 100, 1) to feederCol, "%  " to grey, Fmt.n(f.hoursToEmpty ?: 0.0, 1) to feederCol, " h" to grey), rx, by - lh * 2, true)
        else -> seg(listOf("Empty " to grey, Fmt.n(f.emptyForH, 1) to feederCol, " h" to grey), rx, by - lh * 2, true)
    }
    seg(listOf("Water " to grey, "${Fmt.n(inp.waterC.first, 1)}–${Fmt.n(inp.waterC.second, 1)}" to ideal, "°" to grey), rx, by - lh, true)
    seg(listOf("pH " to grey, "${Fmt.n(inp.waterPh.first, 2)}–${Fmt.n(inp.waterPh.second, 2)}" to ideal), rx, by, true)
}

private fun hhmm(h: Double): String { val m = ((h % 24 + 24) % 24 * 60).toInt(); return String.format("%02d:%02d", m / 60, m % 60) }

/** Draws one bird and returns where its name goes. [lite] birds (the crowd) get shadow, body, head and beak only. */
private fun DrawScope.drawBird(b: Bird, sh: Shape, light: Float, P: (Double, Double, Double) -> Offset, k: Float, camX: Double, camZ: Double, flat: Double, lod: Int = 2): Offset {
    val lite = b.lite
    val lean = lite && lod < 2      // no shadow or beak
    val flatBody = lite && lod < 1  // body not turned with the bird
    val s = cbrt(b.weightG / 42.0) * 0.1        // metres per base unit
    val fx = sin(b.yaw); val fz = cos(b.yaw)
    val rxv = cos(b.yaw); val rzv = -sin(b.yaw)
    // the side facing the viewer
    val near = if (rxv * camX + rzv * camZ >= 0) 1.0 else -1.0
    val bodyY = (sh.L + sh.ry * 0.95 - b.sit * sh.L * 0.95) * s + b.jump
    val dim = 0.45f + 0.55f * light
    val bodyCol = sh.body.copy(red = sh.body.red * dim, green = sh.body.green * dim, blue = sh.body.blue * dim)
    val ink = Color(0xFF1A1410)
    // shadow
    val sc = P(b.x, 0.0, b.zz); val shR = (sh.rz * s * k * 1.1).toFloat()
    val sq = (0.78 * flat).toFloat()
    if (!lean) drawOval(Color.Black.copy(alpha = 0.45f), Offset(sc.x - shR, sc.y - shR * sq), Size(shR * 2, shR * sq * 2))
    // legs
    if (!lite && b.sit < 0.7) {
        for (sd in listOf(-1.0, 1.0)) {
            val hip = P(b.x + rxv * sh.rx * 0.35 * s * sd, bodyY - sh.ry * 0.6 * s, b.zz + rzv * sh.rx * 0.35 * s * sd)
            val sw = b.swing * sd * 0.05 * s * 10 * (1 - b.sit)
            val foot = P(b.x + rxv * sh.rx * 0.35 * s * sd + fx * sw, (if (sd > 0) b.lift * 0.02 else 0.0), b.zz + rzv * sh.rx * 0.35 * s * sd + fz * sw)
            drawLine(sh.legs.copy(alpha = dim), hip, foot, max(1.5f, (0.03 * s * 10 * k * 0.06).toFloat() + 1.5f), StrokeCap.Round)
        }
    }
    // tail
    if (!lite && sh.tail > 0.05) {
        val a = P(b.x - fx * sh.rz * 0.8 * s, bodyY + sh.ry * 0.3 * s, b.zz - fz * sh.rz * 0.8 * s)
        val tip = P(b.x - fx * sh.rz * 1.2 * s, bodyY + sh.ry * (0.3 + 0.8 * sh.tail) * s, b.zz - fz * sh.rz * 1.2 * s)
        val w = (sh.ry * 0.3 * s * k).toFloat()
        drawPath(Path().apply { moveTo(a.x - w, a.y); lineTo(tip.x, tip.y); lineTo(a.x + w, a.y); close() }, bodyCol)
    }
    // body: an oval aligned with the facing direction on screen
    val c = P(b.x, bodyY, b.zz)
    val front = P(b.x + fx * sh.rz * s, bodyY, b.zz + fz * sh.rz * s)
    val ax = front.x - c.x; val ay = front.y - c.y
    val alen = hypot(ax.toDouble(), ay.toDouble()).toFloat()
    // resting and sleeping birds breathe
    val breathe = if (b.state == "sleep" || b.state == "rest") 1.0 + 0.04 * sin(b.t * 2.2) else 1.0
    val minor = (sh.ry * s * k * breathe).toFloat()
    val major = max(alen, (sh.rx * s * k).toFloat())
    val ang = if (alen > (sh.rx * s * k * 0.6).toFloat()) Math.toDegrees(atan2(ay.toDouble(), ax.toDouble())).toFloat() else 0f
    if (flatBody) drawOval(bodyCol, Offset(c.x - major, c.y - minor), Size(major * 2, minor * 2))
    else rotate(ang, c) {
        drawOval(ink, Offset(c.x - major - 1f, c.y - minor - 1f), Size((major + 1f) * 2, (minor + 1f) * 2))
        drawOval(bodyCol, Offset(c.x - major, c.y - minor), Size(major * 2, minor * 2))
    }
    // wing on the near side
    if (!lite) {
        val wingLift = if (b.flap > 0.3) (abs(sin(System.nanoTime() / 1e9 * 30)) * 0.5 * b.flap) else 0.0
        val wc = P(b.x + rxv * near * sh.rx * 0.85 * s - fx * sh.rz * 0.1 * s, bodyY + (0.05 + wingLift) * sh.ry * s, b.zz + rzv * near * sh.rx * 0.85 * s - fz * sh.rz * 0.1 * s)
        rotate(ang, wc) {
            val wl = major * 0.62f; val wh = minor * 0.55f
            drawOval(bodyCol.copy(red = bodyCol.red * 0.9f, green = bodyCol.green * 0.9f, blue = bodyCol.blue * 0.88f), Offset(wc.x - wl, wc.y - wh), Size(wl * 2, wh * 2))
            drawOval(ink.copy(alpha = 0.5f), Offset(wc.x - wl, wc.y - wh), Size(wl * 2, wh * 2), style = Stroke(1f))
        }
    }
    // head (neck swings it down when pecking, up when drinking)
    val hfx = sin(b.yaw + b.headYaw); val hfz = cos(b.yaw + b.headYaw)
    val reach = (sh.rz * 0.55 + sh.neckZ * 0.55 + b.peck * sh.neckY * 0.7) * s
    val headY = bodyY + (sh.ry * 0.35 + sh.neckY * (1 - b.peck * 1.35)) * s
    val hc = P(b.x + hfx * reach, max(sh.hr * s, headY), b.zz + hfz * reach)
    val hr = (sh.hr * s * k).toFloat()
    if (!lean) drawCircle(ink, hr + 1f, hc)
    drawCircle(bodyCol, hr, hc)
    if (lean) return Offset(hc.x, hc.y - hr * 2.2f - 4f)
    if (!lite && sh.comb > 0.05) for (i in -1..1) { val cc = P(b.x + hfx * (reach + i * sh.hr * 0.3 * s), max(sh.hr * s, headY) + sh.hr * 0.95 * s, b.zz + hfz * (reach + i * sh.hr * 0.3 * s)); drawCircle(Color(0xFFD8443A).copy(alpha = dim), (hr * 0.28f * sh.comb.toFloat()) + 0.5f, cc) }
    // beak
    val beakTip = P(b.x + hfx * (reach + sh.hr * 1.55 * s), max(sh.hr * s, headY) - sh.hr * 0.1 * s, b.zz + hfz * (reach + sh.hr * 1.55 * s))
    val bw = hr * 0.35f
    val bx = beakTip.x - hc.x; val byy = beakTip.y - hc.y; val bl = hypot(bx.toDouble(), byy.toDouble()).toFloat().coerceAtLeast(0.1f)
    val nx = -byy / bl * bw; val ny = bx / bl * bw
    val base = Offset(hc.x + bx * 0.55f, hc.y + byy * 0.55f)
    drawPath(Path().apply { moveTo(base.x + nx, base.y + ny); lineTo(beakTip.x, beakTip.y); lineTo(base.x - nx, base.y - ny); close() }, Color(0xFFF3B23C).copy(alpha = dim))
    if (lite) return Offset(hc.x, hc.y - hr * 2.2f - 4f)
    if (sh.wattle > 0.05) drawCircle(Color(0xFFD8443A).copy(alpha = dim), hr * 0.25f * sh.wattle.toFloat() + 0.5f, Offset(base.x, base.y + hr * 0.55f))
    // eye on the near side: closed when asleep, slumped or blinking
    val ec = P(b.x + hfx * (reach + sh.hr * 0.4 * s) + cos(b.yaw + b.headYaw) * near * sh.hr * 0.55 * s,
        max(sh.hr * s, headY) + sh.hr * 0.2 * s,
        b.zz + hfz * (reach + sh.hr * 0.4 * s) - sin(b.yaw + b.headYaw) * near * sh.hr * 0.55 * s)
    val closed = b.state == "sleep" || b.blink > 0.3 || (b.state == "slump" && b.sit > 0.6)
    if (closed) drawLine(ink, Offset(ec.x - hr * 0.3f, ec.y), Offset(ec.x + hr * 0.3f, ec.y), 1.6f, StrokeCap.Round)
    else { drawCircle(ink, max(1.6f, hr * 0.22f), ec); drawCircle(Color.White, max(0.6f, hr * 0.07f), Offset(ec.x + hr * 0.06f, ec.y - hr * 0.06f)) }
    // small cues: chirp lines, sleep marks
    val paint = Paint().apply { isAntiAlias = true; textSize = 10f * density; color = Color.White.copy(alpha = 0.75f).toArgb(); textAlign = Paint.Align.CENTER }
    if (b.chirp > 0) { val o = Offset(beakTip.x + hr * 0.6f, beakTip.y - hr * 0.6f); for (i in 1..2) drawArc(Color.White.copy(alpha = 0.7f), -40f, 80f, false, Offset(o.x - i * 3f, o.y - i * 3f), Size(i * 6f, i * 6f), style = Stroke(1.2f)) }
    if (b.state == "sleep") drawContext.canvas.nativeCanvas.drawText("z z", hc.x + hr, hc.y - hr * 1.4f, paint)
    return Offset(hc.x, hc.y - hr * 2.2f - 4f)
}
