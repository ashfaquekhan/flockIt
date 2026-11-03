package com.example.flock.domain

import kotlin.math.abs

/**
 * The flock on four radars, each a group of alike things scored so that 100 = the target. Pure Kotlin, no Android.
 *
 * Birds (target = the company's standard for the day, more is better, drawn up to 150):
 *  - Growth = weight ÷ the company's weight; Gain = the daily gain since the weighing before ÷ the company's.
 *  - FCR = the company's FCR ÷ the flock's (lifting counted).
 *  - Livability = the company's mortality ÷ the flock's (each at least 0.5 %, so one death is not a cliff).
 *  - Evenness = 8 % ÷ CV.
 *  - EPEF = the flock's ÷ the company's.
 *  The breed's ideal (Ross) is scored the same way, so its shape can be drawn beside the flock's.
 *
 * Feed and water, Air, Floor and litter (target = the ideal band, 100 inside it, 50 at the limit, 0 as far again
 * past it — see [band]): what is eaten against the company's ration, birds a pan and a nipple, the walk to feed and
 * water; the house's temperature, humidity, what the birds feel, the air speed, ammonia; the kg on a m², the floor a
 * bird has, the litter's depth, moisture and caking.
 */
object Radar {
    const val MAX = 150.0

    data class In(
        val weightG: Double?, val weightCom: Double?, val fcr: Double?, val fcrCom: Double?,
        val mortPct: Double?, val mortCom: Double?, val cvPct: Double?, val eatenVsComPct: Double?,
        val epef: Double?, val epefCom: Double?,
        val gainG: Double? = null, val gainCom: Double? = null
    )

    data class Axis(val key: String, val label: String, val score: Double?)

    /** one radar: its axes for the flock, and the ideal's shape when it is not the ring itself */
    data class Group(val key: String, val title: String, val target: String, val flock: List<Axis>, val ideal: List<Axis>? = null, val max: Double = 100.0) {
        /** the average of the axes that have a score */
        val mean: Double? get() = flock.mapNotNull { it.score?.coerceAtMost(max) }.takeIf { it.isNotEmpty() }?.average()
    }

    private fun cap(x: Double) = (x * 100).coerceIn(0.0, MAX)
    private fun ratio(a: Double?, b: Double?) = if (a != null && b != null && b > 0 && a > 0) cap(a / b) else null

    /**
     * Birds, against the company: the order puts the long names at the top and bottom of the chart.
     */
    fun scores(i: In): List<Axis> = listOf(
        Axis("live", "Livability", if (i.mortPct != null && i.mortCom != null) cap(maxOf(0.5, i.mortCom) / maxOf(0.5, i.mortPct)) else null),
        Axis("fcr", "FCR", ratio(i.fcrCom, i.fcr)),
        Axis("epef", "EPEF", ratio(i.epef, i.epefCom)),
        Axis("growth", "Growth", ratio(i.weightG, i.weightCom)),
        Axis("even", "Even", i.cvPct?.takeIf { it > 0 }?.let { cap(8.0 / it) }),
        Axis("gain", "Gain", ratio(i.gainG, i.gainCom))
    )

    /** what is eaten against the company's ration: on it is best, either way loses (2 points a %) */
    fun appetite(eatenVsComPct: Double?): Double? = eatenVsComPct?.let { (100 - abs(it) * 2).coerceIn(0.0, 100.0) }

    /**
     * 100 inside [okLo]–[okHi]; falling to 50 at the limit ([limLo] / [limHi]) and to 0 as far again past it. Without a
     * limit on a side, that side does not lose.
     */
    fun band(x: Double?, okLo: Double, okHi: Double, limLo: Double? = null, limHi: Double? = null): Double? {
        if (x == null) return null
        fun fall(over: Double, span: Double) = if (span <= 0) 0.0 else (100 - 50 * over / span).coerceIn(0.0, 100.0)
        return when {
            x in okLo..okHi -> 100.0
            x > okHi -> if (limHi == null) 100.0 else fall(x - okHi, limHi - okHi)
            else -> if (limLo == null) 100.0 else fall(okLo - x, okLo - limLo)
        }
    }

    data class FeedWater(
        val eatenVsComPct: Double?, val birdsPerPan: Double?, val birdsPerPanMax: Double,
        val birdsPerNipple: Double?, val birdsPerNippleMax: Double,
        val feedWalkM: Double, val waterWalkM: Double, val walk: Travel.Limits
    )

    fun feedWater(i: FeedWater): List<Axis> = listOf(
        Axis("eaten", "Eaten", appetite(i.eatenVsComPct)),
        Axis("pans", "Pans", band(i.birdsPerPan, 0.0, i.birdsPerPanMax * 0.9, null, i.birdsPerPanMax * 1.15)),
        Axis("feedwalk", "Feed walk", Travel.score(i.feedWalkM, i.walk)),
        Axis("waterwalk", "Water walk", Travel.score(i.waterWalkM, i.walk)),
        Axis("nipples", "Nipple", band(i.birdsPerNipple, 0.0, i.birdsPerNippleMax * 0.9, null, i.birdsPerNippleMax * 1.15))
    )

    data class Air(
        val houseC: Double?, val idealC: Double, val minC: Double, val maxC: Double,
        val rh: Double?, val rhIdeal: Double, val rhMin: Double, val rhMax: Double,
        val feltC: Double?, val comfortC: Double,
        val airFpm: Double?, val airMaxFpm: Double,
        val nh3: Double?
    )

    fun air(i: Air): List<Axis> = listOf(
        // the limits at least 4 °C off the ideal, so a degree past a narrow range is not already nought
        Axis("temp", "Temp", band(i.houseC, i.idealC - 1, i.idealC + 1, minOf(i.minC, i.idealC - 4), maxOf(i.maxC, i.idealC + 4))),
        Axis("rh", "RH", band(i.rh, minOf(i.rhIdeal, i.rhMax) - 5, maxOf(i.rhIdeal, i.rhMin) + 5, i.rhMin - 10, i.rhMax)),
        Axis("felt", "Birds feel", band(i.feltC, i.comfortC - 1.5, i.comfortC + 1.5, i.comfortC - 5, i.comfortC + 4)),
        Axis("speed", "Air speed", band(i.airFpm, 0.0, i.airMaxFpm, null, i.airMaxFpm * 1.3)),
        Axis("nh3", "NH₃", band(i.nh3, 0.0, Litter.NH3_IDEAL, null, Litter.NH3_MAX))
    )

    data class Floor(
        val kgM2: Double, val comfortKgM2: Double, val capKgM2: Double, val roomRatio: Double,
        val depthCm: Double, val moisturePct: Double, val cakedPct: Double
    )

    fun floor(i: Floor): List<Axis> = listOf(
        Axis("density", "kg/m²", band(i.kgM2, 0.0, i.comfortKgM2, null, i.capKgM2)),
        Axis("room", "Room", band(i.roomRatio, 0.95, 99.0, 0.8, null)),
        Axis("depth", "Depth", band(i.depthCm, Litter.IDEAL_DEPTH_LO, Litter.IDEAL_DEPTH_HI, 3.0, 15.0)),
        Axis("moist", "Moisture", band(i.moisturePct, Litter.IDEAL_MOIST_LO, Litter.IDEAL_MOIST_HI, 15.0, Litter.MOIST_MAX)),
        Axis("caked", "Caked", band(i.cakedPct, 0.0, 5.0, null, 20.0))
    )
}
