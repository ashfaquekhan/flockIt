package com.example.flock.data

import com.example.flock.engine.CompanyStandard
import com.example.flock.engine.IbController
import com.example.flock.engine.PhysiologicalEngine
import kotlin.math.ceil
import kotlin.math.max

/**
 * Everything the app works out for a flock, day by day, from what was entered: birds alive, weight (measured
 * or carried on from the last weighing), the feed and water plan, FCR, density, the house targets and the
 * minimum ventilation. A pure function of the inputs — the phone's database and the sheet's report tabs both
 * use it, so they can never disagree.
 */
object FlockCalc {
    /** Bags of each feed variety entered for a day. */
    fun usedSplit(r: DailyDataEntity): Map<String, Double> {
        val b = parseFeedBreakdown(r.feedUsedBreakdown)
        if (b.isNotEmpty()) return b.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        return if (r.feedBagsUsed > 0) mapOf(r.feedUsedType.ifBlank { "B1" } to r.feedBagsUsed) else emptyMap()
    }

    /** Feed entered for a day, kg (each variety at its own bag weight). */
    fun usedKg(r: DailyDataEntity, kgOf: Map<String, Double>, bagKg: Double): Double =
        usedSplit(r).entries.sumOf { (code, bags) -> bags * (kgOf[code] ?: bagKg) }

    /** Anything was entered for the day. */
    fun hasInput(d: DailyDataEntity) = d.mortality > 0 || d.feedBagsUsed > 0 || d.feedRecB1 > 0 || d.feedRecB2 > 0 || d.feedRecB3 > 0 ||
        d.birdsLifted > 0 || d.lameSeparated > 0 || d.dieselCansUsed > 0 || d.savedFields.isNotBlank() || d.committed ||
        d.filledSamples().isNotEmpty() || d.indivWeights.isNotBlank() || d.notes.isNotBlank()

    /** The last day anything was entered (0 for a new flock). */
    fun lastEnteredDay(rows: List<DailyDataEntity>): Int = rows.filter { hasInput(it) }.maxOfOrNull { it.dayNumber } ?: 0

    /** How much the feed sitting in the lines varies from day to day (kg): about 40 % of what the lines hold. */
    fun carrySdKg(farm: FarmEntity): Double {
        val bag = if (farm.feedBagKg > 0) farm.feedBagKg else 50.0
        return (0.4 * max(1, farm.feederLines) * max(0.5, farm.lineFillBags) * bag).coerceAtLeast(bag)
    }

    /** A weight carried [days] along the breed curve from where it sits on it — how the app projects a day without a weighing. */
    fun grow(weightG: Double, days: Double, breed: String): Double =
        PhysiologicalEngine.bwFromDay(PhysiologicalEngine.weightAgeFromBW(weightG, breed) + days, breed)

    /**
     * What the app projected for every day before that day's entry (see [com.example.flock.domain.Projection.history]).
     * [rows] are the flock's days after [compute], in day order.
     */
    fun projections(flock: FlockEntity, farm: FarmEntity, rows: List<DailyDataEntity>, kgOf: Map<String, Double>): List<com.example.flock.domain.Projection.DayOut> {
        val bag = if (farm.feedBagKg > 0) farm.feedBagKg else 50.0
        val entry = (flock.birdsPlaced - flock.receptionMort).coerceAtLeast(0)
        return com.example.flock.domain.Projection.history(
            rows.map { com.example.flock.domain.Projection.DayIn(it.dayNumber, it.avgWeight, it.inHouse, it.mortality, usedKg(it, kgOf, bag), it.totalFeedKg) },
            entry, lastEnteredDay(rows), { w, d -> grow(w, d, flock.breed) }, PhysiologicalEngine.bwFromDay(0.0, flock.breed),
            { d -> CompanyStandard.dailyMortPct(CompanyStandard.duringDay(d)) }, carrySdKg(farm))
    }

    /** CV % estimated from group weighings, pooled over the last three weighings up to [day] (null: fewer than 3 groups). */
    fun cvEstimate(rows: List<DailyDataEntity>, day: Int): Double? =
        com.example.flock.domain.Uniformity.cvFromGroups(rows.filter { it.dayNumber <= day }.sortedBy { it.dayNumber }
            .map { r -> r.filledSamples().map { (total, n) -> total / n to n } }.filter { it.size >= 3 })

