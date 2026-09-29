package com.example.flock.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ValueIdeal
import com.example.ui.theme.ValueMax
import com.example.ui.theme.ValuePredicted
import com.example.ui.theme.ValuePresent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Everything the coop needs from the live flock for one frame. Environment values are ideals (no sensors). */
data class CoopInput(
    val age: Int, val meanG: Double, val cvPct: Double?, val live: Int, val entry: Int, val stage: String,
    val light: LightProgram, val feeder: FeederState, val zoneId: java.time.ZoneId,
    val airC: Double, val rhPct: Double, val feelsC: Double, val chillC: Double, val pressurePa: Double,
    val litterC: Double, val litterMoist: Double, val bodyC: Double,
    val waterC: Pair<Double, Double>, val waterPh: Pair<Double, Double>, val travelM: Double
)

private val NAMES = listOf("Pip", "Dot", "Hazel", "Tiko")
private enum class Trait(val label: String) { CURIOUS("curious"), GLUTTON("big eater"), LAZY("lazy"), CHATTY("chatty") }
private val ZS = listOf(-1.1, 0.4, -0.35, 1.1)   // size spread (z-scores) for the CV

private class Bird(val name: String, val trait: Trait, val z: Double, seed: Int) {
    val rnd = Random(seed)
    var x = 0.0; var zz = 0.0; var yaw = rnd.nextDouble(0.0, 2 * PI)
    var state = "idle"; var t = 0.0; var dur = 2.0
    var tx = 0.0; var tz = 0.0
    var step = 0.0; var fullness = 0.8
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

private class CoopSim {
    var birds: List<Bird> = emptyList()
    var side = 2.0          // floor is side × side metres
    var age = -1
    val feederAt get() = Pair(side * 0.36, side * 0.62)
    val drinkerAt get() = Pair(side * 0.72, side * 0.28)
    val panR = 0.165
    fun setup(inp: CoopInput) {
        val n = if (inp.age < 22) 4 else 3
        if (birds.size != n) {
            val spots = listOf(0.25 to 0.30, 0.72 to 0.55, 0.40 to 0.78, 0.62 to 0.22)
            birds = (0 until n).map { i -> Bird(NAMES[i], Trait.values()[i], ZS[i], 31 * i + 7).also { b -> b.x = side * spots[i].first; b.zz = side * spots[i].second } }
        }
        if (inp.age != age) {
            age = inp.age
            val lenM = (shapeFor(inp.age).rz * 2 + shapeFor(inp.age).hr) * 0.1 * cbrt(inp.meanG / 42.0)
            side = (Math.round(lenM * 4.5 / 0.25) * 0.25).coerceIn(1.0, 2.0)
            birds.forEach { b -> b.x = b.x.coerceIn(0.2, side - 0.2); b.zz = b.zz.coerceIn(0.2, side - 0.2) }
        }
        val cv = (inp.cvPct ?: 8.0) / 100.0
        birds.forEach { it.weightG = max(30.0, inp.meanG * (1 + cv * it.z)) }
    }

