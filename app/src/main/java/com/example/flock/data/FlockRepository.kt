package com.example.flock.data

import com.example.flock.engine.PhysiologicalEngine
import com.example.flock.network.WeatherClient
import com.example.flock.network.WeatherResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

class FlockRepository(
    private val database: FlockDatabase
) {
    private val flockDao = database.flockDao()
    private val dailyDataDao = database.dailyDataDao()
    private val farmDao = database.farmDao()
    private val configDao = database.configDao()
    private val routineDao = database.routineDao()
    private val dismissalDao = database.dismissalDao()

    val allFlocks: Flow<List<FlockEntity>> = flockDao.getAllFlocks()
    val farmFlow: Flow<FarmEntity?> = farmDao.getFarmFlow()

    fun getDailyDataFlow(flockId: String): Flow<List<DailyDataEntity>> =
        dailyDataDao.getDailyDataForFlock(flockId)

    fun getDayEntryFlow(flockId: String, dayNumber: Int): Flow<DailyDataEntity?> =
        dailyDataDao.getDayEntryFlow(flockId, dayNumber)

    fun getRoutinesFlow(flockId: String, dayNumber: Int): Flow<List<RoutineEntity>> =
        routineDao.getRoutinesForDay(flockId, dayNumber)

    fun getDismissalsFlow(flockId: String, dateIso: String): Flow<List<DismissalEntity>> =
        dismissalDao.getDismissals(flockId, dateIso)

    suspend fun getFarm(): FarmEntity = withContext(Dispatchers.IO) {
        farmDao.getFarm() ?: FarmEntity()
    }

    suspend fun updateFarm(farm: FarmEntity) = withContext(Dispatchers.IO) {
        farmDao.insertOrUpdateFarm(farm)
    }

    suspend fun createFlock(
        name: String,
        breed: String,
        startDate: String,
        birdsPlaced: Int,
        receptionMort: Int = 0,
        targetWeight: Double = 3200.0,
        harvestAge: Int = 42,
        season: String = "Monsoon"
    ): String = withContext(Dispatchers.IO) {
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sdfId = SimpleDateFormat("yyMMddHHmmss", Locale.US)
        val flockId = "FL" + sdfId.format(Date())

        val flock = FlockEntity(
            flockId = flockId,
            name = name.ifBlank { "House 3" },
            breed = breed.ifBlank { "Ross308" },
            startDate = startDate.ifBlank { sdfDate.format(Date()) },
            birdsPlaced = birdsPlaced,
            receptionMort = receptionMort,
            targetWeight = targetWeight,
            harvestAge = harvestAge,
            season = season,
            status = "active"
        )
        flockDao.insertFlock(flock)

        // Generate day rows 0..harvestAge
        val parsedStart = try {
            sdfDate.parse(flock.startDate) ?: Date()
        } catch (e: Exception) {
            Date()
        }
        val cal = Calendar.getInstance().apply { time = parsedStart }
        val dayRows = mutableListOf<DailyDataEntity>()
        for (d in 0..harvestAge) {
            val dateStr = sdfDate.format(cal.time)
            dayRows.add(
                DailyDataEntity(
                    flockId = flockId,
                    dayNumber = d,
                    date = dateStr,
                    locked = false,
                    sampleEntered = false
                )
            )
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        dailyDataDao.insertDailyData(dayRows)

        recomputeFlock(flockId)
        flockId
    }

    suspend fun deleteFlock(flockId: String) = withContext(Dispatchers.IO) {
        dailyDataDao.deleteDailyDataForFlock(flockId)
        flockDao.deleteFlock(flockId)
    }

    suspend fun saveDayEntry(
        flockId: String,
        dayNumber: Int,
        updateAction: (DailyDataEntity) -> DailyDataEntity
    ) = withContext(Dispatchers.IO) {
        val existing = dailyDataDao.getDayEntry(flockId, dayNumber) ?: return@withContext
        val modified = updateAction(existing)

        // Check if any sample entered
        val hasSample = (modified.n1 ?: 0) > 0 || (modified.n2 ?: 0) > 0 ||
                (modified.n3 ?: 0) > 0 || (modified.n4 ?: 0) > 0 || (modified.n5 ?: 0) > 0

        dailyDataDao.insertOrUpdateDay(modified.copy(sampleEntered = hasSample))
        recomputeFlock(flockId)
    }

    suspend fun setAlarm(routineId: String, alarmOn: Boolean) = withContext(Dispatchers.IO) {
        routineDao.setAlarm(routineId, alarmOn)
    }

    suspend fun addRoutine(routine: RoutineEntity) = withContext(Dispatchers.IO) {
        routineDao.insertRoutine(routine)
    }

    suspend fun deleteRoutine(routineId: String) = withContext(Dispatchers.IO) {
        routineDao.deleteRoutine(routineId)
    }

    suspend fun dismissReminder(flockId: String, dateIso: String, itemId: String) = withContext(Dispatchers.IO) {
        dismissalDao.insertDismissal(
            DismissalEntity(
                flockId = flockId,
                dateISO = dateIso,
                itemId = itemId
            )
        )
    }

    suspend fun undismissReminder(flockId: String, dateIso: String, itemId: String) = withContext(Dispatchers.IO) {
        dismissalDao.deleteDismissal(flockId, dateIso, itemId)
    }

    suspend fun fetchWeather(farm: FarmEntity): WeatherResult = withContext(Dispatchers.IO) {
        WeatherClient.fetchWeather(
            lat = farm.weatherLat,
            lon = farm.weatherLon,
            locationName = farm.weatherName,
            season = farm.season
        )
    }

    /**
     * Complete mathematical recalculation for a flock across its entire life-cycle.
     */
    suspend fun recomputeFlock(flockId: String) = withContext(Dispatchers.IO) {
        val flock = flockDao.getFlockById(flockId) ?: return@withContext
        val farm = farmDao.getFarm() ?: FarmEntity()
        val rows = dailyDataDao.getDailyDataList(flockId).sortedBy { it.dayNumber }
        if (rows.isEmpty()) return@withContext

        val bagKg = farm.feedBagKg
        val usableFt2 = farm.usableLengthFt * farm.usableWidthFt
        val usableM2 = usableFt2 * 0.092903

        var cumMort = 0
        var cumLift = 0
        var cumLame = 0
        var cumFeedKg = 0.0

        var lastSample: Pair<Int, Double>? = null // (day, weightAge)
        val updatedRows = mutableListOf<DailyDataEntity>()

        for (r in rows) {
            val day = r.dayNumber
            cumMort += r.mortality
            cumLift += r.birdsLifted
            cumLame += r.lameSeparated
            cumFeedKg += r.feedBagsUsed * bagKg

            var live = flock.birdsPlaced - flock.receptionMort - cumMort - cumLift - cumLame
            if (live < 0) live = 0

            // 5 locations calculation
            val samples = listOf(
                PhysiologicalEngine.LocationSample(r.w1 ?: 0.0, r.n1 ?: 0),
                PhysiologicalEngine.LocationSample(r.w2 ?: 0.0, r.n2 ?: 0),
                PhysiologicalEngine.LocationSample(r.w3 ?: 0.0, r.n3 ?: 0),
                PhysiologicalEngine.LocationSample(r.w4 ?: 0.0, r.n4 ?: 0),
                PhysiologicalEngine.LocationSample(r.w5 ?: 0.0, r.n5 ?: 0)
            )
            val sampleRes = PhysiologicalEngine.computeWeightSamples(samples)

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
            val heatFactor = PhysiologicalEngine.computeFeedHeatDerate(meanTemp)
            val waterUplift = PhysiologicalEngine.computeWaterUplift(meanTemp)

            val feedPerBird = PhysiologicalEngine.dailyFeedFromDay(weightAge, flock.breed) * heatFactor
            val totalFeedKg = (feedPerBird * live) / 1000.0
            val feedBags = ceil(totalFeedKg / bagKg).toInt()
            val waterPerBird = feedPerBird * 1.8 * waterUplift // mL
            val totalWaterL = (waterPerBird * live) / 1000.0
            val tankRefills = if (farm.drinkTankL > 0) ceil(totalWaterL / farm.drinkTankL).toInt() else 1

            val setTemp = PhysiologicalEngine.interpolate(
                PhysiologicalEngine.CURVE_TEMP_BY_BW,
                if (sampleRes.hasSample) sampleRes.flockAvgG else projWeight
            )
            val incoming = if (tempKnown) meanTemp else setTemp
            val cfmPerBird = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_MINVENT_BY_AGE, weightAge)

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
                day = day
            )

            // FCR & cFCR
            val fcr = if (live > 0 && avgKg > 0) cumFeedKg / (live * avgKg) else null
            val cFcr = if (fcr != null) PhysiologicalEngine.computeCorrectedFcr(avgKg, fcr) else null

            val cumMortPct = if (flock.birdsPlaced > 0) (cumMort.toDouble() / flock.birdsPlaced) * 100.0 else null
            val livability = if (flock.birdsPlaced > 0) (live.toDouble() / flock.birdsPlaced) * 100.0 else null

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

            // Alerts
            val maxMortCeil = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_MAXMORT_BY_AGE, day.toDouble())
            val alertMsgs = mutableListOf<String>()
            var sev = 0 // 0 = ok, 1 = warn, 2 = crit

            if (cumMortPct != null && cumMortPct > maxMortCeil) {
                alertMsgs.add(String.format("Cum mortality %.2f%% over ceiling (%.1f%%)", cumMortPct, maxMortCeil))
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

            if (sampleRes.cvPercent >= 12.0) {
                alertMsgs.add(String.format("CV %.1f%% — very uneven", sampleRes.cvPercent))
                sev = max(sev, 2)
            } else if (sampleRes.cvPercent >= 10.0) {
                alertMsgs.add(String.format("CV %.1f%% — uneven", sampleRes.cvPercent))
                sev = max(sev, 1)
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
            val alertText = if (alertMsgs.isNotEmpty()) alertMsgs.joinToString(" · ") else "All good"

            val airspeed = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_AIRSPEED_BY_AGE, day.toDouble()).roundToInt()
            val chillDelta = 0.0114 * airspeed
            val windChill = if (tempKnown) meanTemp - chillDelta else null

            updatedRows.add(
                r.copy(
                    sampleEntered = sampleRes.hasSample,
                    avgWeight = if (sampleRes.hasSample) sampleRes.flockAvgG else null,
                    cv = if (sampleRes.cvPercent > 0) sampleRes.cvPercent else null,
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
                    fansToRun = ventPlan.fansToRun,
                    fanOnSec = ventPlan.onSec,
                    fanOffSec = ventPlan.offSec,
                    ventMode = ventPlan.mode,
                    fcr = fcr,
                    cFcr = cFcr,
                    projected = isProjected,
                    occupiedFt2 = areaRes.occupiedFt2,
                    barricadeFt = areaRes.barricadeFt,
                    ftPerBird = areaRes.ftPerBird,
                    minFtPerBird = areaRes.minFtPerBird,
                    airspeedFtMin = airspeed,
                    windChillTemp = windChill,
                    alertLevel = alertLevel,
                    alertText = alertText
                )
            )
        }

        dailyDataDao.insertDailyData(updatedRows)
    }
}
