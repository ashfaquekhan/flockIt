package com.example.flock

import com.example.flock.domain.BirdBehaviour
import com.example.flock.domain.BirdEnvironment
import com.example.flock.domain.Projection
import com.example.flock.domain.Uniformity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** What the air does to the birds, how they spend their time by size, and the projections that move with the clock. */
class EnvModelTest {

    @Test fun comfortChangesNothing() {
        for (h in listOf(-2.0, 0.0, 1.0)) {
            val r = BirdEnvironment.respondFelt(21, 1000.0, 24.0 + h, 24.0, 41.2)
            assertEquals(BirdEnvironment.State.COMFORT, r.state)
            assertEquals(1.0, r.feedFactor, 1e-9); assertEquals(1.0, r.waterFactor, 1e-9); assertEquals(1.0, r.gainFactor, 1e-9); assertEquals(1.0, r.mortalityFactor, 1e-9)
            assertEquals(41.2, r.bodyTempC, 1e-9); assertTrue(!r.panting)
        }
    }

    @Test fun heatCutsFeedRaisesWaterAndCostsMoreGrowthThanFeed() {
        // 6 °C over comfort: 4 °C past the comfort band × 1.5 % → 6 % less feed; water +6 % a °C from 1 °C over
        val r = BirdEnvironment.respondFelt(28, 1700.0, 28.0, 22.0, 41.2)
        assertEquals(0.94, r.feedFactor, 1e-9)
        assertEquals(1.30, r.waterFactor, 1e-9)
        assertTrue("growth ${r.gainFactor} falls more than feed", r.gainFactor < r.feedFactor && r.gainFactor > 0.8)
        assertEquals(BirdEnvironment.State.WARM, r.state)
        // strong heat: steeper, body temperature up, panting, more birds at risk
        val hot = BirdEnvironment.respondFelt(35, 2400.0, 32.0, 20.0, 41.2)        // 12 °C over
        assertEquals(1 - 0.015 * 6 - 0.035 * 4, hot.feedFactor, 1e-9)
        assertEquals(BirdEnvironment.State.DANGER, hot.state)
        assertEquals(3.25, hot.bodyRiseC, 1e-9); assertEquals(44.45, hot.bodyTempC, 1e-9)
        assertTrue(hot.panting && hot.breathsPerMin > 150)
        assertTrue(hot.mortalityFactor > 5)
        // each step hotter is worse, never better
        var last = BirdEnvironment.respondFelt(30, 2000.0, 20.0, 20.0, 41.2)
        for (t in 21..40) {
            val x = BirdEnvironment.respondFelt(30, 2000.0, t.toDouble(), 20.0, 41.2)
            assertTrue(x.feedFactor <= last.feedFactor + 1e-12 && x.waterFactor >= last.waterFactor - 1e-12 && x.gainFactor <= last.gainFactor + 1e-12)
            assertTrue(x.bodyTempC >= last.bodyTempC - 1e-12 && x.mortalityFactor >= last.mortalityFactor - 1e-12 && x.breathsPerMin >= last.breathsPerMin - 1e-12)
            last = x
        }
        assertTrue(last.feedFactor >= 0.45 && last.gainFactor >= 0.0)
    }

    @Test fun coldMakesBirdsEatMoreAndChillsChicks() {
        val chick = BirdEnvironment.respondFelt(3, 100.0, 24.0, 31.0, 40.6)          // 7 °C under
        assertEquals(BirdEnvironment.State.COLD, chick.state)
        assertTrue(chick.feedFactor > 1.0 && chick.gainFactor < 1.0)
        assertTrue("chick body ${chick.bodyTempC}", chick.bodyTempC < 40.6)
        assertTrue(chick.mortalityFactor > 1.2)
        val grown = BirdEnvironment.respondFelt(30, 2000.0, 13.0, 20.0, 41.2)
        assertEquals(41.2, grown.bodyTempC, 1e-9); assertEquals(1.0, grown.mortalityFactor, 1e-9)   // a grown bird holds its temperature
    }

