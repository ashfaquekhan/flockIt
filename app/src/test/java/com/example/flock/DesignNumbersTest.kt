package com.example.flock

import com.example.flock.ui.screens.OutputData
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The performance radar of the design options (not in the app yet): six axes scored so that 100 = the company's
 * standard for the day, more = better, capped at 150. Growth = weight ÷ company; feed conversion = company FCR ÷ FCR;
 * livability = company mortality ÷ mortality (a floor of 0.5 % so one death is not a cliff); evenness = 8 % ÷ CV;
 * appetite = 100 − |eaten vs company| × 2 (on the company's ration is best, either way loses); EPEF ÷ company.
 */
object Radar {
    data class In(val weightG: Double?, val weightCom: Double?, val fcr: Double?, val fcrCom: Double?, val mortPct: Double?, val mortCom: Double?,
                  val cvPct: Double?, val eatenVsComPct: Double?, val epef: Double?, val epefCom: Double?)
    data class Axis(val axis: String, val score: Double)
    private fun cap(x: Double) = (x * 100).coerceIn(0.0, 150.0)
    fun scores(i: In): List<Axis> = listOf(
        Axis("Growth", if (i.weightG != null && i.weightCom != null && i.weightCom > 0) cap(i.weightG / i.weightCom) else 0.0),
        Axis("FCR", if (i.fcr != null && i.fcrCom != null && i.fcr > 0) cap(i.fcrCom / i.fcr) else 0.0),
        Axis("Livability", if (i.mortPct != null && i.mortCom != null) cap(maxOf(0.5, i.mortCom) / maxOf(0.5, i.mortPct)) else 0.0),
        Axis("Evenness", if (i.cvPct != null && i.cvPct > 0) cap(8.0 / i.cvPct) else 0.0),
        Axis("Appetite", i.eatenVsComPct?.let { (100 - kotlin.math.abs(it) * 2).coerceIn(0.0, 150.0) } ?: 0.0),
        Axis("EPEF", if (i.epef != null && i.epefCom != null && i.epefCom > 0) cap(i.epef / i.epefCom) else 0.0)
    )
}

/**
 * The farm's own flock on day 44, as the Output page works it out: the numbers the design options and the performance
 * radar are drawn with (printed as one line), and the radar's six scores (100 = the company's standard).
 */
class DesignNumbersTest {
    @Test fun dayFortyFourAsThePageSeesIt() {
        val r = KgfFlock.computed(44)
        val d = OutputData(KgfFlock.flock, KgfFlock.farm, r.last(), r, KgfFlock.feedTypes, null, emptyList(), false, false)
        val m = d.mass!!
        val radar = Radar.scores(Radar.In(
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
            "steps" to d.space.steps.joinToString(" | "), "radar" to radar.joinToString(",") { "${it.axis}=${"%.1f".format(it.score)}" }
        ).joinToString("; ") { (k, v) -> "$k=${if (v is Double) "%.3f".format(v) else v}" }
        println("DESIGN_NUMBERS $line")
        assertTrue(radar.size == 6 && radar.all { it.score in 0.0..150.0 })
    }
}
