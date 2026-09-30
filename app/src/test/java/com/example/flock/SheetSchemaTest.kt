package com.example.flock

import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FarmEntity
import com.example.flock.data.FlockEntity
import com.example.flock.data.TaskEntity
import com.example.flock.sync.SheetSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sheets from older app versions read into the right fields, merge newest-first, and rewrite to the current layout. */
class SheetSchemaTest {
    private val sid = "S1"
    private val farm = FarmEntity(spreadsheetId = sid, farmName = "Maa Tarini", farmId = "farm_1")

    private fun rawOf(blocks: Map<String, List<List<String>>>) = SheetSchema.RawFile(
        titles = SheetSchema.TABS.toSet(), meta = blocks["_Meta"], farm = blocks["_Farm"], config = blocks["_Config"],
        feedTypes = blocks["_FeedTypes"], flocks = blocks["Flocks"], days = blocks["DailyData"], tasks = blocks["Tasks"])

    @Test fun olderDailyLayoutReadsByHeaderName() {
        // an old build: columns in another order, some renamed, the last three missing, and a column the farm added
        val days = listOf(
            listOf("Day", "FlockId", "Date", "Deaths", "Feed Used", "Feed_Used_Type", "W1", "N1", "Notes", "UpdatedAt", "Vet visit"),
            listOf(9, "F1", "2026-09-28", 7, "12.5", "B1", 250, 50, "- litter turned", 1700000000000L, "yes")
        )
        val raw = SheetSchema.RawFile(titles = setOf("_Meta", "_Farm", "Flocks", "DailyData"),
            meta = listOf(listOf("Key", "Value"), listOf("app", "FlockIt"), listOf("schemaVersion", "2")),
            flocks = listOf(listOf("flockId", "name", "startDate", "birdsPlaced"), listOf("F1", "Batch 3", "2026-09-19", 15700)),
            days = days)
        val c = SheetSchema.parse(sid, raw, farm, null)
        val d = c.days.single()
        assertEquals("F1", d.flockId); assertEquals(9, d.dayNumber)
        assertEquals(7, d.mortality); assertEquals(12.5, d.feedBagsUsed, 1e-9); assertEquals("B1", d.feedUsedType)
        assertEquals(250.0, d.w1!!, 1e-9); assertEquals(50, d.n1); assertTrue(d.sampleEntered)
        assertEquals("- litter turned", d.notes); assertEquals(1700000000000L, d.updatedAt)
        assertEquals(15700, c.flocks.single().birdsPlaced)

        val reasons = SheetSchema.upgradeReasons(raw)
        assertTrue(reasons.any { it.startsWith("schema 2") })
        assertTrue(reasons.any { it.startsWith("missing tabs") })
        assertTrue(reasons.contains("DailyData columns differ"))

        // rewritten: current columns first, the farm's own column kept at the right with its value
        val b = SheetSchema.blocks(c, SheetSchema.primaryMeta("farm_1", 2))
        val hdr = b["DailyData"]!![0]
        assertEquals(SheetSchema.DAILY_HEADERS, hdr.take(SheetSchema.DAILY_HEADERS.size))
        assertEquals("Vet visit", hdr.last())
        val row = b["DailyData"]!![1]
        assertEquals("7", row[SheetSchema.DAILY_HEADERS.indexOf("Mortality")])
        assertEquals("12.5", row[SheetSchema.DAILY_HEADERS.indexOf("FeedBagsUsed")])
        assertEquals("yes", row.last())
    }

    @Test fun v33LayoutMissingNewColumnsStillReads() {
        val old = SheetSchema.DAILY_HEADERS.take(47)   // before Committed / FeedUsedBreakdown / SavedFields
        val row = old.map { h -> when (h) { "FlockId" -> "F1"; "Day" -> "4"; "Mortality" -> "3"; "UpdatedAt" -> "10"; else -> "" } }
        val c = SheetSchema.parse(sid, SheetSchema.RawFile(titles = setOf("DailyData"), days = listOf(old, row)), farm, null)
        assertEquals(3, c.days.single().mortality)
        assertFalse(c.days.single().committed)
    }

    @Test fun headerlessTabReadsInCurrentOrder() {
        val d = DailyDataEntity(spreadsheetId = sid, flockId = "F1", dayNumber = 2, date = "2026-09-21", mortality = 5, updatedAt = 42)
        val c = SheetSchema.parse(sid, SheetSchema.RawFile(titles = setOf("DailyData"), days = listOf(SheetSchema.dayRow(d))), farm, null)
        assertEquals(5, c.days.single().mortality)
        assertTrue(SheetSchema.upgradeReasons(SheetSchema.RawFile(titles = SheetSchema.TABS.toSet(), days = listOf(SheetSchema.dayRow(d))))
            .contains("DailyData has no header row"))
    }

