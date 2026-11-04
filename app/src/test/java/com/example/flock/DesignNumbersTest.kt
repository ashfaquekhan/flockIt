package com.example.flock

import com.example.flock.ui.screens.OutputData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The farm's own flock on day 44, as the Output page works it out: the numbers the design options and the performance
 * radar are drawn with (printed as one line), and the radar's six scores (100 = the company's standard).
 */
class DesignNumbersTest {
    @Test fun dayFortyFourAsThePageSeesIt() {
        val r = KgfFlock.computed(44)
        val d = OutputData(KgfFlock.flock, KgfFlock.farm, r.last(), r, KgfFlock.feedTypes, null, emptyList(), false, false)
        val m = d.mass!!
        val radar = com.example.flock.domain.Radar.scores(com.example.flock.domain.Radar.In(
            weightG = d.bwNow, weightCom = d.bwCom, fcr = m.fcr, fcrCom = d.fcrCom, mortPct = d.mortTDPct, mortCom = d.comCumPct,
            cvPct = d.cvEst, eatenVsComPct = d.suggest.w7.feedPct, epef = d.epef, epefCom = d.epefCom, gainG = d.gain, gainCom = d.gainCom))
        val o = d.floorPlan
        val line = listOf(
            "day" to d.day, "live" to d.live, "weightG" to d.bwNow, "weightCom" to d.bwCom, "gain" to d.gain, "gainCom" to d.gainCom,
            "fcr" to m.fcr, "fcrCom" to d.fcrCom, "cfcrCo" to m.cFcrCompany, "cfcrBreed" to m.cFcrBreed, "cfcrCom" to d.cfcrCom,
            "mortPct" to d.mortTDPct, "mortCom" to d.comCumPct, "cv" to d.cvEst, "cvMeasured" to d.cvMeasured,
            "eaten7" to d.suggest.w7.feedPct, "epef" to d.epef, "epefCom" to d.epefCom, "kgM2" to d.space.kgM2,
            "lifted" to m.liftedBirds, "liftedKg" to m.liftedKg, "dayBags" to d.dayBags, "feedings" to d.feedings,
            "bagsPerFeeding" to d.bagsPerFeeding, "pattern" to d.feedPattern.label, "planKey" to o?.key, "why" to d.space.why,
            "steps" to d.space.steps.joinToString(" | "), "radar" to radar.joinToString(",") { "${it.key}=${it.score?.let { s -> "%.1f".format(s) }}" }
        ).joinToString("; ") { (k, v) -> "$k=${if (v is Double) "%.3f".format(v) else v}" }
        println("DESIGN_NUMBERS $line")
        // the four radars of the glance, at midday
        val view = d.nowView(java.time.LocalDate.parse(r.last().date).atTime(12, 0).atZone(java.time.ZoneId.of(KgfFlock.farm.timeZone)))
        val groups = com.example.flock.ui.screens.radarsOf(d, view, 12)
        groups.forEach { g -> println("RADAR ${g.key} mean=${g.mean?.let { "%.1f".format(it) }} " + g.flock.joinToString(",") { "${it.key}=${it.score?.let { s -> "%.1f".format(s) }}" }) }
        assertTrue(groups.size == 4 && groups.all { g -> g.flock.all { it.score == null || it.score!! in 0.0..150.0 } })
        assertTrue(radar.size == 6 && radar.all { it.score != null && it.score!! in 0.0..150.0 })
        // the one score in the radar's middle: the six averaged
        val (entered, projected, _) = com.example.flock.ui.screens.birdScores(d, view)
        val overall = com.example.flock.ui.screens.overallScore(entered, projected)
        println("RADAR overall=${overall?.let { "%.1f".format(it) }}")
        assertTrue(overall != null && overall in 0.0..150.0)
    }

    /**
     * v52: an arrow is only the direction against the standard; green or red says good or bad, and that depends on the
     * number (FCR and mortality up is bad, weight down is bad). No middle grade.
     */
    @Test fun arrowsAreGreenOrRedByWhatTheNumberIs() {
        val G = com.example.flock.ui.screens.Grade.GOOD; val B = com.example.flock.ui.screens.Grade.BAD
        fun t(a: Double, ref: Double, b: com.example.flock.ui.screens.Better) = com.example.flock.ui.screens.trendOf(a, ref, b)!!
        val lower = com.example.flock.ui.screens.Better.LOWER; val higher = com.example.flock.ui.screens.Better.HIGHER
        assertEquals(1 to B, t(1.600, 1.500, lower).let { it.dir to it.grade })        // FCR up: red up
        assertEquals(-1 to G, t(1.400, 1.500, lower).let { it.dir to it.grade })       // FCR down: green down
        assertEquals(1 to B, t(6.5, 5.95, lower).let { it.dir to it.grade })           // mortality up: red up
        assertEquals(-1 to B, t(2900.0, 3100.0, higher).let { it.dir to it.grade })    // weight down: red down
        assertEquals(1 to G, t(3300.0, 3100.0, higher).let { it.dir to it.grade })     // weight up: green up
        assertEquals(0 to G, t(1.51, 1.50, lower).let { it.dir to it.grade })          // on par: green =
        assertEquals(setOf(G, B), com.example.flock.ui.screens.Grade.values().toSet())
        assertEquals(B, com.example.flock.ui.screens.rangeTrend(12.0, null, 10.0)!!.grade)
        assertEquals(G, com.example.flock.ui.screens.scoreGrade(98.0)); assertEquals(B, com.example.flock.ui.screens.scoreGrade(92.0))
    }
}
