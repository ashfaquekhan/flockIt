package com.example.flock.domain

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * How much feed the flock needs today, learned from what was entered so far.
 *
 * WHAT THE RECORDS LOOK LIKE. The bags entered for a day are the bags POURED that day, not exactly what was
 * eaten: feed stays in the hoppers, the lines and the pans. A big entry is followed by a small one (in the
 * farm's own records the day-to-day entries swing by about ±25 % around the plan, while any three days
 * together stay within about ±9 % of it). A method that follows each day's entry chases that swing.
 *
 * THE MODEL (a Kalman filter on a small state-space model — the standard real-time method for this):
 *   appetite  a(t) = a(t−1) + small drift          the flock's eating against the plan, slow to change
 *   in lines  s(t)                                 feed sitting in the lines above (+) or below (−) the usual
 *   entered   y(t) = plan(t) × a(t) + s(t) − s(t−1)
 * so a day with more poured than eaten leaves s high, and the filter expects less to be poured the next day.
 * From it, for today:
 *   likely eaten = plan × a          (what the birds will take; with a range)
 *   to pour      = likely eaten − ½ s  (less when feed is still in the lines, more when they ran low; s is the
 *                                        least sure part, so half of it is acted on)
 *
 * The plan itself already follows the flock: it is the company's feed for birds of the flock's own weight
 * (so a heavy flock is fed as an older one), for the birds alive today. The first days of a flock (trays,
 * paper, the first fill of the lines) are left out of the learning. Pure Kotlin, no Android.
 */
object IntakeForecast {
    /** One past day: the plan's feed for it and what was entered as used (any unit, the same for both). */
    data class Day(val day: Int, val planned: Double, val eaten: Double)

    /** Planned against entered over a run of days. */
    data class Window(val days: Int, val planned: Double, val entered: Double) {
        val pct: Double get() = if (planned > 0) entered / planned * 100 else 100.0
    }

    data class Result(
        /** the flock's appetite against the plan (1.0 = eats exactly the plan) and how sure that is (± 1 sd) */
        val ratio: Double, val ratioSd: Double,
        /** what the birds are likely to eat today, in the plan's unit, and its one-sd spread */
        val mean: Double, val sd: Double,
        /** feed sitting in the lines above (+) or below (−) the usual level, as far as the pour is corrected for it */
        val inLines: Double,
        /** what to pour today = likely eaten − in lines, and its spread */
        val load: Double, val loadSd: Double,
        /** days the forecast has learned from (0–2: still learning, the plan is used as it is) */
        val days: Int,
        /** planned against entered over the last 3 and the last 7 learned days */
        val last3: Window?, val last7: Window?,
        /** how far a single day's entry has been from the plan × appetite, % (mean absolute) */
        val dayScatterPct: Double?
    ) {
        val learning: Boolean get() = days < MIN_DAYS
        fun low(p: Double = 0.10): Double = max(0.0, mean + sd * zOf(p))
        fun high(p: Double = 0.90): Double = mean + sd * zOf(p)
    }

    const val MIN_DAYS = 3
    /** entries before this flock day are not learned from (trays, paper, first fill of the lines) */
    const val LEARN_FROM_DAY = 6
    private const val PRIOR_SD = 0.12
    private const val DRIFT_SD = 0.02          // appetite drifts about 2 % of the plan a day
    private const val DAY_SD = 0.04            // the birds' own day-to-day variation
    private const val LINES_TRUST = 0.5        // share of the estimated feed in the lines that the pour is corrected by

