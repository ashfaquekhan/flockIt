package com.example.flock.data

import com.example.flock.engine.PhysiologicalEngine
import com.example.flock.network.WeatherClient
import com.example.flock.network.WeatherResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

data class FeedStockSummary(
    val totalReceivedBags: Double,
    val totalUsedBags: Double,
    val totalOnHandBags: Double,
    val perTypeOnHand: Map<String, Double>
)

class FlockRepository(
    private val database: FlockDatabase
) {
    private val farmRegistryDao = database.farmRegistryDao()
    private val farmDao = database.farmDao()
    private val configDao = database.configDao()
    private val feedTypeDao = database.feedTypeDao()
    private val flockDao = database.flockDao()
    private val dailyDataDao = database.dailyDataDao()
    private val taskDao = database.taskDao()

    val allFarms: Flow<List<FarmRegistryEntity>> = farmRegistryDao.getAllFarmsFlow()

    suspend fun registerFarm(
        spreadsheetId: String,
        farmName: String,
        role: String = "Editor",
        isOwner: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val reg = FarmRegistryEntity(
            spreadsheetId = spreadsheetId,
            farmName = farmName,
            role = role,
            isOwner = isOwner,
            lastOpened = System.currentTimeMillis()
        )
        farmRegistryDao.insertOrUpdate(reg)
        if (farmDao.getFarm(spreadsheetId) == null) {
            farmDao.insertOrUpdateFarm(FarmEntity(spreadsheetId = spreadsheetId, farmName = farmName))
            configDao.insertOrUpdateConfig(ConfigEntity(spreadsheetId = spreadsheetId))
        }
    }

    fun getFlocksFlow(spreadsheetId: String): Flow<List<FlockEntity>> =
        flockDao.getAllFlocks(spreadsheetId)

    fun getActiveFlocksFlow(spreadsheetId: String): Flow<List<FlockEntity>> =
        flockDao.getActiveFlocks(spreadsheetId)

    fun getFarmFlow(spreadsheetId: String): Flow<FarmEntity?> =
        farmDao.getFarmFlow(spreadsheetId)

    fun getConfigFlow(spreadsheetId: String): Flow<ConfigEntity?> =
        configDao.getConfigFlow(spreadsheetId)

    fun getFeedTypesFlow(spreadsheetId: String): Flow<List<FeedTypeEntity>> =
        feedTypeDao.getFeedTypesFlow(spreadsheetId)

    fun getDailyDataFlow(spreadsheetId: String, flockId: String): Flow<List<DailyDataEntity>> =
        dailyDataDao.getDailyDataForFlock(spreadsheetId, flockId)

    fun getDayEntryFlow(spreadsheetId: String, flockId: String, dayNumber: Int): Flow<DailyDataEntity?> =
        dailyDataDao.getDayEntryFlow(spreadsheetId, flockId, dayNumber)

    fun getTasksFlow(spreadsheetId: String, flockId: String, dayNumber: Int): Flow<List<TaskEntity>> =
        taskDao.getTasksForDay(spreadsheetId, flockId, dayNumber)

    suspend fun getFarm(spreadsheetId: String): FarmEntity = withContext(Dispatchers.IO) {
        farmDao.getFarm(spreadsheetId) ?: FarmEntity(spreadsheetId = spreadsheetId)
    }

    suspend fun getConfig(spreadsheetId: String): ConfigEntity = withContext(Dispatchers.IO) {
        configDao.getConfig(spreadsheetId) ?: ConfigEntity(spreadsheetId = spreadsheetId)
    }

    suspend fun getFeedTypes(spreadsheetId: String): List<FeedTypeEntity> = withContext(Dispatchers.IO) {
        feedTypeDao.getFeedTypes(spreadsheetId)
    }

    suspend fun updateFarm(farm: FarmEntity) = withContext(Dispatchers.IO) {
        farmDao.insertOrUpdateFarm(farm)
        farmRegistryDao.getFarm(farm.spreadsheetId)?.let { reg ->
            farmRegistryDao.insertOrUpdate(reg.copy(farmName = farm.farmName, lastOpened = System.currentTimeMillis()))
        }
    }

    suspend fun updateConfig(config: ConfigEntity) = withContext(Dispatchers.IO) {
        configDao.insertOrUpdateConfig(config)
    }

    suspend fun saveFeedType(feedType: FeedTypeEntity) = withContext(Dispatchers.IO) {
        feedTypeDao.insertFeedType(feedType)
    }

    suspend fun deleteFeedType(spreadsheetId: String, code: String) = withContext(Dispatchers.IO) {
        feedTypeDao.deleteFeedType(spreadsheetId, code)
    }

    suspend fun createFlock(
        spreadsheetId: String,
        name: String,
        breed: String,
        startDate: String,
        birdsPlaced: Int,
        receptionMort: Int = 0,
        targetWeight: Double = 3200.0,
        harvestAge: Int = 42,
        season: String = "Monsoon"
    ): String = withContext(Dispatchers.IO) {
        val farm = getFarm(spreadsheetId)
        val tz = TimeZone.getTimeZone(farm.timeZone)
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }
        val sdfId = SimpleDateFormat("yyMMddHHmmss", Locale.US).apply { timeZone = tz }
        val flockId = "FL" + sdfId.format(Date())

        val flock = FlockEntity(
            spreadsheetId = spreadsheetId,
            flockId = flockId,
            name = name.ifBlank { "Batch #1" },
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
        val cal = Calendar.getInstance(tz).apply { time = parsedStart }
        val dayRows = mutableListOf<DailyDataEntity>()
        for (d in 0..harvestAge) {
            val dateStr = sdfDate.format(cal.time)
            dayRows.add(
                DailyDataEntity(
                    spreadsheetId = spreadsheetId,
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
        recomputeFlock(spreadsheetId, flockId)
        flockId
    }

    suspend fun closeFlock(spreadsheetId: String, flockId: String) = withContext(Dispatchers.IO) {
        val flock = flockDao.getFlockById(spreadsheetId, flockId) ?: return@withContext
        flockDao.updateFlock(flock.copy(status = "closed"))
    }

    suspend fun deleteFlock(spreadsheetId: String, flockId: String) = withContext(Dispatchers.IO) {
        dailyDataDao.deleteDailyDataForFlock(spreadsheetId, flockId)
        taskDao.deleteTasksForFlock(spreadsheetId, flockId)
        flockDao.deleteFlock(spreadsheetId, flockId)
    }

    suspend fun addTask(task: TaskEntity) = withContext(Dispatchers.IO) {
        taskDao.insertTask(task)
    }

    suspend fun deleteTask(spreadsheetId: String, taskId: String) = withContext(Dispatchers.IO) {
        taskDao.deleteTask(spreadsheetId, taskId)
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
     * Calculates the real current flock day relative to start date in the farm's time zone.
     */
    fun calculateCurrentDay(startDateIso: String, timeZoneId: String): Int {
        return try {
            val zone = ZoneId.of(timeZoneId)
            val today = LocalDate.now(zone)
            val start = LocalDate.parse(startDateIso, DateTimeFormatter.ISO_LOCAL_DATE)
            val diff = java.time.temporal.ChronoUnit.DAYS.between(start, today).toInt()
            max(0, diff)
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Checks if hard fields are locked for a given flock and day.
     * Hard fields (samples, mortality, feedBagsUsed) lock after cutoffTime on current day,
     * and are always locked on past or future days.
     */
    suspend fun isDayHardLocked(spreadsheetId: String, flock: FlockEntity, dayNumber: Int): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val farm = getFarm(spreadsheetId)
        val zone = ZoneId.of(farm.timeZone)
        val curDay = calculateCurrentDay(flock.startDate, farm.timeZone)

        if (dayNumber < curDay) {
            return@withContext true to "Past days are read-only. Edit directly in Google Sheet."
        }
        if (dayNumber > curDay) {
            return@withContext true to "Cannot enter data for future days."
        }

        // It is today. Check 11:00 AM cutoff
        val cutoffParts = farm.cutoffTime.split(":")
        val cutoffHour = cutoffParts.getOrNull(0)?.toIntOrNull() ?: 11
        val cutoffMin = cutoffParts.getOrNull(1)?.toIntOrNull() ?: 0
        val cutoff = LocalTime.of(cutoffHour, cutoffMin)
        val now = LocalTime.now(zone)

        if (now.isAfter(cutoff)) {
            return@withContext true to "Inputs locked: cutoff time (${farm.cutoffTime}) has passed for today."
        }

        false to ""
    }

    /**
     * Saves daily inputs and enforces the lock in the data layer (Bug 2 Fix).
     */
    suspend fun saveDayEntry(
        spreadsheetId: String,
        flockId: String,
        dayNumber: Int,
        userEmail: String,
        updateAction: (DailyDataEntity) -> DailyDataEntity
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val flock = flockDao.getFlockById(spreadsheetId, flockId)
            ?: return@withContext Result.failure(Exception("Flock not found"))
        val existing = dailyDataDao.getDayEntry(spreadsheetId, flockId, dayNumber)
            ?: return@withContext Result.failure(Exception("Day entry not found"))

        val modified = updateAction(existing)

        // Enforce hard-lock if hard fields were modified
        val hardFieldsChanged = modified.w1 != existing.w1 || modified.n1 != existing.n1 ||
                modified.w2 != existing.w2 || modified.n2 != existing.n2 ||
                modified.w3 != existing.w3 || modified.n3 != existing.n3 ||
                modified.w4 != existing.w4 || modified.n4 != existing.n4 ||
                modified.w5 != existing.w5 || modified.n5 != existing.n5 ||
                modified.mortality != existing.mortality ||
                modified.feedBagsUsed != existing.feedBagsUsed

        if (hardFieldsChanged) {
            val (locked, reason) = isDayHardLocked(spreadsheetId, flock, dayNumber)
            if (locked) {
                return@withContext Result.failure(IllegalStateException(reason))
            }
        }

        // Bug 4 Fix: sampleEntered is ONLY true when at least one location has BOTH weight > 0 AND count > 0
        val hasSample = ((modified.w1 ?: 0.0) > 0 && (modified.n1 ?: 0) > 0) ||
                ((modified.w2 ?: 0.0) > 0 && (modified.n2 ?: 0) > 0) ||
                ((modified.w3 ?: 0.0) > 0 && (modified.n3 ?: 0) > 0) ||
                ((modified.w4 ?: 0.0) > 0 && (modified.n4 ?: 0) > 0) ||
                ((modified.w5 ?: 0.0) > 0 && (modified.n5 ?: 0) > 0)

        val toSave = modified.copy(
            sampleEntered = hasSample,
            updatedAt = System.currentTimeMillis(),
            updatedBy = userEmail
        )

        dailyDataDao.insertOrUpdateDay(toSave)
        recomputeFlock(spreadsheetId, flockId)
        Result.success(Unit)
    }

    /**
     * Complete mathematical recalculation for a flock across its entire life-cycle.
     */
    suspend fun recomputeFlock(spreadsheetId: String, flockId: String) = withContext(Dispatchers.IO) {
        val flock = flockDao.getFlockById(spreadsheetId, flockId) ?: return@withContext
        val farm = getFarm(spreadsheetId)
        val config = getConfig(spreadsheetId)
        val rows = dailyDataDao.getDailyDataList(spreadsheetId, flockId).sortedBy { it.dayNumber }
        if (rows.isEmpty()) return@withContext

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
            cumFeedKg += r.feedBagsUsed * bagKg
            totalUsedBags += r.feedBagsUsed
            if (r.feedBagsUsed > 0 && r.feedUsedType.isNotBlank()) {
                usedPerType[r.feedUsedType] = (usedPerType[r.feedUsedType] ?: 0.0) + r.feedBagsUsed
            }

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
            val heatFactor = PhysiologicalEngine.computeFeedHeatDerate(meanTemp, config.feedHeatK)
            val waterUplift = PhysiologicalEngine.computeWaterUplift(meanTemp, config.waterHeatK)

            val feedPerBird = PhysiologicalEngine.dailyFeedFromDay(weightAge, flock.breed) * heatFactor
            val totalFeedKg = (feedPerBird * live) / 1000.0
            val feedBags = ceil(totalFeedKg / bagKg).toInt()
            val waterPerBird = feedPerBird * config.wfRatio * waterUplift // mL
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
            val fcr = if (live > 0 && avgKg > 0 && cumFeedKg > 0) cumFeedKg / (live * avgKg) else null
            val cFcr = if (fcr != null) PhysiologicalEngine.computeCorrectedFcr(avgKg, fcr, config.cFcrDivisor) else null

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

            // Climate display targets
            val tempMin = setTemp - config.tempBand
            val tempIdeal = setTemp
            val tempMax = setTemp + config.tempBand

            val rhIdeal = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_RH_BY_AGE, day.toDouble())
            val rhMin = config.rhMin
            val rhMax = config.rhMax

            val airspeed = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_AIRSPEED_BY_AGE, day.toDouble())
            val windChill = if (tempKnown) meanTemp - (0.0114 * airspeed) else null

            val lightHours = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_LIGHT_BY_AGE, day.toDouble())
            val maxMortCeil = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_MAXMORT_BY_AGE, day.toDouble())

            val ventText = when (ventPlan.mode) {
                0 -> "Minimum Ventilation"
                1 -> "Transitional"
                2 -> "Tunnel Ventilation"
                else -> "Minimum Ventilation"
            }

            val cycleText = if (ventPlan.offSec <= 0) "Continuous" else "${ventPlan.onSec}s on / ${ventPlan.offSec}s off"

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

            // Bug 5 Fix: CV gate on hasSample && totalWeighed >= 2
            val cvValue: Double? = if (sampleRes.hasSample && sampleRes.totalWeighed >= 2) sampleRes.cvPercent else null
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
                    alertText = alertText
                )
            )
        }

        dailyDataDao.insertDailyData(updatedRows)
    }

    suspend fun getFeedStockSummary(spreadsheetId: String, flockId: String): FeedStockSummary = withContext(Dispatchers.IO) {
        val rows = dailyDataDao.getDailyDataList(spreadsheetId, flockId)
        var totalRec = 0.0
        var totalUsed = 0.0
        val recPerType = mutableMapOf<String, Double>()
        val usedPerType = mutableMapOf<String, Double>()

        for (r in rows) {
            totalUsed += r.feedBagsUsed
            if (r.feedBagsUsed > 0 && r.feedUsedType.isNotBlank()) {
                usedPerType[r.feedUsedType] = (usedPerType[r.feedUsedType] ?: 0.0) + r.feedBagsUsed
            }
            if (r.feedRecB1 > 0 && r.feedTypeB1.isNotBlank()) {
                totalRec += r.feedRecB1
                recPerType[r.feedTypeB1] = (recPerType[r.feedTypeB1] ?: 0.0) + r.feedRecB1
            }
            if (r.feedRecB2 > 0 && r.feedTypeB2.isNotBlank()) {
                totalRec += r.feedRecB2
                recPerType[r.feedTypeB2] = (recPerType[r.feedTypeB2] ?: 0.0) + r.feedRecB2
            }
            if (r.feedRecB3 > 0 && r.feedTypeB3.isNotBlank()) {
                totalRec += r.feedRecB3
                recPerType[r.feedTypeB3] = (recPerType[r.feedTypeB3] ?: 0.0) + r.feedRecB3
            }
        }

        val allTypes = (recPerType.keys + usedPerType.keys).distinct()
        val onHandMap = mutableMapOf<String, Double>()
        for (t in allTypes) {
            val rec = recPerType[t] ?: 0.0
            val used = usedPerType[t] ?: 0.0
            onHandMap[t] = rec - used
        }

        FeedStockSummary(
            totalReceivedBags = totalRec,
            totalUsedBags = totalUsed,
            totalOnHandBags = totalRec - totalUsed,
            perTypeOnHand = onHandMap
        )
    }
}
