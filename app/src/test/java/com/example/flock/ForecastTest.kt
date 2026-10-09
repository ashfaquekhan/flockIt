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
        assertEquals(4446.0, s[56].bwRoss, 1e-9); assertEquals(239.0, s[56].dFeedRoss, 1e-9)   // days 50–56 are the published rows
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
    private val carry = 200.0     // feed in the lines varies by about this much (same unit as the plan)

    @Test fun intakeFilterLearnsTheFlocksAppetite() {
        // a flock that eats 8 % less than the plan, with a little day-to-day scatter (days 6 on are learned from)
        val noise = listOf(0.01, -0.02, 0.015, -0.01, 0.02, -0.015, 0.0, 0.01, -0.01, 0.005)
        val hist = noise.mapIndexed { i, n -> IntakeForecast.Day(6 + i, 1000.0 + i * 50, (1000.0 + i * 50) * (0.92 + n)) }
        val f = IntakeForecast.forecast(hist, 1500.0, carry)
        assertEquals(10, f.days)
        assertEquals(0.92, f.ratio, 0.03)
        assertEquals(1500 * 0.92, f.mean, 45.0)
        assertTrue(f.sd > 0 && f.sd < 1500 * 0.12)
        assertTrue(f.low() < f.mean && f.mean < f.high())
        assertEquals(92.0, f.last7!!.pct, 1.0); assertEquals(92.0, f.last3!!.pct, 1.5)
        // the first days of a flock (trays, paper, the first fill) are not learned from
        val early = IntakeForecast.forecast((0 until 6).map { IntakeForecast.Day(it, 100.0, 300.0) }, 1500.0, carry)
        assertEquals(0, early.days); assertTrue(early.learning)
        // with nothing to learn from it falls back on the plan, and says so
        val none = IntakeForecast.forecast(emptyList(), 1500.0, carry)
        assertEquals(1500.0, none.mean, 1e-9); assertEquals(1500.0, none.load, 1e-9); assertTrue(none.learning); assertNull(none.last3)
        assertTrue(none.sd > f.sd)
    }

    @Test fun intakeFilterFollowsAChangeOfAppetite() {
        // the flock eats the plan for a week, then 12 % more (cooler weather): the estimate moves most of the way in some days
        val hist = (6 until 13).map { IntakeForecast.Day(it, 1000.0, 1000.0) } + (13 until 23).map { IntakeForecast.Day(it, 1000.0, 1120.0) }
        val f = IntakeForecast.forecast(hist, 1000.0, carry)
        assertTrue("ratio ${f.ratio}", f.ratio in 1.06..1.125)
    }

    @Test fun aBigPourIsNotTakenForABigAppetite() {
        // the flock eats 1,000 a day; one day 1,400 is poured (400 stays in the lines) and 600 the day after
        val steady = (6 until 14).map { IntakeForecast.Day(it, 1000.0, 1000.0) }
        val after = IntakeForecast.forecast(steady + IntakeForecast.Day(14, 1000.0, 1400.0), 1000.0, carry)
        // the appetite hardly moves, the surplus is seen in the lines, and less is to be poured today
        assertTrue("appetite ${after.ratio}", after.ratio in 0.98..1.10)
        println("after the big pour: appetite ${after.ratio}, in the lines ${after.inLines}, pour ${after.load}")
        assertTrue("in the lines ${after.inLines}", after.inLines > 80)
        assertTrue("pour ${after.load}", after.load < 950)
        assertTrue(after.mean in 950.0..1100.0)              // what the birds will eat is still about the plan
        // … and once the small pour follows, everything is back to normal
        val back = IntakeForecast.forecast(steady + IntakeForecast.Day(14, 1000.0, 1400.0) + IntakeForecast.Day(15, 1000.0, 600.0), 1000.0, carry)
        println("after the small pour: appetite ${back.ratio}, in the lines ${back.inLines}, pour ${back.load}")
        assertEquals(1.0, back.ratio, 0.04); assertTrue("pour ${back.load}", back.load in 900.0..1120.0)
        // a method that followed each day's entry would have swung with it
        val swing = listOf(1000.0, 1400.0, 600.0, 1300.0, 700.0, 1000.0, 1400.0, 600.0, 1000.0, 1000.0)
        val lumpy = IntakeForecast.forecast(swing.mapIndexed { i, y -> IntakeForecast.Day(6 + i, 1000.0, y) }, 1000.0, carry)
        assertEquals(1.0, lumpy.ratio, 0.05)
        assertTrue(lumpy.dayScatterPct!! > 15)
    }

    @Test fun adviceIsInWholeBagsAndSaysWhichWay() {
        val f = IntakeForecast.forecast((6 until 16).map { IntakeForecast.Day(it, 1000.0, 920.0) }, 1140.0, carry)     // 19 bags of 60 kg planned
        val a = IntakeForecast.advise(f, 60.0, 19.0)
        println("likely ${"%.2f".format(a.likelyBags)} bags (${"%.2f".format(a.lowBags)}–${"%.2f".format(a.highBags)}), in the lines ${"%.2f".format(a.inLinesBags)}, pour ${a.loadBags}")
        assertEquals(1140 * 0.92 / 60, a.likelyBags, 0.4)
        assertTrue(a.lowBags < a.likelyBags && a.likelyBags < a.highBags)
        assertEquals(a.loadBags, Math.rint(a.loadBags), 0.0)     // whole bags
        assertTrue("pour ${a.loadBags}", a.loadBags in 17.0..18.0)
        assertEquals(-1, a.direction)                            // fewer than the 19 planned
        assertEquals(1, IntakeForecast.advise(f, 60.0, 15.0).direction)
        // a flock eating the plan keeps the plan
        val even = IntakeForecast.advise(IntakeForecast.forecast((6 until 16).map { IntakeForecast.Day(it, 1000.0, 1000.0) }, 1110.0, carry), 60.0, 19.0)
        assertEquals(0, even.direction)
        // still learning: the plan as it is
        val learning = IntakeForecast.advise(IntakeForecast.forecast(emptyList(), 1110.0, carry), 60.0, 19.0)
        assertEquals(19.0, learning.loadBags, 0.0); assertEquals(0, learning.direction)
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
