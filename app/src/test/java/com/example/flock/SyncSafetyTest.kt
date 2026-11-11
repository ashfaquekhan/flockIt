package com.example.flock

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FlockDatabase
import com.example.flock.data.FlockRepository
import com.example.flock.sync.RetryInterceptor
import com.example.flock.sync.SheetSchema
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * v55, keeping the sheet right with two phones and a poor network: Google busy is asked again after a moment (but nothing
 * that could happen twice is repeated blindly), and a value another phone changed meanwhile is never overwritten — the
 * farm chooses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncSafetyTest {

    // ------------------------------------------------------------------ retries

    private class Fake(val answers: MutableList<Any>) {
        var calls = 0
        val slept = mutableListOf<Long>()
        val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(RetryInterceptor(sleep = { slept += it }))
            .addInterceptor { chain ->
                calls++
                when (val a = answers.removeAt(0)) {
                    is Int -> resp(chain.request(), a, null)
                    is Pair<*, *> -> resp(chain.request(), a.first as Int, a.second as String)
                    is IOException -> throw a
                    else -> error("no answer")
                }
            }.build()
        private fun resp(r: Request, code: Int, retryAfter: String?) = Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(code).message("m")
            .apply { if (retryAfter != null) header("Retry-After", retryAfter) }.body("{}".toResponseBody(null)).build()
    }

    private val read = Request.Builder().url("https://sheets.googleapis.com/v4/spreadsheets/S/values:batchGet?ranges=A1").build()
    private val append = Request.Builder().url("https://sheets.googleapis.com/v4/spreadsheets/S/values/DailyData!A1:append")
        .post("{}".toRequestBody(null)).build()
    private val write = Request.Builder().url("https://sheets.googleapis.com/v4/spreadsheets/S/values:batchUpdate").post("{}".toRequestBody(null)).build()

    @Test fun googleBusyIsAskedAgainAfterAMoment() {
        val f = Fake(mutableListOf(503, 503, 200))
        assertEquals(200, f.client.newCall(read).execute().code)
        assertEquals(3, f.calls); assertEquals(listOf(1_000L, 2_000L), f.slept)
        // as long as Google asks, at most 20 s
        val g = Fake(mutableListOf(429 to "5", 200))
        assertEquals(200, g.client.newCall(write).execute().code)
        assertEquals(listOf(5_000L), g.slept)
        // always busy: three tries more, then the answer is passed on (the row stays unsent, sent later)
        val h = Fake(mutableListOf(503, 503, 503, 503))
        assertEquals(503, h.client.newCall(read).execute().code)
        assertEquals(4, h.calls)
    }

    @Test fun nothingThatCouldHappenTwiceIsRepeatedBlindly() {
        // a server fault while adding rows: maybe it went through — not repeated
        val f = Fake(mutableListOf(500))
        assertEquals(500, f.client.newCall(append).execute().code)
        assertEquals(1, f.calls)
        // but "busy" means nothing was done: asked again
        val g = Fake(mutableListOf(429, 200))
        assertEquals(200, g.client.newCall(append).execute().code)
        // a timeout: a read or a cell write is repeated, adding rows is not
        val h = Fake(mutableListOf(SocketTimeoutException("slow"), 200))
        assertEquals(200, h.client.newCall(write).execute().code)
        val k = Fake(mutableListOf(SocketTimeoutException("slow"), 200))
        assertTrue(runCatching { k.client.newCall(append).execute() }.exceptionOrNull() is SocketTimeoutException)
        assertEquals(1, k.calls)
        // no network at all: no waiting — the background sender takes it when the network is back
        val n = Fake(mutableListOf(UnknownHostException("offline"), 200))
        assertTrue(runCatching { n.client.newCall(read).execute() }.exceptionOrNull() is UnknownHostException)
        assertEquals(1, n.calls); assertTrue(n.slept.isEmpty())
    }

    // ------------------------------------------------------------------ two phones, one value

    private val sid = "S1"
    private fun row(deaths: Int, feed: Double = 9.5) = DailyDataEntity(spreadsheetId = sid, flockId = "F1", dayNumber = 12, date = "2026-11-05",
        mortality = deaths, feedBagsUsed = feed, feedUsedType = "B2")

    @Test fun aValueTheOtherPhoneChangedIsAClash() {
        val loaded = row(10)
        // this phone changes deaths 10 → 14; what it started from is kept
        val mine = row(14).copy(dirty = true, dirtyFields = "mortality", dirtyBase = SheetSchema.addBase(loaded, setOf("mortality")))
        val base = SheetSchema.decodeCells(mine.dirtyBase)
        assertEquals("10", base["mortality"])
        val header = SheetSchema.dailyHeader(listOf("B2"), 5)
        fun sheetWith(r: DailyDataEntity) = SheetSchema.dayCells(r, header)
        // the other phone wrote 12 meanwhile: a clash, the sheet's value kept for the choice
        assertEquals(mapOf("mortality" to "12"), SheetSchema.findConflicts(header, sheetWith(row(12)), mine, setOf("mortality"), base))
        // the sheet still has what this phone started from, or already the same as this phone: no clash
        assertTrue(SheetSchema.findConflicts(header, sheetWith(row(10)), mine, setOf("mortality"), base).isEmpty())
        assertTrue(SheetSchema.findConflicts(header, sheetWith(row(14)), mine, setOf("mortality"), base).isEmpty())
        // another column the other phone changed is not this phone's business: no clash
        assertTrue(SheetSchema.findConflicts(header, sheetWith(row(10, feed = 11.0)), mine, setOf("mortality"), base).isEmpty())
        // the same number written another way is the same value
        assertTrue(SheetSchema.sameCell("9.5", "9.500000001")); assertFalse(SheetSchema.sameCell("9.5", "9.6"))
        // a row saved before v55 has no record of what it started from: written as before, no check
        assertTrue(SheetSchema.findConflicts(header, sheetWith(row(12)), mine, setOf("mortality"), emptyMap()).isEmpty())
        assertEquals("Deaths", SheetSchema.columnLabel("mortality")); assertEquals("Weight, location 3", SheetSchema.columnLabel("w3"))
    }

    @Test fun theFarmChoosesWhichToKeep() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, FlockDatabase::class.java).allowMainThreadQueries().build()
        val repo = FlockRepository(db)
        val dao = db.dailyDataDao()
        fun clashRow(day: Int) = row(14).copy(dayNumber = day, dirty = true, dirtyFields = "mortality,notes",
            dirtyBase = SheetSchema.encodeCells(mapOf("mortality" to "10")), conflicts = SheetSchema.encodeCells(mapOf("mortality" to "12")), notes = "- litter turned")
        dao.insertOrUpdateDay(clashRow(12)); dao.insertOrUpdateDay(clashRow(13))
        // shown as one clash a day, both values
        val shown = com.example.flock.ui.clashesOf(dao.getDailyDataList(sid, "F1"))
        assertEquals(2, shown.size); assertEquals("14", shown[0].mine); assertEquals("12", shown[0].theirs)
        // the sheet's: taken, this phone's change to it dropped (the notes still go)
        repo.resolveConflict(sid, "F1", 12, "mortality", keepMine = false)
        val a = dao.getDayEntry(sid, "F1", 12)!!
        assertEquals(12, a.mortality); assertEquals("", a.conflicts); assertEquals("notes", a.dirtyFields); assertTrue(a.dirty)
        assertEquals("- litter turned", a.notes)
        // this phone's: kept, and the next send writes it (the sheet's value is now what it started from)
        repo.resolveConflict(sid, "F1", 13, "mortality", keepMine = true)
        val b = dao.getDayEntry(sid, "F1", 13)!!
        assertEquals(14, b.mortality); assertEquals("", b.conflicts); assertEquals("12", SheetSchema.decodeCells(b.dirtyBase)["mortality"]); assertTrue(b.dirty)
        db.close()
    }

    /** v57: the lifting is saved on its own, at its own time; the morning weighing keeps its time and its weights. */
    @Test fun theLiftingKeepsItsOwnTime() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, FlockDatabase::class.java).allowMainThreadQueries().build()
        val repo = FlockRepository(db)
        val dao = db.dailyDataDao()
        val zone = java.time.ZoneId.of("Asia/Kolkata")
        val start = java.time.LocalDate.now(zone).minusDays(20)
        val flock = com.example.flock.data.FlockEntity(spreadsheetId = sid, flockId = "F1", name = "T", startDate = start.toString(), birdsPlaced = 5000, harvestAge = 0)
        db.flockDao().insertFlock(flock)
        dao.insertDailyData((0..20).map { d -> DailyDataEntity(spreadsheetId = sid, flockId = "F1", dayNumber = d, date = start.plusDays(d.toLong()).toString()) })
        fun at(h: Int, m: Int) = start.plusDays(12).atTime(h, m).atZone(zone).toInstant().toEpochMilli()
        val morning = at(8, 15); val evening = at(20, 30)
        // the morning: weights and deaths
        repo.saveDayEntry(sid, "F1", 12, "a@b", setOf("M"), morning) { it.copy(w1 = 6000.0, n1 = 10, mortality = 3) }.getOrThrow()
        assertEquals(morning, dao.getDayEntry(sid, "F1", 12)!!.weighedAt)
        // the evening: the lifting, on its own — the weighing and its time stay
        repo.saveLifting(sid, "F1", 12, 1200, 3000.0, evening, "a@b").getOrThrow()
        val l = dao.getDayEntry(sid, "F1", 12)!!
        assertEquals(morning, l.weighedAt); assertEquals(6000.0, l.w1!!, 0.0); assertEquals(3, l.mortality)
        assertEquals(evening, l.liftedAt); assertEquals(1200, l.birdsLifted); assertEquals(3000.0, l.weightLifted, 0.0)
        assertTrue("liftedat" in l.dirtyFields.split(","))
        // the event log: the weighing at 08:15, the lifting at 20:30, both entered now
        val evs = db.flockEventDao().events(sid, "F1")
        assertEquals(morning, evs.first { it.kind == "weighing" }.happenedAt)
        assertEquals(evening, evs.first { it.kind == "lifting" }.happenedAt)
        assertTrue(evs.first { it.kind == "lifting" }.enteredAt >= evening)
        assertEquals("3", com.example.flock.graph.FlockEvents.map(evs.first { it.kind == "deaths" }.newValues)["mortality"])
        assertTrue(evs.any { it.kind == "placed" } || evs.none { it.kind == "placed" })
        // the house: the morning's birds until 20:30, the birds left from then
        val rows = dao.getDailyDataList(sid, "F1").sortedBy { it.dayNumber }
        val od = com.example.flock.ui.screens.OutputData(flock, com.example.flock.data.FarmEntity(spreadsheetId = sid), rows.first { it.dayNumber == 12 }, rows,
            emptyList(), null, emptyList(), false, false)
        assertEquals(20.5, od.liftHour!!, 1e-9)
        assertFalse(od.liftedBy(20.0)); assertTrue(od.liftedBy(20.5))
        assertEquals(600.0, od.bw, 1e-9)
        // the morning's entries updated again (their time as first saved): the lifting keeps its time and birds
        repo.saveDayEntry(sid, "F1", 12, "a@b", setOf("M"), morning) { it.copy(mortality = 4) }.getOrThrow()
        dao.getDayEntry(sid, "F1", 12)!!.let { assertEquals(evening, it.liftedAt); assertEquals(1200, it.birdsLifted); assertEquals(morning, it.weighedAt) }
        // a lifting kept before its time was (no time): another save of the day does not give it the morning's
        dao.insertOrUpdateDay(dao.getDayEntry(sid, "F1", 12)!!.copy(liftedAt = 0L))
        repo.saveDayEntry(sid, "F1", 12, "a@b", setOf("M"), morning) { it.copy(mortality = 5) }.getOrThrow()
        assertEquals(0L, dao.getDayEntry(sid, "F1", 12)!!.liftedAt)
        // v59: two trucks that evening, each with its time — the day's sums, the last one's time; the weighing as it was
        val two = listOf(com.example.flock.data.LiftLap("19:40", 700, 1750.0), com.example.flock.data.LiftLap("21:05", 500, 1300.0))
        repo.saveLiftLaps(sid, "F1", 12, two, "a@b").getOrThrow()
        dao.getDayEntry(sid, "F1", 12)!!.let {
            assertEquals(1200, it.birdsLifted); assertEquals(3050.0, it.weightLifted, 1e-9); assertEquals(at(21, 5), it.liftedAt)
            assertEquals("19:40=700:1750;21:05=500:1300", it.liftLaps); assertEquals(morning, it.weighedAt)
            val o = com.example.flock.ui.screens.OutputData(flock, com.example.flock.data.FarmEntity(spreadsheetId = sid), it, dao.getDailyDataList(sid, "F1").sortedBy { r -> r.dayNumber },
                emptyList(), null, emptyList(), false, false)
            // the house through the evening: nobody gone at 19:00, the first truck's birds at 20:00, all of them at 21:30
            assertEquals(0, o.liftedCountBy(19.0)); assertEquals(700, o.liftedCountBy(20.0)); assertEquals(1200, o.liftedCountBy(21.5))
            fun viewAt(h: Int, m: Int) = o.nowView(start.plusDays(12).atTime(h, m).atZone(zone))
            assertEquals((it.liveBirds + 1200).toDouble(), viewAt(19, 0).live, 1e-9)
            assertEquals((it.liveBirds + 500).toDouble(), viewAt(20, 0).live, 1e-9)
            assertEquals(it.liveBirds.toDouble(), viewAt(21, 30).live, 1e-9)
            // the birds' weight grows on from the weighing through the day
            assertTrue(viewAt(18, 0).weightG > viewAt(9, 0).weightG)
            // deaths today are a share of the birds the day began with
            assertEquals(it.liveBirds + 1200 + it.mortality, o.startLive)
        }
        // nothing lifted: the lifting and its time cleared
        repo.saveLifting(sid, "F1", 12, 0, 0.0, null, "a@b").getOrThrow()
        dao.getDayEntry(sid, "F1", 12)!!.let { assertEquals(0, it.birdsLifted); assertEquals(0L, it.liftedAt); assertEquals("", it.liftLaps); assertEquals(morning, it.weighedAt) }
        db.close()
    }
}
