package com.example.flock.domain

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * WHEN the birds eat during a day. Broilers that have a dark period do not eat evenly through the lit
 * hours: feeding is busiest in the hours after the lights come on (they wake with an empty gut) and again
 * before they go off (they fill up for the night), and slowest in the middle of the day — more so when the
 * middle of the day is hot. Under continuous light there are no such peaks.
 *
 * The rate is 1.0 on average over the lit hours: about 1.4 at the two peaks and about 0.8 in between.
 * (Spreading the day's feed evenly over the lit hours — what the app did before — makes a mid-morning
 * feeding look as if it runs out an hour or two earlier than it does.)
 */
object FeedingRhythm {
    private const val TROUGH = 0.80
    private const val PEAK = 0.55
    private fun bell(x: Double) = exp(-x * x)

    private fun raw(sinceOn: Double, lightHours: Double): Double = when {
        sinceOn < 0 || sinceOn > lightHours -> 0.0
        lightHours >= 23.5 -> 1.0
        else -> TROUGH + PEAK * bell((sinceOn - 1.5) / 1.6) + PEAK * bell((lightHours - sinceOn - 1.5) / 1.8)
    }

    /** Eating rate [sinceOn] hours after the lights came on, in a day with [lightHours] of light (average 1 over the lit hours). */
    fun rate(sinceOn: Double, lightHours: Double): Double {
        val l = lightHours.coerceIn(1.0, 24.0)
        var sum = 0.0; var n = 0
        var s = 0.125
        while (s < l) { sum += raw(s, l); n++; s += 0.25 }
        val mean = if (n > 0) sum / n else 1.0
        return if (mean > 0) raw(sinceOn, l) / mean else 0.0
    }

    /**
     * The day as 96 quarter-hours: the share of the day's feed eaten in each (they add up to 1).
     * @param lightLevel 0 dark … 1 light at an hour of the day
     * @param feedFactor how much of its normal feed a bird eats in that hour of the day (heat lowers it)
     */
    fun shares(lightsOnHour: Double, lightHours: Double, lightLevel: (Double) -> Double, feedFactor: (Int) -> Double = { 1.0 }): DoubleArray {
        val out = DoubleArray(96)
        val l = lightHours.coerceIn(1.0, 24.0)
        // the scale is the same for every quarter-hour: work it out once
        var sum = 0.0; var n = 0
        var s = 0.125
        while (s < l) { sum += raw(s, l); n++; s += 0.25 }
        val mean = if (n > 0 && sum > 0) sum / n else 1.0
        for (i in 0 until 96) {
            val h = (i + 0.5) / 4
            val since = ((h - lightsOnHour) % 24 + 24) % 24
            val r = if (l >= 23.5) 1.0 else raw(since, l) / mean
            out[i] = r * lightLevel(h).coerceIn(0.0, 1.0) * feedFactor(h.toInt().coerceIn(0, 23)).coerceAtLeast(0.0)
        }
        val total = out.sum()
        if (total > 0) for (i in out.indices) out[i] /= total else for (i in out.indices) out[i] = 1.0 / 96
        return out
    }

    /** Share of the day's feed eaten by [hour] (0–24). */
    fun eatenBy(hour: Double, shares: DoubleArray): Double {
        val q = (hour * 4).coerceIn(0.0, 96.0)
        val whole = q.toInt().coerceAtMost(96)
        var done = 0.0
        for (i in 0 until whole) done += shares[i]
        if (whole < 96) done += shares[whole] * (q - whole)
        return done.coerceIn(0.0, 1.0)
    }
}

/**
 * How likely the birds are HUNGRY, THIRSTY or PANTING at a moment — likelihoods, because without sensors in
 * the house nothing of this is measured. They are worked out from:
 *  - the feedings poured (how much, when), the feed the flock is likely to eat that day and when in the day it
 *    eats it ([FeedingRhythm]) — with a spread, since the real eating rate may be a good deal slower or faster;
 *  - the lights (birds neither eat nor drink in the dark, and wake hungry and thirsty);
 *  - the heat load from the weather at the farm ([BirdEnvironment]): panting and extra thirst.
 *
 * Hungry: the share of birds that have had no feed within reach for long enough to have an empty crop
 * (it empties in about 2–3 hours; from about an hour without feed the first birds are hungry, by 4 hours all are).
 * Thirsty: a small share at any lit moment, more with heat (water need rises about 6 % per °C) and after
 * the dark. Panting: the share of birds panting, half of them at about 6 °C of heat load — few when
 * comfortable, nearly all in real heat, as heat-stress trials find. Pure Kotlin, no Android.
 */
object FlockNeeds {
    /** A feeding: [at] in hours on a clock where today's midnight is 0 (yesterday 20:30 is −3.5), [kg] poured. */
    data class Feeding(val at: Double, val kg: Double)

    data class State(
        /** likelihoods, 0–1 */
        val hungry: Double, val thirsty: Double, val panting: Double,
        /** feed still in the lines and pans in the middle case, and how long they have been empty in it (hours) */
        val feedLeftKg: Double, val emptyForH: Double
    )

    /** how unsure the eating rate is: one standard deviation, as a share of the rate */
    const val SPREAD = 0.18
    private const val BASE_HUNGRY = 0.03
    private const val BASE_THIRSTY = 0.05

