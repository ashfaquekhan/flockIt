package com.example.flock.domain

import kotlin.math.abs

/**
 * The flock's performance on six axes, each scored so that 100 = the company's standard for the day and more is
 * better (drawn up to 150). Pure Kotlin, no Android.
 *
 *  - Growth = weight ÷ the company's weight.
 *  - FCR = the company's FCR ÷ the flock's (lifting counted).
 *  - Livability = the company's mortality ÷ the flock's (each at least 0.5 %, so one death is not a cliff).
 *  - Evenness = 8 % ÷ CV.
 *  - Appetite = 100 − 2 × the gap to the company's feed over 7 days (on the company's ration is best, either way loses).
 *  - EPEF = the flock's ÷ the company's.
 * The breed's ideal (Ross) is scored the same way, so its shape can be drawn beside the flock's.
 */
object Radar {
    const val MAX = 150.0

    data class In(
        val weightG: Double?, val weightCom: Double?, val fcr: Double?, val fcrCom: Double?,
        val mortPct: Double?, val mortCom: Double?, val cvPct: Double?, val eatenVsComPct: Double?,
        val epef: Double?, val epefCom: Double?
    )

    data class Axis(val key: String, val label: String, val score: Double?)

    private fun cap(x: Double) = (x * 100).coerceIn(0.0, MAX)
    private fun ratio(a: Double?, b: Double?) = if (a != null && b != null && b > 0 && a > 0) cap(a / b) else null

    fun scores(i: In): List<Axis> = listOf(
        Axis("growth", "Growth", ratio(i.weightG, i.weightCom)),
        Axis("fcr", "FCR", ratio(i.fcrCom, i.fcr)),
        Axis("live", "Livability", if (i.mortPct != null && i.mortCom != null) cap(maxOf(0.5, i.mortCom) / maxOf(0.5, i.mortPct)) else null),
        Axis("even", "Evenness", i.cvPct?.takeIf { it > 0 }?.let { cap(8.0 / it) }),
        Axis("appetite", "Appetite", i.eatenVsComPct?.let { (100 - abs(it) * 2).coerceIn(0.0, MAX) }),
        Axis("epef", "EPEF", ratio(i.epef, i.epefCom))
    )
}