    /**
     * The flock's meat, day by day, once birds start to be lifted (sold): every kilo the flock has made is either
     * still in the house (live birds x their weight), gone on the trucks (the lifted kilos, as weighed), or dead
     * (deaths and culls x the weight on their day). FCR counts the first two (feed per kilo of bird produced, the
     * way the company reckons it); [fcrAll] counts the dead as well (feed per kilo the flock grew).
     */
    data class Mass(
        val day: Int,
        /** birds and kg lifted till date */
        val liftedBirds: Int, val liftedKg: Double,
        /** the day's lifting: birds, average kg (null: none) */
        val liftedToday: Int, val liftAvgKg: Double?,
        /** the birds' average as weighed (or projected) that day, grown on to the lifting, and of the birds left (kg); all the same on a day without lifting */
        val avgBeforeKg: Double, val avgAfterKg: Double,
        /** the live birds' mass now, the dead and culled birds' mass till date (estimated), kg */
        val liveKg: Double, val deadKg: Double,
        /** live + lifted: what the feed has produced, kg */
        val producedKg: Double,
        /** average of every bird produced, live and lifted (the company's "cumulative body weight"), kg */
        val cumBwKg: Double,
        val fcr: Double?, val fcrAll: Double?,
        /** the company's cFCR: FCR + (2 - cumulative body weight) x 0.25 */
        val cFcrCompany: Double?,
        /** FCR brought to a 2.0 kg bird along the breed's own FCR-for-weight curve (Ross / Cobb table) */
        val cFcrBreed: Double?,
        val avgAtLiftKg: Double = avgBeforeKg,
        /**
         * v62: the average of the birds left came out further than 20 % under / 10 % over the flock's average at the
         * lifting and is held at that limit (said on the page, not hidden): the truck's kilos and the weighing do not
         * agree, and with few birds left a small error in either would give an impossible weight.
         */
        val afterHeld: Boolean = false
    )

    /** FCR of the breed table at the age the table reaches [kg]. */
    fun breedFcrAt(kg: Double, breed: String): Double =
        PhysiologicalEngine.stdFcrFromDay(PhysiologicalEngine.weightAgeFromBW(kg * 1000.0, breed), breed)

    /** FCR corrected to a 2.0 kg bird with the breed's own curve: FCR - (table FCR at this weight - table FCR at 2.0 kg). */
    fun breedCorrectedFcr(fcr: Double, cumBwKg: Double, breed: String): Double =
        fcr - (breedFcrAt(cumBwKg, breed) - breedFcrAt(2.0, breed))

    /** The meat balance for every day (see [Mass]); worked out with [compute]. */
    fun mass(flock: FlockEntity, farm: FarmEntity, config: ConfigEntity, rows: List<DailyDataEntity>, kgOf: Map<String, Double>): List<Mass> =
        computeAll(flock, farm, config, rows, kgOf).second

    /**
     * @param rows every day of the flock, in day order (inputs filled in; the worked-out fields are replaced)
     * @param kgOf bag weight (kg) of each feed variety
     */
    fun compute(flock: FlockEntity, farm: FarmEntity, config: ConfigEntity, rows: List<DailyDataEntity>, kgOf: Map<String, Double>): List<DailyDataEntity> =
        computeAll(flock, farm, config, rows, kgOf).first

    fun zoneOf(farm: FarmEntity): java.time.ZoneId = try { java.time.ZoneId.of(farm.timeZone) } catch (e: Exception) { java.time.ZoneId.systemDefault() }

    /**
     * The birds that ate during a day (v61; v62: the one definition, for the page, the graphs and the sheet): those in the
     * house at its end, and the birds lifted that day for the part of the day before their truck left.
     */
    fun eaters(r: DailyDataEntity, zone: java.time.ZoneId): Double =
        r.inHouse + r.lapsShown(zone).sumOf { it.birds * (it.hour / 24.0).coerceIn(0.0, 1.0) }

