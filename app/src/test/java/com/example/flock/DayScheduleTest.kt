package com.example.flock

import com.example.flock.domain.DaySchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.min

class DayScheduleTest {
    private fun fwd(a: Double, b: Double) = (((b - a) % 24) + 24) % 24
    private fun gap(a: Double, b: Double) = fwd(a, b).let { min(it, 24 - it) }
    private val hot = DaySchedule.seasonTemps(26.0, 36.0)

    @Test fun seasonTempsCoolAtDawnHotMidAfternoon() {
        assertEquals(5, hot.indexOf(hot.min()))
        assertEquals(15, hot.indexOf(hot.max()))
    }

    @Test fun darkIsOneBlockEndingAtDawn() {
        val p = DaySchedule.plan(DaySchedule.Inputs(18.0, 5.0, 2, 3, hot))
        assertEquals(5.0, p.darkEnd, 1e-9)
        assertEquals(6.0, fwd(p.darkStart, p.darkEnd), 1e-9)
    }

    @Test fun feedsAtLightsOnAndWellBeforeDark() {
        for (n in 1..4) {
            val p = DaySchedule.plan(DaySchedule.Inputs(18.0, 5.0, n, 3, hot))
            assertEquals("feedings for $n", n, p.feeds.size)
            assertTrue("first load just after lights on", fwd(p.darkEnd, p.feeds.first()) <= 0.5)
            p.feeds.forEach { f -> assertTrue("load at $f leaves time to eat before the dark", fwd(f, p.darkStart) >= 2.0) }
        }
    }

    @Test fun noLoadInTheHeatOrJustBeforeIt() {
        val p = DaySchedule.plan(DaySchedule.Inputs(18.0, 5.0, 3, 3, hot))
        val blockFrom = p.hotFrom - 2
        p.feeds.forEach { f -> assertTrue("load at $f is outside the hot window", fwd(blockFrom, f) >= 6.0) }
    }

    @Test fun loadsAreSpreadOut() {
        val p = DaySchedule.plan(DaySchedule.Inputs(20.0, 5.0, 3, 2, hot))
        for (i in p.feeds.indices) for (j in i + 1 until p.feeds.size) assertTrue(gap(p.feeds[i], p.feeds[j]) >= 1.5)
    }

    @Test fun refillsBeforeWakingAndCounted() {
        val p = DaySchedule.plan(DaySchedule.Inputs(18.0, 5.0, 2, 4, hot))
        assertEquals(4, p.refills.size)
        assertTrue(p.refills.any { gap(it, p.darkEnd - 0.5) < 0.01 })
        assertTrue("a refill before the heat", p.refills.any { gap(it, p.hotFrom - 0.5) < 0.01 })
    }

    @Test fun walksAfterLightsOnAndBeforeDark() {
        val p = DaySchedule.plan(DaySchedule.Inputs(18.0, 5.0, 2, 3, hot))
        assertTrue(p.walks.any { gap(it, p.darkEnd + 0.5) < 0.01 })
        assertTrue(p.walks.any { gap(it, p.darkStart - 0.5) < 0.01 })
        p.walks.forEach { w -> assertEquals(0.0, (w * 4) % 1.0, 1e-9) }   // on the quarter hour
    }

    @Test fun mildDaysCanFeedAtMidday() {
        val mild = DaySchedule.seasonTemps(24.0, 26.0)
        val p = DaySchedule.plan(DaySchedule.Inputs(18.0, 5.0, 3, 2, mild))
        assertEquals(3, p.feeds.size)
    }
}
