package com.example.flock.domain

import kotlin.math.abs

/**
 * Performance measures worked out from the flock's own records (weights, feed used, deaths) — the ones
 * that say most about how a broiler flock is doing beyond weight, FCR and mortality to date:
 *
 *  - ADG, average daily gain (g/day) = weight ÷ age: the plain growth rate, comparable between flocks.
 *  - 7-day FCR = feed eaten in the last 7 days ÷ weight gained in them: shows a feed-conversion problem
 *    weeks before the cumulative FCR moves.
 *  - Days ahead / behind = the age at which the standard reaches the flock's weight − the flock's age:
 *    growth in the unit the farm plans in.
 *  - First-week mortality (%): chick quality and brooding; the standard early warning.
 *  - Last-7-days mortality (%): what is happening now, not diluted by the whole batch.
 *  - Mortality-adjusted FCR = feed ÷ (live weight + weight of the birds lost): the feed conversion of the
 *    birds themselves, with the feed eaten by birds that later died taken out of the blame.
 *
 * Pure Kotlin, no Android.
 */
object FlockKpis {
    /**
     * One flock day. [weightG] is the weight that morning (measured or projected), [eatenG] the feed per bird
     * eaten DURING the day (entered the next morning; null = not entered), [deaths] / [culls] entered that day,
     * [live] the birds alive after them.
     */
    data class Day(val day: Int, val weightG: Double, val measured: Boolean, val eatenG: Double?, val deaths: Int, val culls: Int, val live: Int)

    data class Result(
        val adg: Double?,
        val fcr7: Double?, val fcr7Measured: Boolean,
        val firstWeekMortPct: Double?, val firstWeekComplete: Boolean,
        val mort7Pct: Double?,
        val adjFcr: Double?
    )

    /**
     * @param days    every day from 0 to [today], in order
     * @param entry   birds the flock started with
     * @param cumFeedKg feed used to date (kg)
     */
    fun compute(days: List<Day>, today: Int, entry: Int, cumFeedKg: Double): Result {
        val by = days.associateBy { it.day }
        val now = by[today]
        val adg = now?.takeIf { today >= 1 }?.let { it.weightG / today }

        // feed eaten during days today-7 … today-1, and the weight gained from the morning of today-7 to this morning
        var fcr7: Double? = null; var fcr7Measured = false
        val start = by[today - 7]
        if (now != null && start != null && today >= 7) {
            val eaten = (today - 7 until today).map { by[it]?.eatenG }
            val gain = now.weightG - start.weightG
            if (eaten.all { it != null } && gain > 0) {
                fcr7 = eaten.sumOf { it!! } / gain
                fcr7Measured = now.measured && start.measured
            }
        }

        // first week: deaths entered on days 0–6 (the chart's days 1–7)
        val firstWeek = days.filter { it.day in 0..6 }
        val fw = if (entry > 0 && firstWeek.isNotEmpty()) firstWeek.sumOf { it.deaths } * 100.0 / entry else null

        val base = by[today - 7]?.live ?: entry
        val m7 = if (base > 0) days.filter { it.day in (today - 6)..today }.sumOf { it.deaths } * 100.0 / base else null

        // weight of the birds lost, each at the flock's weight on the day it was lost
        val lostKg = days.filter { it.day <= today }.sumOf { (it.deaths + it.culls) * it.weightG / 1000.0 }
        val liveKg = now?.let { it.live * it.weightG / 1000.0 } ?: 0.0
        val adj = if (cumFeedKg > 0 && liveKg + lostKg > 0 && today >= 1) cumFeedKg / (liveKg + lostKg) else null

        return Result(adg, fcr7, fcr7Measured, fw, today >= 6, m7, adj)
    }

    /** One check of a projection against what was then measured. */
    data class Check(val day: Int, val projected: Double, val actual: Double) {
        val errorPct: Double get() = if (actual != 0.0) (projected - actual) / actual * 100 else 0.0
    }

    /** How good the projections of one quantity were: every check, the latest, and the average miss (%). */
    data class Accuracy(val checks: List<Check>) {
        val latest: Check? get() = checks.lastOrNull()
        /** mean absolute error, % */
        val averageMissPct: Double? get() = if (checks.isEmpty()) null else checks.map { abs(it.errorPct) }.average()
        /** mean signed error, %: positive = the app projects too high */
        val biasPct: Double? get() = if (checks.isEmpty()) null else checks.map { it.errorPct }.average()
        fun on(day: Int): Check? = checks.firstOrNull { it.day == day }
        fun upTo(day: Int) = Accuracy(checks.filter { it.day <= day })
    }
}
