package com.example.flock

import com.example.flock.ui.screens.CoopInput
import com.example.flock.ui.screens.CoopSim
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
        val sim = CoopSim()
        val a = input(empty)
        sim.setup(a)
        repeat(600) { sim.update(0.016, a, 1.0) }                      // ~10 s hungry, feeder empty
        val before = sim.birds.map { it.fullness }
        val fed = FeederState(400.0, 400.0, 6.0, 0.0, 8.0, 2_000L, 0.1)  // 8 bags just logged
        val b = input(fed)
        sim.update(0.016, b, 1.0)
        assertTrue("every bird heads for the feeder", sim.birds.all { it.state == "goEat" || it.state == "eat" })
        var ate = false
        repeat(3000) { sim.update(0.016, b, 1.0); if (sim.birds.any { it.state == "eat" }) ate = true }   // ~48 s
        assertTrue("birds reach the feeder and eat", ate)
        assertTrue("birds are fuller than before the feeding", sim.birds.map { it.fullness }.average() > before.average())
    }
}
