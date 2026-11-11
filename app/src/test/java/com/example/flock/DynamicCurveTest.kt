package com.example.flock

import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FlockCalc
import com.example.flock.domain.DynamicCurve
import com.example.flock.engine.CompanyStandard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** v60: the flock's own curve — useful from the first entry, better with each one. */
class DynamicCurveTest {
    private val std: (Int) -> Double? = { CompanyStandard.bw(it) }

    @Test fun withNothingEnteredItIsTheStandard() {
        val f = DynamicCurve.fit(emptyList(), std)
        assertEquals(CompanyStandard.bw(20)!!, f.at(20)!!, 1e-9); assertEquals(1.0, f.ratioAt(33), 1e-12); assertEquals(0, f.n)
    }

    /** A flock running 10 % under the company: each day entered brings the curve closer, for the days to come too. */
    @Test fun everyDayEnteredMakesItBetter() {
        fun truth(d: Int) = CompanyStandard.bw(d)!! * 0.90
        fun errAhead(days: Int): Double {
            val f = DynamicCurve.fit((5 until 5 + days).map { DynamicCurve.Obs(it, truth(it)) }, std, prior = 0.3)
            return (1..5).map { h -> val d = 4 + days + h; abs(f.at(d)!! - truth(d)) / truth(d) }.average()
        }
        val e0 = errAhead(0); val e1 = errAhead(1); val e2 = errAhead(2); val e3 = errAhead(3); val e7 = errAhead(7)
        println("own weight curve, error 1–5 days ahead: no entry %.1f %% · 1 day %.1f %% · 2 days %.1f %% · 3 days %.1f %% · 7 days %.1f %%".format(e0 * 100, e1 * 100, e2 * 100, e3 * 100, e7 * 100))
        assertEquals(0.111, e0, 0.002)                      // the standard alone is 11 % out
        assertTrue(e1 < e0 * 0.35)                          // one weighing takes away two thirds of that
        assertTrue(e2 < e1 && e3 < e2 && e7 < e3)           // and each further day more
        assertTrue(e3 < 0.02); assertTrue(e7 < 0.01)
    }

    /** A flock falling behind day by day: the drift is carried on a little, so the forecast is not always late. */
    @Test fun aDriftIsCarriedOn() {
        fun truth(d: Int) = CompanyStandard.bw(d)!! * (1.0 - 0.01 * (d - 10))     // 1 % further under each day from day 10
        val obs = (10..16).map { DynamicCurve.Obs(it, truth(it)) }
        val f = DynamicCurve.fit(obs, std, prior = 0.3)
        val flat = CompanyStandard.bw(19)!! * f.ratioAt(16)
        assertTrue(abs(f.at(19)!! - truth(19)) < abs(flat - truth(19)))
        // and never runs away: within 10 % of the level last seen
        assertTrue(f.ratioAt(40) >= f.ratioAt(16) * 0.9 - 1e-9)
    }

    @Test fun oneOddEntryDoesNotTakeTheCurveWithIt() {
        val obs = (8..14).map { DynamicCurve.Obs(it, CompanyStandard.bw(it)!!) } + DynamicCurve.Obs(15, CompanyStandard.bw(15)!! * 0.7)   // a bad weighing
        val f = DynamicCurve.fit(obs, std, prior = 0.3)
        assertTrue("ratio after one odd weighing ${f.ratioAt(16)}", f.ratioAt(16) > 0.85)
        // a value of nothing, or on a day the standard has no figure for, is left out
        assertEquals(0, DynamicCurve.fit(listOf(DynamicCurve.Obs(5, 0.0), DynamicCurve.Obs(5, Double.NaN)), std).n)
    }

    /** On the real flock: forecasting each weighing from the ones before it, the own curve beats the company's curve. */
    @Test fun onTheRealFlockItBeatsTheStandard() {
        val r = KgfFlock.computed(44)
        fun report(name: String, obs: List<DynamicCurve.Obs>, standard: (Int) -> Double?, prior: Double): Pair<Double, Double> {
            val checks = DynamicCurve.backtest(obs, standard, prior).filter { it.learnedFrom >= 1 && it.own != null && it.standard != null }
            val own = checks.map { abs(it.own!! - it.entered) / it.entered }.average()
            val st = checks.map { abs(it.standard!! - it.entered) / it.entered }.average()
            val early = checks.filter { it.learnedFrom in 1..3 }.map { abs(it.own!! - it.entered) / it.entered }.average()
            val late = checks.filter { it.learnedFrom >= 8 }.map { abs(it.own!! - it.entered) / it.entered }.takeIf { it.isNotEmpty() }?.average()
            println("$name: ${checks.size} forecasts · own curve %.2f %% out · company %.2f %% out · with 1–3 entries %.2f %% · with 8+ %s".format(own * 100, st * 100, early * 100, late?.let { "%.2f %%".format(it * 100) } ?: "—"))
            return own to st
        }
        val (wOwn, wStd) = report("weight", r.mapNotNull { d -> d.avgWeight?.let { DynamicCurve.Obs(d.dayNumber, it) } }, { CompanyStandard.bw(it) }, 0.3)
        val byDay = r.associateBy { it.dayNumber }
        val kgOf = KgfFlock.feedTypes.associate { it.code to it.bagKg }
        val feed = r.mapNotNull { d: DailyDataEntity -> val live = byDay[d.dayNumber - 1]?.liveBirds?.takeIf { it > 0 } ?: return@mapNotNull null
            FlockCalc.usedKg(d, kgOf, 50.0).takeIf { it > 0 }?.let { DynamicCurve.Obs(d.dayNumber, it * 1000 / live) } }
        val (fOwn, fStd) = report("feed a bird a day", feed, { CompanyStandard.feedPerDay(it) }, 0.7)
        val mort = r.filter { it.dayNumber >= 2 && it.cumMortPct != null && it.dayNumber <= 43 }.map { DynamicCurve.Obs(it.dayNumber, it.cumMortPct!!) }
        val (mOwn, mStd) = report("mortality", mort, { CompanyStandard.cumMortPct(it).takeIf { v -> v > 0 } }, 1.5)
        assertTrue("weight: own $wOwn against company $wStd", wOwn < wStd)
        assertTrue("feed: own $fOwn against company $fStd", fOwn < fStd)
        assertTrue("mortality: own $mOwn against company $mStd", mOwn < mStd)
    }
}
