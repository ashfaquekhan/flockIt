package com.example.flock

import com.example.flock.domain.FeedingRhythm
import com.example.flock.domain.FlockNeeds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When birds eat through the day, and how likely they are hungry, thirsty or panting. */
class FlockNeedsTest {
    // lights on 05:00 to 23:00 (18 h of light), as the farm's programme on day 20
    private val light = { h: Double -> if (h >= 5.0 && h < 23.0) 1.0 else 0.0 }
    private val cool = { _: Double -> 0.0 }

    @Test fun birdsEatMostAfterLightsOnAndBeforeLightsOff() {
        var sum = 0.0; var n = 0
        var s = 0.125
        while (s < 18.0) { sum += FeedingRhythm.rate(s, 18.0); n++; s += 0.25 }
        assertEquals(1.0, sum / n, 0.01)                                  // 1 on average over the lit hours
        assertTrue(FeedingRhythm.rate(1.5, 18.0) > 1.3)                   // the morning meal
        assertTrue(FeedingRhythm.rate(16.5, 18.0) > 1.3)                  // filling up before dark
        assertTrue("midday ${FeedingRhythm.rate(9.0, 18.0)}", FeedingRhythm.rate(9.0, 18.0) in 0.78..0.88)
        assertEquals(0.0, FeedingRhythm.rate(19.0, 18.0), 0.0)            // the dark
        // continuous light: no meals to wait for, an even rate
        assertEquals(1.0, FeedingRhythm.rate(3.0, 24.0), 1e-9); assertEquals(1.0, FeedingRhythm.rate(12.0, 24.0), 1e-9)
        val sh = FeedingRhythm.shares(5.0, 18.0, light)
        assertEquals(1.0, sh.sum(), 1e-9)
        assertEquals(0.0, sh[8] + sh[95], 1e-12)                           // 02:00 and 23:45: dark
        assertEquals(0.0, FeedingRhythm.eatenBy(5.0, sh), 1e-9); assertEquals(1.0, FeedingRhythm.eatenBy(23.0, sh), 1e-9)
        // by 14:00 (half the lit hours) just over half the feed is gone: the morning meal is behind, the evening one ahead
        assertEquals(0.5, FeedingRhythm.eatenBy(14.0, sh), 0.03)
        // a hot afternoon moves eating out of it
        val hot = FeedingRhythm.shares(5.0, 18.0, light) { h -> if (h in 11..16) 0.8 else 1.0 }
        assertTrue(hot[13 * 4] < sh[13 * 4] && hot[7 * 4] > sh[7 * 4])
        assertEquals(1.0, hot.sum(), 1e-9)
    }

    @Test fun theFarmsMorningFeedingLastsToAboutFourNotHalfPastTwo() {
        // day 20 on the farm: 13 bags of 60 kg poured at 08:30 for 15,250 birds of 1,141 g.
        // The chart gives 155.3 g a bird a day (2,368 kg); spread evenly over the 18 lit hours that is 131.6 kg an
        // hour and the feed is gone 5.9 h later, at 14:26 — what the app said. The farm expected about 16:00.
        val chartKg = 2368.0
        val evenEnd = 8.5 + 780.0 / (chartKg / 18.0)
        assertEquals(14.43, evenEnd, 0.02)
        // what really sets the pace: the flock eats 94 % of the chart, mid-morning to mid-afternoon is the slow part
        // of the day, and the afternoon is warm (31–32 °C outside: a few % less while it lasts)
        val shares = FeedingRhythm.shares(5.0, 18.0, light) { h -> if (h in 10..16) 0.96 else 1.0 }
        val fed = listOf(FlockNeeds.Feeding(8.5, 780.0))
        val end = FlockNeeds.emptyAt(8.5, fed, chartKg * 0.94, shares, light)
        println("pans empty at about %.2f h (even spread said %.2f)".format(end, evenEnd))
        assertNotNull(end); assertTrue("empty at $end", end!! in 15.5..16.75)
        // slower and faster than expected: the honest answer is a window, not a minute
        val late = FlockNeeds.emptyAt(8.5, fed, chartKg * 0.94, shares, light, rate = 1 - FlockNeeds.SPREAD)!!
        val early = FlockNeeds.emptyAt(8.5, fed, chartKg * 0.94, shares, light, rate = 1 + FlockNeeds.SPREAD)!!
        println("between %.2f and %.2f".format(early, late))
        assertTrue(early < end && end < late && late - early > 1.5)

        fun at(h: Double) = FlockNeeds.at(h, fed, chartKg * 0.94, shares, light, cool)
        val t1443 = at(14.72); val t16 = at(16.0); val t18 = at(18.0); val t20 = at(20.0)
        println("hungry: 14:43 %.0f %%, 16:00 %.0f %%, 18:00 %.0f %%, 20:00 %.0f %%".format(t1443.hungry * 100, t16.hungry * 100, t18.hungry * 100, t20.hungry * 100))
        assertTrue("at 14:43 ${t1443.hungry}", t1443.hungry < 0.10)        // not "empty for 0.3 h"
        assertTrue(t1443.feedLeftKg > 100)                                  // feed still in the pans in the middle case
        assertTrue("at 16:00 ${t16.hungry}", t16.hungry < 0.25)             // about now the first pans run empty
        assertTrue("at 18:00 ${t18.hungry}", t18.hungry in 0.3..0.75)
        assertTrue("at 20:00 ${t20.hungry}", t20.hungry > 0.8)
        // never falling while nothing more is poured
        var last = 0.0
        for (q in 36..80) { val x = at(q / 4.0).hungry; assertTrue(x >= last - 1e-9); last = x }
        // a second feeding brings it back down within the hour
        val refed = FlockNeeds.at(21.0, fed + FlockNeeds.Feeding(20.0, 780.0), chartKg * 0.94, shares, light, cool)
        assertTrue("an hour after feeding again ${refed.hungry}", refed.hungry < 0.35 && refed.feedLeftKg > 400)
    }

