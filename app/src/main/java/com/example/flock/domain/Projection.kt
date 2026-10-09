package com.example.flock.domain

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * What the app projects, in two forms:
 *
 *  - [history]: for every flock day, the values the app projected for it BEFORE that day's entry — worked out
 *    from the days before it only. Plotted beside the entries it shows how good the projections were; past
 *    the last entry it is the forecast. Because it is recomputed from the records, the whole line exists for
 *    a flock that is already running, and the phone and the sheet show the same numbers.
 *  - [litShare] and [grown]: projections that move with the clock — the weight since the last weighing, the
 *    feed and water gone so far today.
 *
 * Pure Kotlin, no Android.
 */
object Projection {
    /** One flock day as entered, with the feed plan the app made for it. */
    data class DayIn(
        val day: Int,
        /** weighed that day (null: no weighing) */
        val weightG: Double?,
        /** birds alive after the day's deaths, culls and lifting */
        val live: Int,
        val deaths: Int,
        /** feed entered that day, kg — what was eaten the day before (0: nothing entered) */
        val usedKg: Double,
        /** the plan's feed for this day, kg */
        val planKg: Double
    )

    /** What was projected for a day from the days before it (null: nothing to project from). */
    data class DayOut(
        val day: Int, val weightG: Double?,
        /** feed expected to be entered that day (eaten the day before): house kg, per bird, running total per bird */
        val feedKg: Double?, val feedPerBirdG: Double?, val cumFeedPerBirdG: Double?,
        val fcr: Double?, val deaths: Double?, val cumMortPct: Double?, val live: Double?
    )

    /**
     * @param days        every day of the flock from 0, in order
     * @param entry       birds the flock started with
     * @param lastEntered the last day anything was entered (days after it are a forecast from that day)
     * @param grow        weight (g) of a bird that weighed the first argument, the second argument days later
     * @param startG      the weight to grow from before the first weighing (a placed chick)
     * @param stdMortPct  the standard's daily mortality % for a flock day (used until the flock has its own)
     * @param carrySd     see [IntakeForecast.forecast]
     */
    fun history(
        days: List<DayIn>, entry: Int, lastEntered: Int, grow: (Double, Double) -> Double, startG: Double,
        stdMortPct: (Int) -> Double, carrySd: Double
    ): List<DayOut> {
        val by = days.associateBy { it.day }
        val out = ArrayList<DayOut>(days.size)
        // running totals of what was entered, by day
        var cumKg = 0.0; var cumDeaths = 0
        val cumKgAt = HashMap<Int, Double>(); val cumDeathsAt = HashMap<Int, Int>()
        for (d in days) { cumKg += d.usedKg; cumDeaths += d.deaths; cumKgAt[d.day] = cumKg; cumDeathsAt[d.day] = cumDeaths }

        for (d in days) {
            val n = d.day
            if (n < 1) { out += DayOut(n, null, null, null, null, null, null, null, null); continue }
            val k = minOf(n - 1, lastEntered)                    // the last day known when projecting day n
            val ahead = n - k                                     // 1 = the usual next-day projection
            val known = days.filter { it.day <= k }
            val base = by[k]

            // weight: the last weighing carried along the growth curve
            val w0 = known.lastOrNull { it.weightG != null }
            val weight = if (w0 != null) grow(w0.weightG!!, (n - w0.day).toDouble()) else grow(startG, n.toDouble())

            // deaths: the flock's own recent daily rate (newer days count more), the standard until it has one
            var rate: Double? = null
            for (x in known) {
                if (x.day < 1) continue
                val before = (by[x.day - 1]?.live ?: entry).takeIf { it > 0 } ?: continue
                val r = x.deaths * 100.0 / before
                rate = if (rate == null) r else 0.7 * rate + 0.3 * r
            }
            val dayRate = rate ?: stdMortPct(n)
            val liveBase = (base?.live ?: entry).toDouble()
            val liveBefore = liveBase * (1 - dayRate / 100).pow(ahead - 1)
            val deaths = liveBefore * dayRate / 100
            val live = liveBefore - deaths
            val cumMort = if (entry > 0) ((cumDeathsAt[k] ?: 0) + (liveBase - live)) * 100.0 / entry else null

            // feed entered on day n = eaten during day n−1: the plan for that day × what the flock has shown so far
            val hist = known.mapNotNull { x -> val next = by[x.day + 1] ?: return@mapNotNull null
                if (next.day > k || next.usedKg <= 0 || x.planKg <= 0) null else IntakeForecast.Day(x.day, x.planKg, next.usedKg) }
            val plan = by[n - 1]?.planKg ?: 0.0
            val f = IntakeForecast.forecast(hist, plan, carrySd)
            val feedKg = if (plan <= 0) null else if (f.learning) plan else if (ahead == 1) f.load else f.mean
            // feed of the days between the last entry and day n (a forecast more than one day ahead)
            var between = 0.0
            for (m in (k + 1) until n) between += (by[m - 1]?.planKg ?: 0.0) * (if (f.learning) 1.0 else f.ratio)
            val cumFeed = feedKg?.let { (cumKgAt[k] ?: 0.0) + between + it }
            val liveYesterday = if (ahead == 1) liveBase else liveBefore
            out += DayOut(
                n, weight, feedKg, feedKg?.let { if (liveYesterday > 0) it * 1000 / liveYesterday else null },
                cumFeed?.let { if (live > 0) it * 1000 / live else null },
                cumFeed?.let { if (live > 0 && weight > 0) it / (live * weight / 1000) else null },
                deaths, cumMort, live
            )
        }
        return out
    }

