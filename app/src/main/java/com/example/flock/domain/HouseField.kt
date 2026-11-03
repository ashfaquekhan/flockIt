package com.example.flock.domain

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * How things vary across the house — what a map of it shows, like the layers of a map app: body temperature, the
 * birds' density, the litter's temperature and moisture, ammonia, and the air's temperature and humidity from the
 * floor to the roof. For each, the IDEAL (what it should be, the same everywhere) and the PROJECTED (what the model
 * makes of today's house). No sensors: every projected value is a model, and says so. Pure Kotlin.
 *
 * Positions: x ft from the front wall (the pad / inlet end) along the house, y ft across it, z share of the height
 * (0 floor, 1 roof). The model:
 *  - Air warms along the house as it picks up the birds' heat — sensible heat ÷ (air flow × 1,210 J/m³K), up to
 *    4 °C front to back — and rises: still air (minimum ventilation, heaters) is 2–3 °C warmer under the roof than at
 *    the birds; fast tunnel air mixes it to about half a degree.
 *  - Humidity: the same water in the air reads lower where the air is warmer, and the birds add water along the way.
 *  - Birds gather near the feeder and drinker lines; in heat they drift toward the cooler front.
 *  - Litter is wetter under the drinkers and where birds stand thicker, and wetter with age and a humid season; it is
 *    warmer than the air where it composts (age, density, wetness).
 *  - Ammonia comes off wet, warm, old litter and is cleared by the air moved.
 *  - A bird's body runs hotter where the air it feels is hotter (felt = air − wind chill).
 */
object HouseField {
    enum class Layer(val label: String, val unit: String) {
        BODY("Body temp", "°C"), DENSITY("Density", "kg/m²"), LITTER_T("Litter temp", "°C"), MOISTURE("Litter moisture", "%"),
        AMMONIA("Ammonia", "ppm"), AIR_T("Air temp", "°C"), RH("Humidity", "%")
    }

    data class In(
        val lengthFt: Double, val widthFt: Double, val heightFt: Double,
        /** the birds' floor ends here (ft from the front) */
        val barricadeFt: Double,
        val feederYs: List<Double>, val drinkerYs: List<Double>,
        val live: Int, val avgKg: Double, val day: Int,
        /** the house air at the birds (the model's, or the ideal) and its humidity */
        val airC: Double, val rhPct: Double,
        /** the comfort temperature and humidity for today's birds */
        val idealC: Double, val idealRh: Double,
        /** air moved by the fans running (cfm) and the air speed over the birds (m/s) */
        val fanCfm: Double, val airSpeedMs: Double,
        /** heat load the birds feel (felt − comfort, °C); outside humidity (%) */
        val heatLoadC: Double, val outRh: Double,
        /** a comfortable bird's body temperature: low, normal, high (°C) */
        val body: Triple<Double, Double, Double>,
        val heatersOn: Boolean
    )

    /** The colour scale: [lo] … [hi], the good band [goodLo] … [goodHi]. */
    data class Scale(val lo: Double, val goodLo: Double, val goodHi: Double, val hi: Double)

    fun scale(l: Layer, i: In): Scale = when (l) {
        Layer.BODY -> Scale(i.body.first - 0.8, i.body.first, i.body.third, i.body.third + 1.5)
        Layer.DENSITY -> Scale(0.0, 25.0, 34.0, 42.0)
        Layer.LITTER_T -> Scale(i.idealC - 6, i.idealC - 1.5, i.idealC + 2, i.idealC + 8)
        Layer.MOISTURE -> Scale(10.0, 20.0, 30.0, 45.0)
        Layer.AMMONIA -> Scale(0.0, 0.0, 10.0, 30.0)
        Layer.AIR_T -> Scale(i.idealC - 6, i.idealC - 1.5, i.idealC + 1.5, i.idealC + 6)
        Layer.RH -> Scale(30.0, 50.0, 70.0, 95.0)
    }

    // ---------------- the air ----------------
    /** °C the air warms from the front to the back (the birds' sensible heat over the air moved). */
    fun lengthRiseC(i: In): Double {
        val heatW = i.live * 10.62 * Math.pow(max(0.04, i.avgKg), 0.75) * 0.55
        val m3s = max(0.5, i.fanCfm * 0.000471947)
        return min(4.0, heatW / (m3s * 1210.0))
    }
    /** °C the air is warmer under the roof than on the floor: still air stratifies, fast air mixes it. */
    fun stratC(i: In): Double = 0.5 + 2.5 * exp(-i.airSpeedMs / 0.6) + if (i.heatersOn) 1.0 else 0.0

    private const val BIRD_Z = 0.12      // the birds' height as a share of the house height

    fun airC(i: In, x: Double, z: Double): Double {
        val f = (x / max(1.0, i.lengthFt)).coerceIn(0.0, 1.0)
        val rise = lengthRiseC(i); val strat = stratC(i)
        // the birds' level averages the house temperature over the birds' floor
        val birdsMid = (min(i.barricadeFt, i.lengthFt) / max(1.0, i.lengthFt)) / 2
        return i.airC + rise * (f - birdsMid) + strat * (z - BIRD_Z)
    }

    private fun esat(t: Double) = 0.6108 * exp(17.27 * t / (t + 237.3))

    fun rh(i: In, x: Double, z: Double): Double {
        val f = (x / max(1.0, i.lengthFt)).coerceIn(0.0, 1.0)
        val e0 = i.rhPct / 100 * esat(i.airC)
        val e = e0 * (1 + 0.08 * (f - 0.5))                      // the birds breathe water into the air on its way
        return (e / esat(airC(i, x, z)) * 100).coerceIn(5.0, 100.0)
    }

    // ---------------- the birds and the litter ----------------
    private fun near(y: Double, ys: List<Double>, sd: Double): Double = ys.maxOfOrNull { exp(-(y - it) * (y - it) / (2 * sd * sd)) } ?: 0.0

    /** Birds' density relative to the floor's average (1 = average), before normalising. */
    private fun crowd(i: In, x: Double, y: Double): Double {
        if (x > i.barricadeFt) return 0.0
        val lines = 0.30 * near(y, i.feederYs, 1.6) + 0.20 * near(y, i.drinkerYs, 1.3)
        val heat = (i.heatLoadC / 8).coerceIn(0.0, 0.4)
        val f = (x / max(1.0, min(i.barricadeFt, i.lengthFt))).coerceIn(0.0, 1.0)
        val walls = 0.15 * (exp(-y / 2.0) + exp(-(i.widthFt - y) / 2.0)) * heat / 0.4
        return (1 + lines + walls) * (1 + heat * (0.5 - f) * 2)
    }

    private val crowdMean = HashMap<In, Double>()
    private fun crowdNorm(i: In): Double = synchronized(crowdMean) {
        crowdMean.getOrPut(i) {
            var s = 0.0; var n = 0
            val xs = min(i.barricadeFt, i.lengthFt)
            var x = 0.5; while (x < xs) { var y = 0.5; while (y < i.widthFt) { s += crowd(i, x, y); n++; y += 1.0 }; x += 2.0 }
            if (crowdMean.size > 16) crowdMean.clear()
            if (n > 0 && s > 0) s / n else 1.0
        }
    }

    fun densityAvg(i: In): Double = i.live * i.avgKg / (max(1.0, min(i.barricadeFt, i.lengthFt) * i.widthFt) / 10.7639)

    fun density(i: In, x: Double, y: Double): Double = densityAvg(i) * crowd(i, x, y) / crowdNorm(i)

    fun moisture(i: In, x: Double, y: Double): Double {
        if (x > i.barricadeFt) return 18.0
        val age = min(35.0, 20.0 + 0.30 * i.day)
        val humid = ((i.outRh - 70) / 6).coerceIn(-2.0, 5.0)
        val drink = 7.0 * near(y, i.drinkerYs, 1.0)
        val dens = 4.0 * (density(i, x, y) / max(1.0, densityAvg(i)) - 1)
        return (age + humid + drink + dens).coerceIn(10.0, 60.0)
    }

    fun litterC(i: In, x: Double, y: Double): Double {
        val compost = min(6.0, 0.10 * i.day) * (if (x > i.barricadeFt) 0.3 else (density(i, x, y) / max(1.0, densityAvg(i))).coerceIn(0.5, 1.6)) *
            (moisture(i, x, y) / 30.0).coerceIn(0.6, 1.5)
        return airC(i, x, 0.0) + compost
    }

    fun ammonia(i: In, x: Double, y: Double): Double {
        if (i.day < 10) return 0.0
        val wet = max(0.0, moisture(i, x, y) - 22) * 1.1
        val warm = 1 + 0.06 * (litterC(i, x, y) - 25)
        val old = min(1.4, i.day / 30.0)
        val clear = 1 / (1 + 1.2 * i.airSpeedMs)
        return (wet * max(0.3, warm) * old * clear * 1.6).coerceIn(0.0, 60.0)
    }

    fun bodyC(i: In, x: Double, y: Double): Double {
        val chill = if (i.day < 14) 0.0 else min(6.0, 3.0 * i.airSpeedMs)
        val felt = airC(i, x, BIRD_Z) - chill
        val load = max(0.0, felt - i.idealC) + (density(i, x, y) / max(1.0, densityAvg(i)) - 1) * 0.6
        return i.body.second + 0.12 * max(0.0, load)
    }

    /** The PROJECTED value of [l] at (x, y) on the floor, or at height share z for the air layers. */
    fun projected(l: Layer, i: In, x: Double, y: Double, z: Double = BIRD_Z): Double = when (l) {
        Layer.BODY -> bodyC(i, x, y)
        Layer.DENSITY -> density(i, x, y)
        Layer.LITTER_T -> litterC(i, x, y)
        Layer.MOISTURE -> moisture(i, x, y)
        Layer.AMMONIA -> ammonia(i, x, y)
        Layer.AIR_T -> airC(i, x, z)
        Layer.RH -> rh(i, x, z)
    }

    /** The IDEAL value of [l]: the same everywhere the birds are. */
    fun ideal(l: Layer, i: In, x: Double, y: Double, z: Double = BIRD_Z): Double = when (l) {
        Layer.BODY -> i.body.second
        Layer.DENSITY -> if (x > i.barricadeFt) 0.0 else densityAvg(i)
        Layer.LITTER_T -> i.idealC
        Layer.MOISTURE -> 25.0
        Layer.AMMONIA -> 0.0
        Layer.AIR_T -> i.idealC
        Layer.RH -> i.idealRh
    }

    /** Where a value sits on its scale: −1 … 0 below the good band, 0 in it, 0 … 1 above it. */
    fun position(s: Scale, v: Double): Double = when {
        v < s.goodLo -> -((s.goodLo - v) / max(1e-6, s.goodLo - s.lo)).coerceIn(0.0, 1.0)
        v > s.goodHi -> ((v - s.goodHi) / max(1e-6, s.hi - s.goodHi)).coerceIn(0.0, 1.0)
        else -> 0.0
    }

    /** Low, typical and high of a layer over the birds' floor (for the map's caption). */
    fun summary(l: Layer, i: In, ideal: Boolean): Triple<Double, Double, Double> {
        val vals = mutableListOf<Double>()
        val xs = min(i.barricadeFt, i.lengthFt)
        var x = 1.0
        while (x < xs) { var y = 0.5; while (y < i.widthFt) { vals += if (ideal) ideal(l, i, x, y) else projected(l, i, x, y); y += 1.5 }; x += 3.0 }
        if (vals.isEmpty()) return Triple(0.0, 0.0, 0.0)
        vals.sort()
        return Triple(vals.first(), vals[vals.size / 2], vals.last())
    }
}
