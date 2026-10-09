package com.example.flock.domain

import com.example.flock.engine.IbController
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * What the air around the birds does to them: how much they eat, drink and grow, how hard they breathe, their
 * body temperature and the risk of losing birds — for one moment, and summed over a day.
 *
 * Everything is driven by ONE number, the HEAT LOAD: the temperature the birds feel (air temperature, corrected
 * for humidity and for the cooling of moving air — the same "felt" temperature the fan plan uses) minus the
 * comfort temperature for their size. 0 = comfortable, + = too warm, − = too cold.
 *
 * Where the numbers come from (published work; none of it is fitted to this farm yet):
 *  - Feed: about −1.5 % per °C of mild / day-night heat, steepening towards −3.5 % per °C in strong, lasting
 *    heat (Baziz et al. 1996; Teyssier et al. 2022, review of heat stress in chickens).
 *  - Water: about +6 % per °C (NRC 1994, as used by Aviagen and Cobb).
 *  - Growth falls more than feed, because the bird's upkeep is paid first: gain = (feed − upkeep) ÷ cost, with
 *    upkeep ≈ 36–45 % of normal intake, plus the extra the review reports beyond the lost feed.
 *  - Body temperature rise and the danger steps follow Tao & Xin (2003): about +1.0 °C = still coping,
 *    +2.5 °C = alert, +4.0 °C = danger, beyond = emergency.
 *  - Breathing: 20–40 breaths a minute at rest, panting above about 60 (see the ⓘ on the farm window).
 *
 * The air comes from a [Source]: house sensors when the farm has them, a value set by hand, the house worked
 * out from the weather at the farm's location, or the ideal house. Adding sensors means adding a source —
 * nothing here changes. Pure Kotlin, no Android.
 */
object BirdEnvironment {

    /** Where an air reading came from; the first one available is used, in this order. */
    enum class Source(val label: String) {
        SENSOR("house sensor"), MANUAL("set by hand"), WEATHER("from the weather"), SEASON("typical day"), IDEAL("ideal house")
    }

    /** Air at bird height: temperature, humidity and the average air speed along the house (ft/min). */
    data class Air(val tempC: Double, val rhPct: Double, val airFpm: Double = 0.0, val source: Source = Source.IDEAL)

    /** The first reading there is — sensor before hand-set before modelled before ideal. */
    fun pick(vararg readings: Air?): Air? = readings.filterNotNull().minByOrNull { it.source.ordinal }

    enum class State(val label: String, val short: String) {
        COLD("cold", "cold"), COOL("cool", "cool"), COMFORT("comfortable", "comfy"), WARM("warm", "warm"), HOT("hot", "hot"), DANGER("in danger", "danger")
    }

    data class Response(
        /** the temperature the birds feel, and how far that is from their comfort (°C) */
        val feltC: Double, val heatLoadC: Double, val state: State,
        /** multipliers on the normal day: 1.0 = no effect */
        val feedFactor: Double, val waterFactor: Double, val gainFactor: Double, val mortalityFactor: Double,
        val bodyTempC: Double, val bodyRiseC: Double, val breathsPerMin: Double
    ) {
        val panting: Boolean get() = breathsPerMin > PANT_ABOVE
    }

    const val PANT_ABOVE = 60.0
    /** ± this many °C around comfort nothing changes */
    const val COMFORT_BAND_C = 2.0

    fun stateOf(heatLoadC: Double): State = when {
        heatLoadC < -6 -> State.COLD
        heatLoadC < -3 -> State.COOL
        heatLoadC <= 3 -> State.COMFORT
        heatLoadC <= 6 -> State.WARM
        heatLoadC <= 10 -> State.HOT
        else -> State.DANGER
    }

    /** Feed eaten against a comfortable day. */
    fun feedFactor(h: Double): Double = when {
        h > COMFORT_BAND_C -> {
            val mild = min(h, 8.0) - COMFORT_BAND_C            // −1.5 % per °C up to 8 °C over
            val strong = max(0.0, h - 8.0)                      // −3.5 % per °C beyond
            (1.0 - 0.015 * mild - 0.035 * strong).coerceAtLeast(0.45)
        }
        h < -COMFORT_BAND_C -> (1.0 + 0.010 * (-COMFORT_BAND_C - h)).coerceAtMost(1.10)   // cold birds eat to keep warm
        else -> 1.0
    }

    /** Water drunk against a comfortable day. */
    fun waterFactor(h: Double): Double = when {
        h > 1.0 -> (1.0 + 0.06 * (h - 1.0)).coerceAtMost(2.2)
        h < -COMFORT_BAND_C -> (1.0 - 0.010 * (-COMFORT_BAND_C - h)).coerceAtLeast(0.90)
        else -> 1.0
    }

    /** Share of a normal day's feed that only keeps the bird going (no growth), by body weight. */
    fun upkeepShare(bwG: Double): Double = (0.36 + 0.025 * bwG / 1000.0).coerceIn(0.36, 0.45)

