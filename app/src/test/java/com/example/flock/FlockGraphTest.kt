package com.example.flock

import com.example.flock.data.DailyDataEntity
import com.example.flock.data.lapsShown
import com.example.flock.graph.EventKind
import com.example.flock.graph.FlockEvents
import com.example.flock.graph.FlockGraph
import com.example.flock.sync.SheetSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v58: the property graph, the event kinds and how a save becomes events. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FlockGraphTest {

    @Test fun theGraphIsSound() {
        // every key known, no number worked out from itself (feedbacks that act later are marked so)
        assertEquals(emptyList<String>(), FlockGraph.check())
        // every event kind is a point of the map and changes what it says it changes
        EventKind.values().forEach { k -> assertTrue(k.key, FlockGraph.prop(k.nodeKey) != null) }
        assertTrue(FlockGraph.changes.any { it.from == "ev:lifting" && it.to == "liftTime" })
        // each worked-out number has something it comes from; each entered one has an event that sets it
        FlockGraph.props.filter { it.source == FlockGraph.Source.DERIVED }.forEach { p -> assertTrue(p.key, FlockGraph.inputsOf(p.key).isNotEmpty()) }
        FlockGraph.props.filter { it.source == FlockGraph.Source.ENTERED }.forEach { p -> assertTrue(p.key, FlockGraph.eventsOf(p.key).isNotEmpty()) }
        // the weight now goes back to the weighing, through the lifting
        assertTrue(FlockGraph.upstream("weightNow").containsAll(listOf("weight", "samples", "lifted", "liftedKg")))
        // FCR: more feed raises it, more meat in the house lowers it
        assertEquals(+1, FlockGraph.inputsOf("fcr").first { it.from == "feedTD" }.sign)
        assertEquals(-1, FlockGraph.inputsOf("fcr").first { it.from == "kgInHouse" }.sign)
        println("graph: ${FlockGraph.props.size} points, ${FlockGraph.depends.size} links, ${FlockGraph.relations.size} relations, ${FlockGraph.changes.size} from events")
    }

    @Test fun columnsBelongToEvents() {
        assertEquals(EventKind.WEIGHING, EventKind.ofColumn("w1"))
        assertEquals(EventKind.WEIGHING, EventKind.ofColumn("individualweights"))
        assertEquals(EventKind.DEATHS, EventKind.ofColumn("mortality"))
        assertEquals(EventKind.FEED_USED, EventKind.ofColumn("usedb1"))
        assertEquals(EventKind.FEED_RECEIVED, EventKind.ofColumn("feedrecb2"))
        assertEquals(EventKind.LIFTING, EventKind.ofColumn("liftedat"))
        assertEquals(EventKind.BARRICADES, EventKind.ofColumn("penstartft"))
        assertEquals(EventKind.WEATHER, EventKind.ofColumn("outtempmax"))
        assertEquals(EventKind.READING, EventKind.ofColumn("watertempc"))
        assertNull(EventKind.ofColumn("updatedat"))
        assertNull(EventKind.ofColumn("date"))
    }

    @Test fun aSaveBecomesEventsWithTheirOwnTimes() {
        val zone = java.time.ZoneId.of("Asia/Kolkata")
        fun at(h: Int) = java.time.LocalDate.of(2026, 11, 5).atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
        val before = DailyDataEntity(spreadsheetId = "S", flockId = "F", dayNumber = 12, date = "2026-11-05", mortality = 3)
        val after = before.copy(w1 = 6000.0, n1 = 10, weighedAt = at(8), mortality = 4, birdsLifted = 1200, weightLifted = 3000.0, liftedAt = at(20))
        val now = at(23)
        val evs = FlockEvents.fromSave(before, after, SheetSchema.changedColumns(before, after), "a@b", now, at(9))
        assertEquals(setOf("weighing", "deaths", "lifting"), evs.map { it.kind }.toSet())
        val w = evs.first { it.kind == "weighing" }; val d = evs.first { it.kind == "deaths" }; val l = evs.first { it.kind == "lifting" }
        // when it happened: the weighing's time, the lifting's time, the entries' time; entered: now
        assertEquals(at(8), w.happenedAt); assertEquals(at(20), l.happenedAt); assertEquals(at(9), d.happenedAt)
        assertTrue(evs.all { it.enteredAt == now && it.enteredBy == "a@b" && it.day == 12 })
        assertEquals("4", FlockEvents.map(d.newValues)["mortality"]); assertEquals("3", FlockEvents.map(d.oldValues)["mortality"])
        assertEquals("1200", FlockEvents.map(l.newValues)["birdslifted"])
        assertTrue(FlockEvents.summary(d), FlockEvents.summary(d).contains("(was 3)"))
        // nothing changed: no events
        assertTrue(FlockEvents.fromSave(after, after, emptySet(), "a@b", now).isEmpty())
        // the sheet's Events tab: written and read back the same
        val rows = listOf(SheetSchema.EVENT_HEADERS) + evs.map { SheetSchema.eventRow(it, zone) }
        val back = SheetSchema.parseEvents("S", rows)
        assertEquals(evs.map { it.copy(sent = true) }.sortedBy { it.id }, back.sortedBy { it.id })
        assertEquals("2026-11-05 20:00", SheetSchema.eventRow(l, zone)[0])
    }

    /** v59: the map's scenes — the flock's groups on a ring, a group's points on rings, a number's wiring. */
    @Test fun theMapsScenes() {
        val L = com.example.flock.ui.screens.StageLayout
        // the flock: every group once, the flock in the middle
        val home = L.of("center")
        assertEquals(FlockGraph.Group.values().size, home.count { !it.big }); assertEquals("center", home.single { it.big }.key)
        // a group: every one of its points once, on rings of no more than 7
        FlockGraph.Group.values().forEach { g ->
            val sc = L.of("group:" + g.name).filter { !it.big }
            assertEquals(g.name, FlockGraph.props.filter { it.group == g }.map { it.key }.toSet(), sc.map { it.key }.toSet())
            sc.groupBy { it.y }.values.forEach { ring -> assertTrue(ring.size <= L.PER_RING) }
            sc.forEach { p -> assertEquals(1.0, Math.hypot(p.x.toDouble(), p.z.toDouble()), 1e-4) }
        }
        // a number: what it comes from on the left, what it moves on the right, its events above; the strongest shown
        val w = L.of("weightNow")
        assertEquals("weightNow", w.single { it.big }.key)
        assertTrue(w.filter { it.x < 0 }.map { it.key }.containsAll(listOf("weight", "liftAvg")))
        assertTrue(w.filter { it.x > 0 }.map { it.key }.containsAll(listOf("density", "kgInHouse")))
        assertTrue(w.filter { it.x < 0 }.all { it.inward } && w.filter { it.x > 0 }.all { !it.inward })
        val lifted = L.of("lifted")
        assertTrue(lifted.any { it.key == "ev:lifting" && it.y < 0 })
        // a point with many links shows the strongest and says how many more there are
        val live = L.of("live").filter { it.x > 0 }
        assertTrue(live.size <= L.MAX_SIDE)
        assertEquals(FlockGraph.outputsOf("live").size - live.size, live.last().more)
        // an event in the middle: what it changes
        assertTrue(L.of("ev:lifting").map { it.key }.containsAll(listOf("lifted", "liftedKg", "liftTime")))
    }

    /** v59: a day's liftings one by one. */
    @Test fun liftLapsReadAndWrite() {
        val laps = listOf(com.example.flock.data.LiftLap("21:10", 800, 2100.5), com.example.flock.data.LiftLap("19:30", 1200, 3000.0))
        val text = com.example.flock.data.formatLiftLaps(laps)
        assertEquals("19:30=1200:3000;21:10=800:2100.5", text)
        assertEquals(laps.sortedBy { it.hhmm }, com.example.flock.data.parseLiftLaps(text))
        assertTrue(com.example.flock.data.parseLiftLaps("").isEmpty()); assertTrue(com.example.flock.data.parseLiftLaps("x=1:2;20:00=0:5").isEmpty())
        assertEquals(19.5, laps[1].hour, 1e-9)
        // a day lifted before laps were kept is one lifting at its time (20:00 when none was kept)
        val zone = java.time.ZoneId.of("Asia/Kolkata")
        val old = DailyDataEntity(spreadsheetId = "S", flockId = "F", dayNumber = 30, date = "2026-11-05", birdsLifted = 500, weightLifted = 1250.0)
        assertEquals(listOf(com.example.flock.data.LiftLap("20:00", 500, 1250.0)), old.lapsShown(zone))
        // the sheet keeps them
        val header = SheetSchema.dailyHeader(listOf("B2"), 5)
        assertTrue("LiftLaps" in header)
        assertEquals(text, SheetSchema.dayCells(old.copy(liftLaps = text), header)[header.indexOf("LiftLaps")])
        assertEquals(com.example.flock.graph.EventKind.LIFTING, com.example.flock.graph.EventKind.ofColumn("liftlaps"))
    }
}
