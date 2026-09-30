package com.example.flock.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.unit.sp
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
internal enum class Trait(val label: String) { CURIOUS("curious"), GLUTTON("big eater"), LAZY("lazy"), CHATTY("chatty") }
private val ZS = listOf(-1.1, 0.4, -0.35, 1.1)   // size spread (z-scores) for the CV

internal class Bird(val name: String, val trait: Trait, val z: Double, seed: Int) {
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

internal class CoopSim {
    var birds: List<Bird> = emptyList()
    var side = 2.0          // floor is side × side metres
    var age = -1
    val feederAt get() = Pair(side * 0.36, side * 0.62)
    val drinkerAt get() = Pair(side * 0.72, side * 0.28)
    val panR = 0.165

    // ---- interaction and effects ----
    class Grain(val x: Double, val z: Double, var life: Double)
    /** kind 0 feed falling / crumbs, 1 dust, 2 water */
    class Particle(var x: Double, var y: Double, var z: Double, var vx: Double, var vy: Double, var vz: Double, var life: Double, val kind: Int)
    val grains = mutableListOf<Grain>()
    val particles = mutableListOf<Particle>()
    var clock = 0.0
    var pourT = 0.0            // seconds of feed still running down the drop tube
    var shownFill = -1.0       // pan fill as drawn, easing toward the real level
    var feederShake = 0.0
    var drinkerRipple = 0.0
    var selected: Bird? = null
    var selectedT = 0.0
    private val fxRnd = Random(99)

    /** Tap on a bird: it flaps and calls; its numbers show for a few seconds. */
    fun poke(b: Bird) {
        selected = b; selectedT = 6.0
        b.chirp = 1.0
        if (b.state != "sleep") { b.state = "flap"; b.t = 0.0; b.dur = 0.9 }
    }
    /** Tap on the floor: a few grains land there; birds close enough (curious ones from further) come to peck. */
    fun dropGrains(x: Double, z: Double) {
        repeat(6) { grains += Grain((x + fxRnd.nextDouble(-0.05, 0.05)).coerceIn(0.1, side - 0.1), (z + fxRnd.nextDouble(-0.05, 0.05)).coerceIn(0.1, side - 0.1), 12.0) }
        repeat(8) { particles += Particle(x, 0.25, z, fxRnd.nextDouble(-0.25, 0.25), 0.0, fxRnd.nextDouble(-0.25, 0.25), 0.6, 0) }
        birds.forEach { b ->
            val reach = when (b.trait) { Trait.CURIOUS -> 2.0; Trait.GLUTTON -> 1.2; Trait.LAZY -> 0.45; else -> 0.9 }
            if (b.state != "sleep" && b.state != "eat" && hypot(b.x - x, b.zz - z) <= reach) { b.state = "goGrain"; b.t = 0.0; b.dur = 12.0; b.tx = x; b.tz = z }
        }
    }
    /** Tap on the feeder: it rattles; birds that aren't full come over. */
    fun tapFeeder() {
        feederShake = 1.0
        val (fx, fz) = feederAt
        repeat(10) { particles += Particle(fx, 0.08, fz, fxRnd.nextDouble(-0.3, 0.3), fxRnd.nextDouble(0.4, 0.9), fxRnd.nextDouble(-0.3, 0.3), 0.8, 0) }
        birds.forEach { b -> if (b.state != "sleep" && b.fullness < 0.9) { b.state = "goEat"; b.t = 0.0; b.dur = 20.0 } }
    }
    /** Tap on the drinker: the water ripples and the nearest awake bird goes to drink. */
    fun tapDrinker() {
        drinkerRipple = 1.0
        val (dx, dz) = drinkerAt
        birds.filter { it.state != "sleep" }.minByOrNull { hypot(it.x - dx, it.zz - dz) }?.let { it.state = "goDrink"; it.t = 0.0; it.dur = 20.0 }
    }

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

    private var lastFedSeen: Long? = null
    private var fedSeenInit = false