    @Test fun felTemperatureCountsHumidityAndMovingAir() {
        val still = BirdEnvironment.respond(28, 1700.0, BirdEnvironment.Air(30.0, 65.0, 0.0), 22.0, 41.2)
        val humid = BirdEnvironment.respond(28, 1700.0, BirdEnvironment.Air(30.0, 85.0, 0.0), 22.0, 41.2)
        val windy = BirdEnvironment.respond(28, 1700.0, BirdEnvironment.Air(30.0, 65.0, 400.0), 22.0, 41.2)
        assertEquals(30.0, still.feltC, 1e-9)
        assertTrue(humid.feltC > still.feltC && windy.feltC < still.feltC - 2)
        assertTrue(windy.feedFactor > still.feedFactor)
        // a sensor reading takes the place of the modelled air, and a hand-set one of both below it
        val model = BirdEnvironment.Air(28.0, 70.0, 100.0, BirdEnvironment.Source.WEATHER)
        val sensor = BirdEnvironment.Air(26.5, 62.0, 100.0, BirdEnvironment.Source.SENSOR)
        assertEquals(sensor, BirdEnvironment.pick(model, null, sensor))
        assertEquals(model, BirdEnvironment.pick(null, model, BirdEnvironment.Air(24.0, 60.0)))
        assertNull(BirdEnvironment.pick(null, null))
    }

    @Test fun dayAddsUpTheHoursWithFeedCountedWhileTheLightsAreOn() {
        // a day that is comfortable at night and 8 °C over comfort from 11:00 to 16:00
        val hours = (0 until 24).map { h ->
            val felt = if (h in 11..16) 30.0 else 22.0
            BirdEnvironment.Hour(h, null, null, BirdEnvironment.Air(felt, 65.0), null, BirdEnvironment.respondFelt(28, 1700.0, felt, 22.0, 41.2))
        }
        val allLit = BirdEnvironment.day(hours) { 1.0 }!!
        assertEquals((18 * 1.0 + 6 * 0.91) / 24, allLit.feedFactor, 1e-9)
        assertEquals(6, allLit.hoursHot); assertEquals(0, allLit.hoursCold); assertTrue(allLit.hottest.hour in 11..16)
        assertEquals(BirdEnvironment.State.HOT, allLit.worst)
        // lights off through the hot hours: the heat no longer touches the feed, but growth and risk still count it
        val darkNoon = BirdEnvironment.day(hours) { if (it in 11..16) 0.0 else 1.0 }!!
        assertTrue(darkNoon.feedFactor > allLit.feedFactor)
        assertEquals(allLit.gainFactor, darkNoon.gainFactor, 1e-12)
        assertNull(BirdEnvironment.day(emptyList()) { 1.0 })
    }

    @Test fun birdsRestMoreAndWalkLessAndSlowerAsTheyGrow() {
        // Tickle et al. 2018: about 12 % of the time moving under 0.5 kg, 5 % at 0.9 kg, 4 % above 2 kg
        assertEquals(0.1206, BirdBehaviour.activeShare(0.2), 1e-9)
        assertEquals(0.050, BirdBehaviour.activeShare(0.911), 1e-9)
        assertEquals(0.0403, BirdBehaviour.activeShare(2.5), 1e-9)
        assertEquals(0.1346, BirdBehaviour.walkSpeed(0.2), 1e-4); assertTrue(BirdBehaviour.walkSpeed(2.5) in 0.085..0.10)
        assertTrue(BirdBehaviour.peakSpeed(0.2) > 1.3 && BirdBehaviour.peakSpeed(2.5) < 0.45)
        var last = BirdBehaviour.budget(60.0, 1)
        for (g in listOf(200.0, 500.0, 900.0, 1500.0, 2500.0, 3200.0)) {
            val b = BirdBehaviour.budget(g, 20)
            assertEquals(1.0, b.rest + b.walk + b.eat + b.drink + b.forage + b.preen + b.stand, 1e-9)
            assertTrue("rest grows at $g g", b.rest >= last.rest - 1e-9)
            assertTrue(b.walk <= last.walk + 1e-9 && b.walkMs <= last.walkMs + 1e-9 && b.restSec >= last.restSec)
            last = b
        }
        // a 900 g bird (the farm's flock on day 19): lying about two thirds of the time, on the move about a twentieth
        val now = BirdBehaviour.budget(900.0, 19)
        assertTrue("rest ${now.rest}", now.rest in 0.60..0.75); assertTrue("walk ${now.walk}", now.walk in 0.04..0.06)
        assertTrue("speed ${now.walkMs}", now.walkMs in 0.09..0.11)
    }

