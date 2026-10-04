package com.example.flock.sync

import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FlockEntity
import com.example.flock.data.filledSamples
import com.example.flock.engine.PhysiologicalEngine
import kotlin.math.max

/**
 * Report tabs of the farm spreadsheet. They are worked out from the input rows (Flocks, DailyData,
 * _FeedTypes) every time a day is saved or the sheet is repaired, and are never read back — so an edit
 * made in them is simply replaced, and a repair can always rebuild them. Pure functions, no Android.
 *
 *  - [FEED_LEDGER]: for every flock day, each feed variety's bags received and used that day, the running
 *    totals and what is left in store, then the same for all varieties together.
 *  - [DAILY_SUMMARY]: birds alive, deaths, culls, lifting, sample weight and feed — the day's figure and
 *    the running total — with feed per bird and FCR.
 */
object SheetReports {
    const val FEED_LEDGER = "FeedLedger"
    const val DAILY_SUMMARY = "DailySummary"
    val TABS = listOf(FEED_LEDGER, DAILY_SUMMARY)

    private fun r(v: Double, places: Int = 2): Double { val f = Math.pow(10.0, places.toDouble()); return Math.round(v * f) / f }

    private fun received(d: DailyDataEntity): Map<String, Double> = buildMap {
        listOf(d.feedTypeB1 to d.feedRecB1, d.feedTypeB2 to d.feedRecB2, d.feedTypeB3 to d.feedRecB3).forEach { (code, bags) ->
            if (bags > 0 && code.isNotBlank()) put(code, (get(code) ?: 0.0) + bags)
        }
    }

    private fun hasInput(d: DailyDataEntity) = d.mortality > 0 || d.feedBagsUsed > 0 || d.feedRecB1 > 0 || d.feedRecB2 > 0 || d.feedRecB3 > 0 ||
        d.birdsLifted > 0 || d.lameSeparated > 0 || d.dieselCansUsed > 0 || d.savedFields.isNotBlank() || d.committed ||
        d.filledSamples().isNotEmpty() || d.indivWeights.isNotBlank() || d.notes.isNotBlank()

    /** A flock's rows from day 0 to the last day anything was entered. */
    private fun activeDays(c: SheetSchema.Content, f: FlockEntity): List<DailyDataEntity> {
        val days = c.days.filter { it.flockId == f.flockId }.sortedBy { it.dayNumber }
        val last = days.lastOrNull { hasInput(it) }?.dayNumber ?: return emptyList()
        return days.filter { it.dayNumber <= last }
    }

    private fun codes(c: SheetSchema.Content): List<String> =
        (SheetSchema.usedCodes(c) + c.days.flatMap { received(it).keys }).filter { it.isNotBlank() }.distinct()

    fun feedLedger(c: SheetSchema.Content): List<List<Any>> {
        val codes = codes(c)
        val header = listOf("FlockId", "Flock", "Day", "Date") +
            codes.flatMap { listOf("$it received", "$it used", "$it received till date", "$it used till date", "$it in store") } +
            listOf("All received", "All used", "All received till date", "All used till date", "All in store", "Used kg", "Used kg till date")
        val kgOf = c.feedTypes.associate { it.code to it.bagKg }
        val bagKg = if (c.farm.feedBagKg > 0) c.farm.feedBagKg else 50.0
        val out = mutableListOf<List<Any>>(header)
        for (f in c.flocks) {
            val cumRec = HashMap<String, Double>(); val cumUsed = HashMap<String, Double>(); var cumKg = 0.0
            for (d in activeDays(c, f)) {
                val rec = received(d); val used = SheetSchema.usedSplit(d)
                rec.forEach { (k, v) -> cumRec[k] = (cumRec[k] ?: 0.0) + v }
                used.forEach { (k, v) -> cumUsed[k] = (cumUsed[k] ?: 0.0) + v }
                val kg = used.entries.sumOf { (k, v) -> v * (kgOf[k] ?: bagKg) }
                cumKg += kg
                val row = mutableListOf<Any>(f.flockId, f.name, d.dayNumber, d.date)
                for (code in codes) {
                    row += r(rec[code] ?: 0.0); row += r(used[code] ?: 0.0)
                    row += r(cumRec[code] ?: 0.0); row += r(cumUsed[code] ?: 0.0)
                    row += r((cumRec[code] ?: 0.0) - (cumUsed[code] ?: 0.0))
                }
                row += r(rec.values.sum()); row += r(used.values.sum())
                row += r(cumRec.values.sum()); row += r(cumUsed.values.sum()); row += r(cumRec.values.sum() - cumUsed.values.sum())
                row += r(kg, 1); row += r(cumKg, 1)
                out += row
            }
        }
        return out
    }

