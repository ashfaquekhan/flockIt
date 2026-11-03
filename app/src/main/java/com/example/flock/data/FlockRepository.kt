package com.example.flock.data

import com.example.flock.engine.CompanyStandard
import com.example.flock.engine.IbController
import com.example.flock.engine.PhysiologicalEngine
import com.example.flock.network.ForecastResult
import com.example.flock.network.WeatherClient
import com.example.flock.network.WeatherResult
import com.example.flock.sync.SheetsSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.flock.sync.SheetSchema
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

/** Parses a "CODE=bags;CODE=bags" feed-used breakdown string into (code, bags) pairs. */
fun parseFeedBreakdown(s: String): List<Pair<String, Double>> {
    if (s.isBlank()) return emptyList()
    return s.split(";").mapNotNull { part ->
        val kv = part.split("=")
        val code = kv.getOrNull(0)?.trim().orEmpty()
        val bags = kv.getOrNull(1)?.trim()?.toDoubleOrNull()
        if (code.isNotBlank() && bags != null) code to bags else null
    }
}

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

    /** Set by the ViewModel once the auth/sync layer is ready. Null → offline (Room only). */
    var sync: SheetsSyncManager? = null

    /** Last cloud read/write outcome, surfaced to the user so sheet sync is never silent. */
    private val _syncNote = MutableStateFlow<String?>(null)
    val syncNote: StateFlow<String?> = _syncNote.asStateFlow()
    fun clearSyncNote() { _syncNote.value = null }

    val allFarms: Flow<List<FarmRegistryEntity>> = farmRegistryDao.getAllFarmsFlow()
    val deletedFarms: Flow<List<FarmRegistryEntity>> = farmRegistryDao.getDeletedFarmsFlow()
    val deletedFlocks: Flow<List<FlockEntity>> = flockDao.getDeletedFlocksFlow()

    /**
     * Pulls the whole farm workspace from the sheet, then recomputes every flock locally.
     * No-op (success) when offline. Used on farm open and on login.
     */
    suspend fun refreshFromCloud(spreadsheetId: String): Boolean = withContext(Dispatchers.IO) {
        val s = sync ?: return@withContext false
        if (!s.isOnline()) return@withContext false
        // one load or save at a time: a load never lands on top of a save in flight (or the other way round)
        ioLock.withLock {
            resendDirtyDays(spreadsheetId)
            val res = s.pullFarmData(spreadsheetId)
            if (res.isSuccess) {
                flockDao.getAllFlocksList(spreadsheetId).forEach { recomputeFlock(spreadsheetId, it.flockId) }
                lastLoadedAt[spreadsheetId] = System.currentTimeMillis()
                true
            } else {
                _syncNote.value = "Couldn't load latest from Google Sheet: ${res.exceptionOrNull()?.message ?: "unknown"}"
                false
            }
        }
    }

    /** Every read from and write to the sheet goes one at a time (see [refreshFromCloud], [saveDayEntry]). */
    private val ioLock = Mutex()
    /** When each farm was last loaded from its sheet (epoch ms). */
    val lastLoadedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** Runs a verified cloud write and records a user-visible note. No-op when offline. */
    private suspend fun cloudPush(label: String, block: suspend (SheetsSyncManager) -> Result<Unit>): Boolean {
        val s = sync ?: return false
        if (!s.isOnline()) {
            _syncNote.value = "$label saved on the phone — it will be sent to the Google Sheet when online"
            return false
        }
        val r = try { block(s) } catch (e: Exception) { Result.failure(e) }
        _syncNote.value = if (r.isSuccess) "$label synced to Google Sheet"
            else "$label saved locally but not synced: ${r.exceptionOrNull()?.message ?: "error"}"
        return r.isSuccess
    }

    /**
     * Saves a day row as "not yet sent" (with the columns changed on this phone), then sends it: only those columns
     * are written and the row comes back as the sheet now has it — another phone's entries included.
     */
    private suspend fun saveAndPushDay(label: String, spreadsheetId: String, row: DailyDataEntity) {
        dailyDataDao.insertOrUpdateDay(row.copy(dirty = true))
        recomputeFlock(spreadsheetId, row.flockId)
        pushDirtyDay(label, spreadsheetId, row.flockId, row.dayNumber, updateReports = true)
    }

    /** Sends one unsent day row; true once the sheet has it. */
    private suspend fun pushDirtyDay(label: String?, spreadsheetId: String, flockId: String, day: Int, updateReports: Boolean): Boolean {
        val row = dailyDataDao.getDayEntry(spreadsheetId, flockId, day)?.takeIf { it.dirty } ?: return true
        var merged: DailyDataEntity? = null
        val push: suspend (SheetsSyncManager) -> Result<Unit> = { s -> s.pushDayEntry(spreadsheetId, row, updateReports, row.dirtyColumns()).map { merged = it } }
        val ok = if (label != null) cloudPush(label, push) else (sync?.let { push(it).isSuccess } ?: false)
        merged?.let { m ->
            // the sheet's row with this phone's changes in; an edit made here meanwhile (newer) stays unsent
            val now = dailyDataDao.getDayEntry(spreadsheetId, flockId, day)
            if (now == null || now.updatedAt <= row.updatedAt) {
                dailyDataDao.insertOrUpdateDay(m.copy(spreadsheetId = spreadsheetId, dirty = false, dirtyFields = ""))
                recomputeFlock(spreadsheetId, flockId)
            }
        }
        return ok
    }

    /** Re-sends day rows saved while offline (or whose push failed). */
    private suspend fun resendDirtyDays(spreadsheetId: String) {
        val s = sync ?: return
        if (!s.isOnline()) return
        var sent = 0
        flockDao.getAllFlocksList(spreadsheetId).forEach { f ->
            dailyDataDao.getDailyDataList(spreadsheetId, f.flockId).filter { it.dirty }.forEach { row ->
                if (pushDirtyDay(null, spreadsheetId, row.flockId, row.dayNumber, updateReports = false)) sent++
            }
        }
        if (sent > 0) s.pushReports(spreadsheetId)      // the report tabs once, after all the rows
    }

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
        cloudPush("Farm settings") { it.pushFarmSettings(farm.spreadsheetId, farm) }
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
        season: String = "Monsoon",
        startTime: String = "08:00"
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
            startTime = startTime.ifBlank { "08:00" },
            birdsPlaced = birdsPlaced,
            receptionMort = receptionMort,
            targetWeight = targetWeight,
            harvestAge = harvestAge.coerceIn(14, PhysiologicalEngine.MAX_FLOCK_DAY),
            season = season,
            status = "active"
        )
        flockDao.insertFlock(flock)

        // Generate day rows 0..harvestAge (more are added as the flock runs on, see ensureDayRows)
        val parsedStart = try {
            sdfDate.parse(flock.startDate) ?: Date()
        } catch (e: Exception) {
            Date()
        }
        val cal = Calendar.getInstance(tz).apply { time = parsedStart }
        val dayRows = mutableListOf<DailyDataEntity>()
        for (d in 0..harvestAge.coerceIn(1, PhysiologicalEngine.MAX_FLOCK_DAY)) {
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
        // Persist the flock + its day rows to the authoritative Google Sheet.
        cloudPush("Flock \"${flock.name}\"") { it.pushFlock(spreadsheetId, flock, dayRows) }
        flockId
    }

    /**
     * A flock has no fixed last day: it has a row for every day up to the planned harvest age, and for
     * every day it has lived beyond that (plus tomorrow), up to the oldest a broiler is kept.
     */
    fun lastDayFor(flock: FlockEntity, timeZone: String): Int =
        max(flock.harvestAge, calculateCurrentDay(flock.startDate, timeZone) + 1).coerceIn(1, PhysiologicalEngine.MAX_FLOCK_DAY)

    /** Adds the day rows a flock is missing up to [lastDayFor]. Returns true when rows were added. */
    private suspend fun ensureDayRows(flock: FlockEntity, timeZone: String, existing: List<DailyDataEntity>): Boolean {
        val last = lastDayFor(flock, timeZone)
        val have = existing.map { it.dayNumber }.toSet()
        val start = try { LocalDate.parse(flock.startDate, DateTimeFormatter.ISO_LOCAL_DATE) } catch (e: Exception) { return false }
        val add = (0..last).filter { it !in have }.map { d ->
            DailyDataEntity(spreadsheetId = flock.spreadsheetId, flockId = flock.flockId, dayNumber = d,
                date = start.plusDays(d.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE))
        }
        if (add.isEmpty()) return false
        dailyDataDao.insertDailyData(add)
        return true
    }

    /** Target weight and planned harvest age of a flock (they steer the forecasts, not the records). */
    suspend fun updateFlockPlan(spreadsheetId: String, flockId: String, targetWeightG: Double, harvestAge: Int) = withContext(Dispatchers.IO) {
        val flock = flockDao.getFlockById(spreadsheetId, flockId) ?: return@withContext
        val updated = flock.copy(targetWeight = targetWeightG.coerceIn(500.0, 6000.0), harvestAge = harvestAge.coerceIn(14, PhysiologicalEngine.MAX_FLOCK_DAY))
        flockDao.updateFlock(updated)
        recomputeFlock(spreadsheetId, flockId)
        cloudPush("Flock plan") { it.upsertFlock(spreadsheetId, updated) }
    }

    /**
     * Clears a day: all of that day's inputs (samples, mortality, feed, deliveries, notes) and its
     * lock, recomputes, and pushes the cleared row to the sheet.
     */
    suspend fun revertDay(spreadsheetId: String, flockId: String, dayNumber: Int, userEmail: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val existing = dailyDataDao.getDayEntry(spreadsheetId, flockId, dayNumber)
                ?: return@withContext Result.failure(Exception("Day entry not found"))
            val cleared = existing.copy(
                w1 = null, n1 = null, w2 = null, n2 = null, w3 = null, n3 = null,
                w4 = null, n4 = null, w5 = null, n5 = null, indivWeights = "", moreSamples = "",
                mortality = 0, feedBagsUsed = 0.0, feedUsedType = "B1", feedUsedBreakdown = "",
                birdsLifted = 0, weightLifted = 0.0, lameSeparated = 0,
                feedRecB1 = 0.0, feedRecB2 = 0.0, feedRecB3 = 0.0,
                broodingLength = null, actualFans = null, actualFanTime = null, outTemp = null, outRH = null,
                notes = "", waterTempC = null, waterPh = null, feedMoisturePct = null,
                measuredCo2 = null, measuredNh3 = null, measuredO2 = null, measuredPressure = null,
                measuredAirspeed = null, padWetMin = null, padDryMin = null, luxPerFt2 = null,
                dieselCansUsed = 0.0, weighedAt = 0L, liftedAt = 0L, feedings = "",
                sampleEntered = false, committed = false, savedFields = "",
                updatedAt = System.currentTimeMillis(), updatedBy = userEmail
            )
            ioLock.withLock {
                saveAndPushDay("Day $dayNumber cleared", spreadsheetId,
                    cleared.copy(dirtyFields = mergeDirtyFields(existing.dirty, existing.dirtyFields, SheetSchema.changedColumns(existing, cleared))))
            }
            Result.success(Unit)
        }

    /**
     * Closes a batch: every unsent day row is written to the sheet first, then the flock is marked
     * closed and read-only locally and in the sheet, so the whole batch is saved as it stands.
     */
    suspend fun closeFlock(spreadsheetId: String, flockId: String): Boolean = withContext(Dispatchers.IO) {
        val flock = flockDao.getFlockById(spreadsheetId, flockId) ?: return@withContext false
        recomputeFlock(spreadsheetId, flockId)
        ioLock.withLock { resendDirtyDays(spreadsheetId) }
        val closed = flock.copy(status = "closed", locked = true)
        flockDao.updateFlock(closed)
        cloudPush("Batch \"${flock.name}\" closed") { it.upsertFlock(spreadsheetId, closed) }
    }

    suspend fun setFlockLocked(spreadsheetId: String, flockId: String, locked: Boolean) = withContext(Dispatchers.IO) {
        val flock = flockDao.getFlockById(spreadsheetId, flockId) ?: return@withContext
        val updated = flock.copy(locked = locked)
        flockDao.updateFlock(updated)
        cloudPush(if (locked) "Flock locked" else "Flock unlocked") { it.upsertFlock(spreadsheetId, updated) }
    }

    suspend fun setFarmLocked(spreadsheetId: String, locked: Boolean) = withContext(Dispatchers.IO) {
        val reg = farmRegistryDao.getFarm(spreadsheetId) ?: return@withContext
        farmRegistryDao.insertOrUpdate(reg.copy(locked = locked))
    }

    suspend fun deleteFlock(spreadsheetId: String, flockId: String) = withContext(Dispatchers.IO) {
        dailyDataDao.deleteDailyDataForFlock(spreadsheetId, flockId)
        taskDao.deleteTasksForFlock(spreadsheetId, flockId)
        flockDao.deleteFlock(spreadsheetId, flockId)
    }

    // ---- Recycle bin (soft delete + restore) ----
    suspend fun softDeleteFlock(spreadsheetId: String, flockId: String) = withContext(Dispatchers.IO) {
        flockDao.setFlockDeleted(spreadsheetId, flockId, true, System.currentTimeMillis())
    }
    suspend fun restoreFlock(spreadsheetId: String, flockId: String) = withContext(Dispatchers.IO) {
        flockDao.setFlockDeleted(spreadsheetId, flockId, false, 0L)
    }
    suspend fun softDeleteFarm(spreadsheetId: String) = withContext(Dispatchers.IO) {
        farmRegistryDao.setFarmDeleted(spreadsheetId, true, System.currentTimeMillis())
    }
    suspend fun restoreFarm(spreadsheetId: String) = withContext(Dispatchers.IO) {
        farmRegistryDao.setFarmDeleted(spreadsheetId, false, 0L)
    }

    /** Removes a farm and all its local data (the Google Sheet itself, if any, is left intact). */
    suspend fun deleteFarm(spreadsheetId: String) = withContext(Dispatchers.IO) {
        dailyDataDao.deleteAllDailyData(spreadsheetId)
        taskDao.deleteAllTasks(spreadsheetId)
        flockDao.deleteAllFlocks(spreadsheetId)
        feedTypeDao.deleteAllFeedTypes(spreadsheetId)
        configDao.deleteConfig(spreadsheetId)
        farmDao.deleteFarm(spreadsheetId)
        farmRegistryDao.deleteFarm(spreadsheetId)
    }

    fun getFlockTasksFlow(spreadsheetId: String, flockId: String): Flow<List<TaskEntity>> =
        taskDao.getTasksForFlock(spreadsheetId, flockId)

    suspend fun getFlockTasks(spreadsheetId: String, flockId: String): List<TaskEntity> =
        withContext(Dispatchers.IO) { taskDao.getTasksList(spreadsheetId, flockId) }

    /** Insert or update a task (upsert), pushing the change to the sheet. */
    suspend fun upsertTask(task: TaskEntity) = withContext(Dispatchers.IO) {
        taskDao.insertTask(task)
        cloudPush("Task") { it.pushTask(task.spreadsheetId, task) }
    }

    suspend fun addTask(task: TaskEntity) = upsertTask(task)

    /** Mark a task done/undone on a specific flock-day. */
    suspend fun setTaskCompleted(spreadsheetId: String, taskId: String, day: Int, done: Boolean) =
        withContext(Dispatchers.IO) {
            val t = taskDao.getTaskById(spreadsheetId, taskId) ?: return@withContext
            upsertTask(t.withCompletion(day, done))
        }

    suspend fun deleteTask(spreadsheetId: String, taskId: String) = withContext(Dispatchers.IO) {
        taskDao.deleteTask(spreadsheetId, taskId)
        cloudPush("Task removed") { it.deleteTaskRow(spreadsheetId, taskId) }
    }

    suspend fun fetchWeather(farm: FarmEntity): WeatherResult = withContext(Dispatchers.IO) {
        WeatherClient.fetchWeather(
            lat = farm.weatherLat,
            lon = farm.weatherLon,
            locationName = farm.weatherName,
            season = farm.season
        )
    }

    suspend fun fetchForecast(farm: FarmEntity): ForecastResult = withContext(Dispatchers.IO) {
        WeatherClient.fetchForecast(farm.weatherLat, farm.weatherLon, farm.weatherName)
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
     * Saves a day's inputs. Nothing locks with the clock or once saved: any day up to today can be changed (a closed or
     * locked flock or farm cannot). [at] is when what was entered happened (the weighing, the lifting) — now unless an
     * earlier time was picked. Only the columns this save changes are sent to the sheet.
     */
    suspend fun saveDayEntry(
        spreadsheetId: String,
        flockId: String,
        dayNumber: Int,
        userEmail: String,
        entered: Set<String> = emptySet(),
        at: Long? = null,
        updateAction: (DailyDataEntity) -> DailyDataEntity
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val flock = flockDao.getFlockById(spreadsheetId, flockId)
            ?: return@withContext Result.failure(Exception("Flock not found"))
        if (flock.locked) {
            return@withContext Result.failure(IllegalStateException("This flock is locked. Unlock it to edit."))
        }
        if (farmRegistryDao.getFarm(spreadsheetId)?.locked == true) {
            return@withContext Result.failure(IllegalStateException("This farm is locked. Unlock it to edit."))
        }
        ioLock.withLock {
            val existing = dailyDataDao.getDayEntry(spreadsheetId, flockId, dayNumber)
                ?: return@withLock Result.failure(Exception("Day entry not found"))
            val modified = updateAction(existing)
            val changed = SheetSchema.changedColumns(existing, modified)
            val curDay = calculateCurrentDay(flock.startDate, getFarm(spreadsheetId).timeZone)
            if (changed.isNotEmpty() && dayNumber > curDay) {
                return@withLock Result.failure(IllegalStateException("Day $dayNumber has not come yet — nothing to enter."))
            }
            val hasSample = modified.filledSamples().isNotEmpty() || PhysiologicalEngine.parseWeights(modified.indivWeights).isNotEmpty()
            val weightsChanged = changed.any { SheetSchema.isWeightColumn(it) }
            val liftChanged = "birdslifted" in changed || "weightlifted" in changed
            val now = System.currentTimeMillis()
            val stamp = at?.takeIf { it in 1..(now + 60_000) } ?: now
            val groups = existing.savedGroups().toMutableSet().apply {
                if (hasSample) add("W") else remove("W")
                if ("M" in entered) add("M")
                if ("F" in entered || dayNumber == 0) add("F")
            }
            val stamped = modified.copy(
                // when the weights were taken: the time-based projections start from here
                weighedAt = if (!hasSample) 0L else if (weightsChanged || existing.weighedAt == 0L) stamp else existing.weighedAt,
                liftedAt = if (modified.birdsLifted <= 0) 0L else if (liftChanged || existing.liftedAt == 0L) stamp else existing.liftedAt,
                sampleEntered = hasSample,
                savedFields = listOf("W", "M", "F").filter { it in groups }.joinToString(","),
                committed = groups.containsAll(listOf("W", "M", "F")),
                updatedAt = now,
                updatedBy = userEmail
            )
            val toSave = stamped.copy(dirtyFields = mergeDirtyFields(existing.dirty, existing.dirtyFields, SheetSchema.changedColumns(existing, stamped)))
            saveAndPushDay("Day $dayNumber", spreadsheetId, toSave)
            Result.success(Unit)
        }
    }

    /** The feedings poured on a day ("HH:mm=bags;…"), sent to the sheet like any other entry. */
    suspend fun setFeedings(spreadsheetId: String, flockId: String, dayNumber: Int, feedings: String, userEmail: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            ioLock.withLock {
                val existing = dailyDataDao.getDayEntry(spreadsheetId, flockId, dayNumber)
                    ?: return@withLock Result.failure(Exception("Day entry not found"))
                if (existing.feedings == feedings) return@withLock Result.success(Unit)
                val row = existing.copy(feedings = feedings, updatedAt = System.currentTimeMillis(), updatedBy = userEmail)
                saveAndPushDay("Feeding", spreadsheetId, row.copy(dirtyFields = mergeDirtyFields(existing.dirty, existing.dirtyFields, setOf("feedings"))))
                Result.success(Unit)
            }
        }

    /**
     * Complete mathematical recalculation for a flock across its entire life-cycle.
     */
    suspend fun recomputeFlock(spreadsheetId: String, flockId: String) = withContext(Dispatchers.IO) {
        val flock = flockDao.getFlockById(spreadsheetId, flockId) ?: return@withContext
        val farm = getFarm(spreadsheetId)
        val config = getConfig(spreadsheetId)
        var rows = dailyDataDao.getDailyDataList(spreadsheetId, flockId).sortedBy { it.dayNumber }
        if (rows.isEmpty()) return@withContext
        // a flock that has run past its planned harvest age gets the days it is missing
        if (ensureDayRows(flock, farm.timeZone, rows)) rows = dailyDataDao.getDailyDataList(spreadsheetId, flockId).sortedBy { it.dayNumber }

        val kgOf = feedTypeDao.getFeedTypes(spreadsheetId).associate { it.code to it.bagKg }
        dailyDataDao.insertDailyData(FlockCalc.compute(flock, farm, config, rows, kgOf))
    }

    suspend fun getFeedStockSummary(spreadsheetId: String, flockId: String): FeedStockSummary = withContext(Dispatchers.IO) {
        val rows = dailyDataDao.getDailyDataList(spreadsheetId, flockId)
        var totalRec = 0.0
        var totalUsed = 0.0
        val recPerType = mutableMapOf<String, Double>()
        val usedPerType = mutableMapOf<String, Double>()

        for (r in rows) {
            totalUsed += r.feedBagsUsed
            val usedRows = parseFeedBreakdown(r.feedUsedBreakdown)
            if (usedRows.isNotEmpty()) {
                for ((code, bags) in usedRows) if (bags > 0) {
                    usedPerType[code] = (usedPerType[code] ?: 0.0) + bags
                }
            } else if (r.feedBagsUsed > 0 && r.feedUsedType.isNotBlank()) {
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
