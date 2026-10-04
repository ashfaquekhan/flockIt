package com.example.flock

import com.example.flock.domain.FlockKpis
import com.example.flock.domain.GrowthForecast
import com.example.flock.domain.IntakeForecast
import com.example.flock.engine.CompanyStandard
import com.example.flock.engine.PhysiologicalEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Curves to day 70, which chart day is which flock day, the feed and growth forecasts, and the trend measures. */
class ForecastTest {

    // ------------------------------------------------------------------ curves
    @Test fun breedCurveRunsToDay70AndKeepsSlowing() {
        val s = PhysiologicalEngine.STANDARDS
        assertEquals(PhysiologicalEngine.MAX_FLOCK_DAY, s.last().day)
        assertEquals(3791.0, s[49].bwRoss, 1e-9)                       // the published table is untouched
        for (i in 50..70) {
            val gain = s[i].bwRoss - s[i - 1].bwRoss
            val before = s[i - 1].bwRoss - s[i - 2].bwRoss
            assertTrue("day $i still growing", gain > 30)
            assertTrue("day $i gain $gain no faster than the day before $before", gain <= before + 0.5)
            assertTrue("FCR rises with age", s[i].fcrRoss > s[i - 1].fcrRoss)
            assertEquals(s[i - 1].cumFeedRoss + s[i].dFeedRoss, s[i].cumFeedRoss, 1e-6)
        }
        println("Ross d56 ${s[56].bwRoss.toInt()} g feed ${s[56].dFeedRoss.toInt()} · d63 ${s[63].bwRoss.toInt()} g · d70 ${s[70].bwRoss.toInt()} g feed ${s[70].dFeedRoss.toInt()} FCR ${"%.3f".format(s[70].fcrRoss)}")
        assertTrue(s[70].bwRoss in 5000.0..6200.0)                     // a heavy roaster, not a turkey
        assertTrue(s[70].dFeedRoss in 225.0..290.0)
        assertTrue(abs(s[50].bwRoss - s[49].bwRoss - (s[49].bwRoss - s[48].bwRoss)) < 6)   // no jump where the model takes over
        assertTrue(abs(s[50].dFeedRoss - s[49].dFeedRoss) < 6)
    }

    @Test fun companyChartIsCarriedOnPastDay55() {
        assertEquals(4701.0, CompanyStandard.bw(55)!!, 1e-9)
        assertTrue(CompanyStandard.bw(70)!! > CompanyStandard.bw(56)!!)
        assertEquals(CompanyStandard.cumFeed(55)!! + 180 * 15, CompanyStandard.cumFeed(70)!!, 1e-9)
        assertEquals(0.15 * 12 + 0.10 * 16 + 0.15 * 42, CompanyStandard.cumMortPct(70), 1e-9)
        assertEquals(CompanyStandard.bw(70)!!, CompanyStandard.bw(99)!!, 1e-9)      // clamped at the last day
    }

    @Test fun chartDayOneIsThePlacementDay() {
        // what happens DURING flock day N is the chart's row N + 1
        assertEquals(1, CompanyStandard.duringDay(0)); assertEquals(12, CompanyStandard.duringDay(11))
        // a chick at placement eats the chart's day-1 ration; a bird at the chart's day-11 weight eats day 12's
        assertEquals(13.0, CompanyStandard.feedForWeight(40.0), 1e-9)
        assertEquals(17.0, CompanyStandard.feedForWeight(50.0), 1e-9)
        assertEquals(66.0, CompanyStandard.feedForWeight(382.0), 1e-9)
        assertEquals(11.0, CompanyStandard.ageForWeight(382.0), 1e-9)
        assertEquals(10.5, CompanyStandard.ageForWeight((334 + 382) / 2.0), 1e-9)
        assertEquals(63.0, CompanyStandard.feedForWeight((334 + 382) / 2.0), 1e-9)   // halfway between day 11's 60 g and day 12's 66 g
        // never a step backwards as the bird grows
        var last = 0.0
        for (w in 40..5000 step 20) { val f = CompanyStandard.feedForWeight(w.toDouble()); assertTrue(f >= last - 1e-9); last = f }
    }

