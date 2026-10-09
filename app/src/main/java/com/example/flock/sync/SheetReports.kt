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
    /** every value the app works out for a day, beside the entries it comes from */
    const val COMPUTED = "Computed"
    /** what the app projected for each day before the entry, the entry, and how far off it was */
    const val PROJECTIONS = "Projections"
    /** how each worked-out value is calculated and from which columns */
    const val FORMULAS = "Formulas"
    val TABS = listOf(FEED_LEDGER, DAILY_SUMMARY, COMPUTED, PROJECTIONS, FORMULAS)

    /** Every report tab's block. */
    fun all(c: SheetSchema.Content): LinkedHashMap<String, List<List<Any>>> = linkedMapOf(
        FEED_LEDGER to feedLedger(c), DAILY_SUMMARY to dailySummary(c), COMPUTED to computed(c), PROJECTIONS to projections(c), FORMULAS to formulas())

    private fun r(v: Double, places: Int = 2): Double { val f = Math.pow(10.0, places.toDouble()); return Math.round(v * f) / f }

    private fun received(d: DailyDataEntity): Map<String, Double> = buildMap {
        listOf(d.feedTypeB1 to d.feedRecB1, d.feedTypeB2 to d.feedRecB2, d.feedTypeB3 to d.feedRecB3).forEach { (code, bags) ->
            if (bags > 0 && code.isNotBlank()) put(code, (get(code) ?: 0.0) + bags)
        }
    }

    private fun hasInput(d: DailyDataEntity) = com.example.flock.data.FlockCalc.hasInput(d)

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

    // ------------------------------------------------------------------ worked-out values, projections, formulas

    private fun o(v: Double?, places: Int = 2): Any = if (v == null || v.isNaN() || v.isInfinite()) "" else r(v, places)
    private fun kgOf(c: SheetSchema.Content) = c.feedTypes.associate { it.code to it.bagKg }
    private fun bagKg(c: SheetSchema.Content) = if (c.farm.feedBagKg > 0) c.farm.feedBagKg else 50.0

    /** A flock's days with everything worked out, exactly as the phone does it. */
    private fun worked(c: SheetSchema.Content, f: FlockEntity): List<DailyDataEntity> {
        val rows = c.days.filter { it.flockId == f.flockId }.sortedBy { it.dayNumber }
        if (rows.isEmpty()) return emptyList()
        val cfg = c.config ?: com.example.flock.data.ConfigEntity(spreadsheetId = c.farm.spreadsheetId)
        return com.example.flock.data.FlockCalc.compute(f, c.farm, cfg, rows, kgOf(c))
    }

    /**
     * Every value the app works out for a day (to the last day entered), beside the entries it is worked
     * out from. The [formulas] tab says how each one is calculated.
     */
    fun computed(c: SheetSchema.Content): List<List<Any>> {
        val header = listOf("FlockId", "Flock", "Day", "Date",
            // entered
            "Birds weighed", "Weight of them g", "Deaths", "Culls", "Lifted", "Feed entered bags", "Feed entered kg",
            // birds
            "Live birds", "Deaths till date", "Mortality %", "Livability %",
            // weight
            "Weight g", "Weight is", "Weight age days", "Ideal weight g", "Commercial weight g", "Days ahead of commercial",
            "Gain g a day", "CV % (birds weighed singly)", "CV % (estimated from groups)", "Spread between locations %",
            // feed
            "Feed till date kg", "Feed till date g per bird", "FCR", "cFCR", "EPEF",
            "Feed plan g per bird", "Feed plan kg", "Feed plan bags", "Commercial feed g per bird", "Ideal feed g per bird",
            "Feed store bags",
            // water, house
            "Water plan mL per bird", "Water plan L", "Tank refills",
            "House temp min C", "House temp ideal C", "House temp max C", "Humidity ideal %",
            "Min air cfm per bird", "Min vent fans", "Min vent on s", "Min vent off s", "Light hours",
            "Density kg per m2", "Floor ft2 per bird", "Floor in use ft2")
        val out = mutableListOf<List<Any>>(header)
        val bag = bagKg(c); val kg = kgOf(c)
        val std = com.example.flock.engine.CompanyStandard
        for (f in c.flocks) {
            val rows = worked(c, f)
            val last = com.example.flock.data.FlockCalc.lastEnteredDay(rows)
            var cumKg = 0.0; var prevW: Pair<Int, Double>? = null
            for (d in rows) {
                if (d.dayNumber > last) break
                val usedKg = com.example.flock.data.FlockCalc.usedKg(d, kg, bag)
                cumKg += usedKg
                val samples = d.filledSamples()
                val singles = PhysiologicalEngine.parseWeights(d.indivWeights)
                val w = d.avgWeight ?: PhysiologicalEngine.bwFromDay(d.weightAge, f.breed)
                val gain = d.avgWeight?.let { now -> prevW?.let { (pd, pw) -> (now - pw) / (d.dayNumber - pd) } }
                if (d.avgWeight != null) prevW = d.dayNumber to d.avgWeight
                val fcr = d.fcr; val liv = d.livability
                val epef = if (d.dayNumber >= 7 && fcr != null && fcr > 0 && liv != null) liv * (w / 1000) * 100 / (d.dayNumber * fcr) else null
                out += listOf<Any>(
                    f.flockId, f.name, d.dayNumber, d.date,
                    samples.sumOf { it.second } + singles.size, r(samples.sumOf { it.first } + singles.sum(), 1), d.mortality, d.lameSeparated, d.birdsLifted,
                    r(d.feedBagsUsed), r(usedKg, 1),
                    d.liveBirds, d.cumMort, o(d.cumMortPct, 3), o(d.livability, 3),
                    r(w, 1), if (d.avgWeight != null) "measured" else "projected", r(d.weightAge, 2), r(d.idealWeight, 1), o(std.bw(d.dayNumber), 1),
                    r(std.ageForWeight(w) - d.dayNumber, 2),
                    o(gain, 1), o(d.cv, 2), o(com.example.flock.data.FlockCalc.cvEstimate(rows, d.dayNumber)?.takeIf { samples.size >= 3 }, 1), o(d.locSpreadPct, 2),
                    r(cumKg, 1), if (d.liveBirds > 0 && cumKg > 0) r(cumKg * 1000 / d.liveBirds, 1) else "", o(d.fcr, 3), o(d.cFcr, 3), o(epef, 1),
                    r(d.feedPerBird, 1), r(d.totalFeedKg, 1), d.feedBags, o(std.feedPerDay(std.duringDay(d.dayNumber)), 1),
                    r(PhysiologicalEngine.dailyFeedFromDay((d.dayNumber + 1).toDouble(), f.breed), 1),
                    r(d.stockOnHand),
                    r(d.waterPerBird, 1), r(d.totalWaterL, 1), d.tankRefills,
                    r(d.tempMin, 1), r(d.tempIdeal, 1), r(d.tempMax, 1), r(d.rhIdeal, 1),
                    r(d.cfmPerBird, 3), d.fansToRun, d.fanOnSec, d.fanOffSec, r(d.lightHours, 1),
                    o(d.densityKgM2, 2), r(d.ftPerBird, 3), r(d.occupiedFt2, 0)
                )
            }
        }
        return out
    }

    /**
     * For every day: what the app projected before the day's entry (from the days before it only), what was
     * then entered, and the miss in %. Days after the last entry carry the forecast only.
     */
    fun projections(c: SheetSchema.Content): List<List<Any>> {
        val items = listOf("Weight g", "Feed bags", "Feed g per bird", "Feed till date g per bird", "FCR", "Deaths", "Mortality %", "Live birds")
        val header = listOf("FlockId", "Flock", "Day", "Date") + items.flatMap { listOf("$it projected", "$it entered", "$it off %") }
        val out = mutableListOf<List<Any>>(header)
        val bag = bagKg(c); val kg = kgOf(c)
        for (f in c.flocks) {
            val rows = worked(c, f)
            if (rows.isEmpty()) continue
            val last = com.example.flock.data.FlockCalc.lastEnteredDay(rows)
            val by = rows.associateBy { it.dayNumber }
            val proj = com.example.flock.data.FlockCalc.projections(f, c.farm, rows, kg)
            var cumKg = 0.0
            for (p in proj) {
                val d = by[p.day] ?: continue
                val usedKg = com.example.flock.data.FlockCalc.usedKg(d, kg, bag)
                cumKg += usedKg
                if (p.day < 1) continue
                val known = p.day <= last
                val liveY = by[p.day - 1]?.liveBirds ?: 0
                fun trio(pv: Double?, ev: Double?, places: Int): List<Any> = listOf(
                    o(pv, places), o(ev, places), if (pv != null && ev != null && ev != 0.0) r((pv - ev) / ev * 100, 1) else "")
                val fed = usedKg > 0 && known
                val row = mutableListOf<Any>(f.flockId, f.name, p.day, d.date)
                row.addAll(trio(p.weightG, d.avgWeight, 1))
                row.addAll(trio(p.feedKg?.let { it / bag }, if (fed) usedKg / bag else null, 2))
                row.addAll(trio(p.feedPerBirdG, if (fed && liveY > 0) usedKg * 1000 / liveY else null, 1))
                row.addAll(trio(p.cumFeedPerBirdG, if (fed && d.liveBirds > 0) cumKg * 1000 / d.liveBirds else null, 1))
                row.addAll(trio(p.fcr, if (fed && d.avgWeight != null) d.fcr else null, 3))
                val mortIn = known && ("M" in d.savedFields.split(",").map { it.trim() } || d.mortality > 0 || p.day < last)
                row.addAll(trio(p.deaths, if (mortIn) d.mortality.toDouble() else null, 1))
                row.addAll(trio(p.cumMortPct, if (mortIn) d.cumMortPct else null, 3))
                row.addAll(trio(p.live, if (mortIn) d.liveBirds.toDouble() else null, 0))
                out += row
            }
        }
        return out
    }

    /** How every worked-out value is calculated and which entries it comes from (the same text for every farm). */
    fun formulas(): List<List<Any>> = listOf(
        listOf("Value", "Unit", "How it is worked out", "Worked out from"),
        listOf("Live birds", "birds", "Birds placed − reception deaths − deaths, culls and birds lifted till date", "Flocks: birdsPlaced, receptionMort · DailyData: Mortality, LameSeparated, BirdsLifted"),
        listOf("Mortality %", "%", "Deaths till date ÷ (birds placed − reception deaths) × 100", "Mortality · birdsPlaced, receptionMort"),
        listOf("Livability %", "%", "Live birds ÷ (birds placed − reception deaths) × 100", "Live birds"),
        listOf("Weight g (measured)", "g", "Total weight of every location ÷ birds weighed (birds weighed one by one are added in)", "W1…W12, N1…N12, IndividualWeights"),
        listOf("Weight g (projected)", "g", "The last weighing carried along the breed curve: its weight age + the days since", "last weighing · breed curve (Ross 308 / Cobb 500)"),
        listOf("Weight age days", "days", "The age at which the breed curve has the flock's weight", "Weight g · breed curve"),
        listOf("Days ahead of commercial", "days", "The age at which the company chart has the flock's weight − the flock's day (+ ahead, − behind)", "Weight g · company chart"),
        listOf("Gain g a day", "g/day", "(This weighing − the weighing before) ÷ days between them", "Weight g of two weighings"),
        listOf("CV % (birds weighed singly)", "%", "Standard deviation ÷ average × 100 of birds weighed one by one (10 or more)", "IndividualWeights"),
        listOf("CV % (estimated from groups)", "%", "Spread between the location averages × √(birds per location), pooled over the last 3 weighings. Rough: a light or heavy corner of the house counts too", "W1…W12, N1…N12"),
        listOf("Feed till date kg", "kg", "Sum of the bags entered × that variety's bag weight", "Used B1 / B2 / B3 · _FeedTypes: bagKg"),
        listOf("Feed g per bird (a day)", "g", "Feed entered on a day × 1,000 ÷ the birds alive the day before (the entry is what they ate the day before)", "Feed entered kg · Live birds"),
        listOf("FCR", "", "Feed till date kg ÷ (live birds × weight kg)", "Feed till date · Live birds · Weight g"),
        listOf("cFCR", "", "FCR + (2 − weight kg) × 0.25  (FCR brought to a 2 kg bird)", "FCR · Weight g · _Config: cFcrDivisor"),
        listOf("EPEF", "", "Livability % × weight kg × 100 ÷ (day × FCR), from day 7", "Livability · Weight g · FCR"),
        listOf("Feed plan g per bird", "g", "The company chart's feed for a bird of the flock's weight (the chart day after the one that has that weight)", "Weight g · company chart"),
        listOf("Feed plan kg / bags", "kg, bags", "Feed plan g per bird × live birds ÷ 1,000; bags = that ÷ bag weight, rounded up", "Feed plan g per bird · Live birds · feedBagKg"),
        listOf("Feed store bags", "bags", "Bags received till date − bags used till date, per variety and together (see FeedLedger)", "FeedRecB1…B3 · Used B1…B3"),
        listOf("Water plan", "mL, L", "Feed plan g per bird × water-to-feed ratio; × live birds for the house", "Feed plan · _Config: wfRatio"),
        listOf("Tank refills", "", "Water plan L ÷ tank size × the farm's refill factor, rounded up", "Water plan L · _Farm: drinkTankL, waterRefillFactor"),
        listOf("House temp ideal C", "°C", "The comfort temperature for a bird of the flock's weight (Aviagen table); min and max = ± the temperature band", "Weight g · _Config: tempBand"),
        listOf("Min air cfm per bird", "cfm", "Ross minimum-ventilation rate for the flock's weight × 1.3 air-quality margin × the farm's factor", "Weight g · _Farm: minVentFactor"),
        listOf("Min vent fans / on / off", "s", "The first step of the fan ladder that delivers the minimum air for the house", "Min air · Live birds · _Farm: fanCount, fanRatedCfm, fanDerate"),
        listOf("Light hours", "h", "23 h for two days, down to 18 h by day 7, 18 h through the grow-out, back to 23 h over the last week", "Day · Flocks: harvestAge"),
        listOf("Density kg per m2", "kg/m²", "Live birds × weight kg ÷ floor area", "Live birds · Weight g · _Farm: usableLengthFt, usableWidthFt"),
        listOf("Floor in use ft2", "ft²", "30 % of the house on day 0, a little more every day, all of it from day 11", "Day · floor area"),
        listOf("Projected weight (Projections)", "g", "The weighing before, carried along the breed curve to the day", "weighings of the days before"),
        listOf("Projected feed (Projections)", "bags", "Feed plan of the day before × the flock's appetite so far − feed still in the lines (Kalman filter on the entries from day 6)", "Feed plan · feed entered on the days before"),
        listOf("Projected FCR (Projections)", "", "(Feed till the day before + projected feed) ÷ (projected live birds × projected weight)", "the three projections above"),
        listOf("Projected deaths (Projections)", "birds", "Birds alive the day before × the flock's own recent daily mortality (newer days count more)", "Mortality of the days before"),
        listOf("Off %", "%", "(Projected − entered) ÷ entered × 100: + the app projected too high, − too low", "the projected and the entered value")
    )
}
