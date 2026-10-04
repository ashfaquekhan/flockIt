package com.example.flock

import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FarmEntity
import com.example.flock.data.FeedTypeEntity
import com.example.flock.data.FlockEntity
import com.example.flock.data.filledSamples
import com.example.flock.data.formatMoreSamples
import com.example.flock.data.locationsShown
import com.example.flock.data.parseMoreSamples
import com.example.flock.data.sampleList
import com.example.flock.sync.SheetReports
import com.example.flock.sync.SheetSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sheet layout 6: feed varieties and extra sample locations in their own columns, and the report tabs. */
class DataBackendTest {
    private val sid = "S1"
    private val farm = FarmEntity(spreadsheetId = sid, farmName = "Maa Tarini", farmId = "farm_1", feedBagKg = 60.0)
    private val feeds = listOf(
        FeedTypeEntity(spreadsheetId = sid, code = "B1", name = "Starter", bagKg = 50.0, sortOrder = 1),
        FeedTypeEntity(spreadsheetId = sid, code = "B2", name = "Grower", bagKg = 60.0, sortOrder = 2))
    private val flock = FlockEntity(spreadsheetId = sid, flockId = "F1", name = "Batch 3", startDate = "2026-09-19", birdsPlaced = 1000, receptionMort = 10)

    private fun rawOf(blocks: Map<String, List<List<Any>>>) = SheetSchema.RawFile(
        titles = (SheetSchema.TABS + SheetReports.TABS).toSet(), meta = blocks["_Meta"], farm = blocks["_Farm"], config = blocks["_Config"],
        feedTypes = blocks["_FeedTypes"], flocks = blocks["Flocks"], days = blocks["DailyData"], tasks = blocks["Tasks"])

    private fun day(n: Int, mort: Int = 0, used: String = "", rec: Pair<String, Double>? = null, more: String = "", locs: Int = 0, w1: Double? = null, n1: Int? = null) =
        DailyDataEntity(spreadsheetId = sid, flockId = "F1", dayNumber = n, date = "2026-09-${19 + n}", mortality = mort,
            feedUsedBreakdown = used, feedBagsUsed = com.example.flock.data.parseFeedBreakdown(used).sumOf { it.second },
            feedUsedType = com.example.flock.data.parseFeedBreakdown(used).firstOrNull()?.first ?: "B1",
            feedRecB1 = rec?.second ?: 0.0, feedTypeB1 = rec?.first ?: "B1", moreSamples = more, locCount = locs, w1 = w1, n1 = n1,
            savedFields = if (mort > 0 || used.isNotEmpty()) "M,F" else "", updatedAt = n.toLong() + 1)

    @Test fun moreSamplesTextRoundTrips() {
        val l = parseMoreSamples("5200:14;:;4800.5:13")
        assertEquals(listOf(5200.0 to 14, null to null, 4800.5 to 13), l)
        assertEquals("5200:14;:;4800.5:13", formatMoreSamples(l))
        assertEquals("5200:14", formatMoreSamples(listOf(5200.0 to 14, null to null)))     // blank locations at the end are dropped
        val d = day(3, more = "5200:14;:;4800:13", w1 = 5000.0, n1 = 14)
        assertEquals(8, d.sampleList().size)
        assertEquals(listOf(5000.0 to 14, 5200.0 to 14, 4800.0 to 13), d.filledSamples())
        assertEquals(8, d.locationsShown())
        assertEquals(6, d.copy(locCount = 6).locationsShown())
    }