    // ------------------------------------------------------------------ feed forecast
    @Test fun intakeFilterLearnsTheFlocksAppetite() {
        // a flock that eats 8 % less than the plan, with a little day-to-day scatter
        val noise = listOf(0.01, -0.02, 0.015, -0.01, 0.02, -0.015, 0.0, 0.01, -0.01, 0.005)
        val hist = noise.mapIndexed { i, n -> IntakeForecast.Day(i, 1000.0 + i * 50, (1000.0 + i * 50) * (0.92 + n)) }
        val f = IntakeForecast.forecast(hist, 1500.0)
        assertEquals(10, f.days)
        assertEquals(0.92, f.ratio, 0.02)
        assertEquals(1500 * 0.92, f.mean, 30.0)
        assertTrue(f.sd > 0 && f.sd < 1500 * 0.08)
        assertNotNull(f.pastErrorPct); assertTrue(f.pastErrorPct!! < 5)
        // with no history it falls back on the plan, and says it is unsure
        val none = IntakeForecast.forecast(emptyList(), 1500.0)
        assertEquals(1500.0, none.mean, 1e-9); assertNull(none.pastErrorPct); assertTrue(none.sd > f.sd)
        // probabilities behave
        assertEquals(0.5, f.chanceEnough(f.mean), 1e-6)
        assertTrue(f.chanceEnough(f.mean + 2 * f.sd) > 0.97 && f.chanceEnough(f.mean - 2 * f.sd) < 0.03)
        assertEquals(f.mean + 1.2816 * f.sd, f.quantile(0.9), 0.02 * f.sd)
    }

    @Test fun intakeFilterFollowsAChangeOfAppetite() {
        // the flock eats the plan for a week, then 12 % more (cooler weather): the estimate moves most of the way in a few days
        val hist = (0 until 7).map { IntakeForecast.Day(it, 1000.0, 1000.0) } + (7 until 12).map { IntakeForecast.Day(it, 1000.0, 1120.0) }
        val f = IntakeForecast.forecast(hist, 1000.0)
        assertTrue("ratio ${f.ratio}", f.ratio in 1.07..1.125)
    }

    @Test fun adviceGivesASafeRangeAndADirection() {
        val f = IntakeForecast.forecast((0 until 10).map { IntakeForecast.Day(it, 1000.0, 920.0) }, 1140.0)     // 19 bags of 60 kg planned
        val a = IntakeForecast.advise(f, 60.0, 19.0)
        println("forecast ${"%.2f".format(a.forecastBags)} bags (${"%.2f".format(a.lowBags)}–${"%.2f".format(a.highBags)}), safe ${a.safeFrom}–${a.safeTo}, " +
            a.choices.joinToString { "${it.bags.toInt()}: ${"%.0f".format(it.chanceEnough * 100)}%" })
        assertEquals(1140 * 0.92 / 60, a.forecastBags, 0.3)
        assertTrue(a.lowBags < a.forecastBags && a.forecastBags < a.highBags)
        assertTrue(a.safeFrom <= a.safeTo)
        // 19 bags is the top of the safe range (enough practically always): the plan stands
        assertEquals(0, a.direction); assertEquals(19.0, a.safeTo, 1e-9); assertEquals(18.0, a.safeFrom, 1e-9)
        // a plan above the safe range is brought down to its top, one below it is brought up to its bottom
        val high = IntakeForecast.advise(f, 60.0, 21.0)
        assertEquals(-1, high.direction); assertEquals(19.0, high.suggestedBags, 1e-9)
        val low = IntakeForecast.advise(f, 60.0, 16.0)
        assertEquals(1, low.direction); assertEquals(18.0, low.suggestedBags, 1e-9)
        // the chance a whole-bag amount is enough rises with the bags
        a.choices.zipWithNext { x, y -> assertTrue(y.chanceEnough >= x.chanceEnough); assertTrue(y.expectedLeftBags >= x.expectedLeftBags) }
        // a flock eating the plan keeps the plan
        val even = IntakeForecast.advise(IntakeForecast.forecast((0 until 10).map { IntakeForecast.Day(it, 1000.0, 1000.0) }, 1110.0), 60.0, 19.0)
        assertEquals(0, even.direction)
    }