    fun update(dt: Double, inp: CoopInput, lightLevel: Double) {
        val hunger = inp.feeder.hunger
        val feedIn = inp.feeder.levelKg > 0
        birds.forEach { b ->
            b.t += dt
            b.nextBlink -= dt; if (b.nextBlink < 0) { b.blink = 1.0; b.nextBlink = b.rnd.nextDouble(1.8, 4.5) }
            b.blink = max(0.0, b.blink - dt * 7)
            b.chirp = max(0.0, b.chirp - dt)
            val heavy = smooth(18.0, 45.0, inp.age.toDouble())
            val s = cbrt(b.weightG / 42.0) * 0.1
            val sh = shapeFor(inp.age)
            b.tSit = 0.0; b.tPeck = 0.0; b.tHeadYaw = 0.0; b.swing = 0.0; b.lift = 0.0
            // fullness follows the flock's hunger, nudged by trait
            val targetFull = (1 - hunger) * when (b.trait) { Trait.GLUTTON -> 0.85; Trait.LAZY -> 1.05; else -> 1.0 }
            b.fullness = damp(b.fullness, targetFull.coerceIn(0.0, 1.0), 0.2, dt)

            if (lightLevel < 0.5 && b.state != "sleep") { b.state = "sleep"; b.t = 0.0; b.dur = 9999.0 }
            if (lightLevel >= 0.5 && b.state == "sleep") { b.state = "idle"; b.t = 0.0; b.dur = b.rnd.nextDouble(0.5, 2.0) }

            fun walkTo(x: Double, z: Double, stop: Double, speedMul: Double = 1.0): Boolean {
                val dx = x - b.x; val dz = z - b.zz; val d = hypot(dx, dz)
                if (d <= stop) return true
                val want = atan2(dx, dz)
                var dy = ((want - b.yaw + PI * 3) % (2 * PI)) - PI
                b.yaw += dy.coerceIn(-4 * dt, 4 * dt)
                val speed = (0.25 + 0.2 * sqrt(s * 10)) * speedMul * (1 - heavy * 0.45)
                if (abs(dy) < 1.1) { val st = min(d - stop, speed * dt); b.x += sin(b.yaw) * st; b.zz += cos(b.yaw) * st; b.step += dt * speed * 9 / (s * 10) }
                b.swing = sin(b.step * 2 * PI) * 0.6
                return false
            }
            val (fx, fz) = feederAt; val (dx0, dz0) = drinkerAt
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
                "goEat" -> { val a = (b.z + 2) * 1.6; if (walkTo(fx + sin(a) * rim, fz + cos(a) * rim, 0.02, 1.2)) { b.state = "eat"; b.t = 0.0; b.dur = b.rnd.nextDouble(3.0, 6.0) } }
                "eat" -> {
                    b.yaw = atan2(fx - b.x, fz - b.zz)
                    val ph = (b.t * 3.0) % 1; b.tPeck = if (feedIn) (if (ph < 0.4) 0.6 + 0.4 * sin(ph / 0.4 * PI) else 0.55) else 0.45 + 0.3 * sin(b.t * 1.5)
                    if (!feedIn && b.t > 2 && b.chirp <= 0 && hunger > 0.4) b.chirp = 0.9
                }
                "goDrink" -> { val a = b.z * 2.2; if (walkTo(dx0 + sin(a) * (0.13 + sh.rz * s), dz0 + cos(a) * (0.13 + sh.rz * s), 0.02)) { b.state = "drink"; b.t = 0.0; b.dur = b.rnd.nextDouble(2.5, 4.0) } }
                "drink" -> { b.yaw = atan2(dx0 - b.x, dz0 - b.zz); val ph = (b.t * 0.9) % 1; b.tPeck = if (ph < 0.35) 0.6 else -0.6 }
                "sleep" -> { b.tSit = 1.0; b.tPeck = 0.35 }
                "slump" -> { b.tSit = 1.0; b.tPeck = 0.5; if (b.chirp <= 0 && b.rnd.nextDouble() < dt * 0.3) b.chirp = 0.7 }
            }
            if (b.state != "flap") { b.flap = max(0.0, b.flap - dt * 3); b.jump = damp(b.jump, 0.0, 10.0, dt) }
            if (b.t >= b.dur) choose(b, inp, hunger, feedIn, heavy)
            b.sit = damp(b.sit, b.tSit, 6.0, dt); b.peck = damp(b.peck, b.tPeck, 14.0, dt); b.headYaw = damp(b.headYaw, b.tHeadYaw, 6.0, dt)
            b.x = b.x.coerceIn(0.12, side - 0.12); b.zz = b.zz.coerceIn(0.12, side - 0.12)
        }
    }

