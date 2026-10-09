package com.example.flock

import com.example.flock.data.FlockCalc
import com.example.flock.domain.IntakeForecast
import com.example.flock.sync.SheetReports
import com.example.flock.sync.SheetSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The calculations and the projections run on the farm's own flock ([KgfFlock], entries to day 19): the numbers
 * must match the sheet's, and the projections are scored against what was then really entered.
 */
class RealFlockTest {
    private val rows = FlockCalc.compute(KgfFlock.flock, KgfFlock.farm, KgfFlock.config, KgfFlock.rows(), KgfFlock.kgOf)
    private val by = rows.associateBy { it.dayNumber }

    @Test fun calculationMatchesTheSheet() {
        assertEquals(43, rows.size)
        // DailySummary of the farm's sheet: day 18 → 15,272 birds, 856.5 g, FCR 1.133; day 19 → 2.286 % mortality
        val d18 = by[18]!!
        assertEquals(15272, d18.liveBirds)
        assertEquals(856.5, d18.avgWeight!!, 0.05)
        assertEquals(1.133, d18.fcr!!, 0.001)
        assertEquals(2.286, by[19]!!.cumMortPct!!, 0.001)
        assertNull("day 19 was not weighed", by[19]!!.avgWeight)
        assertTrue(by[19]!!.projected)
        assertEquals(19, FlockCalc.lastEnteredDay(rows))
        // the store: 361 bags in, 279 used
        assertEquals(82.0, by[19]!!.stockOnHand, 1e-9)
    }

    @Test fun projectionsAreScoredAgainstTheEntries() {
        val proj = FlockCalc.projections(KgfFlock.flock, KgfFlock.farm, rows, KgfFlock.kgOf).associateBy { it.day }
        assertEquals(43, proj.size)
        val bag = 60.0
        println("day  weight proj/entered     bags proj/entered    FCR proj/entered   mort% proj/entered")
        val wMiss = mutableListOf<Double>(); val fMiss = mutableListOf<Double>(); val mMiss = mutableListOf<Double>()
        var p3 = 0.0; var e3 = 0.0; val three = mutableListOf<Double>()
        for (n in 1..19) {
            val p = proj[n]!!; val d = by[n]!!
            val used = FlockCalc.usedKg(d, KgfFlock.kgOf, bag) / bag
            println(String.format("%2d   %7.1f / %-7s   %6.2f / %-5.1f   %5.3f / %-6s   %5.3f / %5.3f", n, p.weightG, d.avgWeight?.let { "%.1f".format(it) } ?: "—",
                p.feedKg!! / bag, used, p.fcr ?: 0.0, d.fcr?.takeIf { d.avgWeight != null }?.let { "%.3f".format(it) } ?: "—", p.cumMortPct, d.cumMortPct))
            d.avgWeight?.let { wMiss += abs(p.weightG!! - it) / it * 100 }
            if (n >= IntakeForecast.LEARN_FROM_DAY + 4) { fMiss += abs(p.feedKg!! / bag - used) / used * 100; p3 += p.feedKg!! / bag; e3 += used
                if ((n - IntakeForecast.LEARN_FROM_DAY) % 3 == 0) { three += abs(p3 - e3) / e3 * 100; p3 = 0.0; e3 = 0.0 } }
            mMiss += abs(p.cumMortPct!! - d.cumMortPct!!)
        }
        println("weight: average miss ${"%.2f".format(wMiss.average())} % over ${wMiss.size} weighings")
        println("feed: one day ${"%.1f".format(fMiss.average())} %, three days together ${"%.1f".format(three.average())} %")
        println("mortality: average miss ${"%.3f".format(mMiss.average())} points")
        // a weighing of 50 birds cannot be projected much closer than this; the flock's own record sets the bar
        assertTrue("weight miss ${wMiss.average()}", wMiss.average() < 6.0)
        // one day's bags swing (feed stays in the lines), three days together must be close
        assertTrue("3-day feed miss ${three.average()}", three.average() < 15.0)
        assertTrue("mortality miss ${mMiss.average()}", mMiss.average() < 0.08)
        // beyond the last entry the line goes on as a forecast, and keeps rising
        assertTrue(proj[30]!!.weightG!! > proj[20]!!.weightG!!)
        assertTrue(proj[30]!!.cumFeedPerBirdG!! > proj[20]!!.cumFeedPerBirdG!!)
        assertTrue(proj[42]!!.cumMortPct!! > by[19]!!.cumMortPct!!)
        assertTrue("FCR at day 42 ${proj[42]!!.fcr}", proj[42]!!.fcr!! in 1.2..2.0)
    }

