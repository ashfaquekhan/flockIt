package com.example.flock.domain

import kotlin.math.min

/**
 * Whether to load a little more or less than the standard feed to bring weight or FCR back on track —
 * deliberately small and conditional, because birds eat to appetite: extra feed only helps if they clear
 * the pans, and cutting feed risks uniformity.
 *  - first week: none (chicks feed to appetite);
 *  - weight more than 3 % under commercial: + half the gap, at most +5 %;
 *  - FCR more than 3 % over commercial while weight is on target: − half the gap, at most −3 % (check wastage first);
 *  - otherwise none.
 */
object FeedCorrection {
    enum class Why { FIRST_WEEK, UNDER_WEIGHT, HIGH_FCR, ON_TRACK, NO_STANDARD }
    data class Advice(val pct: Double, val why: Why)

    const val MAX_UP_PCT = 5.0
    const val MAX_DOWN_PCT = 3.0
    const val TRIGGER_PCT = 3.0

    fun advise(day: Int, bw: Double?, bwCom: Double?, fcr: Double?, fcrCom: Double?): Advice {
        if (day <= 7) return Advice(0.0, Why.FIRST_WEEK)
        if (bw == null || bwCom == null || bwCom <= 0) return Advice(0.0, Why.NO_STANDARD)
        val bwGap = (bw - bwCom) / bwCom * 100
        if (bwGap < -TRIGGER_PCT) return Advice(min(MAX_UP_PCT, -bwGap / 2), Why.UNDER_WEIGHT)
        if (fcr != null && fcrCom != null && fcrCom > 0) {
            val fcrGap = (fcr - fcrCom) / fcrCom * 100
            if (fcrGap > TRIGGER_PCT && bwGap >= 0) return Advice(-min(MAX_DOWN_PCT, fcrGap / 2), Why.HIGH_FCR)
        }
        return Advice(0.0, Why.ON_TRACK)
    }
}