    // ------------------------------------------------------------------ growth forecast
    @Test fun growthForecastFindsTheTargetDay() {
        val std = { d: Int -> CompanyStandard.bw(d) ?: CompanyStandard.CHICK_G }
        // a flock 5 % under the commercial curve, weighed every 3–4 days
        val samples = listOf(4, 7, 11, 14, 18, 21).map { GrowthForecast.Sample(it, std(it) * 0.95) }
        val g = GrowthForecast.forecast(samples, std, 21, 70, 2200.0, 35)
        assertEquals(0.95, g.share, 0.01)
        val day = g.targetDay!!
        assertTrue(std(day) * 0.95 >= 2200.0 - 30 && std(day - 1) * 0.95 < 2200.0)
        assertTrue(g.targetDayEarly!! <= day && day <= g.targetDayLate!!)
        val h = g.atHarvest!!
        assertEquals(std(35) * 0.95, h.mean, 25.0)
        assertTrue(h.low < h.mean && h.mean < h.high)
        assertTrue(g.chanceTargetAtHarvest!! > 0.5)                // 2,336 g forecast against a 2,200 g target
        // the range widens the further ahead it looks
        val w0 = g.curve.first().let { it.high - it.low } / g.curve.first().mean
        val w1 = g.curve.last().let { it.high - it.low } / g.curve.last().mean
        assertTrue(w1 > w0)
        // no weighings: the standard itself, with a wide range
        val none = GrowthForecast.forecast(emptyList(), std, 5, 70, 2200.0, 35)
        assertEquals(1.0, none.share, 1e-9); assertEquals(0, none.samples)
    }

    // ------------------------------------------------------------------ trend measures
    @Test fun flockTrendMeasures() {
        // 1,000 birds; weight doubles roughly as the standard; 60 g/bird eaten each day in the last week
        val days = (0..14).map { d ->
            FlockKpis.Day(d, weightG = 40.0 + d * 35, measured = d % 7 == 0, eatenG = if (d < 14) 60.0 else null,
                deaths = if (d in 0..6) 1 else 2, culls = 0, live = 1000 - (if (d <= 6) d + 1 else 7 + (d - 6) * 2))
        }
        val cumFeedKg = 14 * 60.0 * 990 / 1000
        val k = FlockKpis.compute(days, 14, 1000, cumFeedKg)
        assertEquals((40.0 + 14 * 35) / 14, k.adg!!, 1e-9)
        assertEquals(7 * 60.0 / (7 * 35.0), k.fcr7!!, 1e-9)        // 420 g eaten for 245 g gained
        assertTrue(k.fcr7Measured)                                 // weighed on day 7 and day 14
        assertEquals(0.7, k.firstWeekMortPct!!, 1e-9)              // 7 deaths in days 0–6 of 1,000
        assertTrue(k.firstWeekComplete)
        assertEquals(14 * 100.0 / days[7].live, k.mort7Pct!!, 1e-9)   // days 8–14: 2 a day, of the birds alive on day 7
        val lostKg = days.sumOf { it.deaths * it.weightG / 1000 }
        assertEquals(cumFeedKg / (days[14].live * 530.0 / 1000 + lostKg), k.adjFcr!!, 1e-9)
        assertTrue(k.adjFcr!! < cumFeedKg / (days[14].live * 0.530))  // counting the lost birds' weight lowers it
        // a missing feed entry in the week: no 7-day FCR rather than a wrong one
        val gap = days.map { if (it.day == 10) it.copy(eatenG = null) else it }
        assertNull(FlockKpis.compute(gap, 14, 1000, cumFeedKg).fcr7)
    }

    @Test fun projectionChecks() {
        val a = FlockKpis.Accuracy(listOf(FlockKpis.Check(7, 210.0, 200.0), FlockKpis.Check(14, 540.0, 560.0), FlockKpis.Check(21, 1030.0, 1000.0)))
        assertEquals(5.0, a.checks[0].errorPct, 1e-9)
        assertEquals((5.0 + 100 * 20 / 560.0 + 3.0) / 3, a.averageMissPct!!, 1e-9)
        assertEquals((5.0 - 100 * 20 / 560.0 + 3.0) / 3, a.biasPct!!, 1e-9)
        assertEquals(21, a.latest!!.day); assertEquals(14, a.upTo(15).latest!!.day); assertNull(a.on(10))
        assertNull(FlockKpis.Accuracy(emptyList()).averageMissPct)
    }
}