    @Test fun usedAndExtraLocationColumnsSitInPlaceAndReadBack() {
        val d = day(12, mort = 4, used = "B1=10.0;B2=6.0", more = "5200:14;4800:13", locs = 7, w1 = 5000.0, n1 = 14)
        val c = SheetSchema.Content(farm, null, feeds, listOf(flock), listOf(d), emptyList())
        val b = SheetSchema.blocks(c, SheetSchema.primaryMeta("farm_1", 5))
        val hdr = b["DailyData"]!![0].map { it.toString() }
        val row = b["DailyData"]!![1]
        // the varieties sit right beside the total, the extra locations right after the fifth
        assertEquals(listOf("FeedBagsUsed", "Used B1", "Used B2", "FeedUsedType"), hdr.subList(hdr.indexOf("FeedBagsUsed"), hdr.indexOf("FeedBagsUsed") + 4))
        assertEquals(listOf("N5", "W6", "N6", "W7", "N7", "IndividualWeights"), hdr.subList(hdr.indexOf("N5"), hdr.indexOf("N5") + 6))
        assertEquals(16.0, row[hdr.indexOf("FeedBagsUsed")]); assertEquals(10.0, row[hdr.indexOf("Used B1")]); assertEquals(6.0, row[hdr.indexOf("Used B2")])
        assertEquals(5200.0, row[hdr.indexOf("W6")]); assertEquals(14, row[hdr.indexOf("N6")]); assertEquals(13, row[hdr.indexOf("N7")])
        assertEquals(7, row[hdr.indexOf("Locations")])
        // every fixed field has a column
        assertEquals(SheetSchema.DAILY_HEADERS.toSet(), SheetSchema.dailyHeader(emptyList(), 5).toSet())
        // the written file is current and reads back the same
        val raw = rawOf(b)
        assertTrue(SheetSchema.upgradeReasons(raw).toString(), SheetSchema.upgradeReasons(raw).isEmpty())
        val back = SheetSchema.parse(sid, raw, farm, null).days.single()
        assertEquals("5200:14;4800:13", back.moreSamples); assertEquals(7, back.locCount)
        assertEquals(mapOf("B1" to 10.0, "B2" to 6.0), SheetSchema.usedSplit(back))
        assertEquals(16.0, back.feedBagsUsed, 1e-9)
        assertTrue(back.sampleEntered)
    }

    @Test fun aColumnAddedAtTheEndIsTidiedNextTime() {
        val c = SheetSchema.Content(farm, null, feeds, listOf(flock), listOf(day(2, used = "B1=3.0")), emptyList())
        val b = SheetSchema.blocks(c, SheetSchema.primaryMeta("farm_1", 6)).toMutableMap()
        // a day save found no column for a new variety and a sixth location: it put them at the end of the header
        b["DailyData"] = listOf(b["DailyData"]!![0] + listOf("Used B4", "W6", "N6"), b["DailyData"]!![1] + listOf(2.0, 5100, 14))
        val raw = rawOf(b)
        assertEquals(listOf("DailyData columns differ"), SheetSchema.upgradeReasons(raw))
        // nothing is lost when it is rewritten in order
        val again = SheetSchema.blocks(SheetSchema.parse(sid, raw, farm, null), SheetSchema.primaryMeta("farm_1", 6))
        val hdr = again["DailyData"]!![0].map { it.toString() }
        assertEquals("Used B4", hdr[hdr.indexOf("Used B2") + 1])
        assertEquals(mapOf("B1" to 3.0, "B4" to 2.0), SheetSchema.usedSplit(SheetSchema.parse(sid, rawOf(again), farm, null).days.single()))
        assertEquals("5100:14", SheetSchema.parse(sid, rawOf(again), farm, null).days.single().moreSamples)
        assertTrue(SheetSchema.upgradeReasons(rawOf(again)).isEmpty())
    }

    @Test fun schemaFiveSheetUpgradesWithoutLosingAnything() {
        // the layout of the version before: fixed columns in the old order, then the per-variety columns
        val old = SheetSchema.DAILY_HEADERS.dropLast(1) + listOf("Used B1", "Used B2")
        val d = day(9, mort = 7, used = "B1=10.0;B2=6.0", w1 = 5000.0, n1 = 14)
        val vals = SheetSchema.dayValues(d)
        val raw = SheetSchema.RawFile(titles = SheetSchema.TABS.toSet(),
            meta = listOf(listOf("Key", "Value"), listOf("schemaVersion", 5)),
            flocks = listOf(SheetSchema.FLOCK_HEADERS, SheetSchema.flockRow(flock)),
            days = listOf(old, old.map { vals[SheetSchema.norm(it)] ?: "" }))
        val reasons = SheetSchema.upgradeReasons(raw)
        assertTrue(reasons.any { it.startsWith("schema 5") }); assertTrue(reasons.any { it.contains("FeedLedger") })
        val c = SheetSchema.parse(sid, raw, farm, null)
        assertEquals(7, c.days.single().mortality)
        assertEquals(mapOf("B1" to 10.0, "B2" to 6.0), SheetSchema.usedSplit(c.days.single()))
        val b = SheetSchema.blocks(c, SheetSchema.primaryMeta("farm_1", 5))
        assertTrue(SheetSchema.verify(c, rawOf(b)).isEmpty())
        assertTrue(b.containsKey(SheetReports.FEED_LEDGER) && b.containsKey(SheetReports.DAILY_SUMMARY))
    }