    fun update(dt: Double, inp: CoopInput, lightLevel: Double) {
        val hunger = inp.feeder.hunger
        val feedIn = inp.feeder.levelKg > 0
        // a new feeding logged: the feeder runs and every awake bird heads for it
        if (!fedSeenInit) { lastFedSeen = inp.feeder.lastFedAt; fedSeenInit = true }
        else if (inp.feeder.lastFedAt != lastFedSeen) {
            lastFedSeen = inp.feeder.lastFedAt
            pourT = 3.5
            birds.forEach { b -> if (b.state != "sleep") { b.state = "goEat"; b.t = 0.0; b.dur = 20.0; b.fullness = min(b.fullness, 0.5) } }
        }
        stepEffects(dt, inp)
        birds.forEach { b ->
            b.t += dt
            b.nextBlink -= dt; if (b.nextBlink < 0) { b.blink = 1.0; b.nextBlink = b.rnd.nextDouble(1.8, 4.5) }
            b.blink = max(0.0, b.blink - dt * 7)
            b.chirp = max(0.0, b.chirp - dt)
            val heavy = smooth(18.0, 45.0, inp.age.toDouble())
            val s = cbrt(b.weightG / 42.0) * 0.1
            val sh = shapeFor(inp.age)
            b.tSit = 0.0; b.tPeck = 0.0; b.tHeadYaw = 0.0; b.swing = 0.0; b.lift = 0.0
            // fullness only rises by eating at the feeder; it drops as they digest, and an empty feeder caps it
            b.fullness -= dt * (if (b.trait == Trait.GLUTTON) 0.016 else 0.011)
            if (b.state == "eat" && feedIn) b.fullness += dt * 0.09
            if (!feedIn) b.fullness = min(b.fullness, 1 - hunger * 0.9)
            b.fullness = b.fullness.coerceIn(0.0, 1.0)

            if (lightLevel < 0.5 && b.state != "sleep") { b.state = "sleep"; b.t = 0.0; b.dur = 9999.0 }
            if (lightLevel >= 0.5 && b.state == "sleep") { b.state = "idle"; b.t = 0.0; b.dur = b.rnd.nextDouble(0.5, 2.0) }

            fun walkTo(x: Double, z: Double, stop: Double, speedMul: Double = 1.0): Boolean {
                val dx = x - b.x; val dz = z - b.zz; val d = hypot(dx, dz)
                if (d <= stop + 1e-4) return true   // arrived (the slack stops a bird hanging a hair short of its spot)
                val want = atan2(dx, dz)
                var dy = ((want - b.yaw + PI * 3) % (2 * PI)) - PI
                b.yaw += dy.coerceIn(-4 * dt, 4 * dt)
                val speed = (0.25 + 0.2 * sqrt(s * 10)) * speedMul * (1 - heavy * 0.45)
                if (abs(dy) < 1.1) { val st = min(d, speed * dt); b.x += sin(b.yaw) * st; b.zz += cos(b.yaw) * st; b.step += dt * speed * 9 / (s * 10) }
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
                "goEat" -> { val a = (b.z + 2) * 1.6; if (walkTo(fx + sin(a) * rim, fz + cos(a) * rim, 0.02, 1.4)) { b.state = "eat"; b.t = 0.0; b.dur = if (feedIn) b.rnd.nextDouble(6.0, 12.0) else b.rnd.nextDouble(3.0, 6.0) } }
                "eat" -> {
                    b.yaw = atan2(fx - b.x, fz - b.zz)
                    val ph = (b.t * 3.0) % 1; b.tPeck = if (feedIn) (if (ph < 0.4) 0.6 + 0.4 * sin(ph / 0.4 * PI) else 0.55) else 0.45 + 0.3 * sin(b.t * 1.5)
                    if (!feedIn && b.t > 2 && b.chirp <= 0 && hunger > 0.4) b.chirp = 0.9
                    if (feedIn && b.fullness >= (if (b.trait == Trait.GLUTTON) 0.99 else 0.95)) b.t = b.dur
                }
                "goGrain" -> if (walkTo(b.tx, b.tz, 0.06, 1.3)) { b.state = "peckGrain"; b.t = 0.0; b.dur = b.rnd.nextDouble(2.0, 3.5) }
                "peckGrain" -> {
                    b.yaw = atan2(b.tx - b.x, b.tz - b.zz)
                    val ph = (b.t * 2.8) % 1; b.tPeck = if (ph < 0.35) 0.4 + 0.6 * sin(ph / 0.35 * PI) else 0.35; b.tSit = 0.2
                    if (ph < 0.05) grains.filter { hypot(it.x - b.x, it.z - b.zz) < 0.2 }.minByOrNull { hypot(it.x - b.tx, it.z - b.tz) }?.let { grains.remove(it); b.fullness = min(1.0, b.fullness + 0.01) }
                }
                "goDrink" -> { val a = b.z * 2.2; if (walkTo(dx0 + sin(a) * (0.13 + sh.rz * s), dz0 + cos(a) * (0.13 + sh.rz * s), 0.02)) { b.state = "drink"; b.t = 0.0; b.dur = b.rnd.nextDouble(2.5, 4.0) } }
                "drink" -> { b.yaw = atan2(dx0 - b.x, dz0 - b.zz); val ph = (b.t * 0.9) % 1; b.tPeck = if (ph < 0.35) 0.6 else -0.6 }
                "sleep" -> { b.tSit = 1.0; b.tPeck = 0.35 }
                "slump" -> { b.tSit = 1.0; b.tPeck = 0.5; if (b.chirp <= 0 && b.rnd.nextDouble() < dt * 0.3) b.chirp = 0.7 }
            }
            if (b.state == "scratch" && b.lift > 0.6 && b.rnd.nextDouble() < dt * 8) particles += Particle(b.x, 0.01, b.zz, b.rnd.nextDouble(-0.15, 0.15), b.rnd.nextDouble(0.05, 0.2), b.rnd.nextDouble(-0.15, 0.15), 0.9, 1)
            if (b.state == "eat" && feedIn && b.rnd.nextDouble() < dt * 3) particles += Particle(b.x + sin(b.yaw) * 0.12, 0.05, b.zz + cos(b.yaw) * 0.12, b.rnd.nextDouble(-0.2, 0.2), b.rnd.nextDouble(0.3, 0.6), b.rnd.nextDouble(-0.2, 0.2), 0.5, 0)
            if (b.state == "drink" && b.rnd.nextDouble() < dt * 1.5) drinkerRipple = max(drinkerRipple, 0.6)
            if (b.state != "flap") { b.flap = max(0.0, b.flap - dt * 3); b.jump = damp(b.jump, 0.0, 10.0, dt) }
            if (b.t >= b.dur) choose(b, inp, hunger, feedIn, heavy)
            b.sit = damp(b.sit, b.tSit, 6.0, dt); b.peck = damp(b.peck, b.tPeck, 14.0, dt); b.headYaw = damp(b.headYaw, b.tHeadYaw, 6.0, dt)
            b.x = b.x.coerceIn(0.12, side - 0.12); b.zz = b.zz.coerceIn(0.12, side - 0.12)
        }
        for (i in birds.indices) for (j in i + 1 until birds.size) {
            val a = birds[i]; val c = birds[j]
            val minD = 0.9 * (cbrt(a.weightG / 42.0) + cbrt(c.weightG / 42.0)) * 0.1 * 0.36
            val dx = c.x - a.x; val dz = c.zz - a.zz; val d = hypot(dx, dz)
            if (d in 1e-6..minD) {
                val push = (minD - d) * 0.5 * min(1.0, dt * 12)
                val wa = if (a.state == "eat" || a.state == "sleep") 0.2 else 1.0; val wc = if (c.state == "eat" || c.state == "sleep") 0.2 else 1.0
                a.x -= dx / d * push * wa; a.zz -= dz / d * push * wa; c.x += dx / d * push * wc; c.zz += dz / d * push * wc
            }
        }
    }

