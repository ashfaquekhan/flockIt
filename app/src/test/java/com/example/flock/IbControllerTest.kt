package com.example.flock

import com.example.flock.data.FarmEntity
import com.example.flock.engine.CompanyStandard
import com.example.flock.engine.IbController
import com.example.flock.engine.PhysiologicalEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IbControllerTest {
    // Min-vent sizing used 20,000 cfm per fan (25,000 × 0.8), 300 ft² cross-section.
    private val farm = FarmEntity(spreadsheetId = "t", fanDerate = 0.2, usableWidthFt = 40.0, heightFt = 7.5)

    @Test
    fun ladderShape() {
        val l = IbController.ladder(10)
        assertEquals(20, l.size)
        assertEquals(listOf(5), l[9].cont)          // L10: first fan non-stop
        assertEquals(listOf(3), l[10].cyc)          // L11: + fan 3 on a timer
        assertEquals(listOf(5, 3), l[11].cont)      // L12: two fans
        assertEquals(10, l[19].cont.size)           // L20: all fans
        assertEquals(5.9, IbController.accu(l).last(), 1e-9)
    }

    @Test
    fun wetBulbStull() {
        assertEquals(13.7, PhysiologicalEngine.wetBulb(20.0, 50.0), 0.1)
    }

    @Test
    fun broodingIsWarm() {
        // Standard chick ~33 °C; a small 34 g chick is brooded warmer.
        assertEquals(33.0, PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_TEMP_BY_BW, 42.0), 0.01)
        assertTrue(PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_TEMP_BY_BW, 34.0) > 34.0)
        assertEquals(20.0, PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_TEMP_BY_BW, 2600.0), 0.01)
    }

    @Test
    fun dayPlansMatchSettingsSheet() {
        val d1 = IbController.dayPlan(1, PhysiologicalEngine.bwFromDay(1.0, "Ross308"), 15976, farm)
        val d7 = IbController.dayPlan(7, PhysiologicalEngine.bwFromDay(7.0, "Ross308"), 15832, farm)
        val d35 = IbController.dayPlan(35, PhysiologicalEngine.bwFromDay(35.0, "Ross308"), 15288, farm)
        println("d1 MIN ${d1.minLevel} MAX ${d1.maxLevel} SET ${d1.set} HEAT ${d1.heat}")
        println("d7 MIN ${d7.minLevel} MAX ${d7.maxLevel} SET ${d7.set} HEAT ${d7.heat}")
        println("d35 MIN ${d35.minLevel} MAX ${d35.maxLevel} SET ${d35.set} HEAT ${d35.heat}")
        assertEquals(1, d1.minLevel); assertEquals(14, d1.maxLevel); assertEquals(32.5, d1.set, 0.2)
        assertEquals(d1.minLevel, d1.safeLevel)
        assertEquals(4, d7.minLevel); assertEquals(14, d7.maxLevel); assertEquals(29.6, d7.set, 0.2)
        assertEquals(10, d35.minLevel); assertEquals(20, d35.maxLevel); assertEquals(19.7, d35.set, 0.2)
    }

    @Test
    fun houseBalancePlausible() {
        val bw = PhysiologicalEngine.bwFromDay(21.0, "Ross308")
        val p = IbController.dayPlan(21, bw, 15568, farm)
        val s = IbController.simulate(p, 29.2, 78.0, 14, 15568, bw, farm, rhCompensation = true)
        println("d21 Sep 14h: L${s.level} fans ${s.fans} house ${s.houseC} rh ${s.houseRh} felt ${s.feltC}")
        assertTrue(s.fans in 4.0..7.5)
        assertTrue(s.houseC in 29.0..31.5)
        assertTrue(s.feltC in 23.0..29.5)
    }

    @Test
    fun companyStandardConsistent() {
        assertEquals(55, CompanyStandard.ROWS.size)
        for (r in CompanyStandard.ROWS) {
            val cf = (2 - r.bw / 1000.0) * 0.25 + r.fcr
            assertEquals("cFCR day ${r.day}", r.cfcr, cf, 0.011)
            assertEquals("FCR day ${r.day}", r.fcr, r.cumFeed.toDouble() / r.bw, 0.011)
        }
        assertEquals(0.15 * 12 + 0.10 * 16 + 0.15 * 7, CompanyStandard.cumMortPct(35), 1e-9)
        // ration follows the company feed curve at the flock's weight: a bird at the chart's day-24 weight eats day 25's feed
        assertEquals(CompanyStandard.feedPerDay(25)!!, CompanyStandard.feedForWeight(CompanyStandard.bw(24)!!), 1e-9)
        assertEquals(CompanyStandard.feedPerDay(1)!!, CompanyStandard.feedForWeight(40.0), 1e-9)
    }
}
