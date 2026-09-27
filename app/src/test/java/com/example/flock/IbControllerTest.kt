package com.example.flock

import com.example.flock.data.FarmEntity
import com.example.flock.engine.IbController
import com.example.flock.engine.PhysiologicalEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IbControllerTest {
    // Match the planning model: 18,600 cfm per fan (derate 0.256), 300 ft² cross-section.
    private val farm = FarmEntity(spreadsheetId = "t", fanDerate = 0.256, usableWidthFt = 40.0, heightFt = 7.5)

    @Test
    fun ladderShape() {
        val l = IbController.ladder(10)
        assertEquals(19, l.size)
        assertEquals(listOf(5), l[8].cont)          // L9: first fan non-stop
        assertEquals(listOf(5, 3), l[10].cont)      // L11: two fans
        assertEquals(5.6, IbController.accu(l).last(), 1e-9)
    }

    // Min-vent sizing in the model used 20,000 cfm per fan (lower static than tunnel).
    private val farmMv = FarmEntity(spreadsheetId = "t", fanDerate = 0.2, usableWidthFt = 40.0, heightFt = 7.5)

    @Test
    fun wetBulbStull() {
        assertEquals(13.7, PhysiologicalEngine.wetBulb(20.0, 50.0), 0.1)
    }

    @Test
    fun dayPlansMatchModel() {
        val farm = farmMv
        val d35 = IbController.dayPlan(35, PhysiologicalEngine.bwFromDay(35.0, "Ross308"), 15288, farm)
        println("d35 MIN ${d35.minLevel} MAX ${d35.maxLevel} SET ${d35.set} HEAT ${d35.heat} target ${d35.target}")
        assertEquals(9, d35.minLevel)
        assertEquals(19, d35.maxLevel)
        assertEquals(20.0, d35.set, 0.2)
        val d7 = IbController.dayPlan(7, PhysiologicalEngine.bwFromDay(7.0, "Ross308"), 15832, farm)
        println("d7 MIN ${d7.minLevel} MAX ${d7.maxLevel} SET ${d7.set} HEAT ${d7.heat}")
        assertEquals(3, d7.minLevel)
        assertEquals(11, d7.maxLevel)
        assertEquals(26.7, d7.set, 0.2)
    }

    @Test
    fun houseBalanceMatchesModel() {
        // Python/JS model: day 21, September 14:00 (29.2 °C / 78 %) → ≈ 30.5 °C house, felt ≈ 27.2, 5 fans.
        val bw = PhysiologicalEngine.bwFromDay(21.0, "Ross308")
        val p = IbController.dayPlan(21, bw, 15568, farm)
        val s = IbController.simulate(p, 29.2, 78.0, 14, 15568, bw, farm, rhCompensation = true)
        println("d21 Sep 14h: L${s.level} fans ${s.fans} house ${s.houseC} rh ${s.houseRh} felt ${s.feltC}")
        assertEquals(5.0, s.fans, 0.3)
        assertTrue(s.houseC in 29.5..31.5)
        assertTrue(s.feltC in 26.0..28.5)
    }
}
