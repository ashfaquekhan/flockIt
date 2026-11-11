package com.example.flock.domain

import kotlin.math.abs
import kotlin.math.pow

/**
 * The flock's own curve (v60): a standard curve (the company's) bent to this flock by every value entered so far, so
 * that it is useful from the first day's entry and gets better with each one.
 *
 * It learns the flock's **ratio to the standard** — how far above or below it this flock runs — not the values
 * themselves, so one entry already moves the whole curve and the standard's shape fills in the days nobody entered.
 *  - The ratio at a day is a weighted mean of the ratios observed, nearer days counting more ([halfLife] days halve a
 *    weight), pulled toward 1 by a prior worth [prior] observations: with no data the curve IS the standard, with one
 *    entry it moves most of the way to it, with several it follows the flock.
 *  - Entries are not all worth the same: the first days of a flock are odd (a few chicks on a scale, feed poured on trays
 *    and paper), so an entry counts in full only from day 7 on (day 0 an eighth); and one entry never moves the curve by
 *    more than a fifth of where it stood — a bad weighing or a miscounted bag bends it, it does not take it away.
 *  - Ahead of the last entry the ratio is carried on, with the recent trend of the ratio (when there are at least three
 *    entries) added for a few days and damped — a flock falling behind keeps falling a little, not for ever.
 *
 * Nothing here is tuned to one farm: [prior] says how noisy one entry is (a weighing of 50 birds: small; one day's
 * deaths: large) and is the only knob. Every forecast and what then happened is kept in the model log, for training a
 * better model later.
 */
object DynamicCurve {
    data class Obs(val day: Int, val value: Double)

    class Fit internal constructor(
        /** day, ratio (held within a fifth of the curve as it stood), how much the entry counts (0–1) */
        private val ratios: List<Triple<Int, Double, Double>>, private val standard: (Int) -> Double?,
        private val prior: Double, private val halfLife: Double, private val trend: Double
    ) {
        /** how many entries the curve has learned from */
        val n: Int get() = ratios.size
        val lastDay: Int? get() = ratios.lastOrNull()?.first

        /** The flock's ratio to the standard on [day] (1 = on the standard). */
        fun ratioAt(day: Int): Double {
            if (ratios.isEmpty()) return 1.0
            val first = ratios.first().first; val last = ratios.last().first
            val at = day.coerceIn(first, last)
            var sw = prior; var swr = prior * 1.0
            ratios.forEach { (d, r, rel) -> val w = rel * 0.5.pow(abs(at - d) / halfLife); sw += w; swr += w * r }
            val base = swr / sw
            if (day <= last) return base
            // ahead of the last entry: the recent drift of the ratio goes on for a few days, damped
            val ahead = (day - last).coerceAtMost(5)
            return (base + trend * ahead * 0.5).coerceIn(base * 0.9, base * 1.1)
        }

        /** The flock's own curve on [day]; null where the standard has no value. */
        fun at(day: Int): Double? = standard(day)?.times(ratioAt(day))
    }

    /**
     * @param obs      the values entered so far (any order; a day entered twice counts once, its last value)
     * @param standard the curve the flock is measured against (the company's), by flock day
     * @param prior    how many entries the standard is worth: lower trusts the flock sooner
     * @param halfLife days over which an entry's say halves
     */
    fun fit(obs: List<Obs>, standard: (Int) -> Double?, prior: Double = 0.5, halfLife: Double = 4.0): Fit {
        val raw = obs.associateBy { it.day }.values.sortedBy { it.day }.mapNotNull { o ->
            val st = standard(o.day)
            if (st == null || st <= 0 || o.value <= 0 || o.value.isNaN()) null else o.day to (o.value / st)
        }
        // in the order they came: each entry held within a fifth of where the curve stood, early days counting less
        var est = 1.0; var weight = prior.coerceAtLeast(0.05)
        val ratios = raw.map { (d, r) ->
            val held = est + (r - est).coerceIn(-0.2 * est, 0.2 * est)
            val rel = ((d + 1) / 8.0).coerceIn(0.125, 1.0)
            est = (est * weight + held * rel) / (weight + rel)
            weight = (weight + rel).coerceAtMost(2.5)              // keeps following a flock that changes
            Triple(d, held, rel)
        }
        // the drift of the ratio per day over the last entries (least squares on up to five of them)
        val recent = ratios.takeLast(5)
        val trend = if (recent.size < 3) 0.0 else {
            val mx = recent.map { it.first }.average(); val my = recent.map { it.second }.average()
            val sxx = recent.sumOf { (it.first - mx) * (it.first - mx) }
            if (sxx <= 0) 0.0 else recent.sumOf { (it.first - mx) * (it.second - my) } / sxx
        }
        return Fit(ratios, standard, prior, halfLife, trend)
    }

    /** One forecast checked against what was then entered. */
    data class Check(val day: Int, val entered: Double, val own: Double?, val standard: Double?, val learnedFrom: Int)

    /**
     * How the curve would have done on this flock: for each entry, the curve fitted on the entries before it (at least
     * [gap] days before) against what was then entered — the evidence that it improves as entries come in.
     */
    fun backtest(obs: List<Obs>, standard: (Int) -> Double?, prior: Double = 0.5, halfLife: Double = 4.0, gap: Int = 1): List<Check> {
        val sorted = obs.sortedBy { it.day }
        return sorted.map { o ->
            val before = sorted.filter { it.day <= o.day - gap }
            Check(o.day, o.value, fit(before, standard, prior, halfLife).at(o.day), standard(o.day), before.size)
        }
    }
}