    @Test fun farmKeysAnyCaseAndOlderNames() {
        val kv = listOf(listOf("Key", "Value"), listOf("FarmName", "Hill Farm"), listOf("pansperfeederline", "112"), listOf("feederLineBags", "3"))
        val f = SheetSchema.kvToFarm(sid, kv, farm)
        assertEquals("Hill Farm", f.farmName); assertEquals(112, f.pansPerFeederLine); assertEquals(3.0, f.lineFillBags, 1e-9)
    }

    @Test fun mergeKeepsNewestRowAndRecoversFromBackups() {
        fun day(n: Int, t: Long, m: Int, dirty: Boolean = false) = DailyDataEntity(spreadsheetId = sid, flockId = "F1", dayNumber = n, date = "", mortality = m, updatedAt = t, dirty = dirty)
        val f1 = FlockEntity(spreadsheetId = sid, flockId = "F1", name = "Batch 3", startDate = "2026-09-19", birdsPlaced = 15700, createdAt = 1)
        val f2 = FlockEntity(spreadsheetId = sid, flockId = "F2", name = "Batch 2", startDate = "2026-07-01", birdsPlaced = 15000, createdAt = 0)
        val live = SheetSchema.Content(farm, null, emptyList(), listOf(f1), listOf(day(5, 100, 1)),
            listOf(TaskEntity(spreadsheetId = sid, taskId = "t1", flockId = "F1", block = "Morning", label = "Drinkers", time = "06:00")))
        val phone = SheetSchema.Content(farm, null, emptyList(), listOf(f1), listOf(day(5, 150, 2), day(7, 300, 9, dirty = true)), emptyList())
        val backup = SheetSchema.Content(farm, null, emptyList(), listOf(f1, f2), listOf(day(5, 200, 3), day(6, 50, 4)),
            listOf(TaskEntity(spreadsheetId = sid, taskId = "t_old", flockId = "F1", block = "Night", label = "Deleted task", time = "22:00")))
        val m = SheetSchema.merge(live, phone, listOf(backup))
        val byDay = m.content.days.associateBy { it.dayNumber }
        assertEquals(3, byDay[5]!!.mortality)      // the backup's row was edited last
        assertEquals(4, byDay[6]!!.mortality)      // only the backup had day 6
        assertEquals(9, byDay[7]!!.mortality)      // not yet sent from the phone
        assertFalse(byDay[7]!!.dirty)
        assertEquals(2, m.recoveredDays); assertEquals(1, m.fromPhone)
        assertEquals(setOf("F1", "F2"), m.content.flocks.map { it.flockId }.toSet()); assertEquals(1, m.recoveredFlocks)
        assertEquals(listOf("t1"), m.content.tasks.map { it.taskId })   // a task deleted from the sheet is not brought back
    }

    @Test fun rewriteRoundTripsAndVerifies() {
        val flock = FlockEntity(spreadsheetId = sid, flockId = "F1", name = "Batch 3", startDate = "2026-09-19", birdsPlaced = 15700, deletedAt = 0)
        val days = (0..11).map { DailyDataEntity(spreadsheetId = sid, flockId = "F1", dayNumber = it, date = "d$it", mortality = it, feedBagsUsed = it * 1.5, updatedAt = it.toLong()) }
        val c = SheetSchema.Content(farm, null, emptyList(), listOf(flock), days, emptyList())
        val blocks = SheetSchema.blocks(c, SheetSchema.primaryMeta("farm_1", 2))
        val back = rawOf(blocks)
        assertTrue(SheetSchema.upgradeReasons(back).isEmpty())
        assertTrue(SheetSchema.verify(c, back).isEmpty())
        val again = SheetSchema.parse(sid, back, farm, null)
        assertEquals(days.map { it.mortality }, again.days.map { it.mortality })
        assertEquals(days.map { it.feedBagsUsed }, again.days.map { it.feedBagsUsed })
        // a read-back that lost a row is caught
        val short = blocks.toMutableMap().also { it["DailyData"] = blocks["DailyData"]!!.dropLast(1) }
        assertTrue(SheetSchema.verify(c, rawOf(short)).any { it.contains("day row") })
    }

    @Test fun columnLetters() {
        assertEquals(listOf("A", "Z", "AA", "AZ", "BA", "AAA"), listOf(1, 26, 27, 52, 53, 703).map { SheetSchema.colLetter(it) })
    }
}