    @Test fun birdsWakeHungryAndThirstyAndSettleOnceTheyCanEatAndDrink() {
        val shares = FeedingRhythm.shares(5.0, 18.0, light)
        // feed poured the evening before, plenty left for the morning
        val fed = listOf(FlockNeeds.Feeding(-3.0, 3000.0))
        val preDawn = FlockNeeds.at(4.75, fed, 2200.0, shares, light, cool)
        val soonAfter = FlockNeeds.at(5.75, fed, 2200.0, shares, light, cool)
        val later = FlockNeeds.at(7.0, fed, 2200.0, shares, light, cool)
        println("hungry / thirsty: 04:45 %.0f / %.0f %%, 05:45 %.0f / %.0f %%, 07:00 %.0f / %.0f %%".format(
            preDawn.hungry * 100, preDawn.thirsty * 100, soonAfter.hungry * 100, soonAfter.thirsty * 100, later.hungry * 100, later.thirsty * 100))
        assertTrue(preDawn.hungry > 0.9 && preDawn.thirsty > 0.8)          // six hours of dark behind them
        assertTrue(later.hungry < 0.10)
        assertEquals(0.05, later.thirsty, 0.02)                             // the ordinary share on the way to a drink
        assertTrue(soonAfter.hungry < preDawn.hungry && soonAfter.thirsty < preDawn.thirsty)
        // no feed at lights-on: they stay hungry
        val unfed = FlockNeeds.at(7.0, listOf(FlockNeeds.Feeding(-10.0, 200.0)), 2200.0, shares, light, cool)
        assertTrue(unfed.hungry > 0.9)
    }

    @Test fun heatBringsPantingAndThirst() {
        assertTrue(FlockNeeds.pantingShare(0.0) < 0.01)
        assertEquals(0.5, FlockNeeds.pantingShare(6.0), 1e-6)
        assertTrue(FlockNeeds.pantingShare(4.0) in 0.1..0.2 && FlockNeeds.pantingShare(10.0) > 0.95)
        var last = 0.0
        for (h in -5..15) { val p = FlockNeeds.pantingShare(h.toDouble()); assertTrue(p >= last); last = p }
        val shares = FeedingRhythm.shares(5.0, 18.0, light)
        val fed = listOf(FlockNeeds.Feeding(6.0, 3000.0))
        val comfy = FlockNeeds.at(13.0, fed, 2200.0, shares, light, cool)
        val warm = FlockNeeds.at(13.0, fed, 2200.0, shares, light, { 5.0 })
        val hot = FlockNeeds.at(13.0, fed, 2200.0, shares, light, { 10.0 })
        assertTrue(comfy.thirsty < 0.08 && comfy.panting < 0.01)
        assertTrue(warm.thirsty in 0.2..0.4 && warm.panting in 0.25..0.4)
        assertTrue(hot.thirsty > 0.8 && hot.panting > 0.95)
        // the heat does not make them hungry: feed is in the pans
        assertEquals(comfy.hungry, hot.hungry, 1e-9)
    }
}
