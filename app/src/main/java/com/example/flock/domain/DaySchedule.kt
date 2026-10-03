package com.example.flock.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The farm's day: when it is dark, when to load the feeders, when to refill the water tank, when to walk
 * the house. Pure rules, no Android — the clock on the Output page only draws what this returns.
 *
 * Rules (Ross broiler management guidance and common hot-climate practice):
 *  - darkness is one unbroken block at night ending at [Inputs.darkEndHour] (lights on at dawn, the coolest
 *    hour), as long as the lighting programme gives (24 − light hours);
 *  - birds eat most just after lights on and just before lights off (they fill the crop for the dark), so
 *    the first feeding is at lights on and the last one ends at least 2 h before the dark;
 *  - in hot weather feed is not loaded in the hottest part of the day or the 2 h before it (digesting feed
 *    adds heat); extra feedings go to the coolest light hours left, spaced as evenly as possible;
 *  - the tank is refilled before the birds wake, before the heat and evenly through the light hours, so the
 *    water stays fresh and cool when they drink most (with feed and in the heat);
 *  - the house is walked after lights on, after each feeding, at the peak of the heat and before the dark.
 */
object DaySchedule {
    data class Inputs(
        val lightHours: Double,
        val darkEndHour: Double = 5.0,
        val feedings: Int,
        val refills: Int,
        /** outside temperature for each hour 0–23 */
        val temps: List<Double>
    )

    data class Plan(
        val darkStart: Double, val darkEnd: Double,
        val feeds: List<Double>, val refills: List<Double>, val walks: List<Double>,
        /** the hottest light hours, [hotFrom, hotTo) — no feed loaded from 2 h before it */
        val hotFrom: Double, val hotTo: Double,
        val temps: List<Double>
    )

    private fun wrap(h: Double) = ((h % 24) + 24) % 24
    private fun q(h: Double) = wrap((h * 4).roundToInt() / 4.0)
    /** hours from a to b going forward round the clock */
    private fun fwd(a: Double, b: Double) = wrap(b - a)

    /** Outside temperature through a typical day: coolest at 05:00, hottest at 15:00 (cosine between). */
    fun seasonTemps(coolC: Double, hotC: Double): List<Double> = (0 until 24).map { h ->
        val t = if (h in 5..15) (h - 5) / 10.0 else { val back = if (h > 15) h - 15 else h + 9; 1 - back / 14.0 }
        coolC + (hotC - coolC) * (1 - cos(PI * t)) / 2
    }

    fun plan(i: Inputs): Plan {
        val temps = if (i.temps.size == 24) i.temps else seasonTemps(24.0, 32.0)
        val light = i.lightHours.coerceIn(1.0, 24.0)
        val darkEnd = wrap(i.darkEndHour)
        val darkStart = wrap(darkEnd - (24 - light))
        val lightLen = light
        fun inLight(h: Double) = light >= 24 || fwd(darkEnd, h) < lightLen
        fun tAt(h: Double) = temps[wrap(h).toInt().coerceIn(0, 23)]

        // hottest 4 consecutive light hours
        var bestStart = darkEnd; var bestSum = -1e9
        var s = 0.0
        while (s <= lightLen - 4) {
            val st = wrap(darkEnd + s)
            val sum = (0 until 4).sumOf { tAt(st + it) }
            if (sum > bestSum) { bestSum = sum; bestStart = st }
            s += 1.0
        }
        val hotFrom = bestStart; val hotTo = wrap(bestStart + 4)
        val hotSpread = temps.max() - temps.min()
        val heatMatters = hotSpread >= 4 || temps.max() >= 30
        fun blocked(h: Double) = heatMatters && fwd(wrap(hotFrom - 2), h) < 6.0   // 2 h before + the 4 hot hours

        // feedings
        val n = i.feedings.coerceIn(1, 6)
        val first = q(darkEnd + 0.25)
        val last = q(darkStart - 2.5)
        val feeds = mutableListOf(first)
        if (n >= 2 && inLight(last) && fwd(first, last) >= 3) feeds += last
        val candidates = (0 until (lightLen * 4).toInt()).map { q(darkEnd + it / 4.0) }
            .filter { inLight(it) && fwd(it, darkStart) >= 2.0 && !blocked(it) }
        while (feeds.size < n) {
            val pick = candidates.filter { c -> feeds.none { abs(fwd(it, c)).let { d -> min(d, 24 - d) } < 1.5 } }
                .maxWithOrNull(compareBy<Double>({ c -> feeds.minOf { f -> val d = fwd(f, c); min(d, 24 - d) } }, { c -> -tAt(c) }))
                ?: break
            feeds += pick
        }
        // not enough cool hours left (very long heat): fill evenly anyway
        while (feeds.size < n) feeds += q(darkEnd + lightLen * feeds.size / n)

        // refills: before the birds wake, before the heat, then evenly through the light hours
        val k = i.refills.coerceIn(1, 12)
        val refills = mutableListOf(q(darkEnd - 0.5))
        if (k >= 2 && heatMatters) refills += q(hotFrom - 0.5)
        var j = 1
        while (refills.size < k && j < 48) {
            val c = q(darkEnd - 0.5 + lightLen * j / k)
            if (refills.none { val d = fwd(it, c); min(d, 24 - d) < 1.0 }) refills += c
            j++
        }

        // walks: after lights on, an hour after each feeding, at the peak of the heat, before the dark
        val walks = mutableListOf(q(darkEnd + 0.5))
        feeds.forEach { walks += q(it + 1.0) }
        if (heatMatters) walks += q(hotFrom + 2)
        walks += q(darkStart - 0.5)
        val uniqueWalks = walks.sorted().fold(mutableListOf<Double>()) { acc, w -> if (acc.none { val d = fwd(it, w); min(d, 24 - d) < 0.75 }) acc += w; acc }

        fun order(l: List<Double>) = l.sortedBy { fwd(darkEnd - 1, it) }
        return Plan(darkStart, darkEnd, order(feeds), order(refills), order(uniqueWalks.filter { inLight(it) || it == q(darkEnd + 0.5) }),
            hotFrom, hotTo, temps)
    }
}