    /** Growth against a comfortable day: what is left of the feed after upkeep, less the extra cost of heat or cold. */
    fun gainFactor(h: Double, bwG: Double): Double {
        val mu = upkeepShare(bwG)
        return when {
            h > COMFORT_BAND_C -> {
                val fromFeed = (feedFactor(h) - mu) / (1 - mu)
                (1.0 - (1.0 - fromFeed) * 1.15).coerceIn(0.0, 1.0)
            }
            h < -COMFORT_BAND_C -> (1.0 - 0.012 * (-COMFORT_BAND_C - h)).coerceAtLeast(0.80)   // feed burnt for warmth
            else -> 1.0
        }
    }

    /** Rise of the body temperature above normal (°C); chicks that are cold drop below it. */
    fun bodyRise(h: Double, day: Int): Double = when {
        h > 13 -> min(5.5, 4.0 + 0.8 * (h - 13))
        h > 11 -> 2.5 + 0.75 * (h - 11)
        h > 8 -> 1.0 + 0.5 * (h - 8)
        h > 4 -> 0.25 * (h - 4)
        h < -4 && day <= 10 -> max(-2.0, -0.15 * (-4 - h))     // chilled chicks (Ross: under 39.4 °C at the vent)
        else -> 0.0
    }

    /** Daily deaths against a normal day: climbs with the body temperature; cold in the first days costs chicks too. */
    fun mortalityFactor(h: Double, day: Int): Double {
        val rise = bodyRise(h, day)
        return when {
            rise > 0.5 -> min(40.0, exp(0.85 * (rise - 0.5)))
            h < -3 && day <= 10 -> min(2.5, 1.0 + 0.12 * (-3 - h))
            else -> 1.0
        }
    }

    /** Breaths a minute: resting rate up to comfort + 2 °C, then rising fast (panting above [PANT_ABOVE]). */
    fun breaths(h: Double, day: Int): Double {
        val rest = if (day <= 7) 40.0 else 30.0
        return min(240.0, rest * exp(0.22 * max(0.0, h - COMFORT_BAND_C)))
    }

    /**
     * @param comfortC    the comfort temperature for birds of this size (still air, 65 % RH)
     * @param normalBodyC the body temperature of a comfortable bird of this age
     */
    fun respond(day: Int, bwG: Double, air: Air, comfortC: Double, normalBodyC: Double): Response =
        respondFelt(day, bwG, air.tempC + IbController.rhOffsetC(air.rhPct) - IbController.chillC(air.airFpm, day, air.tempC), comfortC, normalBodyC)

    /** The same from a felt temperature that is already worked out (the fan plan has its own). */
    fun respondFelt(day: Int, bwG: Double, feltC: Double, comfortC: Double, normalBodyC: Double): Response {
        val h = feltC - comfortC
        val rise = bodyRise(h, day)
        return Response(feltC, h, stateOf(h), feedFactor(h), waterFactor(h), gainFactor(h, bwG), mortalityFactor(h, day),
            normalBodyC + rise, rise, breaths(h, day))
    }

    /** One hour of a day: the weather outside, the air in the house and what it does to the birds. */
    data class Hour(val hour: Int, val outC: Double?, val outRh: Double?, val air: Air, val fans: Double?, val r: Response)

    /** A whole day: the 24 hours and their sum. */
    data class Day(
        val hours: List<Hour>, val source: Source,
        val feedFactor: Double, val waterFactor: Double, val gainFactor: Double, val mortalityFactor: Double,
        val hoursWarm: Int, val hoursHot: Int, val hoursCold: Int
    ) {
        val hottest: Hour get() = hours.maxBy { it.r.heatLoadC }
        val coolest: Hour get() = hours.minBy { it.r.heatLoadC }
        val outMinC: Double? get() = hours.mapNotNull { it.outC }.minOrNull()
        val outMaxC: Double? get() = hours.mapNotNull { it.outC }.maxOrNull()
        val outMeanC: Double? get() = hours.mapNotNull { it.outC }.takeIf { it.isNotEmpty() }?.average()
        val outMeanRh: Double? get() = hours.mapNotNull { it.outRh }.takeIf { it.isNotEmpty() }?.average()
        val worst: State get() = hours.map { it.r.state }.let { s -> s.maxByOrNull { kotlin.math.abs(it.ordinal - State.COMFORT.ordinal) } ?: State.COMFORT }
        fun at(hour: Int): Hour? = hours.firstOrNull { it.hour == hour }
    }

    /**
     * Sums the hours into the day. Birds eat and drink while the lights are on, so those hours carry the feed
     * and water effect ([lightLevel] 0–1 for an hour); growth and the risk of deaths run all 24 hours.
     */
    fun day(hours: List<Hour>, lightLevel: (Int) -> Double): Day? {
        if (hours.isEmpty()) return null
        val w = hours.map { max(0.05, lightLevel(it.hour)) }
        val ws = w.sum()
        fun lit(f: (Response) -> Double) = hours.indices.sumOf { f(hours[it].r) * w[it] } / ws
        return Day(
            hours, hours.minBy { it.air.source.ordinal }.air.source,
            feedFactor = lit { it.feedFactor }, waterFactor = lit { it.waterFactor },
            gainFactor = hours.map { it.r.gainFactor }.average(), mortalityFactor = hours.map { it.r.mortalityFactor }.average(),
            hoursWarm = hours.count { it.r.state == State.WARM }, hoursHot = hours.count { it.r.state >= State.HOT },
            hoursCold = hours.count { it.r.state <= State.COOL }
        )
    }
}