    private fun stepEffects(dt: Double, inp: CoopInput) {
        clock += dt
        selectedT = max(0.0, selectedT - dt); if (selectedT <= 0) selected = null
        feederShake = max(0.0, feederShake - dt * 1.6)
        drinkerRipple = max(0.0, drinkerRipple - dt * 0.8)
        grains.forEach { it.life -= dt }; grains.removeAll { it.life <= 0 }
        // feed running down the drop tube into the pan after a feeding
        val (fx, fz) = feederAt
        if (pourT > 0) {
            pourT -= dt
            if (fxRnd.nextDouble() < dt * 40) particles += Particle(fx + fxRnd.nextDouble(-0.02, 0.02), side * 0.5, fz + fxRnd.nextDouble(-0.02, 0.02), 0.0, -0.4, 0.0, 1.2, 0)
        }
        val target = inp.feeder.fillFrac.coerceIn(0.0, 1.0)
        shownFill = if (shownFill < 0) target else damp(shownFill, target, if (pourT > 0) 0.9 else 2.0, dt)
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val q = iter.next()
            q.life -= dt
            if (q.kind == 1) { q.vy -= dt * 0.1 } else q.vy -= dt * 3.2
            q.x += q.vx * dt; q.y += q.vy * dt; q.z += q.vz * dt
            if (q.y < 0.0) { q.y = 0.0; q.vx *= 0.3; q.vz *= 0.3; q.vy = 0.0 }
            if (q.life <= 0) iter.remove()
        }
        if (particles.size > 220) particles.subList(0, particles.size - 220).clear()
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
/** View of the box: turned by [yaw], looked at from [elev] above the floor, [zoom] × the fitted size. */
internal class CoopCam {
    var yaw = 0.0; var elev = ISO_ELEV; var zoom = 1.0
    var yawVel = 0.0
    var resetting = false
    fun step(dt: Double) {
        if (resetting) {
            yaw = damp(yaw, 0.0, 6.0, dt); elev = damp(elev, ISO_ELEV, 6.0, dt); zoom = damp(zoom, 1.0, 6.0, dt)
            if (abs(yaw) < 1e-3 && abs(elev - ISO_ELEV) < 1e-3 && abs(zoom - 1.0) < 1e-3) { yaw = 0.0; elev = ISO_ELEV; zoom = 1.0; resetting = false }
        } else if (abs(yawVel) > 1e-3) { yaw += yawVel * dt; yawVel *= kotlin.math.exp(-3.0 * dt) }
    }
}
internal const val ISO_ELEV = 0.6154797      // atan(1/√2): true isometric
private const val SQRT2 = 1.4142135623730951

/** World (x, y, z in metres, floor = y 0) to screen, for one frame's size and camera; and back onto the floor. */
internal class CoopProj(val side: Double, val wallH: Double, w: Float, h: Float, cam: CoopCam) {
    private val cy = cos(cam.yaw); private val sy = sin(cam.yaw)
    private val se = sin(cam.elev); private val ce = cos(cam.elev)
    private val c = side / 2
    /** px per metre */
    val S: Double = cam.zoom * min(w * 0.62 / (side * SQRT2), h * 0.58 / (side * SQRT2 * se + wallH * ce))
    private val ox = w / 2.0
    private val oy = h * 0.53 + S * wallH * ce * 0.45
    /** vertical squash of circles lying on the floor */
    val flat = se
    /** world direction toward the viewer (x, z) */
    val camX = (cy + sy) / SQRT2; val camZ = (cy - sy) / SQRT2
    private fun rot(x: Double, z: Double): Pair<Double, Double> { val dx = x - c; val dz = z - c; return Pair(dx * cy - dz * sy, dx * sy + dz * cy) }
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
        return Pair(c + xr * cy + zr * sy, c - xr * sy + zr * cy)
    }
}

/**
 * Small 3-plane box (floor + the two far walls, hollow, on black) with a few birds of the running
 * flock. Drag sideways to turn it (it keeps spinning a little), two fingers to tilt and zoom,
 * double-tap to square it up. Tap a bird, the feeder, the drinker or the floor.
 */
@Composable
fun Coop3D(inp: CoopInput, modifier: Modifier = Modifier) {
    val sim = remember { CoopSim() }
    sim.setup(inp)
    // the frame loop starts once: read the latest input (new feedings, lights) through this, not the first one
    val cur = androidx.compose.runtime.rememberUpdatedState(inp)
    val cam = remember { CoopCam() }
    var tick by remember { mutableLongStateOf(0L) }
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
    androidx.compose.foundation.layout.Column(modifier) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val h = (maxWidth * 0.82f).coerceIn(250.dp, 440.dp)
            Canvas(
                Modifier.fillMaxWidth().height(h).testTag("coop")
                    // two fingers: tilt (up/down), zoom (pinch) and turn; one finger up/down still scrolls the page
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val ev = awaitPointerEvent()
                                if (ev.changes.count { it.pressed } >= 2) {
                                    val pan = ev.calculatePan()
                                    cam.resetting = false
                                    cam.zoom = (cam.zoom * ev.calculateZoom()).coerceIn(0.7, 2.4)
                                    cam.elev = (cam.elev + pan.y * 0.004).coerceIn(0.22, 1.2)
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
                                val pr = CoopProj(sim.side, sim.side * 0.45, size.width.toFloat(), size.height.toFloat(), cam)
                                val sh = shapeFor(cur.value.age)
                                val hitR = 30.dp.toPx()
                                val bird = sim.birds.map { b ->
                                    val sc = cbrt(b.weightG / 42.0) * 0.1
                                    b to (pr.P(b.x, (sh.L + sh.ry) * sc, b.zz) - at).getDistance()
                                }.filter { it.second < hitR }.minByOrNull { it.second }?.first
                                val (fx, fz) = sim.feederAt; val (dx, dz) = sim.drinkerAt
                                when {
                                    bird != null -> sim.poke(bird)
                                    (pr.P(fx, 0.05, fz) - at).getDistance() < (sim.panR * pr.S).toFloat() + 14.dp.toPx() -> sim.tapFeeder()
                                    (pr.P(dx, 0.08, dz) - at).getDistance() < (0.13 * pr.S).toFloat() + 14.dp.toPx() -> sim.tapDrinker()
                                    else -> {
                                        val (x, z) = pr.floorAt(at)
                                        if (x in 0.05..sim.side - 0.05 && z in 0.05..sim.side - 0.05) sim.dropGrains(x, z)
                                    }
                                }
                            }
                        )
                    }
            ) {
                @Suppress("UNUSED_VARIABLE") val frame = tick
                val pr = CoopProj(sim.side, sim.side * 0.45, size.width, size.height, cam)
                drawCoop(sim, inp, lightNow().toFloat(), java.time.ZonedDateTime.now(inp.zoneId), awake, pr)
            }
            if (scheduledDark || awake) {
                androidx.compose.material3.OutlinedButton(
                    onClick = { awake = !awake },
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                    modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter).padding(top = 6.dp).height(32.dp)
                ) { androidx.compose.material3.Text(if (awake) "Sleep" else "Wake", color = Color.White) }
            }
        }
        androidx.compose.material3.Text(
            sim.birds.joinToString("   ") { "${it.name} ${it.trait.label} ${com.example.flock.ui.Fmt.n(it.weightG, 1)} g" },
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
        )
    }
}