    /**
     * Feed per bird till date, g, by flock day (v62): each day's feed over the birds that ate it, added up. (Before: all
     * the feed so far over the birds in the house today — which gave the birds left after a lifting the feed of every
     * bird already sold, and the living the feed of the dead.) The feed entered on day x is what was eaten on day x − 1.
     */
    fun cumFeedPerBirdG(rows: List<DailyDataEntity>, kgOf: Map<String, Double>, bagKg: Double, zone: java.time.ZoneId): Map<Int, Double> {
        val by = rows.associateBy { it.dayNumber }
        var cum = 0.0
        return rows.sortedBy { it.dayNumber }.associate { r ->
            val ate = by[r.dayNumber - 1]?.let { eaters(it, zone) } ?: 0.0
            if (ate > 0) cum += usedKg(r, kgOf, bagKg) * 1000.0 / ate
            r.dayNumber to cum
        }
    }

    /** Hour of the day (farm time) of an epoch-ms moment; null when not known. */
    private fun hourOf(ms: Long, zone: java.time.ZoneId): Double? = if (ms <= 0) null else
        java.time.Instant.ofEpochMilli(ms).atZone(zone).let { it.hour + it.minute / 60.0 }

    private fun computeAll(flock: FlockEntity, farm: FarmEntity, config: ConfigEntity, rows: List<DailyDataEntity>, kgOf: Map<String, Double>): Pair<List<DailyDataEntity>, List<Mass>> {
        val bagKg = farm.feedBagKg
        val zone = try { java.time.ZoneId.of(farm.timeZone) } catch (e: Exception) { java.time.ZoneId.systemDefault() }

        var cumMort = 0
        var cumLift = 0
        var cumLame = 0
        var cumFeedKg = 0.0
        var cumLiftKg = 0.0
        var cumDeadKg = 0.0
        val masses = mutableListOf<Mass>()
        // the barricade the farm entered last (ft from the front wall); it holds until another is entered
        var penFt: Double? = null
        // the front barricade (ft from the front wall), carried on like the back one until a day changes it
        var penStart = 0.0
        // a barricade (or "none") set on any day so far: the brooding plan no longer applies
        var penSet = false
        // barricades along the house (v60): the floor's near and far edge across the width, carried on like the others
        var sideStart = 0.0
        var sideEnd: Double? = null

        // Stock tracking per feed type (Bug 3 Fix)
        var totalReceivedBags = 0.0
        var totalUsedBags = 0.0
        val receivedPerType = mutableMapOf<String, Double>()
        val usedPerType = mutableMapOf<String, Double>()

        var lastSample: Pair<Int, Double>? = null // (day, weightAge)
        val updatedRows = mutableListOf<DailyDataEntity>()

        for (r in rows) {
            val day = r.dayNumber
            cumMort += r.mortality
            cumLift += r.birdsLifted
            cumLame += r.lameSeparated

            // Feed consumption & delivery accumulation
            // each variety on its own: bags per type, at that type's bag weight
            val split = usedSplit(r)
            cumFeedKg += split.entries.sumOf { (code, bags) -> bags * (kgOf[code] ?: bagKg) }
            totalUsedBags += split.values.sum()
            split.forEach { (code, bags) -> usedPerType[code] = (usedPerType[code] ?: 0.0) + bags }

            // All 3 feed delivery slots (Bug 1 Fix)
            if (r.feedRecB1 > 0 && r.feedTypeB1.isNotBlank()) {
                totalReceivedBags += r.feedRecB1
                receivedPerType[r.feedTypeB1] = (receivedPerType[r.feedTypeB1] ?: 0.0) + r.feedRecB1
            }
            if (r.feedRecB2 > 0 && r.feedTypeB2.isNotBlank()) {
                totalReceivedBags += r.feedRecB2
                receivedPerType[r.feedTypeB2] = (receivedPerType[r.feedTypeB2] ?: 0.0) + r.feedRecB2
            }
            if (r.feedRecB3 > 0 && r.feedTypeB3.isNotBlank()) {
                totalReceivedBags += r.feedRecB3
                receivedPerType[r.feedTypeB3] = (receivedPerType[r.feedTypeB3] ?: 0.0) + r.feedRecB3
            }

            val currentStockOnHand = totalReceivedBags - totalUsedBags

            // v62: the count as the entries give it is what is kept, with its sign; the house's sums (feed, floor, air)
            // work on the birds that can be there
            val count = flock.birdsPlaced - flock.receptionMort - cumMort - cumLift - cumLame
            val live = count.coerceAtLeast(0)

            // every sample location of the day (five, or as many as were added)
            val samples = r.sampleList().map { (w, n) -> PhysiologicalEngine.LocationSample(w ?: 0.0, n ?: 0) }
            val sampleRes = PhysiologicalEngine.computeWeightSamples(samples, PhysiologicalEngine.parseWeights(r.indivWeights))

            val weightAge: Double
            val projWeight: Double
            val isProjected: Boolean

            if (sampleRes.hasSample) {
                weightAge = PhysiologicalEngine.weightAgeFromBW(sampleRes.flockAvgG, flock.breed)
                lastSample = day to weightAge
                projWeight = sampleRes.flockAvgG
                isProjected = false
            } else if (lastSample != null) {
                weightAge = lastSample.second + (day - lastSample.first)
                projWeight = PhysiologicalEngine.bwFromDay(weightAge, flock.breed)
                isProjected = true
            } else {
                weightAge = day.toDouble()
                projWeight = PhysiologicalEngine.bwFromDay(day.toDouble(), flock.breed)
                isProjected = true
            }

            val avgKg = (if (sampleRes.hasSample) sampleRes.flockAvgG else projWeight) / 1000.0

            // ---- lifting: the birds that stay are what is left of the flock's meat once the trucks are loaded ----
            val liftAvg = if (r.birdsLifted > 0 && r.weightLifted > 0) r.weightLifted / r.birdsLifted else null
            var avgAfterKg = avgKg
            var avgAtLift = avgKg
            var afterHeld = false
            if (liftAvg != null && live > 0) {
                val weighH = hourOf(r.weighedAt, zone) ?: 8.0
                val liftH = hourOf(r.liftedAt, zone) ?: 20.0     // the trucks are loaded in the evening unless told otherwise
                if (sampleRes.hasSample && r.weighedAt > 0 && r.liftedAt > 0 && r.weighedAt > r.liftedAt) {
                    // weighed after the lifting: the weighing is already the birds that stayed
                    avgAfterKg = avgKg
                } else {
                    // the flock's average grown on to the lifting, then the lifted kilos taken out
                    val dt = ((liftH - weighH) / 24.0).coerceIn(0.0, 1.0)
                    val beforeAtLift = grow(avgKg * 1000.0, dt, flock.breed) / 1000.0
                    avgAtLift = beforeAtLift
                    val after = ((live + r.birdsLifted) * beforeAtLift - r.weightLifted) / live
                    avgAfterKg = after.coerceIn(beforeAtLift * 0.80, beforeAtLift * 1.10)
                    afterHeld = avgAfterKg != after
                    // the next days are carried on from the birds that stayed
                    lastSample = day to (PhysiologicalEngine.weightAgeFromBW(avgAfterKg * 1000.0, flock.breed) - dt)
                }
            }
            cumLiftKg += r.weightLifted.coerceAtLeast(0.0)
            cumDeadKg += (r.mortality + r.lameSeparated).coerceAtLeast(0) * avgKg
            val liveKg = live * avgAfterKg
            val producedKg = liveKg + cumLiftKg
            val producedBirds = live + cumLift
            val cumBwKg = if (producedBirds > 0) producedKg / producedBirds else avgKg

            // Temperature & ventilation
            val tempKnown = r.outTemp != null
            val meanTemp = if (tempKnown) r.outTemp!! else 20.0
            val heatFactor = PhysiologicalEngine.computeFeedHeatDerate(meanTemp, config.feedHeatK)
            val waterUplift = PhysiologicalEngine.computeWaterUplift(meanTemp, config.waterHeatK)

            // Ration to give follows the company (commercial) feed curve at the flock's actual weight;
            // the Ross objective intake runs ~20 % below what commercial flocks eat. Day 0 still
            // gets the day-1 starter ration (this does NOT touch FCR, which uses actual bags logged).
            val feedPerBird = CompanyStandard.feedForWeight(avgKg * 1000.0) * heatFactor
            val totalFeedKg = (feedPerBird * live) / 1000.0
            val feedBags = ceil(totalFeedKg / bagKg).toInt()
            val waterPerBird = feedPerBird * config.wfRatio * waterUplift // mL
            val totalWaterL = (waterPerBird * live) / 1000.0
            val tankRefills = if (farm.drinkTankL > 0) ceil(totalWaterL / farm.drinkTankL * farm.waterRefillFactor).toInt() else 1

            // Approx daily gain (g/bird) from the growth curve at the current weight-age
            val gainPerBird = PhysiologicalEngine.bwFromDay(weightAge, flock.breed) -
                    PhysiologicalEngine.bwFromDay(max(0.0, weightAge - 1.0), flock.breed)
            // Drinker line pressure (inches) by age, and water throughput per line (L/hr over 16 active hrs)
            val drinkerPressureIn = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_WATERLINE_BY_AGE, day.toDouble())
            val drinkerFlowLHrLine = if (farm.drinkerLines > 0) totalWaterL / farm.drinkerLines / 16.0 else totalWaterL / 16.0
            // Total-water sensitivity to a ±3°C day
            val waterLowL = (feedPerBird * config.wfRatio *
                    PhysiologicalEngine.computeWaterUplift(meanTemp - 3.0, config.waterHeatK) * live) / 1000.0
            val waterHighL = (feedPerBird * config.wfRatio *
                    PhysiologicalEngine.computeWaterUplift(meanTemp + 3.0, config.waterHeatK) * live) / 1000.0

            val setTemp = PhysiologicalEngine.interpolate(
                PhysiologicalEngine.CURVE_TEMP_BY_BW,
                if (sampleRes.hasSample) sampleRes.flockAvgG else projWeight
            )
            val incoming = if (tempKnown) meanTemp else setTemp
            // Weight-based Ross floor × 1.3 air-quality margin × farm calibration.
            val cfmPerBird = PhysiologicalEngine.designMinVentCfmPerBird(avgKg, farm.minVentFactor)

            val ventPlan = PhysiologicalEngine.computeVentPlan(
                fanCount = farm.fanCount,
                fanRatedCfm = farm.fanRatedCfm,
                fanDerate = farm.fanDerate,
                usableWidthFt = farm.usableWidthFt,
                usableHeightFt = farm.heightFt,
                avgKg = avgKg,
                incomingTempC = incoming,
                setTempC = setTemp,
                liveBirds = live,
                cfmPerBirdReq = cfmPerBird,
                day = day,
                // Wire the farm's configured timer/trigger settings (were being ignored).
                cycleSec = config.cycleSec,
                minOnSec = config.minOnSec,
                tempBand = config.tempBand,
                tunTrigBig = config.tunTrigBig,
                tunTrigYoung = config.tunTrigYoung
            )

            // FCR: feed per kilo produced - the birds in the house and the birds already lifted
            val fcr = if (producedKg > 0 && cumFeedKg > 0) cumFeedKg / producedKg else null
            val cFcr = if (fcr != null) PhysiologicalEngine.computeCorrectedFcr(cumBwKg, fcr, config.cFcrDivisor) else null
            masses += Mass(day, cumLift, cumLiftKg, r.birdsLifted, liftAvg, avgKg, avgAfterKg, liveKg, cumDeadKg, producedKg, cumBwKg,
                fcr, if (cumFeedKg > 0 && producedKg + cumDeadKg > 0) cumFeedKg / (producedKg + cumDeadKg) else null,
                cFcr, fcr?.let { breedCorrectedFcr(it, cumBwKg, flock.breed) }, avgAtLift, afterHeld)

            // Reception / transit deaths only reduce the entry flock; they are not flock mortality.
            val entryBirds = (flock.birdsPlaced - flock.receptionMort).coerceAtLeast(0)
            val cumMortPct = if (entryBirds > 0) ((cumMort + cumLame).toDouble() / entryBirds) * 100.0 else null   // culls are mortality too (v60)
            // birds lifted (sold) are not lost: livability is the share still alive in the house or on the trucks
            // (v61: from the losses themselves, so a count that does not add up after the last lifting cannot move it)
            val livability = if (entryBirds > 0) ((entryBirds - cumMort - cumLame).coerceAtLeast(0).toDouble() / entryBirds) * 100.0 else null

            // Area & density: the floor the birds have (the barricade entered, else the brooding plan) and the birds after any lifting
            r.broodingLength?.let { penSet = true; penFt = if (it <= 0 || it >= farm.usableLengthFt) null else it }
            r.penStartFt?.let { penSet = true; penStart = it.coerceIn(0.0, max(0.0, farm.usableLengthFt - 10)) }
            // the birds' floor runs from the front barricade to the back one (the back wall when there is none); the brooding
            // plan (a part of the house growing to all of it by day 11) only until the farm sets a barricade (v54: once set,
            // "none" means the whole house)
            val penLen = if (penStart > 0 || penFt != null) ((penFt ?: farm.usableLengthFt) - penStart).coerceAtLeast(10.0)
                else if (penSet) farm.usableLengthFt else null
            r.penSideStartFt?.let { penSet = true; sideStart = it.coerceIn(0.0, max(0.0, farm.usableWidthFt - 4)) }
            r.penSideEndFt?.let { penSet = true; sideEnd = if (it <= 0 || it >= farm.usableWidthFt) null else it }
            val widthUse = ((sideEnd ?: farm.usableWidthFt) - sideStart).coerceIn(4.0, max(4.0, farm.usableWidthFt))
            val narrowed = widthUse < farm.usableWidthFt - 0.01
            val areaRes = PhysiologicalEngine.computeAreaAndDensity(
                liveBirds = live,
                avgKg = avgAfterKg,
                usableLengthFt = farm.usableLengthFt,
                usableWidthFt = widthUse,
                broodDensity = farm.broodDensity,
                densityCapDefault = farm.densityCapDefault,
                day = day,
                // a floor narrowed along the house is as long as its end barricades say (the whole house when there are none)
                penLengthFt = penLen ?: if (narrowed) farm.usableLengthFt else null
            )

            // Climate display targets
            val tempMin = setTemp - config.tempBand
            val tempIdeal = setTemp
            val tempMax = setTemp + config.tempBand

            val rhIdeal = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_RH_BY_AGE, day.toDouble())
            val rhMin = config.rhMin
            val rhMax = config.rhMax