    @Test fun heatAndColdChangeTheBehaviour() {
        val calm = BirdBehaviour.budget(1700.0, 28, 0.0)
        val hot = BirdBehaviour.budget(1700.0, 28, 9.0)
        assertTrue(hot.walk < calm.walk && hot.eat < calm.eat && hot.drink > calm.drink)
        assertEquals(0.0, calm.pant, 1e-9); assertEquals(1.0, hot.pant, 1e-9); assertTrue(hot.wingsOut > 0.5 && hot.spread > 0.9)
        assertEquals(0.0, hot.huddle, 1e-9)
        val coldChick = BirdBehaviour.budget(100.0, 3, -8.0)
        assertEquals(1.0, coldChick.huddle, 1e-9); assertEquals(0.0, coldChick.pant, 1e-9)
        assertTrue(coldChick.eat < BirdBehaviour.budget(100.0, 3, 0.0).eat)
        assertTrue(BirdBehaviour.budget(2500.0, 35, -8.0).huddle < 0.5)        // grown birds crowd far less
        // the meals after lights-on and before lights-off
        assertTrue(BirdBehaviour.budget(900.0, 19, 0.0, sinceLightsOnH = 0.5, untilDarkH = 17.0).eat > BirdBehaviour.budget(900.0, 19, 0.0, sinceLightsOnH = 9.0, untilDarkH = 9.0).eat)
    }

    @Test fun clockProjectionsFollowTheLitHours() {
        // lights on 05:00 to 23:00: nothing eaten by 05:00, half by 14:00, all by 23:00
        val light = { h: Double -> if (h >= 5.0 && h < 23.0) 1.0 else 0.0 }
        assertEquals(0.0, Projection.litShare(5.0, light), 1e-9)
        assertEquals(0.5, Projection.litShare(14.0, light), 0.01)
        assertEquals(1.0, Projection.litShare(23.5, light), 1e-9)
        var last = -1.0
        for (i in 0..96) { val s = Projection.litShare(i / 4.0, light); assertTrue(s >= last); last = s }
        // a weight grown for half a day, and held back by heat
        val grow = { w: Double, d: Double -> w + 70.0 * d }
        assertEquals(835.0, Projection.grown(800.0, 0.5, grow), 1e-9)
        assertEquals(828.0, Projection.grown(800.0, 0.5, grow, 0.8), 1e-9)
        assertEquals(800.0, Projection.grown(800.0, 0.0, grow), 1e-9)
    }

    @Test fun cvFromGroupsMatchesTheSpreadOfTheBirds() {
        // 12 groups of 10 drawn from birds with a known 10 % CV (fixed numbers, no randomness in the test)
        val z = listOf(-1.2, 0.4, 0.9, -0.3, 1.5, -0.8, 0.1, -1.6, 0.7, 1.1, -0.5, -0.3)
        val groups = z.map { 1000.0 * (1 + 0.10 * it / Math.sqrt(10.0)) to 10 }
        val v = Uniformity.relVariance(groups)!!
        assertEquals(11, v.second)
        assertEquals(10.0, Math.sqrt(v.first) * 100, 1.5)
        assertNull(Uniformity.relVariance(groups.take(2)))
        assertNull(Uniformity.cvFromGroups(emptyList()))
        // pooled over the last three weighings only
        val even = z.map { 1000.0 * (1 + 0.04 * it / Math.sqrt(10.0)) to 10 }
        assertTrue(abs(Uniformity.cvFromGroups(listOf(groups, even, even, even))!! - 4.0) < 0.8)
    }
}