    @Test fun appetiteIsLearnedFromTheLumpyEntries() {
        // entries swing 16 → 37 → 16 → 24 → 32 bags around a plan of 25–33: the appetite must stay near what
        // the flock eats over several days, not chase a single day
        val hist = (0 until 19).mapNotNull { n ->
            val eaten = FlockCalc.usedKg(by[n + 1]!!, KgfFlock.kgOf, 60.0)
            if (eaten <= 0) null else IntakeForecast.Day(n, by[n]!!.totalFeedKg, eaten)
        }
        val f = IntakeForecast.forecast(hist, by[19]!!.totalFeedKg, FlockCalc.carrySdKg(KgfFlock.farm))
        val a = IntakeForecast.advise(f, 60.0, Math.ceil(by[19]!!.totalFeedKg / 60.0))
        println("appetite ${"%.3f".format(f.ratio)} ± ${"%.3f".format(f.ratioSd)}; likely ${"%.1f".format(a.likelyBags)} bags (${"%.1f".format(a.lowBags)}–${"%.1f".format(a.highBags)}), " +
            "in the lines ${"%+.1f".format(a.inLinesBags)}, pour ${a.loadBags}; last 3 days ${"%.1f".format(f.last3!!.pct)} %, last 7 ${"%.1f".format(f.last7!!.pct)} %, one day ± ${"%.1f".format(f.dayScatterPct!!)} %")
        assertEquals(13, f.days)                                // entries of days 6 … 18
        assertTrue("appetite ${f.ratio}", f.ratio in 0.85..1.05)
        assertTrue(a.lowBags < a.likelyBags && a.likelyBags < a.highBags)
        assertTrue(a.loadBags in 20.0..40.0)
        assertTrue(f.dayScatterPct!! > 10)                      // single days really do swing
        assertTrue(abs(f.last7!!.pct - 100) < 12)              // a week together is close to the plan
        assertEquals(288.0, FlockCalc.carrySdKg(KgfFlock.farm), 1e-9)   // 40 % of 4 lines × 3 bags × 60 kg
    }

    @Test fun cvIsEstimatedFromTheGroupWeighings() {
        // day 18: five groups of ten, 841–875 g a bird; pooled with days 16 and 17
        val cv = FlockCalc.cvEstimate(rows, 18)
        println("CV estimated at day 18: ${"%.1f".format(cv)} %")
        assertNotNull(cv); assertTrue("cv $cv", cv!! in 6.0..13.0)
        assertNull(by[18]!!.cv)                                 // no birds were weighed one by one: no true CV
        // day 5 had one group only: the estimate comes from the days before it
        assertNotNull(FlockCalc.cvEstimate(rows, 5))
        assertNull(FlockCalc.cvEstimate(rows.filter { it.dayNumber == 5 }, 5))
    }

    @Test fun sheetGetsTheWorkedOutValuesTheProjectionsAndTheFormulas() {
        val c = SheetSchema.Content(KgfFlock.farm, KgfFlock.config, KgfFlock.feedTypes, listOf(KgfFlock.flock), KgfFlock.rows(), emptyList())
        val all = SheetReports.all(c)
        assertEquals(SheetReports.TABS, all.keys.toList())
        val comp = all[SheetReports.COMPUTED]!!
        val h = comp[0].map { it.toString() }
        assertEquals(21, comp.size)                              // header + days 0 … 19
        assertTrue(comp.all { it.size == h.size })
        fun cell(day: Int, col: String) = comp[day + 1][h.indexOf(col)]
        assertEquals(15272, cell(18, "Live birds")); assertEquals(856.5, cell(18, "Weight g")); assertEquals("measured", cell(18, "Weight is"))
        assertEquals(1.133, cell(18, "FCR")); assertEquals("projected", cell(19, "Weight is"))
        assertEquals(82.0, cell(19, "Feed store bags"))
        assertTrue((cell(18, "CV % (estimated from groups)") as Double) in 6.0..13.0)
        assertEquals("", cell(18, "CV % (birds weighed singly)"))
        val pr = all[SheetReports.PROJECTIONS]!!
        val ph = pr[0].map { it.toString() }
        assertEquals(43, pr.size)                                // header + days 1 … 42
        assertTrue(pr.all { it.size == ph.size })
        val r18 = pr[18]
        assertEquals(18, r18[ph.indexOf("Day")]); assertEquals(856.5, r18[ph.indexOf("Weight g entered")])
        assertTrue(r18[ph.indexOf("Weight g projected")] is Double && r18[ph.indexOf("Weight g off %")] is Double)
        // a day after the last entry: the forecast only
        val r30 = pr[30]
        assertTrue(r30[ph.indexOf("Weight g projected")] is Double); assertEquals("", r30[ph.indexOf("Weight g entered")]); assertEquals("", r30[ph.indexOf("Deaths entered")])
        // every worked-out column of Computed is explained in Formulas, in words
        val f = all[SheetReports.FORMULAS]!!
        assertEquals(listOf("Value", "Unit", "How it is worked out", "Worked out from"), f[0])
        assertTrue(f.size > 25 && f.all { it.size == 4 })
        // the reports survive the write → read → verify round trip with the rest of the file
        val b = SheetSchema.blocks(c, SheetSchema.primaryMeta("farm_1", 6))
        assertTrue(SheetReports.TABS.all { b.containsKey(it) })
        assertEquals(SheetSchema.VERSION.toString(), b["_Meta"]!!.first { it[0] == "schemaVersion" }[1].toString())
    }
}