private fun DrawScope.drawCoop(sim: CoopSim, inp: CoopInput, light: Float, now: java.time.ZonedDateTime, awake: Boolean, pr: CoopProj) {
    val side = sim.side
    val wallH = pr.wallH
    fun P(x: Double, y: Double, z: Double) = pr.P(x, y, z)
    val S = pr.S
    val line = Color.White.copy(alpha = 0.55f)
    val faint = Color.White.copy(alpha = 0.16f)
    fun quad(a: Offset, b: Offset, c: Offset, d: Offset) = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close() }
    // walls: the two planes on the far side of the floor, whichever way the box is turned
    listOf(
        Triple(0.0 to 0.0, side to 0.0, 0.0 to -1.0), Triple(0.0 to 0.0, 0.0 to side, -1.0 to 0.0),
        Triple(0.0 to side, side to side, 0.0 to 1.0), Triple(side to 0.0, side to side, 1.0 to 0.0)
    ).filter { (_, _, n) -> n.first * pr.camX + n.second * pr.camZ < 0 }.forEach { (p0, p1, _) ->
        drawPath(quad(P(p0.first, 0.0, p0.second), P(p1.first, 0.0, p1.second), P(p1.first, wallH, p1.second), P(p0.first, wallH, p0.second)), line, style = Stroke(1.2f))
    }
    // floor: litter tone follows the lights (the ground is animated with the lighting programme)
    val floor = quad(P(0.0, 0.0, 0.0), P(side, 0.0, 0.0), P(side, 0.0, side), P(0.0, 0.0, side))
    val litter = Color(0xFF2A2218).copy(alpha = 0.20f + 0.45f * light)
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
        // grains dropped by a tap
        sim.grains.forEach { gr -> drawCircle(Color(0xFFE2BC6A).copy(alpha = (0.4f + 0.6f * light) * min(1.0, gr.life / 2).toFloat()), max(1.8f, (0.012 * S).toFloat()), P(gr.x, 0.0, gr.z)) }
    }
    drawPath(floor, line, style = Stroke(1.2f))

    // props: feeder pan on its drop tube (rattles when tapped), bell drinker (ripples)
    val (fx0, fz0) = sim.feederAt
    val wob = sin(sim.clock * 38) * sim.feederShake * 0.012
    val fx = fx0 + wob; val fz = fz0
    val panR = sim.panR
    fun ellipseAt(x: Double, y: Double, z: Double, r: Double, col: Color, stroke: Boolean = false, w: Float = 1.2f) {
        val c = P(x, y, z); val rx = (r * S).toFloat(); val ry = (r * S * pr.flat).toFloat()
        if (stroke) drawOval(col, Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2), style = Stroke(w)) else drawOval(col, Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2))
    }
    drawLine(line, P(fx, 0.06, fz), P(fx, wallH * 1.4, fz), 1.2f)
    val fill = sim.shownFill.coerceIn(0.0, 1.0)
    ellipseAt(fx, 0.02 + 0.03 * fill, fz, panR * 0.86, Color(0xFFD8B064).copy(alpha = (0.15 + 0.85 * fill).toFloat()))
    ellipseAt(fx, 0.06, fz, panR, line, stroke = true)
    val (dx, dz) = sim.drinkerAt
    drawLine(line, P(dx, 0.08, dz), P(dx, wallH * 1.4, dz), 1.2f)
    ellipseAt(dx, 0.08, dz, 0.13, line, stroke = true)
    if (sim.drinkerRipple > 0) for (i in 0..1) {
        val ph = ((sim.clock * 1.4 + i * 0.5) % 1.0)
        ellipseAt(dx, 0.07, dz, 0.03 + 0.09 * ph, ValueIdeal.copy(alpha = (0.7 * (1 - ph) * sim.drinkerRipple).toFloat()), stroke = true, w = 1f)
    }
    // feed and dust particles
    sim.particles.forEach { q ->
        val c = P(q.x, q.y, q.z)
        when (q.kind) {
            1 -> drawCircle(Color(0xFFB59A74).copy(alpha = (0.35 * min(1.0, q.life) * light).toFloat()), (0.015 * S * (1.8 - q.life)).toFloat().coerceAtLeast(1.5f), c)
            else -> drawCircle(Color(0xFFE2BC6A).copy(alpha = (0.5f + 0.5f * light) * min(1.0, q.life * 3).toFloat()), max(1.4f, (0.008 * S).toFloat()), c)
        }
    }

    // birds, far to near
    val sh = shapeFor(inp.age)
    val k = (S / 1.2247).toFloat()
    val sel = sim.selected
    if (sel != null) ellipseAt(sel.x, 0.0, sel.zz, 0.07 * cbrt(sel.weightG / 42.0), ValuePresent.copy(alpha = 0.85f), stroke = true, w = 1.6f)
    val nameAt = sim.birds.sortedBy { pr.depth(it.x, it.zz) }.map { b -> b to drawBird(b, sh, light, ::P, k, pr.camX, pr.camZ, pr.flat) }
    // names above the heads, nudged up so two never overlap
    val np = Paint().apply { isAntiAlias = true; textSize = 9f * density; color = Color.White.copy(alpha = 0.55f).toArgb(); textAlign = Paint.Align.CENTER }
    val placed = mutableListOf<androidx.compose.ui.geometry.Rect>()
    nameAt.sortedByDescending { it.second.y }.forEach { (b, at) ->
        val w = np.measureText(b.name) + 4f; val hh = np.textSize + 2f
        var r = androidx.compose.ui.geometry.Rect(at.x - w / 2, at.y - hh, at.x + w / 2, at.y)
        var guard = 0
        while (placed.any { it.overlaps(r) } && guard++ < 6) r = r.translate(0f, -(hh))
        placed += r
        drawContext.canvas.nativeCanvas.drawText(b.name, r.center.x, r.bottom - 2f, np)
    }
    // numbers of a tapped bird, next to it
    if (sel != null) {
        val F = com.example.flock.ui.Fmt
        val lp = Paint().apply { isAntiAlias = true; textSize = (size.width / 34f).coerceIn(10f * density, 14f * density); typeface = Typeface.MONOSPACE }
        val sc = cbrt(sel.weightG / 42.0) * 0.1
        val anchor = P(sel.x, (sh.L + sh.ry * 2 + sh.neckY + sh.hr * 3) * sc, sel.zz)
        val mean = inp.meanG
        val rows = listOf(
            listOf("${sel.name} " to Color.White, sel.trait.label to Color.White.copy(alpha = 0.6f)),
            listOf(F.n(sel.weightG, 1) to ValuePresent, " g  " to Color.White.copy(alpha = 0.6f), (if (sel.weightG >= mean) "+" else "") + F.n((sel.weightG / mean - 1) * 100, 1) to ValuePresent, "%" to Color.White.copy(alpha = 0.6f)),
            listOf("full " to Color.White.copy(alpha = 0.6f), F.n(sel.fullness * 100, 1) to ValuePredicted, "%" to Color.White.copy(alpha = 0.6f))
        )
        val lh2 = lp.textSize * 1.3f
        val wBox = rows.maxOf { r -> r.sumOf { lp.measureText(it.first).toDouble() } }.toFloat() + 16f
        val hBox = lh2 * rows.size + 10f
        val bx = (anchor.x - wBox / 2).coerceIn(4f, size.width - wBox - 4f)
        val by = (anchor.y - hBox - 6f).coerceIn(4f, size.height - hBox - 4f)
        drawRoundRect(Color.Black.copy(alpha = 0.78f), Offset(bx, by), Size(wBox, hBox), androidx.compose.ui.geometry.CornerRadius(8f, 8f))
        drawRoundRect(Color.White.copy(alpha = 0.6f), Offset(bx, by), Size(wBox, hBox), androidx.compose.ui.geometry.CornerRadius(8f, 8f), style = Stroke(1f))
        rows.forEachIndexed { i, r ->
            var x = bx + 8f
            r.forEach { (t, col) -> lp.color = col.toArgb(); drawContext.canvas.nativeCanvas.drawText(t, x, by + 5f + lh2 * (i + 0.8f), lp); x += lp.measureText(t) }
        }
    }

    // text inside the box: grey labels, coloured numbers (ideal values where there is no sensor)
    val F = com.example.flock.ui.Fmt
    val paint = Paint().apply { isAntiAlias = true; textSize = (size.width / 38f).coerceIn(9f * density, 13f * density); typeface = Typeface.MONOSPACE }
    val grey = Color.White.copy(alpha = 0.55f)
    fun seg(parts: List<Pair<String, Color>>, x: Float, y: Float, right: Boolean) {
        val widths = parts.map { paint.measureText(it.first) }
        var cx0 = if (right) x - widths.sum() else x
        parts.forEachIndexed { i, (t, c) -> paint.color = c.toArgb(); paint.textAlign = Paint.Align.LEFT; drawContext.canvas.nativeCanvas.drawText(t, cx0, y, paint); cx0 += widths[i] }
    }
    val lh = paint.textSize * 1.35f; val pad = 9.dp.toPx()
    val present = ValuePresent; val ideal = ValueIdeal
    val rx = size.width - pad
    seg(listOf(F.i(inp.live) to present, " birds" to grey), pad, pad + lh, false)
    seg(listOf(F.n(inp.meanG, 1) to present, " g  CV " to grey, (inp.cvPct?.let { F.n(it, 2) } ?: "—") to present, "%" to grey), pad, pad + lh * 2, false)
    seg(listOf("Day " to grey, "${inp.age}" to present), pad, pad + lh * 3, false)
    seg(listOf("Air " to grey, F.n(inp.airC, 1) to ideal, "°  " to grey, F.n(inp.rhPct, 1) to ideal, "%" to grey), rx, pad + lh, true)
    seg(listOf("Feels " to grey, F.n(inp.feelsC, 1) to ideal, "°  chill " to grey, F.n(inp.chillC, 1) to ideal, "°" to grey), rx, pad + lh * 2, true)
    seg(listOf("Static " to grey, F.n(inp.pressurePa, 1) to ideal, " Pa" to grey), rx, pad + lh * 3, true)
    val by = size.height - pad
    seg(listOf("Litter " to grey, F.n(inp.litterC, 1) to ideal, "°  " to grey, F.n(inp.litterMoist, 1) to ideal, "%" to grey), pad, by - lh * 2, false)
    seg(listOf("Body " to grey, F.n(inp.bodyC, 1) to ideal, "°" to grey), pad, by - lh, false)
    seg(listOf(if (awake) "Lights on (woken)" to grey else ("Dark " to grey), if (awake) "" to grey else "${hhmm(inp.light.darkStartHour)}–${hhmm(inp.light.darkEndHour)}" to ideal), pad, by, false)
    val f = inp.feeder
    val feederCol = when { f.levelKg <= 0 && f.hunger > 0.6 -> ValueMax; f.levelKg <= 0 -> ValuePredicted; else -> ValuePredicted }
    when {
        f.lastFedAt == null -> seg(listOf("Feeder " to grey, "—" to grey), rx, by - lh * 2, true)
        f.levelKg > 0 -> seg(listOf("Feeder " to grey, F.n(f.fillFrac * 100, 1) to feederCol, "%  " to grey, F.n(f.hoursToEmpty ?: 0.0, 1) to feederCol, " h" to grey), rx, by - lh * 2, true)
        else -> seg(listOf("Empty " to grey, F.n(f.emptyForH, 1) to feederCol, " h" to grey), rx, by - lh * 2, true)
    }
    seg(listOf("Water " to grey, "${F.n(inp.waterC.first, 1)}–${F.n(inp.waterC.second, 1)}" to ideal, "°" to grey), rx, by - lh, true)
    seg(listOf("pH " to grey, "${F.n(inp.waterPh.first, 2)}–${F.n(inp.waterPh.second, 2)}" to ideal), rx, by, true)
}

private fun hhmm(h: Double): String { val m = ((h % 24 + 24) % 24 * 60).toInt(); return String.format("%02d:%02d", m / 60, m % 60) }

private fun DrawScope.drawBird(b: Bird, sh: Shape, light: Float, P: (Double, Double, Double) -> Offset, k: Float, camX: Double, camZ: Double, flat: Double): Offset {
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
    drawOval(Color.Black.copy(alpha = 0.45f), Offset(sc.x - shR, sc.y - shR * sq), Size(shR * 2, shR * sq * 2))
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
    // resting and sleeping birds breathe
    val breathe = if (b.state == "sleep" || b.state == "rest") 1.0 + 0.04 * sin(b.t * 2.2) else 1.0
    val minor = (sh.ry * s * k * breathe).toFloat()
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
    return Offset(hc.x, hc.y - hr * 2.2f - 4f)
}