    @Test fun feedLedgerKeepsEachVarietyApartWithRunningTotals() {
        val days = listOf(
            day(0, rec = "B1" to 30.0),
            day(1, mort = 2, used = "B1=4.0"),
            day(2, mort = 1, used = "B1=6.0;B2=2.0", rec = "B2" to 20.0),
            day(3, used = "B2=9.5"),
            day(4))                                    // nothing entered yet: not in the report
        val c = SheetSchema.Content(farm, null, feeds, listOf(flock), days, emptyList())
        val t = SheetReports.feedLedger(c)
        val h = t[0].map { it.toString() }
        assertEquals(5, t.size)                        // header + days 0–3
        fun cell(day: Int, col: String) = (t[day + 1][h.indexOf(col)] as Number).toDouble()
        assertEquals(30.0, cell(0, "B1 received"), 1e-9); assertEquals(30.0, cell(0, "B1 in store"), 1e-9)
        assertEquals(6.0, cell(2, "B1 used"), 1e-9); assertEquals(10.0, cell(2, "B1 used till date"), 1e-9); assertEquals(20.0, cell(2, "B1 in store"), 1e-9)
        assertEquals(2.0, cell(2, "B2 used"), 1e-9); assertEquals(18.0, cell(2, "B2 in store"), 1e-9)
        assertEquals(11.5, cell(3, "B2 used till date"), 1e-9); assertEquals(8.5, cell(3, "B2 in store"), 1e-9)
        assertEquals(21.5, cell(3, "All used till date"), 1e-9); assertEquals(28.5, cell(3, "All in store"), 1e-9)
        // kilograms use each variety's own bag weight: 10 × 50 + 11.5 × 60
        assertEquals(10 * 50.0 + 11.5 * 60.0, cell(3, "Used kg till date"), 1e-6)
    }

    @Test fun dailySummaryCountsAndRunningTotals() {
        val days = listOf(
            day(0, w1 = 4000.0, n1 = 100),
            day(1, mort = 5, used = "B1=1.0"),
            day(2, mort = 3, used = "B1=1.5", w1 = 8000.0, n1 = 100))
        val c = SheetSchema.Content(farm, null, feeds, listOf(flock), days, emptyList())
        val t = SheetReports.dailySummary(c)
        val h = t[0].map { it.toString() }
        fun cell(day: Int, col: String) = t[day + 1][h.indexOf(col)]
        assertEquals(990, cell(0, "Live birds")); assertEquals(985, cell(1, "Live birds")); assertEquals(982, cell(2, "Live birds"))
        assertEquals(8, cell(2, "Deaths till date"))
        assertEquals(0.808, (cell(2, "Mortality % till date") as Number).toDouble(), 1e-9)       // 8 of 990
        assertEquals(80.0, (cell(2, "Average weight g") as Number).toDouble(), 1e-9)
        assertEquals("", cell(1, "Average weight g"))                                              // no weighing that day
        assertEquals(2.5, (cell(2, "Feed used bags till date") as Number).toDouble(), 1e-9)
        assertEquals(125.0, (cell(2, "Feed used kg till date") as Number).toDouble(), 1e-9)
        // feed per bird: day 2's 75 kg was eaten by the 985 birds alive the day before
        assertEquals(76.1, (cell(2, "Feed g per bird") as Number).toDouble(), 1e-9)
        // FCR = 125 kg ÷ (982 birds × 0.080 kg)
        assertEquals(1.591, (cell(2, "FCR") as Number).toDouble(), 1e-9)
    }
}