    private fun choose(b: Bird, inp: CoopInput, hunger: Double, feedIn: Boolean, heavy: Double) {
        b.t = 0.0
        val r = b.rnd.nextDouble()
        // hunger comes first: go to the feeder, and once very hungry, slump with the head low
        if (feedIn && b.fullness < when (b.trait) { Trait.GLUTTON -> 0.95; else -> 0.8 }) { b.state = "goEat"; b.dur = 20.0; return }
        if (!feedIn && hunger > 0.75 && r < 0.6) { b.state = "slump"; b.dur = b.rnd.nextDouble(5.0, 10.0); return }
        if (!feedIn && hunger > 0.3 && r < 0.55 + hunger * 0.3) { b.state = "goEat"; b.dur = 20.0; return }
        val w = mutableListOf(
            "idle" to 2.0, "walk" to (1.5 + (if (b.trait == Trait.CURIOUS) 3.0 else 0.0)) * (1 - heavy * 0.6),
            "peck" to 2.0, "scratch" to 1.5 * (1 - heavy * 0.5), "preen" to 1.0 + heavy,
            "rest" to (0.4 + heavy * 3.0) * (if (b.trait == Trait.LAZY) 3.0 else 1.0) * (1 + hunger),
            "flap" to 0.6 * (1 - heavy), "chirp" to (0.6 + (if (b.trait == Trait.CHATTY) 2.5 else 0.0)) * (1 + hunger * 2),
            "goDrink" to 0.6
        )
        var sum = w.sumOf { it.second }; var pick = b.rnd.nextDouble() * sum
        for ((s, wt) in w) { pick -= wt; if (pick <= 0) { b.state = s; break } }
        when (b.state) {
            "walk" -> {
                // stay within the allowed travel radius of the feeder (and inside the box)
                val (fx, fz) = feederAt
                val rad = min(inp.travelM, side * 0.55) * (if (b.trait == Trait.CURIOUS) 1.0 else 0.6)
                val a = b.rnd.nextDouble(0.0, 2 * PI); val d = b.rnd.nextDouble(0.25, 1.0) * rad
                b.tx = (fx + sin(a) * d).coerceIn(0.2, side - 0.2); b.tz = (fz + cos(a) * d).coerceIn(0.2, side - 0.2); b.dur = 15.0
            }
            "rest" -> b.dur = b.rnd.nextDouble(4.0, 8.0) + heavy * 8
            "goDrink" -> b.dur = 20.0
            "flap" -> b.dur = 1.0
            else -> b.dur = b.rnd.nextDouble(1.5, 3.5)
        }
    }
}

/**
 * Small 3-plane box (floor + two back walls, hollow, on black) with a few birds of the running
 * flock. Only the floor and the birds move; the feeder shows the logged feed left in the lines.
 */
@Composable
fun Coop3D(inp: CoopInput, modifier: Modifier = Modifier) {
    val sim = remember { CoopSim() }
    sim.setup(inp)
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016 else ((now - last) / 1e9).coerceAtMost(0.05)
                last = now
                val z = java.time.ZonedDateTime.now(inp.zoneId)
                sim.update(dt, inp, inp.light.level(z.hour + z.minute / 60.0 + z.second / 3600.0))
                tick = now
            }
        }
    }
    androidx.compose.foundation.layout.Column(modifier) {
    Canvas(Modifier.fillMaxWidth().height(320.dp)) {
        @Suppress("UNUSED_VARIABLE") val frame = tick
        val zNow = java.time.ZonedDateTime.now(inp.zoneId)
        val hour = zNow.hour + zNow.minute / 60.0
        val lightLv = inp.light.level(hour).toFloat()
        drawCoop(sim, inp, lightLv, zNow)
    }
    // who is who: name, character and weight (the spread follows the flock's CV)
    androidx.compose.material3.Text(
        sim.birds.joinToString("   ") { "${it.name} · ${it.trait.label} · ${com.example.flock.ui.Fmt.n(it.weightG, 1)} g" },
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
    )
    }
}

