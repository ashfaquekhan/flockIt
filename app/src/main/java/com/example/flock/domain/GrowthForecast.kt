package com.example.flock.domain

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Where the flock's weight is heading, from its own weighings: when it reaches the target weight and what
 * it will weigh at the planned harvest age, each with a range.
 *
 * The flock is tracked as a share of the standard curve (weighed ÷ standard for that day) with the same
 * Kalman filter as the feed forecast: the share drifts a little from day to day, each weighing is a noisy
 * reading of it (a sample of a few hundred birds, a scale, a time of day). Projecting forward, the share is
 * held and its uncertainty grows with every day ahead; weight on day d = standard(d) × share.
 * Pure Kotlin, no Android.
 */
object GrowthForecast {
    data class Sample(val day: Int, val weightG: Double)
    data class Point(val day: Int, val mean: Double, val low: Double, val high: Double)

    data class Result(
        /** the flock as a share of the standard now (1.0 = on the standard), ± 1 sd */
        val share: Double, val shareSd: Double, val samples: Int,
        /** forecast weight for every day from [fromDay] to the last day asked for (80 % range) */
        val curve: List<Point>,
        /** first day the forecast weight reaches the target (null: not within the days asked for) */
        val targetDay: Int?, val targetDayEarly: Int?, val targetDayLate: Int?,
        /** forecast at the planned harvest age, and the chance the target is met by then */
        val atHarvest: Point?, val chanceTargetAtHarvest: Double?
    )

    private const val PRIOR_SD = 0.10
    private const val R_OBS = 0.03 * 0.03      // one weighing: about ±3 % (sampling, scale, gut fill)
    private const val Q_DAY = 0.006 * 0.006    // the share drifts about 0.6 % a day

    /**
     * @param standard weight (g) on the standard curve for a flock day
     * @param fromDay  the day to forecast from (today)
     * @param toDay    the last day to forecast
     */
    fun forecast(samples: List<Sample>, standard: (Int) -> Double, fromDay: Int, toDay: Int, targetG: Double, harvestDay: Int): Result {
        val obs = samples.filter { it.weightG > 0 && standard(it.day) > 0 && it.day <= fromDay }.sortedBy { it.day }
        var x = 1.0; var p = PRIOR_SD * PRIOR_SD
        var last = obs.firstOrNull()?.day ?: fromDay
        for (s in obs) {
            p += Q_DAY * max(0, s.day - last)
            val y = s.weightG / standard(s.day)
            val k = p / (p + R_OBS)
            x += k * (y - x); p *= (1 - k)
            last = s.day
        }
        fun at(d: Int): Point {
            val v = p + Q_DAY * max(0, d - last)
            val sd = sqrt(v); val std = standard(d)
            return Point(d, std * x, std * (x - 1.2816 * sd), std * (x + 1.2816 * sd))
        }
        val curve = (fromDay..max(fromDay, toDay)).map { at(it) }
        val harvest = if (harvestDay >= fromDay) at(harvestDay) else null
        val chance = harvest?.let {
            val sd = sqrt(p + Q_DAY * max(0, harvestDay - last)) * standard(harvestDay)
            if (sd <= 0) (if (it.mean >= targetG) 1.0 else 0.0) else 1 - IntakeForecast.phi((targetG - it.mean) / sd)
        }
        return Result(
            share = x, shareSd = sqrt(p), samples = obs.size, curve = curve,
            targetDay = curve.firstOrNull { it.mean >= targetG }?.day,
            targetDayEarly = curve.firstOrNull { it.high >= targetG }?.day,
            targetDayLate = curve.firstOrNull { it.low >= targetG }?.day,
            atHarvest = harvest, chanceTargetAtHarvest = chance
        )
    }
}