            val airspeed = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_AIRSPEED_BY_AGE, day.toDouble())
            val windChill = if (tempKnown) meanTemp - (0.0114 * airspeed) else null

            val lightHours = PhysiologicalEngine.lightHours(day, flock.harvestAge)
            val maxMortCeil = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_MAXMORT_BY_AGE, day.toDouble())

            val ventText = when (ventPlan.mode) {
                0 -> "Minimum Ventilation"
                1 -> "Transitional"
                2 -> "Tunnel Ventilation"
                else -> "Minimum Ventilation"
            }

            // Minimum ventilation = the MIN level of the controller ladder for today's birds.
            val ibPlan = IbController.dayPlan(day, if (sampleRes.hasSample) sampleRes.flockAvgG else projWeight, live, farm)
            val minLv = ibPlan.minLv
            val cycleText = if (minLv.isTimer) "${minLv.on}s on / ${minLv.off}s off" else "${minLv.fansOn} fan${if (minLv.fansOn > 1) "s" else ""} non-stop"

            // Alerts
            val alertMsgs = mutableListOf<String>()
            var sev = 0 // 0 = ok, 1 = warn, 2 = crit

            if (cumMortPct != null && cumMortPct > maxMortCeil) {
                alertMsgs.add(String.format("Cum mortality %.2f%% exceeds ceiling %.1f%%", cumMortPct, maxMortCeil))
                sev = max(sev, 2)
            } else if (cumMortPct != null && cumMortPct > maxMortCeil * 0.85) {
                alertMsgs.add("Cum mortality nearing ceiling")
                sev = max(sev, 1)
            }

            // the day's losses (dead and culled) over the birds the day began with (v61: not over those left after a lifting)
            val lostToday = r.mortality + r.lameSeparated
            val dayStart = live + lostToday + r.birdsLifted
            val tmPct = if (dayStart > 0) (lostToday.toDouble() / dayStart) * 100.0 else 0.0
            if (tmPct > 0.30) {
                alertMsgs.add(String.format("Today's mortality %d (%.2f%%) high", lostToday, tmPct))
                sev = max(sev, 2)
            } else if (tmPct > 0.15) {
                alertMsgs.add("Today's mortality elevated")
                sev = max(sev, 1)
            }

            // The CV is only the true bird-to-bird CV (birds weighed one by one); bulk weighing gives a location spread.
            val cvValue: Double? = sampleRes.birdCv
            if (cvValue != null) {
                if (cvValue >= config.cvCrit) {
                    alertMsgs.add(String.format("CV %.1f%% — critical spread", cvValue))
                    sev = max(sev, 2)
                } else if (cvValue >= config.cvWarn) {
                    alertMsgs.add(String.format("CV %.1f%% — uneven spread", cvValue))
                    sev = max(sev, 1)
                }
            }

            if (areaRes.densityKgM2 > farm.densityCapDefault) {
                alertMsgs.add(String.format("Density %.1f over cap %.1f kg/m²", areaRes.densityKgM2, farm.densityCapDefault))
                sev = max(sev, 2)
            }

            val alertLevel = when {
                sev >= 2 -> "crit"
                sev >= 1 -> "warn"
                else -> "ok"
            }
            val alertText = if (alertMsgs.isNotEmpty()) alertMsgs.joinToString(" · ") else "All targets nominal"

            updatedRows.add(
                r.copy(
                    sampleEntered = sampleRes.hasSample,
                    avgWeight = if (sampleRes.hasSample) sampleRes.flockAvgG else null,
                    cv = cvValue,
                    uniformityPct = sampleRes.uniformityPct,
                    locSpreadPct = sampleRes.locSpreadPct,
                    weightAge = weightAge,
                    idealWeight = PhysiologicalEngine.bwFromDay(day.toDouble(), flock.breed),
                    liveBirds = count,
                    cumMort = cumMort,
                    cumMortPct = cumMortPct,
                    livability = livability,
                    densityKgM2 = areaRes.densityKgM2,
                    setTemp = setTemp,
                    feedPerBird = feedPerBird,
                    totalFeedKg = totalFeedKg,
                    feedBags = feedBags,
                    waterPerBird = waterPerBird,
                    totalWaterL = totalWaterL,
                    tankRefills = tankRefills,
                    cfmPerBird = cfmPerBird,
                    fansToRun = minLv.fansOn,
                    fanOnSec = if (minLv.isTimer) minLv.on else 300,
                    fanOffSec = if (minLv.isTimer) minLv.off else 0,
                    ventMode = ventPlan.mode,
                    fcr = fcr,
                    cFcr = cFcr,
                    projected = isProjected,
                    tempMin = tempMin,
                    tempIdeal = tempIdeal,
                    tempMax = tempMax,
                    rhMin = rhMin,
                    rhIdeal = rhIdeal,
                    rhMax = rhMax,
                    co2Max = config.co2Crit,
                    nh3Max = config.nh3Crit,
                    airspeed = airspeed,
                    windChill = windChill,
                    lightHours = lightHours,
                    maxMortPct = maxMortCeil,
                    occupiedFt2 = areaRes.occupiedFt2,
                    barricadeFt = areaRes.barricadeFt,
                    ftPerBird = areaRes.ftPerBird,
                    minFtPerBird = areaRes.minFtPerBird,
                    stockOnHand = currentStockOnHand,
                    ventText = ventText,
                    cycleText = cycleText,
                    alertLevel = alertLevel,
                    alertText = alertText,
                    gainPerBird = gainPerBird,
                    drinkerPressureIn = drinkerPressureIn,
                    drinkerFlowLHrLine = drinkerFlowLHrLine,
                    waterLowL = waterLowL,
                    waterHighL = waterHighL
                )
            )
        }

        return updatedRows to masses
    }
}