private fun DrawScope.drawCoop(sim: CoopSim, inp: CoopInput, light: Float, now: java.time.ZonedDateTime) {
    val side = sim.side
    val wallH = side * 0.45
    val c30 = cos(PI / 6); val s30 = sin(PI / 6)
    val k = (size.width * 0.66 / ((side * 2) * c30)).toFloat()
    val cx = size.width / 2f
    val top = size.height * 0.17f
    fun P(x: Double, y: Double, z: Double) = Offset(cx + ((x - z) * c30 * k).toFloat(), top + ((x + z) * s30 * k - y * k).toFloat() + (wallH * k).toFloat())
    val line = Color.White.copy(alpha = 0.55f)
    val faint = Color.White.copy(alpha = 0.16f)
    // walls: two hollow planes behind the floor
    fun quad(a: Offset, b: Offset, c: Offset, d: Offset) = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close() }
    val backWall = quad(P(0.0, 0.0, 0.0), P(side, 0.0, 0.0), P(side, wallH, 0.0), P(0.0, wallH, 0.0))
    val leftWall = quad(P(0.0, 0.0, 0.0), P(0.0, 0.0, side), P(0.0, wallH, side), P(0.0, wallH, 0.0))
    drawPath(backWall, line, style = Stroke(1.2f)); drawPath(leftWall, line, style = Stroke(1.2f))
    // floor: litter tone follows the lights (the ground is animated with the lighting programme)
    val floor = quad(P(0.0, 0.0, 0.0), P(side, 0.0, 0.0), P(side, 0.0, side), P(0.0, 0.0, side))
    val litter = Color(0xFF3A2E22).copy(alpha = 0.25f + 0.55f * light)
    drawPath(floor, litter)
    clipPath(floor) {
        // 0.5 m grid for scale
        var g = 0.5
        while (g < side - 1e-6) { drawLine(faint, P(g, 0.0, 0.0), P(g, 0.0, side), 1f); drawLine(faint, P(0.0, 0.0, g), P(side, 0.0, g), 1f); g += 0.5 }
        // allowed travel ring around the feeder
        val (fx, fz) = sim.feederAt
        val ring = Path(); val R = inp.travelM
        for (i in 0..72) { val a = i / 72.0 * 2 * PI; val p = P(fx + sin(a) * R, 0.0, fz + cos(a) * R); if (i == 0) ring.moveTo(p.x, p.y) else ring.lineTo(p.x, p.y) }
        drawPath(ring, ValueIdeal.copy(alpha = 0.55f), style = Stroke(1.4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
        // litter specks shimmer slightly
        val rnd = Random(7)
        val shimmer = (sin(now.second / 60.0 * 2 * PI + now.nano / 1e9) * 0.5 + 0.5).toFloat()
        repeat(90) { val p = P(rnd.nextDouble(0.05, side - 0.05), 0.0, rnd.nextDouble(0.05, side - 0.05)); drawCircle(Color(0xFFC9A36B).copy(alpha = (0.10f + 0.25f * light) * (0.7f + 0.3f * shimmer * rnd.nextFloat())), 1.4f, p) }
    }
    drawPath(floor, line, style = Stroke(1.2f))

    // props (static): feeder pan on its drop tube, bell drinker
    val (fx, fz) = sim.feederAt
    val panR = sim.panR
    fun ellipseAt(x: Double, y: Double, z: Double, r: Double, col: Color, stroke: Boolean = false, w: Float = 1.2f) {
        val c = P(x, y, z); val rx = (r * c30 * k * 1.41).toFloat(); val ry = (r * s30 * k * 1.41).toFloat()
        if (stroke) drawOval(col, Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2), style = Stroke(w)) else drawOval(col, Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2))
    }
    drawLine(line, P(fx, 0.06, fz), P(fx, wallH * 1.4, fz), 1.2f)
    val fill = inp.feeder.fillFrac.coerceIn(0.0, 1.0)
    ellipseAt(fx, 0.0, fz, panR, Color(0xFF6B2A22).copy(alpha = 0.9f))
    ellipseAt(fx, 0.02 + 0.03 * fill, fz, panR * 0.86, Color(0xFFD8B064).copy(alpha = (0.15 + 0.85 * fill).toFloat()))
    ellipseAt(fx, 0.06, fz, panR, line, stroke = true)
    val (dx, dz) = sim.drinkerAt
    drawLine(line, P(dx, 0.08, dz), P(dx, wallH * 1.4, dz), 1.2f)
    ellipseAt(dx, 0.05, dz, 0.13, Color(0xFF2F7EA8).copy(alpha = 0.55f))
    ellipseAt(dx, 0.08, dz, 0.13, line, stroke = true)

    // birds, far to near
    val sh = shapeFor(inp.age)
    sim.birds.sortedBy { it.x + it.zz }.forEach { b -> drawBird(b, sh, light, ::P, k) }

    // text inside the box (small, light): ideals where there is no sensor
    val paint = Paint().apply { isAntiAlias = true; textSize = 10f * density; typeface = Typeface.MONOSPACE }
    fun txt(s: String, x: Float, y: Float, col: Color, align: Paint.Align = Paint.Align.LEFT) { paint.color = col.toArgb(); paint.textAlign = align; drawContext.canvas.nativeCanvas.drawText(s, x, y, paint) }
    val lh = 13f * density; val pad = 9f * density
    val present = ValuePresent; val ideal = ValueIdeal
    txt("${com.example.flock.ui.Fmt.i(inp.live)} birds", pad, pad + lh * 0.8f, present)
    txt("${com.example.flock.ui.Fmt.n(inp.meanG, 1)} g · CV ${inp.cvPct?.let { com.example.flock.ui.Fmt.n(it, 2) + "%" } ?: "—"}", pad, pad + lh * 1.8f, present)
    txt("Day ${inp.age} · ${inp.stage}", pad, pad + lh * 2.8f, Color.White.copy(alpha = 0.7f))
    val rx = size.width - pad
    txt("Air ${com.example.flock.ui.Fmt.n(inp.airC, 1)}° · ${com.example.flock.ui.Fmt.n(inp.rhPct, 1)}%", rx, pad + lh * 0.8f, ideal, Paint.Align.RIGHT)
    txt("Feels ${com.example.flock.ui.Fmt.n(inp.feelsC, 1)}° · chill ${com.example.flock.ui.Fmt.n(inp.chillC, 1)}°", rx, pad + lh * 1.8f, ideal, Paint.Align.RIGHT)
    txt("Static ${com.example.flock.ui.Fmt.n(inp.pressurePa, 1)} Pa", rx, pad + lh * 2.8f, ideal, Paint.Align.RIGHT)
    val by = size.height - pad
    txt("Litter ${com.example.flock.ui.Fmt.n(inp.litterC, 1)}° · ${com.example.flock.ui.Fmt.n(inp.litterMoist, 1)}%", pad, by - lh * 2f, ideal)
    txt("Body ${com.example.flock.ui.Fmt.n(inp.bodyC, 1)}°", pad, by - lh, ideal)
    val lightOn = inp.light.isLight(now.hour + now.minute / 60.0)
    txt("${if (lightOn) "Light" else "Dark"} ${hhmm(inp.light.darkStartHour)}–${hhmm(inp.light.darkEndHour)} off", pad, by, Color.White.copy(alpha = 0.75f))
    val f = inp.feeder
    val feederCol = when { f.levelKg <= 0 && f.hunger > 0.6 -> ValueMax; f.levelKg <= 0 -> ValuePredicted; else -> present }
    val feederTxt = when {
        f.lastFedAt == null -> "Feeder: log a feeding"
        f.levelKg > 0 -> "Feed ${com.example.flock.ui.Fmt.n(f.fillFrac * 100, 1)}% · ${com.example.flock.ui.Fmt.n(f.hoursToEmpty ?: 0.0, 1)} h left"
        else -> "Empty ${com.example.flock.ui.Fmt.n(f.emptyForH, 1)} h"
    }
    txt(feederTxt, rx, by - lh * 2f, feederCol, Paint.Align.RIGHT)
    txt("Water ${com.example.flock.ui.Fmt.n(inp.waterC.first, 1)}–${com.example.flock.ui.Fmt.n(inp.waterC.second, 1)}°", rx, by - lh, ideal, Paint.Align.RIGHT)
    txt("pH ${com.example.flock.ui.Fmt.n(inp.waterPh.first, 2)}–${com.example.flock.ui.Fmt.n(inp.waterPh.second, 2)}", rx, by, ideal, Paint.Align.RIGHT)
    // reach ring label and floor scale
    val (fx2, fz2) = sim.feederAt
    val ringLbl = P(fx2 + inp.travelM * 0.7071, 0.0, fz2 - inp.travelM * 0.7071)
    paint.textSize = 9f * density
    if (ringLbl.x in 0f..size.width && ringLbl.y in 0f..size.height) txt("reach ${com.example.flock.ui.Fmt.n(inp.travelM, 1)} m", ringLbl.x, ringLbl.y, ideal)
    else txt("reach ${com.example.flock.ui.Fmt.n(inp.travelM, 1)} m covers the box · grid 0.5 m", size.width / 2, by + lh * 0.0f - lh * 3.1f, ideal, Paint.Align.CENTER)
}

