package com.example.flock

import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FarmEntity
import com.example.flock.data.FlockEntity
import com.example.flock.ui.screens.OutputData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pan on/off plan for the farm's layout: 4 feeder lines × 60 pans, 3 bags fill one line (so one bag
 * fills 20 pans), full house open.
 */
class FeedPlanTest {
    private val farm = FarmEntity(spreadsheetId = "t", feedBagKg = 50.0, feederLines = 4, pansPerFeederLine = 60, feederLineBags = 3,
        usableLengthFt = 299.0, usableWidthFt = 39.0, drinkerLines = 5)
    private val flock = FlockEntity(spreadsheetId = "t", flockId = "f", name = "F", startDate = "2026-09-19", birdsPlaced = 15700, receptionMort = 40)

    private fun plan(day: Int, bags: Double, bw: Double, live: Int): OutputData {
        val e = DailyDataEntity(spreadsheetId = "t", flockId = "f", dayNumber = day, date = "2026-09-30", liveBirds = live,
            avgWeight = bw, sampleEntered = true, weightAge = day.toDouble(), totalFeedKg = bags * 50.0, feedPerBird = bags * 50_000.0 / live,
            occupiedFt2 = 299.0 * 39.0, lightHours = 18.0, tempIdeal = 28.0, rhIdeal = 60.0)
        return OutputData(flock, farm, e, listOf(e), emptyList(), null, emptyList(), false)
    }

    @Test fun day11SixteenBags() {
        val d = plan(11, 16.0, 400.0, 15400)
        assertEquals(20.0, d.pansPerBag, 1e-9)
        assertEquals(3, d.feedingsWanted)
        // 3 feedings × 5.33 bags fill only 26.7 pans a line: the patterns that fit (2 on·3 off = 24 pans)
        // put 160 birds on a pan — too many for 400 g birds — so the plan drops to 2 feedings × 8 bags.
        assertEquals(2, d.feedings)
        assertEquals(8.0, d.bagsPerFeeding, 1e-9)
        assertEquals("2 on · 1 off", d.feedPattern.label)
        assertEquals(40, d.pansOpenPerLine)
        assertTrue(d.feedPattern.travelM <= 2.0)
        assertTrue(d.birdsPerPan <= d.birdsPerPanMax)
        assertTrue(d.patternFits)
        println("day 11: ${d.feedings}× ${d.bagsPerFeeding} bags, ${d.feedPattern.label}, ${d.pansOpenPerLine}/line, walk ${d.feedPattern.travelM} m, ${d.birdsPerPan} birds/pan (max ${d.birdsPerPanMax})")
    }

    @Test fun day20TwentyFiveBags() {
        val d = plan(20, 25.0, 900.0, 15200)
        assertEquals(3, d.feedings)
        assertEquals("2 on · 1 off", d.feedPattern.label)
        println("day 20: ${d.feedings}× ${d.bagsPerFeeding} bags, ${d.feedPattern.label}, ${d.pansOpenPerLine}/line")
    }

    @Test fun bigBirdsAllPansOn() {
        val d = plan(32, 44.0, 2100.0, 15000)
        assertEquals(2, d.feedings)
        assertEquals("All on", d.feedPattern.label)
        assertTrue(d.hopperBagsPerFeeding > 0)   // 22 bags a feeding, lines hold 12: the rest waits in the hopper
    }
}
