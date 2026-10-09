package com.example.flock

import com.example.flock.ui.screens.CoopInput
import com.example.flock.ui.screens.CoopSim
import com.example.flock.ui.screens.FarmLayout
import com.example.flock.ui.screens.FeederState
import com.example.flock.ui.screens.LightProgram
import org.junit.Assert.assertTrue
import org.junit.Test

/** Logging a feeding must send the awake birds to the feeder, and they must fill up there. */
class CoopFeedTest {
    private fun input(f: FeederState) = CoopInput(
        age = 11, meanG = 360.0, cvPct = 8.0, live = 15300, entry = 15660, stage = "grower", light = LightProgram(18.0), feeder = f,
        zoneId = java.time.ZoneId.of("UTC"), airC = 28.0, rhPct = 60.0, feelsC = 28.0, chillC = 0.0, pressurePa = 22.5,
        litterC = 28.0, litterMoist = 25.0, bodyC = 41.0, waterC = 18.0 to 21.0, waterPh = 6.0 to 6.8, travelM = 2.0)

    @Test fun birdsEatAfterFeeding() {
        val empty = FeederState(0.0, 400.0, null, 6.0, 0.0, 1_000L, 0.7)
        // a 10 × 10 ft window on the second feeder line, 40 ft from the front: pans, nipples and ~130 birds
        val sim = CoopSim().apply { lenFt = 10.0; widFt = 10.0; x0Ft = 40.0; y0Ft = FarmLayout.DEMO.lineY(3) - 5.0 }
        val a = input(empty)
        sim.setup(a)
        repeat(600) { sim.update(0.016, a, 1.0) }                      // ~10 s hungry, feeder empty
        val before = sim.birds.map { it.fullness }
        val fed = FeederState(400.0, 400.0, 6.0, 0.0, 8.0, 2_000L, 0.1)  // 8 bags just logged
        val b = input(fed)
        sim.update(0.016, b, 1.0)
        assertTrue("the window holds pans and birds", sim.pans.any { it.open } && sim.birds.size > 100)
        val atPans = sim.birds.count { it.state == "goEat" || it.state == "eat" }
        assertTrue("the pans fill up to their rims ($atPans birds head in)", atPans >= 20 && atPans < sim.birds.size)
        assertTrue("the named birds join in", sim.birds.take(4).count { it.state == "goEat" || it.state == "eat" } >= 2)
        var ate = false
        var most = 0
        repeat(3000) { sim.update(0.016, b, 1.0); val n = sim.birds.count { it.state == "eat" }; if (n > 0) ate = true; most = maxOf(most, n) }   // ~48 s
        assertTrue("many birds at the pans at once ($most)", most >= 20)
        assertTrue("birds reach the feeder and eat", ate)
        val states = sim.birds.groupingBy { it.state }.eachCount()
        assertTrue("birds are fuller than before the feeding (${before.average()} → ${sim.birds.map { it.fullness }.average()}, most at the pans $most, now $states)",
            sim.birds.map { it.fullness }.average() > before.average())
    }
}