private fun hhmm(h: Double): String { val m = ((h % 24 + 24) % 24 * 60).toInt(); return String.format("%02d:%02d", m / 60, m % 60) }

private fun DrawScope.drawBird(b: Bird, sh: Shape, light: Float, P: (Double, Double, Double) -> Offset, k: Float) {
    val s = cbrt(b.weightG / 42.0) * 0.1        // metres per base unit
    val fx = sin(b.yaw); val fz = cos(b.yaw)
    val rxv = cos(b.yaw); val rzv = -sin(b.yaw)
    // the side facing the viewer (camera looks from +x, +z)
    val near = if (rxv + rzv >= 0) 1.0 else -1.0
    val bodyY = (sh.L + sh.ry * 0.95 - b.sit * sh.L * 0.95) * s + b.jump
    val dim = 0.45f + 0.55f * light
    val bodyCol = sh.body.copy(red = sh.body.red * dim, green = sh.body.green * dim, blue = sh.body.blue * dim)
    val ink = Color(0xFF1A1410)
    // shadow
    val sc = P(b.x, 0.0, b.zz); val shR = (sh.rz * s * k * 1.1).toFloat()
    drawOval(Color.Black.copy(alpha = 0.45f), Offset(sc.x - shR, sc.y - shR * 0.45f), Size(shR * 2, shR * 0.9f))
    // legs
    if (b.sit < 0.7) {
        for (sd in listOf(-1.0, 1.0)) {
            val hip = P(b.x + rxv * sh.rx * 0.35 * s * sd, bodyY - sh.ry * 0.6 * s, b.zz + rzv * sh.rx * 0.35 * s * sd)
            val sw = b.swing * sd * 0.05 * s * 10 * (1 - b.sit)
            val foot = P(b.x + rxv * sh.rx * 0.35 * s * sd + fx * sw, (if (sd > 0) b.lift * 0.02 else 0.0), b.zz + rzv * sh.rx * 0.35 * s * sd + fz * sw)
            drawLine(sh.legs.copy(alpha = dim), hip, foot, max(1.5f, (0.03 * s * 10 * k * 0.06).toFloat() + 1.5f), StrokeCap.Round)
        }
    }
    // tail
    if (sh.tail > 0.05) {
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
    val minor = (sh.ry * s * k).toFloat()
    val major = max(alen, (sh.rx * s * k).toFloat())
    val ang = if (alen > (sh.rx * s * k * 0.6).toFloat()) Math.toDegrees(atan2(ay.toDouble(), ax.toDouble())).toFloat() else 0f
    rotate(ang, c) {
        drawOval(ink, Offset(c.x - major - 1.2f, c.y - minor - 1.2f), Size((major + 1.2f) * 2, (minor + 1.2f) * 2))
        drawOval(bodyCol, Offset(c.x - major, c.y - minor), Size(major * 2, minor * 2))
    }
    // wing on the near side
    val wingLift = if (b.flap > 0.3) (abs(sin(System.nanoTime() / 1e9 * 30)) * 0.5 * b.flap) else 0.0
    val wc = P(b.x + rxv * near * sh.rx * 0.85 * s - fx * sh.rz * 0.1 * s, bodyY + (0.05 + wingLift) * sh.ry * s, b.zz + rzv * near * sh.rx * 0.85 * s - fz * sh.rz * 0.1 * s)
    rotate(ang, wc) {
        val wl = major * 0.62f; val wh = minor * 0.55f
        drawOval(bodyCol.copy(red = bodyCol.red * 0.9f, green = bodyCol.green * 0.9f, blue = bodyCol.blue * 0.88f), Offset(wc.x - wl, wc.y - wh), Size(wl * 2, wh * 2))
        drawOval(ink.copy(alpha = 0.5f), Offset(wc.x - wl, wc.y - wh), Size(wl * 2, wh * 2), style = Stroke(1f))
    }
    // head (neck swings it down when pecking)
    val hfx = sin(b.yaw + b.headYaw); val hfz = cos(b.yaw + b.headYaw)
    val reach = (sh.rz * 0.55 + sh.neckZ * 0.55 + b.peck * sh.neckY * 0.7) * s
    val headY = bodyY + (sh.ry * 0.35 + sh.neckY * (1 - b.peck * 1.35)) * s
    val hc = P(b.x + hfx * reach, max(sh.hr * s, headY), b.zz + hfz * reach)
    val hr = (sh.hr * s * k).toFloat()
    drawCircle(ink, hr + 1.2f, hc); drawCircle(bodyCol, hr, hc)
    // comb & wattle
    if (sh.comb > 0.05) for (i in -1..1) { val cc = P(b.x + hfx * (reach + i * sh.hr * 0.3 * s), max(sh.hr * s, headY) + sh.hr * 0.95 * s, b.zz + hfz * (reach + i * sh.hr * 0.3 * s)); drawCircle(Color(0xFFD8443A).copy(alpha = dim), (hr * 0.28f * sh.comb.toFloat()) + 0.5f, cc) }
    // beak
    val beakTip = P(b.x + hfx * (reach + sh.hr * 1.55 * s), max(sh.hr * s, headY) - sh.hr * 0.1 * s, b.zz + hfz * (reach + sh.hr * 1.55 * s))
    val bw = hr * 0.35f
    val bx = beakTip.x - hc.x; val byy = beakTip.y - hc.y; val bl = hypot(bx.toDouble(), byy.toDouble()).toFloat().coerceAtLeast(0.1f)
    val nx = -byy / bl * bw; val ny = bx / bl * bw
    val base = Offset(hc.x + bx * 0.55f, hc.y + byy * 0.55f)
    drawPath(Path().apply { moveTo(base.x + nx, base.y + ny); lineTo(beakTip.x, beakTip.y); lineTo(base.x - nx, base.y - ny); close() }, Color(0xFFF3B23C).copy(alpha = dim))
    if (sh.wattle > 0.05) drawCircle(Color(0xFFD8443A).copy(alpha = dim), hr * 0.25f * sh.wattle.toFloat() + 0.5f, Offset(base.x, base.y + hr * 0.55f))
    // eye on the near side: closed when asleep, slumped or blinking
    val ec = P(b.x + hfx * (reach + sh.hr * 0.4 * s) + cos(b.yaw + b.headYaw) * near * sh.hr * 0.55 * s,
        max(sh.hr * s, headY) + sh.hr * 0.2 * s,
        b.zz + hfz * (reach + sh.hr * 0.4 * s) - sin(b.yaw + b.headYaw) * near * sh.hr * 0.55 * s)
    val closed = b.state == "sleep" || b.blink > 0.3 || (b.state == "slump" && b.sit > 0.6)
    if (closed) drawLine(ink, Offset(ec.x - hr * 0.3f, ec.y), Offset(ec.x + hr * 0.3f, ec.y), 1.6f, StrokeCap.Round)
    else { drawCircle(ink, max(1.6f, hr * 0.22f), ec); drawCircle(Color.White, max(0.6f, hr * 0.07f), Offset(ec.x + hr * 0.06f, ec.y - hr * 0.06f)) }
    // small cues: chirp lines, sleep marks
    val paint = Paint().apply { isAntiAlias = true; textSize = 9f * density; color = Color.White.copy(alpha = 0.75f).toArgb(); textAlign = Paint.Align.CENTER }
    if (b.chirp > 0) { val o = Offset(beakTip.x + hr * 0.6f, beakTip.y - hr * 0.6f); for (i in 1..2) drawArc(Color.White.copy(alpha = 0.7f), -40f, 80f, false, Offset(o.x - i * 3f, o.y - i * 3f), Size(i * 6f, i * 6f), style = Stroke(1.2f)) }
    if (b.state == "sleep") drawContext.canvas.nativeCanvas.drawText("z z", hc.x + hr, hc.y - hr * 1.4f, paint)
    // name and weight (shows the CV spread)
    paint.color = Color.White.copy(alpha = 0.55f).toArgb()
    drawContext.canvas.nativeCanvas.drawText(b.name, hc.x, hc.y - hr * 2.2f - 4f, paint)
}