    private fun smooth(x: Double, from: Double, to: Double): Double { val t = ((x - from) / (to - from)).coerceIn(0.0, 1.0); return t * t * (3 - 2 * t) }

    /** Share of birds panting at a heat load (felt temperature − comfort temperature, °C). */
    fun pantingShare(heatLoadC: Double): Double = IntakeForecast.phi((heatLoadC - 6.0) / 2.0)
    /** Share of birds made thirsty by heat alone. */
    fun heatThirst(heatLoadC: Double): Double = IntakeForecast.phi((heatLoadC - 7.0) / 3.0)

    // seven cases of the eating rate, from much slower to much faster than expected, weighted as a normal spread
    private val Z = doubleArrayOf(-2.0, -1.33, -0.67, 0.0, 0.67, 1.33, 2.0)
    private val W: DoubleArray = Z.map { exp(-it * it / 2) }.let { p -> val s = p.sum(); p.map { it / s }.toDoubleArray() }

    /**
     * @param now        the moment asked about, hours since today's midnight (27.0 = 03:00 tomorrow)
     * @param feedings   the feedings to reckon with (the last day and a half)
     * @param dayEatKg   what the flock is likely to eat in a day
     * @param shares     the day's eating by quarter-hour ([FeedingRhythm.shares])
     * @param lightLevel 0 dark … 1 light at an hour of the day
     * @param heatLoadAt heat load (°C) at an hour of the day; 0 = the ideal house
     */
    fun at(now: Double, feedings: List<Feeding>, dayEatKg: Double, shares: DoubleArray, lightLevel: (Double) -> Double,
           heatLoadAt: (Double) -> Double, spread: Double = SPREAD): State {
        val dt = 0.25
        val feeds = feedings.filter { it.at <= now && it.at >= now - 36 && it.kg > 0 }.sortedBy { it.at }
        // start a little before the first feeding (or half a day back), on a quarter-hour
        val t0 = Math.floor(((feeds.firstOrNull()?.at ?: (now - 12)) - 0.25) * 4) / 4
        var hungry = 0.0; var left = 0.0; var emptyFor = 0.0
        for (c in Z.indices) {
            val k = max(0.2, 1 + spread * Z[c])
            var stock = 0.0; var fast = 0.0; var empty = 0.0
            var i = 0
            var t = t0
            while (t < now - 1e-9) {
                val step = min(dt, now - t)
                while (i < feeds.size && feeds[i].at <= t + step) { stock += feeds[i].kg; i++ }
                val hd = ((t % 24) + 24) % 24
                val lit = lightLevel(hd) > 0.5
                val want = dayEatKg * shares[((hd * 4).toInt()).coerceIn(0, 95)] * (step / dt) * k
                val eat = min(stock, want)
                stock -= eat
                val fed = lit && want > 0 && eat >= want * 0.5
                // birds that can eat catch up within about an hour; birds that cannot (dark, or nothing in the pans) go hungrier
                if (fed) fast *= exp(-step / 0.6) else fast += step
                if (lit && want > 0 && stock <= 1e-6) empty += step else if (stock > 1e-6) empty = 0.0
                t += step
            }
            hungry += W[c] * (BASE_HUNGRY + (1 - BASE_HUNGRY) * smooth(fast, 1.0, 4.0))
            if (Z[c] == 0.0) { left = stock; emptyFor = empty }
        }
        // thirst: the dark (no drinking), the heat, and the ordinary share of birds on their way to a drink
        var dry = 0.0
        var t = now - 12
        while (t < now - 1e-9) {
            val hd = ((t % 24) + 24) % 24
            if (lightLevel(hd) > 0.5) dry *= exp(-dt / 0.3) else dry += dt
            t += dt
        }
        val hourNow = ((now % 24) + 24) % 24
        val h = heatLoadAt(hourNow)
        val base = if (lightLevel(hourNow) > 0.5) BASE_THIRSTY else 0.0
        val thirsty = 1 - (1 - base) * (1 - heatThirst(h)) * (1 - smooth(dry, 2.0, 6.0))
        return State(hungry.coerceIn(0.0, 1.0), thirsty.coerceIn(0.0, 1.0), pantingShare(h), left, emptyFor)
    }

    /** The hour (same clock as [Feeding.at]) by which the pans are most likely empty in the middle case, null if not within [horizonH]. */
    fun emptyAt(from: Double, feedings: List<Feeding>, dayEatKg: Double, shares: DoubleArray, lightLevel: (Double) -> Double, rate: Double = 1.0, horizonH: Double = 30.0): Double? {
        val feeds = feedings.filter { it.kg > 0 }.sortedBy { it.at }
        if (feeds.isEmpty()) return null
        var stock = 0.0; var i = 0
        var t = Math.floor((feeds.first().at) * 4) / 4
        val dt = 0.25
        while (t < from + horizonH) {
            while (i < feeds.size && feeds[i].at <= t + dt) { stock += feeds[i].kg; i++ }
            val hd = ((t % 24) + 24) % 24
            val want = dayEatKg * shares[((hd * 4).toInt()).coerceIn(0, 95)] * rate
            if (want > 0 && lightLevel(hd) > 0.5) {
                if (stock <= want && i >= feeds.size) return t + (if (want > 0) stock / want * dt else 0.0)
                stock = max(0.0, stock - want)
            }
            t += dt
        }
        return null
    }
}
