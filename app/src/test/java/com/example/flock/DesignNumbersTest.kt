package com.example.flock

import com.example.flock.ui.screens.OutputData
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
            cvPct = d.cvEst, eatenVsComPct = d.suggest.w7.feedPct, epef = d.epef, epefCom = d.epefCom))
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
        assertTrue(radar.size == 6 && radar.all { it.score != null && it.score!! in 0.0..150.0 })
    }
}
