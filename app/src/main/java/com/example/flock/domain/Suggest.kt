package com.example.flock.domain

import kotlin.math.abs

/**
 * What the last 3 and 7 days say, and what to do about it: feed eaten against the company's feed, the daily gain
 * against the company's gain, and how even the flock is (CV) — read together, because each alone misleads (eating
 * less is fine when the birds still grow to the standard; eating more is not when they grow less). Pure Kotlin.
 */
object Suggest {
    /** One flock day: feed eaten a bird that day (g) and the company's, the weighing (g) and the company's weight, the CV of the weighing. */
    data class Day(val day: Int, val eatenG: Double?, val comFeedG: Double?, val weightG: Double?, val comBwG: Double?, val cvPct: Double?)

    data class Window(
        val days: Int,
        /** eaten against the company, % (+ more) */
        val feedPct: Double?,
        /** daily gain between the weighings in the window, and the company's over the same days (g a day) */
        val gainG: Double?, val comGainG: Double?,
        /** CV of the first and the last weighing in the window */
        val cvFrom: Double?, val cvTo: Double?
    ) {
        val gainPct: Double? get() = if (gainG != null && comGainG != null && comGainG > 0) (gainG / comGainG - 1) * 100 else null
    }

    /** level 0 good / note, 1 watch, 2 act */
    data class Line(val level: Int, val text: String)
    data class Out(val w3: Window, val w7: Window, val lines: List<Line>)

    private fun window(days: List<Day>, today: Int, n: Int): Window {
        val inWin = days.filter { it.day in (today - n + 1)..today }
        val fed = inWin.filter { it.eatenG != null && it.comFeedG != null && it.comFeedG > 0 }
        val feedPct = if (fed.isEmpty()) null else (fed.sumOf { it.eatenG!! } / fed.sumOf { it.comFeedG!! } - 1) * 100
        // the weighings: the last one in the window against the last one before it ended (two or more days apart)
        val weighed = days.filter { it.weightG != null && it.day <= today }.sortedBy { it.day }
        val last = weighed.lastOrNull { it.day >= today - n + 1 }
        val first = last?.let { l -> weighed.lastOrNull { it.day <= l.day - 2 && it.day >= today - n - 2 } }
        var gain: Double? = null; var comGain: Double? = null
        if (last != null && first != null) {
            val span = (last.day - first.day).toDouble()
            gain = (last.weightG!! - first.weightG!!) / span
            if (last.comBwG != null && first.comBwG != null) comGain = (last.comBwG - first.comBwG) / span
        }
        val cvs = weighed.filter { it.day >= today - n + 1 && it.cvPct != null }
        return Window(n, feedPct, gain, comGain, cvs.firstOrNull()?.cvPct, cvs.lastOrNull()?.cvPct.takeIf { cvs.size >= 2 })
    }

    private fun f1(x: Double) = String.format("%.1f", x)
    private fun signed(x: Double) = (if (x >= 0) "+" else "−") + f1(abs(x))

    fun advise(days: List<Day>, today: Int): Out {
        val w3 = window(days, today, 3)
        val w7 = window(days, today, 7)
        val lines = mutableListOf<Line>()
        val feed = w3.feedPct ?: w7.feedPct
        val gainPct = w3.gainPct ?: w7.gainPct
        val gainW = if (w3.gainPct != null) w3 else w7

        if (feed != null) lines += Line(0, "Eaten: ${signed(feed)} % against the company over the last ${if (w3.feedPct != null) 3 else 7} days" +
            (if (w3.feedPct != null && w7.feedPct != null) " (7 days ${signed(w7.feedPct)} %)" else "") + ".")
        if (gainW.gainG != null) lines += Line(0, "Gain: ${f1(gainW.gainG)} g a day over the last ${gainW.days} days" +
            (gainW.comGainG?.let { " — the company ${f1(it)} g" } ?: "") + (gainPct?.let { " (${signed(it)} %)" } ?: "") + ".")

        when {
            feed == null || gainPct == null -> {}
            feed < -8 && gainPct < -8 -> lines += Line(2, "Eating and growing below the company: check water flow, heat in the house and how quickly the feed reaches the far pans; feed more often.")
            feed < -4 && gainPct >= -3 -> lines += Line(0, "Eating less but growing to the standard: the birds convert well — keep the ration, do not cut it.")
            feed > 8 && gainPct < 0 -> lines += Line(2, "Eating more than the company but growing less: look for spilt or wasted feed, heat or sickness — the FCR is climbing.")
            feed > 4 && gainPct > 4 -> lines += Line(0, "Eating and growing above the company: keep the feed coming, watch the FCR.")
            gainPct < -8 -> lines += Line(1, "Growth is slowing: check the feeder lines run full, the water and the heat.")
            else -> lines += Line(0, "Feed and growth go together: keep to the plan.")
        }
        val cvW = if (w7.cvTo != null) w7 else w3
        if (cvW.cvFrom != null && cvW.cvTo != null) {
            val up = cvW.cvTo - cvW.cvFrom
            when {
                cvW.cvTo >= 12 -> lines += Line(2, "Very uneven (CV ${f1(cvW.cvTo)} %): grade the small birds into their own pen or give them more feeder space.")
                up >= 1.5 -> lines += Line(1, "Getting uneven: CV ${f1(cvW.cvFrom)} → ${f1(cvW.cvTo)} % — more feeder space, feed spread faster, check the far end.")
                up <= -1.5 -> lines += Line(0, "Getting more even: CV ${f1(cvW.cvFrom)} → ${f1(cvW.cvTo)} %.")
                else -> {}
            }
        }
        return Out(w3, w7, lines)
    }
}