    fun dailySummary(c: SheetSchema.Content): List<List<Any>> {
        val header = listOf("FlockId", "Flock", "Day", "Date", "Live birds", "Deaths", "Deaths till date", "Mortality % till date",
            "Culls", "Culls till date", "Lifted", "Lifted till date", "Lifted kg till date",
            "Locations weighed", "Birds weighed", "Average weight g", "CV %",
            "Feed used bags", "Feed used bags till date", "Feed used kg", "Feed used kg till date",
            "Feed g per bird", "Feed g per bird till date", "FCR", "Diesel cans", "Diesel cans till date", "Saved")
        val kgOf = c.feedTypes.associate { it.code to it.bagKg }
        val bagKg = if (c.farm.feedBagKg > 0) c.farm.feedBagKg else 50.0
        val out = mutableListOf<List<Any>>(header)
        for (f in c.flocks) {
            val entry = max(0, f.birdsPlaced - f.receptionMort)
            var cumMort = 0; var cumCull = 0; var cumLift = 0; var cumLiftKg = 0.0
            var cumBags = 0.0; var cumKg = 0.0; var cumDiesel = 0.0
            var prevLive = entry
            for (d in activeDays(c, f)) {
                cumMort += d.mortality; cumCull += d.lameSeparated; cumLift += d.birdsLifted; cumLiftKg += d.weightLifted
                val used = SheetSchema.usedSplit(d)
                val bags = used.values.sum(); val kg = used.entries.sumOf { (k, v) -> v * (kgOf[k] ?: bagKg) }
                cumBags += bags; cumKg += kg; cumDiesel += d.dieselCansUsed
                val live = max(0, entry - cumMort - cumCull - cumLift)
                val samples = d.filledSamples()
                val res = PhysiologicalEngine.computeWeightSamples(
                    samples.map { PhysiologicalEngine.LocationSample(it.first, it.second) }, PhysiologicalEngine.parseWeights(d.indivWeights))
                val avg: Any = if (res.hasSample) r(res.flockAvgG, 1) else ""
                out += listOf<Any>(
                    f.flockId, f.name, d.dayNumber, d.date, live, d.mortality, cumMort,
                    if (entry > 0) r(cumMort * 100.0 / entry, 3) else "",
                    d.lameSeparated, cumCull, d.birdsLifted, cumLift, r(cumLiftKg, 1),
                    samples.size, res.totalWeighed, avg, res.birdCv?.let { r(it, 2) } ?: "",
                    r(bags), r(cumBags), r(kg, 1), r(cumKg, 1),
                    if (d.dayNumber >= 1 && prevLive > 0 && kg > 0) r(kg * 1000 / prevLive, 1) else "",
                    if (live > 0 && cumKg > 0) r(cumKg * 1000 / live, 1) else "",
                    if (res.hasSample && live > 0 && cumKg > 0) r(cumKg / (live * res.flockAvgG / 1000.0), 3) else "",
                    r(d.dieselCansUsed), r(cumDiesel), d.savedFields
                )
                prevLive = live
            }
        }
        return out
    }
}
