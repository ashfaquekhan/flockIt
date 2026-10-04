package com.example.flock.domain

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * How much the flock will eat today, learned from what it has eaten so far — a Kalman filter (the standard
 * real-time method for tracking a slowly changing quantity through noisy readings).
 *
 * What is tracked is the flock's APPETITE RATIO: feed actually eaten ÷ feed the plan expected, day by day.
 * It is modelled as a level that drifts a little each day (local-level state-space model):
 *     level today = level yesterday + drift          (drift variance q)
 *     reading     = level + noise                    (noise variance r: bags are counted in halves, feed is
 *                                                      left in pans, birds eat more or less on a given day)
 * After every day's entry the filter moves its estimate towards the reading by the Kalman gain
 * K = P / (P + r), where P is how unsure it still is. Today's forecast is plan × level, and its spread is
 * √(P + q + r) — so the result is a range with probabilities, not one number.
 *
 * The noise r is taken from the flock's own day-to-day scatter once there are enough days (and kept within
 * sensible bounds); q is a quarter of it. Pure Kotlin, no Android.
 */
object IntakeForecast {
    /** One past day: the plan's feed for it and what was actually eaten (any unit, the same for both). */
    data class Day(val day: Int, val planned: Double, val eaten: Double)

    data class Result(
        /** the flock's appetite against the plan (1.0 = eats exactly the plan) and how sure that is (± 1 sd) */
        val ratio: Double, val ratioSd: Double,
        /** today's intake in the plan's unit: the middle of the forecast and its one-sd spread */
        val mean: Double, val sd: Double,
        /** days the forecast is built on */
        val days: Int,
        /** how far off the one-day-ahead forecasts were so far, % (mean absolute) — null with under 3 days */
        val pastErrorPct: Double?
    ) {
        /** The value the day's intake stays under with probability [p] (0–1). */
        fun quantile(p: Double): Double = mean + sd * zOf(p)
        /** Chance that the flock needs no more than [amount]. */
        fun chanceEnough(amount: Double): Double = if (sd <= 0) (if (amount >= mean) 1.0 else 0.0) else phi((amount - mean) / sd)
    }

    private const val PRIOR_SD = 0.15          // before any data: within ±15 % of the plan, most likely
    private const val R_MIN = 0.02 * 0.02
    private const val R_MAX = 0.15 * 0.15
    private const val R_DEFAULT = 0.06 * 0.06

    fun forecast(history: List<Day>, plannedToday: Double): Result {
        val obs = history.filter { it.planned > 0 && it.eaten > 0 }.sortedBy { it.day }.map { it.day to it.eaten / it.planned }
        // reading noise from the flock's own day-to-day scatter: Var(Δ reading) = 2r + q, with q = r / 4
        val diffs = obs.zipWithNext { a, b -> b.second - a.second }
        val r = if (diffs.size >= 4) (diffs.sumOf { it * it } / diffs.size / 2.25).coerceIn(R_MIN, R_MAX) else R_DEFAULT
        val q = r / 4
        var x = 1.0; var p = PRIOR_SD * PRIOR_SD
        var lastDay: Int? = null
        val errs = mutableListOf<Double>()
        for ((d, y) in obs) {
            p += q * max(1, d - (lastDay ?: (d - 1)))            // drift since the last reading
            if (lastDay != null) errs += abs(y - x) / y * 100    // how good the forecast for this day was
            val k = p / (p + r)
            x += k * (y - x); p *= (1 - k)
            lastDay = d
        }
        val pPred = p + q
        return Result(
            ratio = x, ratioSd = sqrt(pPred), mean = plannedToday * x, sd = plannedToday * sqrt(pPred + r),
            days = obs.size, pastErrorPct = if (errs.size >= 2) errs.average() else null
        )
    }

    /** What to load: the whole-bag amounts around the forecast with the chance each one is enough. */
    data class BagChoice(val bags: Double, val chanceEnough: Double, val expectedLeftBags: Double)
    data class Advice(
        val forecastBags: Double, val lowBags: Double, val highBags: Double,
        /** safe range to load, in whole bags: enough at least 80 % of the time … not over the 95 % point */
        val safeFrom: Double, val safeTo: Double,
        val choices: List<BagChoice>,
        /** −1 give fewer than the plan, 0 the plan is right, +1 give more */
        val direction: Int, val suggestedBags: Double
    )

    /**
     * Turns a forecast (in kg for the whole house) into bags: the middle and the 10–90 % range, the chance each
     * whole-bag amount near it is enough, the safe range, and whether the plan should go up or down.
     */
    fun advise(f: Result, bagKg: Double, planBags: Double): Advice {
        val mean = f.mean / bagKg; val sd = f.sd / bagKg
        fun chance(b: Double) = f.chanceEnough(b * bagKg)
        // expected bags left over when loading b: E[max(0, b − X)] for a normal X
        fun left(b: Double): Double { if (sd <= 0) return max(0.0, b - mean); val z = (b - mean) / sd; return sd * (z * phi(z) + pdf(z)) }
        val lo = floor(mean - 1.2816 * sd); val hi = ceil(mean + 1.2816 * sd)
        val from = max(1.0, min(planBags, lo) - 1); val to = max(planBags, hi) + 1
        val choices = generateSequence(from) { it + 1 }.takeWhile { it <= to }.map { BagChoice(it, chance(it), left(it)) }.toList()
        val safeFrom = choices.firstOrNull { it.chanceEnough >= 0.80 }?.bags ?: ceil(mean)
        val safeTo = max(safeFrom, choices.lastOrNull { it.chanceEnough <= 0.95 }?.bags?.let { it + 1 } ?: safeFrom)
        val dir = when { planBags < safeFrom -> 1; planBags > safeTo -> -1; else -> 0 }
        return Advice(mean, max(0.0, mean - 1.2816 * sd), mean + 1.2816 * sd, safeFrom, safeTo, choices, dir, if (dir == 0) planBags else if (dir > 0) safeFrom else safeTo)
    }

    private fun pdf(z: Double) = exp(-z * z / 2) / sqrt(2 * Math.PI)
    /** Standard normal distribution function (Abramowitz & Stegun 7.1.26). */
    fun phi(z: Double): Double {
        val t = 1 / (1 + 0.3275911 * abs(z) / sqrt(2.0))
        val y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-z * z / 2)
        return if (z >= 0) (1 + y) / 2 else (1 - y) / 2
    }
    /** Inverse of [phi] (Acklam's approximation, good to ~1e-4 here). */
    fun zOf(p: Double): Double {
        val pp = p.coerceIn(1e-6, 1 - 1e-6)
        val a = doubleArrayOf(-39.69683028665376, 220.9460984245205, -275.9285104469687, 138.3577518672690, -30.66479806614716, 2.506628277459239)
        val b = doubleArrayOf(-54.47609879822406, 161.5858368580409, -155.6989798598866, 66.80131188771972, -13.28068155288572)
        val c = doubleArrayOf(-0.007784894002430293, -0.3223964580411365, -2.400758277161838, -2.549732539343734, 4.374664141464968, 2.938163982698783)
        val d = doubleArrayOf(0.007784695709041462, 0.3224671290700398, 2.445134137142996, 3.754408661907416)
        return when {
            pp < 0.02425 -> { val q = sqrt(-2 * Math.log(pp)); (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1) }
            pp > 1 - 0.02425 -> { val q = sqrt(-2 * Math.log(1 - pp)); -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1) }
            else -> { val q = pp - 0.5; val r = q * q; (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q / (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1) }
        }
    }
}
