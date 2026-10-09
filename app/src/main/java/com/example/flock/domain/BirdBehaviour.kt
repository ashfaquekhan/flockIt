package com.example.flock.domain

import kotlin.math.max
import kotlin.math.min

/**
 * How a broiler spends its time, by size, and how heat or cold changes it — the rules behind the birds in the
 * farm window.
 *
 * A broiler is a resting animal, and more so every week:
 *  - share of time on the move: about 12 % under 0.5 kg, falling to about 5 % by 0.9 kg and 4 % above 2 kg;
 *    average walking speed about 0.13 m/s for a chick and 0.10 m/s from 0.8 kg on; the fastest short runs go
 *    from about 1.5 m/s for a small chick to 0.4 m/s once it is heavy (Tickle, Hutchinson & Codd 2018,
 *    "Energy allocation and behaviour in the growing broiler chicken");
 *  - at the feeder about 15 % and at the drinker about 7 % of the time, the rest mostly lying (Ross 308
 *    time budgets over weeks 1–6; feeding is busiest after the lights come on and before they go off).
 *
 * Heat (see [BirdEnvironment]): birds move and eat less, drink more, lie apart, hold their wings off the
 * body and pant. Cold: chicks crowd together and call, and leave the feeders and drinkers.
 * Pure Kotlin, no Android.
 */
object BirdBehaviour {
    /**
     * Shares of the waking time (they add up to 1) and how the birds move.
     * [pant], [wingsOut], [spread] and [huddle] run from 0 (not at all) to 1 (fully).
     */
    data class Budget(
        val rest: Double, val walk: Double, val eat: Double, val drink: Double, val forage: Double, val preen: Double, val stand: Double,
        val pant: Double, val wingsOut: Double, val spread: Double, val huddle: Double,
        /** everyday walking speed, the speed when feed arrives, and how far one walk goes */
        val walkMs: Double, val hurryMs: Double, val walkBoutM: Double,
        /** how long a meal, a drink and a rest last (seconds, middle value) */
        val mealSec: Double, val drinkSec: Double, val restSec: Double
    )

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t.coerceIn(0.0, 1.0)
    private fun ramp(x: Double, from: Double, to: Double) = ((x - from) / (to - from)).coerceIn(0.0, 1.0)

    /** Share of time moving about, by body weight (kg). */
    fun activeShare(kg: Double): Double = when {
        kg <= 0.30 -> 0.1206
        kg <= 0.911 -> lerp(0.1206, 0.050, (kg - 0.30) / 0.611)
        kg <= 2.0 -> lerp(0.050, 0.0403, (kg - 0.911) / 1.089)
        else -> 0.0403
    }

    /** Average walking speed, m/s. */
    fun walkSpeed(kg: Double): Double = if (kg <= 0.816) 0.1457 - 0.05549 * kg else max(0.085, 0.09885 - 0.001872 * kg)
    /** The fastest short run, m/s. */
    fun peakSpeed(kg: Double): Double = if (kg <= 0.69) max(0.4, 1.906 - 2.184 * kg) else max(0.3, 0.4126 - 0.01996 * kg)

    /**
     * @param heatLoadC      felt temperature − comfort temperature (0 when the house is at its ideal)
     * @param sinceLightsOnH hours since the lights came on, and [untilDarkH] hours until they go off (null: not known)
     */
    fun budget(bwG: Double, day: Int, heatLoadC: Double = 0.0, sinceLightsOnH: Double? = null, untilDarkH: Double? = null): Budget {
        val kg = max(0.03, bwG / 1000.0)
        val grown = ramp(kg, 0.2, 2.0)
        var walk = activeShare(kg)
        var eat = lerp(0.13, 0.09, grown)
        var drink = lerp(0.07, 0.05, grown)
        var forage = lerp(0.06, 0.02, grown)
        val preen = 0.035
        var stand = lerp(0.05, 0.03, grown)
        // the two meals of the day: after the lights come on and before they go off
        val rush = max(sinceLightsOnH?.let { 1 - ramp(it, 0.5, 2.5) } ?: 0.0, untilDarkH?.let { 1 - ramp(it, 0.5, 2.5) } ?: 0.0)
        eat *= 0.9 + 0.6 * rush
        drink *= 0.95 + 0.3 * rush

        val h = heatLoadC
        val pant = ramp(h, 4.0, 8.0)
        val wings = ramp(h, 5.0, 10.0)
        val spread = ramp(h, 3.0, 9.0)
        val cold = ramp(-h, 3.0, 8.0)
        val huddle = cold * (if (day <= 14) 1.0 else 0.4)
        if (h > BirdEnvironment.COMFORT_BAND_C) {
            val slow = max(0.3, 1 - 0.10 * (h - BirdEnvironment.COMFORT_BAND_C))
            walk *= slow; forage *= slow; stand *= 1 + 0.5 * pant
            eat *= BirdEnvironment.feedFactor(h)
            drink *= min(2.2, Math.pow(BirdEnvironment.waterFactor(h), 1.2))
        } else if (cold > 0) {
            walk *= 1 - 0.4 * huddle; forage *= 1 - 0.5 * huddle
            eat *= 1 - 0.3 * huddle; drink *= 1 - 0.3 * huddle
        }
        val busy = walk + eat + drink + forage + preen + stand
        val rest = max(0.20, 1 - busy)
        val sum = rest + busy
        val speed = walkSpeed(kg) * (if (h > BirdEnvironment.COMFORT_BAND_C) max(0.6, 1 - 0.05 * h) else 1.0)
        return Budget(
            rest / sum, walk / sum, eat / sum, drink / sum, forage / sum, preen / sum, stand / sum,
            pant, wings, spread, huddle,
            walkMs = speed, hurryMs = max(speed * 1.5, peakSpeed(kg) * 0.5), walkBoutM = (1.2 - 0.35 * kg).coerceIn(0.4, 1.2),
            mealSec = lerp(25.0, 80.0, grown), drinkSec = lerp(8.0, 16.0, grown), restSec = lerp(30.0, 150.0, grown)
        )
    }
}
