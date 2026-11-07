package com.example.flock

import com.example.flock.data.DailyDataEntity
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

    @Test fun theMapPlacesEveryPoint() {
        val m = com.example.flock.ui.screens.MindLayout.build()
        val leaves = m.nodes.filter { it.level == 2 }
        assertEquals(FlockGraph.props.size, leaves.size)
        assertEquals(FlockGraph.Group.values().size, m.nodes.count { it.level == 1 })
        assertEquals(leaves.size, leaves.map { it.key }.toSet().size)
        val c = com.example.flock.ui.screens.MindLayout.S / 2
        leaves.forEach { n -> assertTrue(n.key, Math.hypot((n.x - c).toDouble(), (n.y - c).toDouble()) < c * 0.7) }
    }
}