    /**
     * @param history      past days, any order
     * @param plannedToday the plan for today
     * @param carrySd      how much the feed in the lines varies from day to day (plan's unit) — about 40 % of
     *                     what the feeder lines hold
     */
    fun forecast(history: List<Day>, plannedToday: Double, carrySd: Double, learnFromDay: Int = LEARN_FROM_DAY): Result {
        val obs = history.filter { it.planned > 0 && it.eaten > 0 && it.day >= learnFromDay }.sortedBy { it.day }
        val vs = max(1e-6, carrySd * carrySd)
        val q = DRIFT_SD * DRIFT_SD
        val r = vs * 0.01
        // state [a, s(t), s(t−1)] and its covariance (symmetric, kept as six numbers)
        var a = 1.0; var s = 0.0; var sp = 0.0
        var paa = PRIOR_SD * PRIOR_SD; var pas = 0.0; var pap = 0.0; var pss = vs; var psp = 0.0; var ppp = vs
        fun predict() {
            // a stays (with drift); the new s is unknown around 0; the old s moves to s(t−1)
            sp = s; s = 0.0
            ppp = pss; pap = pas; psp = 0.0
            pss = vs; pas = 0.0; paa += q
        }
        var last: Int? = null
        val scatter = mutableListOf<Double>()
        for (o in obs) {
            repeat(max(1, o.day - (last ?: (o.day - 1)))) { predict() }      // a day without an entry: one more step
            val p = o.planned
            val yHat = p * a + s - sp
            if (last != null) scatter += abs(o.eaten - p * a) / (p * a) * 100
            // H = [p, 1, −1]
            val phA = p * paa + pas - pap
            val phS = p * pas + pss - psp
            val phP = p * pap + psp - ppp
            val sInn = p * phA + phS - phP + r
            val kA = phA / sInn; val kS = phS / sInn; val kP = phP / sInn
            val e = o.eaten - yHat
            a += kA * e; s += kS * e; sp += kP * e
            // P = P − K (H P)
            paa -= kA * phA; pas -= kA * phS; pap -= kA * phP
            pss -= kS * phS; psp -= kS * phP; ppp -= kP * phP
            last = o.day
        }
        predict()
        val p = plannedToday
        val meanEat = p * a
        val sdEat = p * sqrt(max(0.0, paa) + DAY_SD * DAY_SD)
        // what sits in the lines is the least sure part of the estimate (after a big pour and a small one it cannot
        // tell which of the two days was the odd one), so only half of it is acted on
        val lines = LINES_TRUST * sp
        val loadVar = p * p * (max(0.0, paa) + DAY_SD * DAY_SD) + LINES_TRUST * LINES_TRUST * max(0.0, ppp) - 2 * LINES_TRUST * p * pap
        fun window(n: Int): Window? = obs.takeLast(n).takeIf { it.size >= n }?.let { w -> Window(n, w.sumOf { it.planned }, w.sumOf { it.eaten }) }
        return Result(
            ratio = a, ratioSd = sqrt(max(0.0, paa)), mean = meanEat, sd = sdEat,
            inLines = if (obs.size >= MIN_DAYS) lines else 0.0,
            load = max(0.0, if (obs.size >= MIN_DAYS) meanEat - lines else p), loadSd = sqrt(max(0.0, loadVar)),
            days = obs.size, last3 = window(3), last7 = window(7),
            dayScatterPct = if (scatter.size >= 3) scatter.average() else null
        )
    }

    /** The forecast in whole bags. */
    data class Advice(
        val planBags: Double,
        /** what the birds are likely to eat, with the range it falls in 8 days out of 10 */
        val likelyBags: Double, val lowBags: Double, val highBags: Double,
        /** bags sitting in the lines above (+) or below (−) the usual */
        val inLinesBags: Double,
        /** whole bags to pour today */
        val loadBags: Double,
        /** −1 pour fewer than the plan, 0 the plan is right, +1 pour more */
        val direction: Int
    )

    /** Turns a forecast (kg for the whole house) into bags; [planBags] is the plan in whole bags. */
    fun advise(f: Result, bagKg: Double, planBags: Double): Advice {
        if (f.learning || bagKg <= 0) return Advice(planBags, f.mean / max(1e-9, bagKg), f.low() / max(1e-9, bagKg), f.high() / max(1e-9, bagKg), 0.0, planBags, 0)
        val load = max(1.0, Math.round(f.load / bagKg).toDouble())
        return Advice(planBags, f.mean / bagKg, f.low() / bagKg, f.high() / bagKg, f.inLines / bagKg, load,
            when { load >= planBags + 1 -> 1; load <= planBags - 1 -> -1; else -> 0 })
    }

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
