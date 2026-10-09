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
            rows.map { com.example.flock.domain.Projection.DayIn(it.dayNumber, it.avgWeight, it.liveBirds, it.mortality, usedKg(it, kgOf, bag), it.totalFeedKg) },
            entry, lastEnteredDay(rows), { w, d -> grow(w, d, flock.breed) }, PhysiologicalEngine.bwFromDay(0.0, flock.breed),
            { d -> CompanyStandard.dailyMortPct(CompanyStandard.duringDay(d)) }, carrySdKg(farm))
    }

    /** CV % estimated from group weighings, pooled over the last three weighings up to [day] (null: fewer than 3 groups). */
    fun cvEstimate(rows: List<DailyDataEntity>, day: Int): Double? =
        com.example.flock.domain.Uniformity.cvFromGroups(rows.filter { it.dayNumber <= day }.sortedBy { it.dayNumber }
            .map { r -> r.filledSamples().map { (total, n) -> total / n to n } }.filter { it.size >= 3 })

    /**
     * @param rows every day of the flock, in day order (inputs filled in; the worked-out fields are replaced)
     * @param kgOf bag weight (kg) of each feed variety
     */
    fun compute(flock: FlockEntity, farm: FarmEntity, config: ConfigEntity, rows: List<DailyDataEntity>, kgOf: Map<String, Double>): List<DailyDataEntity> {
        val bagKg = farm.feedBagKg

        var cumMort = 0
        var cumLift = 0
        var cumLame = 0
        var cumFeedKg = 0.0

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

            var live = flock.birdsPlaced - flock.receptionMort - cumMort - cumLift - cumLame
            if (live < 0) live = 0

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

            // FCR & cFCR
            val fcr = if (live > 0 && avgKg > 0 && cumFeedKg > 0) cumFeedKg / (live * avgKg) else null
            val cFcr = if (fcr != null) PhysiologicalEngine.computeCorrectedFcr(avgKg, fcr, config.cFcrDivisor) else null

            // Reception / transit deaths only reduce the entry flock; they are not flock mortality.
            val entryBirds = (flock.birdsPlaced - flock.receptionMort).coerceAtLeast(0)
            val cumMortPct = if (entryBirds > 0) (cumMort.toDouble() / entryBirds) * 100.0 else null
            val livability = if (entryBirds > 0) (live.toDouble() / entryBirds) * 100.0 else null

            // Area & density
            val areaRes = PhysiologicalEngine.computeAreaAndDensity(
                liveBirds = live,
                avgKg = avgKg,
                usableLengthFt = farm.usableLengthFt,
                usableWidthFt = farm.usableWidthFt,
                broodDensity = farm.broodDensity,
                densityCapDefault = farm.densityCapDefault,
                day = day
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

            val tmPct = if (live > 0) (r.mortality.toDouble() / live) * 100.0 else 0.0
            if (tmPct > 0.30) {
                alertMsgs.add(String.format("Today's mortality %d (%.2f%%) high", r.mortality, tmPct))
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
                    liveBirds = live,
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

        return updatedRows
    }
}