    /** Share (0–1) of a day's eating done by [hour] (0–24): birds eat while the lights are on ([light] 0–1 at an hour). */
    fun litShare(hour: Double, light: (Double) -> Double): Double {
        var done = 0.0; var all = 0.0
        var t = 0.125
        while (t < 24.0) { val l = light(t); all += l; if (t <= hour) done += l; t += 0.25 }
        return if (all > 0) (done / all).coerceIn(0.0, 1.0) else (hour / 24).coerceIn(0.0, 1.0)
    }

    /** A weight carried forward [days] along the curve; [gainFactor] slows (or speeds) the growth since the weighing. */
    fun grown(weightG: Double, days: Double, grow: (Double, Double) -> Double, gainFactor: Double = 1.0): Double =
        if (days <= 0) weightG else weightG + (grow(weightG, days) - weightG) * gainFactor
}

/**
 * Evenness of the flock. The true CV needs birds weighed one by one. When birds are weighed in groups
 * (ten in a crate), the group averages still carry it: the average of n birds varies √n less than single
 * birds do, so   bird CV ≈ spread between the group averages × √(birds per group).
 * It is a rough figure — few groups, and a light or heavy corner of the house counts as unevenness too — so
 * it is pooled over the last weighings and shown as an estimate.
 */
object Uniformity {
    /** One weighing: each group's average weight (g) and the birds in it. Returns (CV as a fraction)², and its degrees of freedom. */
    fun relVariance(groups: List<Pair<Double, Int>>): Pair<Double, Int>? {
        val g = groups.filter { it.first > 0 && it.second > 0 }
        if (g.size < 3) return null
        val mean = g.sumOf { it.first * it.second } / g.sumOf { it.second }
        val meanOfMeans = g.map { it.first }.average()
        val between = g.sumOf { (it.first - meanOfMeans) * (it.first - meanOfMeans) } / (g.size - 1)
        val perGroup = g.map { it.second }.average()
        return if (mean > 0) between * perGroup / (mean * mean) to g.size - 1 else null
    }

    /** CV % estimated from the last [use] weighings that had three or more groups (newest last in [weighings]). */
    fun cvFromGroups(weighings: List<List<Pair<Double, Int>>>, use: Int = 3): Double? {
        val v = weighings.mapNotNull { relVariance(it) }.takeLast(use)
        if (v.isEmpty()) return null
        val df = v.sumOf { it.second }
        return sqrt(max(0.0, v.sumOf { it.first * it.second } / df)) * 100
    }
}
