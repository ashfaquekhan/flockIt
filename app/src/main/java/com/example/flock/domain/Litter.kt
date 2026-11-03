package com.example.flock.domain

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * The deep litter as a whole (there are no sensors: a model, shown as projected): how deep it is, how wet, how warm,
 * the ammonia it gives off and how much of the floor has caked — and what each should be. Pure Kotlin, no Android.
 *
 *  - Depth: the bedding put down ([In.beddingCm], 7.5 cm unless known) plus the droppings: a quarter of the feed eaten
 *    comes out as dry matter, which packs at about 400 kg/m³ on the floor the birds have; old litter settles 15 %.
 *  - Moisture: fresh bedding is about 12 %; the birds' droppings and spilt water take it up towards 30 % over the
 *    flock, faster on a humid day and on a crowded floor; under the drinker lines it is about 7 points wetter.
 *  - Temperature: the air at the floor, plus the warmth of the litter working (more in old, wet, crowded litter).
 *  - Ammonia: off wet (over 22 %), warm, old litter, cleared by the air moved over it.
 *  - Ideal (Ross): 5–10 cm of bedding, 20–25 % moisture (30 % at most), ammonia under 10 ppm (25 ppm the limit).
 */
object Litter {
    const val BEDDING_CM = 7.5
    const val IDEAL_DEPTH_LO = 5.0
    const val IDEAL_DEPTH_HI = 10.0
    const val IDEAL_MOIST_LO = 20.0
    const val IDEAL_MOIST_HI = 25.0
    const val MOIST_MAX = 30.0
    const val NH3_IDEAL = 10.0
    const val NH3_MAX = 25.0

    data class In(
        val day: Int,
        /** kg on each m² of the floor the birds have */
        val kgM2: Double,
        /** feed eaten till date by the whole flock (kg) and the floor the birds have (m²) */
        val feedKgTD: Double, val floorM2: Double,
        /** the air at the floor (°C) and its humidity (%); air speed over the birds (m/s) */
        val airC: Double, val rhPct: Double, val airSpeedMs: Double,
        val beddingCm: Double = BEDDING_CM
    )

    data class Out(
        val depthCm: Double, val beddingCm: Double,
        /** the house's average and under the drinker lines (%) */
        val moisturePct: Double, val underDrinkersPct: Double,
        val tempC: Double, val nh3Ppm: Double,
        /** share of the floor caked (%) */
        val cakedPct: Double
    )

    fun state(i: In): Out {
        val dryKgM2 = max(0.0, i.feedKgTD) * 0.25 / max(1.0, i.floorM2)
        val settle = if (i.day >= 21) 0.85 else 1.0 - 0.15 * i.day / 21.0
        val depth = (i.beddingCm + dryKgM2 / 400.0 * 100.0) * settle
        val humid = ((i.rhPct - 65) / 5).coerceIn(-2.0, 5.0)
        val crowd = (3 * (i.kgM2 / 30.0 - 1)).coerceIn(-2.0, 4.0)
        val moist = (12 + 18 * (1 - exp(-i.day / 18.0)) + humid + crowd).coerceIn(8.0, 60.0)
        val under = min(60.0, moist + 7)
        val temp = i.airC + min(6.0, 0.10 * i.day) * (moist / 30.0).coerceIn(0.6, 1.5) * (i.kgM2 / 30.0).coerceIn(0.5, 1.6)
        val nh3 = if (i.day < 10) 0.0 else {
            val wet = max(0.0, moist - 22) * 1.1
            val warm = max(0.3, 1 + 0.06 * (temp - 25))
            val old = min(1.4, i.day / 30.0)
            val clear = 1 / (1 + 1.2 * max(0.0, i.airSpeedMs))
            (2.0 * old + wet * warm * old * clear * 1.6).coerceIn(0.0, 60.0)
        }
        // caking starts where the litter is wetter than 30 %: under the drinkers first
        val caked = (max(0.0, under - MOIST_MAX) * 2.5 + max(0.0, moist - MOIST_MAX) * 6.0).coerceIn(0.0, 100.0)
        return Out(depth, i.beddingCm, moist, under, temp, nh3, caked)
    }
}
