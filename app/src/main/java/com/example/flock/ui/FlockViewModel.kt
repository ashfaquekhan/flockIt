package com.example.flock.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flock.data.ConfigEntity
import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FarmEntity
import com.example.flock.data.FarmRegistryEntity
import com.example.flock.data.FeedStockSummary
import com.example.flock.data.FeedTypeEntity
import com.example.flock.data.FlockDatabase
import com.example.flock.data.FlockEntity
import com.example.flock.data.FlockRepository
import com.example.flock.data.TaskEntity
import com.example.flock.network.ForecastResult
import com.example.flock.network.WeatherResult
import com.example.flock.notify.TaskNotify
import com.example.flock.sync.AuthUserState
import com.example.flock.sync.GoogleAuthManager
import com.example.flock.sync.SheetsSyncManager
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

data class LockStatus(
    val isPastDay: Boolean,
    val isToday: Boolean,
    val isFuture: Boolean,
    val isHardLocked: Boolean,
    val cutoffTime: String,
    val isCutoffApproaching: Boolean,
    val lockReason: String = ""
)

/** Top-level screen the user is on once signed in. */
enum class AppScreen { FARMS, FLOCKS, DASHBOARD }

/** All editable daily inputs, passed as one object from the Entry screen. */
data class DailyInputs(
    val w1: Double?, val n1: Int?, val w2: Double?, val n2: Int?, val w3: Double?, val n3: Int?,
    val w4: Double?, val n4: Int?, val w5: Double?, val n5: Int?,
    val mortality: Int, val feedBagsUsed: Double, val feedUsedType: String,
    val feedUsedBreakdown: String = "",
    val birdsLifted: Int, val weightLifted: Double, val lameSeparated: Int,
    val feedRecB1: Double, val feedTypeB1: String,
    val feedRecB2: Double, val feedTypeB2: String,
    val feedRecB3: Double, val feedTypeB3: String,
    val broodingLength: Double?, val actualFans: Int?, val actualFanTime: Int?,
    val outTemp: Double?, val outRH: Double?, val notes: String,
    val waterTempC: Double? = null, val waterPh: Double? = null, val feedMoisturePct: Double? = null,
    val measuredCo2: Double? = null, val measuredNh3: Double? = null, val measuredO2: Double? = null,
    val measuredPressure: Double? = null, val measuredAirspeed: Double? = null,
    val padWetMin: Double? = null, val padDryMin: Double? = null, val luxPerFt2: Double? = null,
    val dieselCansUsed: Double = 0.0
)

class FlockViewModel(application: Application) : AndroidViewModel(application) {

    private val database = FlockDatabase.getDatabase(application, viewModelScope)
    val repository = FlockRepository(database)
    val authManager = GoogleAuthManager(application)
    val syncManager = SheetsSyncManager(authManager, database)

    val authState: StateFlow<AuthUserState> = authManager.authState

    val farms: StateFlow<List<FarmRegistryEntity>> = repository.allFarms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedSpreadsheetId = MutableStateFlow("local_default")
    val selectedSpreadsheetId: StateFlow<String> = _selectedSpreadsheetId.asStateFlow()

    private val _farm = MutableStateFlow(FarmEntity())
    val farm: StateFlow<FarmEntity> = _farm.asStateFlow()

    private val _config = MutableStateFlow(ConfigEntity())
    val config: StateFlow<ConfigEntity> = _config.asStateFlow()

    private val _feedTypes = MutableStateFlow<List<FeedTypeEntity>>(emptyList())
    val feedTypes: StateFlow<List<FeedTypeEntity>> = _feedTypes.asStateFlow()

    private val _flocks = MutableStateFlow<List<FlockEntity>>(emptyList())
    val flocks: StateFlow<List<FlockEntity>> = _flocks.asStateFlow()

    private val _activeFlock = MutableStateFlow<FlockEntity?>(null)
    val activeFlock: StateFlow<FlockEntity?> = _activeFlock.asStateFlow()

    private val _selectedDay = MutableStateFlow(0)
    val selectedDay: StateFlow<Int> = _selectedDay.asStateFlow()

    private val _dailyRows = MutableStateFlow<List<DailyDataEntity>>(emptyList())
    val dailyRows: StateFlow<List<DailyDataEntity>> = _dailyRows.asStateFlow()

    private val _currentDayEntry = MutableStateFlow<DailyDataEntity?>(null)
    val currentDayEntry: StateFlow<DailyDataEntity?> = _currentDayEntry.asStateFlow()

    private val _tasks = MutableStateFlow<List<TaskEntity>>(emptyList())
    val tasks: StateFlow<List<TaskEntity>> = _tasks.asStateFlow()

    private val _feedStockSummary = MutableStateFlow(
        FeedStockSummary(0.0, 0.0, 0.0, emptyMap())
    )
    val feedStockSummary: StateFlow<FeedStockSummary> = _feedStockSummary.asStateFlow()

    private val _weather = MutableStateFlow<WeatherResult?>(null)
    val weather: StateFlow<WeatherResult?> = _weather.asStateFlow()

    private val _forecast = MutableStateFlow<ForecastResult?>(null)
    val forecast: StateFlow<ForecastResult?> = _forecast.asStateFlow()
    private val _showForecast = MutableStateFlow(false)
    val showForecast: StateFlow<Boolean> = _showForecast.asStateFlow()

    private val _syncStatus = MutableStateFlow("synced")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    // Pull-to-refresh spinner state (shared by all pages).
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // When false, the daily cut-off timer does NOT hard-lock today's inputs (user override).
    private val _cutoffLockEnabled = MutableStateFlow(true)
    val cutoffLockEnabled: StateFlow<Boolean> = _cutoffLockEnabled.asStateFlow()

    private val _lockStatus = MutableStateFlow(
        LockStatus(
            isPastDay = false,
            isToday = true,
            isFuture = false,
            isHardLocked = false,
            cutoffTime = "11:00",
            isCutoffApproaching = false
        )
    )
    val lockStatus: StateFlow<LockStatus> = _lockStatus.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    // Top-level navigation: the user lands on FARMS after sign-in and drills in explicitly.
    private val _appScreen = MutableStateFlow(AppScreen.FARMS)
    val appScreen: StateFlow<AppScreen> = _appScreen.asStateFlow()

    private var farmDataJob: Job? = null
    private var flockDataJob: Job? = null

    init {
        // The repository is the workspace gateway; give it the cloud layer so every
        // mutation persists to Google Sheets and surfaces a verified sync note.
        repository.sync = syncManager
        viewModelScope.launch {
            repository.syncNote.collect { note -> if (note != null) _userMessage.value = note }
        }

        // Returning signed-in users: refresh the Drive/Sheets token and pull their farms
        // (owned + accepted-shared) from the appDataFolder index so they reappear after
        // logout / on a new device.
        if (authState.value.isSignedIn && !authState.value.isDemoMode) {
            viewModelScope.launch {
                authManager.refreshAccessToken()
                _syncStatus.value = "syncing"
                val ok = runCatching { runFullSync() }.isSuccess
                _syncStatus.value = if (ok) "synced" else "offline"
            }
        }
    }

    /**
     * Full cloud sync used on login / return / new-device:
     *   1) index (control plane, appDataFolder) — fast, cross-device, includes accepted shared farms
     *   2) legacy Drive discovery — catches owned sheets not yet in the index (migration)
     *   3) backfill the index from whatever is now known locally
     * Returns the count of farms newly imported into the local registry.
     */
    private suspend fun runFullSync(): Int {
        var imported = 0
        val fromIndex = syncManager.syncFromIndex()
        if (fromIndex.isSuccess) imported += fromIndex.getOrNull() ?: 0
        val legacy = syncManager.syncUserFarmsFromDrive()
        if (legacy.isSuccess) imported += legacy.getOrNull() ?: 0
        syncManager.backfillIndexFromRegistry()
        return imported
    }

    /** Pull-to-refresh on the Farms list: re-sync the whole account (index + owned + shared). */
    fun refreshFarms() {
        if (authState.value.isDemoMode) { _isRefreshing.value = false; return }
        viewModelScope.launch {
            _isRefreshing.value = true
            _syncStatus.value = "syncing"
            authManager.refreshAccessToken()
            val imported = runCatching { runFullSync() }.getOrDefault(0)
            _syncStatus.value = "synced"
            if (imported > 0) _userMessage.value = "Loaded $imported farm(s) from your account"
            _isRefreshing.value = false
        }
    }

    /** Pull-to-refresh on a farm/flock/dashboard: pull the sheet + weather for the open farm. */
    fun refreshCurrentFarm() {
        val id = _selectedSpreadsheetId.value
        viewModelScope.launch {
            _isRefreshing.value = true
            if (!authState.value.isDemoMode && id != "local_default") {
                _syncStatus.value = "syncing"
                runCatching { repository.refreshFromCloud(id) }
                _syncStatus.value = "synced"
            }
            refreshWeather()
            _isRefreshing.value = false
        }
    }

    // No auto-selection of a farm/flock: the user opens one explicitly from the lists.

    /** Google Sign-In client for the real OAuth flow (launched from the Activity). */
    fun googleSignInClient() = authManager.getGoogleSignInClient()

    fun goToFarms() {
        _appScreen.value = AppScreen.FARMS
    }

    fun openFarm(spreadsheetId: String) {
        selectFarm(spreadsheetId)
        _appScreen.value = AppScreen.FLOCKS
    }

    fun goToFlocks() {
        _appScreen.value = AppScreen.FLOCKS
    }

    fun openFlock(flockId: String) {
        selectFlock(flockId)
        _appScreen.value = AppScreen.DASHBOARD
        // Auto-refresh from the sheet when a flock is opened (collaborators' latest data).
        refreshCurrentFarm()
    }

    fun selectFarm(spreadsheetId: String) {
        _selectedSpreadsheetId.value = spreadsheetId
        loadFarmData(spreadsheetId)
        // Pull the latest workspace from the sheet (collaborators' changes, cross-device).
        if (authState.value.isSignedIn && !authState.value.isDemoMode && spreadsheetId != "local_default") {
            viewModelScope.launch {
                _syncStatus.value = "syncing"
                repository.refreshFromCloud(spreadsheetId)
                _syncStatus.value = "synced"
            }
        }
    }

    private fun loadFarmData(spreadsheetId: String) {
        farmDataJob?.cancel()
        farmDataJob = viewModelScope.launch {
            // Load Farm
            launch {
                repository.getFarmFlow(spreadsheetId).collect { f ->
                    if (f != null) {
                        _farm.value = f
                        refreshWeather(f)
                        // Cut-off time lives on the farm, so re-evaluate the lock whenever it changes.
                        updateLockStatus()
                    }
                }
            }

            // Load Config
            launch {
                repository.getConfigFlow(spreadsheetId).collect { c ->
                    if (c != null) _config.value = c
                }
            }

            // Load FeedTypes
            launch {
                repository.getFeedTypesFlow(spreadsheetId).collect { ft ->
                    _feedTypes.value = ft
                }
            }

            // Load Flocks
            launch {
                repository.getFlocksFlow(spreadsheetId).collect { fl ->
                    _flocks.value = fl
                    val currentActive = _activeFlock.value
                    if (currentActive == null || fl.none { it.flockId == currentActive.flockId }) {
                        val firstActive = fl.firstOrNull { it.status == "active" } ?: fl.firstOrNull()
                        if (firstActive != null) {
                            selectFlock(firstActive.flockId)
                        } else {
                            _activeFlock.value = null
                            _dailyRows.value = emptyList()
                            _currentDayEntry.value = null
                        }
                    }
                }
            }
        }
    }

    fun selectFlock(flockId: String) {
        flockDataJob?.cancel()
        flockDataJob = viewModelScope.launch {
            val spreadsheetId = _selectedSpreadsheetId.value
            val flock = _flocks.value.firstOrNull { it.flockId == flockId }
                ?: repository.getFlocksFlow(spreadsheetId).firstOrNull()?.firstOrNull { it.flockId == flockId }

            _activeFlock.value = flock
            if (flock == null) return@launch

            // Calculate current real flock day in farm's timezone
            val curDay = repository.calculateCurrentDay(flock.startDate, _farm.value.timeZone)
            val clampedDay = curDay.coerceIn(0, flock.harvestAge)
            _selectedDay.value = clampedDay

            // Collect DailyData
            launch {
                repository.getDailyDataFlow(spreadsheetId, flockId).collect { rows ->
                    _dailyRows.value = rows
                    _currentDayEntry.value = rows.firstOrNull { it.dayNumber == _selectedDay.value }
                    updateLockStatus()
                    refreshFeedStock()
                }
            }

            // Collect ALL tasks for the flock (the planner filters by day) + (re)schedule alerts.
            launch {
                repository.getFlockTasksFlow(spreadsheetId, flockId).collect { t ->
                    _tasks.value = t
                    rescheduleAlerts()
                }
            }

            // Pull in any completions the user tapped from a notification while the app was closed.
            ingestNotificationCompletions()
        }
    }

    fun selectDay(day: Int) {
        val flock = _activeFlock.value ?: return
        val clamped = day.coerceIn(0, flock.harvestAge)
        _selectedDay.value = clamped
        _currentDayEntry.value = _dailyRows.value.firstOrNull { it.dayNumber == clamped }
        updateLockStatus()
    }

    private fun updateLockStatus() {
        val flock = _activeFlock.value ?: return
        val farm = _farm.value
        val day = _selectedDay.value

        try {
            val zone = ZoneId.of(farm.timeZone)
            val today = LocalDate.now(zone)
            val start = LocalDate.parse(flock.startDate, DateTimeFormatter.ISO_LOCAL_DATE)
            val curDay = java.time.temporal.ChronoUnit.DAYS.between(start, today).toInt()

            val isPast = day < curDay
            val isToday = day == curDay
            val isFuture = day > curDay

            val cutoffParts = farm.cutoffTime.split(":")
            val cutoffHour = cutoffParts.getOrNull(0)?.toIntOrNull() ?: 11
            val cutoffMin = cutoffParts.getOrNull(1)?.toIntOrNull() ?: 0
            val cutoff = LocalTime.of(cutoffHour, cutoffMin)
            val now = LocalTime.now(zone)

            val cutoffPassed = _cutoffLockEnabled.value && isToday && now.isAfter(cutoff)
            val isHardLocked = isPast || isFuture || cutoffPassed

            val isApproaching = _cutoffLockEnabled.value && isToday && !cutoffPassed && now.isAfter(cutoff.minusMinutes(45))

            val reason = when {
                isPast -> "Past days are read-only. Direct sheet edit required."
                isFuture -> "Future day — inputs not allowed."
                cutoffPassed -> "Inputs locked: ${farm.cutoffTime} cut-off has passed for today."
                else -> ""
            }

            _lockStatus.value = LockStatus(
                isPastDay = isPast,
                isToday = isToday,
                isFuture = isFuture,
                isHardLocked = isHardLocked,
                cutoffTime = farm.cutoffTime,
                isCutoffApproaching = isApproaching,
                lockReason = reason
            )
        } catch (e: Exception) {
            _lockStatus.value = LockStatus(
                isPastDay = false,
                isToday = true,
                isFuture = false,
                isHardLocked = false,
                cutoffTime = "11:00",
                isCutoffApproaching = false
            )
        }
    }

    private fun refreshFeedStock() {
        val flock = _activeFlock.value ?: return
        viewModelScope.launch {
            val summary = repository.getFeedStockSummary(_selectedSpreadsheetId.value, flock.flockId)
            _feedStockSummary.value = summary
        }
    }

    fun refreshWeather(farm: FarmEntity = _farm.value) {
        viewModelScope.launch {
            val w = repository.fetchWeather(farm)
            _weather.value = w
        }
    }

    /** Opens the 14-day forecast popup for the farm's configured location. */
    fun openWeatherForecast() {
        val farm = _farm.value
        if (farm.weatherLat == 0.0 && farm.weatherLon == 0.0) {
            _userMessage.value = "Set your farm location in settings to see the forecast."
            return
        }
        _forecast.value = null
        _showForecast.value = true
        viewModelScope.launch { _forecast.value = repository.fetchForecast(farm) }
    }

    fun closeWeatherForecast() { _showForecast.value = false }

    /** Backs up the farm's spreadsheet then rewrites it to the current schema (for old sheets). */
    fun repairFarmSheet(spreadsheetId: String) {
        viewModelScope.launch {
            _syncStatus.value = "syncing"
            _userMessage.value = "Backing up & repairing the sheet…"
            val res = syncManager.backupAndRepairFarm(spreadsheetId)
            _syncStatus.value = if (res.isSuccess) "synced" else "offline"
            _userMessage.value = if (res.isSuccess) "Sheet repaired ✓ (backup: ${res.getOrNull()})"
                else "Repair failed: ${res.exceptionOrNull()?.message ?: "unknown"}"
        }
    }

    /** Enable/disable the daily cut-off timer lock for entering data. */
    fun toggleCutoffLock() {
        _cutoffLockEnabled.value = !_cutoffLockEnabled.value
        updateLockStatus()
        _userMessage.value = if (_cutoffLockEnabled.value) "Cut-off timer lock ON" else "Cut-off timer lock OFF — you can edit past the cut-off"
    }

    /** Safely clears the currently-selected day's entries (and unlocks it). */
    fun revertDay() {
        val flock = _activeFlock.value ?: return
        val day = _selectedDay.value
        viewModelScope.launch {
            val res = repository.revertDay(_selectedSpreadsheetId.value, flock.flockId, day, authState.value.email)
            _userMessage.value = if (res.isSuccess) "Day $day reverted — you can re-enter it" else (res.exceptionOrNull()?.message ?: "Could not revert")
        }
    }

    fun saveDayEntry(
        inputs: DailyInputs,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val flock = _activeFlock.value ?: return
        val day = _selectedDay.value
        val userEmail = authState.value.email

        viewModelScope.launch {
            val result = repository.saveDayEntry(
                spreadsheetId = _selectedSpreadsheetId.value,
                flockId = flock.flockId,
                dayNumber = day,
                userEmail = userEmail
            ) { existing ->
                existing.copy(
                    w1 = inputs.w1, n1 = inputs.n1,
                    w2 = inputs.w2, n2 = inputs.n2,
                    w3 = inputs.w3, n3 = inputs.n3,
                    w4 = inputs.w4, n4 = inputs.n4,
                    w5 = inputs.w5, n5 = inputs.n5,
                    mortality = inputs.mortality,
                    feedBagsUsed = if (day == 0) 0.0 else inputs.feedBagsUsed,
                    feedUsedType = inputs.feedUsedType,
                    feedUsedBreakdown = if (day == 0) "" else inputs.feedUsedBreakdown,
                    birdsLifted = inputs.birdsLifted,
                    weightLifted = inputs.weightLifted,
                    lameSeparated = inputs.lameSeparated,
                    feedRecB1 = inputs.feedRecB1,
                    feedTypeB1 = inputs.feedTypeB1,
                    feedRecB2 = inputs.feedRecB2,
                    feedTypeB2 = inputs.feedTypeB2,
                    feedRecB3 = inputs.feedRecB3,
                    feedTypeB3 = inputs.feedTypeB3,
                    broodingLength = inputs.broodingLength,
                    actualFans = inputs.actualFans,
                    actualFanTime = inputs.actualFanTime,
                    outTemp = inputs.outTemp,
                    outRH = inputs.outRH,
                    notes = inputs.notes,
                    waterTempC = inputs.waterTempC,
                    waterPh = inputs.waterPh,
                    feedMoisturePct = inputs.feedMoisturePct,
                    measuredCo2 = inputs.measuredCo2,
                    measuredNh3 = inputs.measuredNh3,
                    measuredO2 = inputs.measuredO2,
                    measuredPressure = inputs.measuredPressure,
                    measuredAirspeed = inputs.measuredAirspeed,
                    padWetMin = inputs.padWetMin,
                    padDryMin = inputs.padDryMin,
                    luxPerFt2 = inputs.luxPerFt2,
                    dieselCansUsed = inputs.dieselCansUsed
                )
            }

            result.onSuccess {
                _userMessage.value = "Saved Day $day"
                onSuccess()
            }.onFailure { err ->
                _userMessage.value = err.message ?: "Failed to save entry"
                onError(err.message ?: "Failed to save entry")
            }
        }
    }

    /** Save a new or edited planner task. Pass an existing taskId to edit, or null to create. */
    fun saveTask(
        taskId: String?,
        block: String,
        label: String,
        time: String,
        startDay: Int,
        endDay: Int,
        recurrence: String,
        everyN: Int,
        alertEnabled: Boolean,
        kind: String
    ) {
        val flock = _activeFlock.value ?: return
        val spreadsheetId = _selectedSpreadsheetId.value
        viewModelScope.launch {
            val existing = taskId?.let { id -> _tasks.value.firstOrNull { it.taskId == id } }
            val task = (existing ?: TaskEntity(
                spreadsheetId = spreadsheetId,
                taskId = "tsk_" + System.currentTimeMillis(),
                flockId = flock.flockId,
                block = block, label = label, time = time
            )).copy(
                block = block, label = label, time = time,
                startDay = startDay, endDay = endDay, recurrence = recurrence, everyN = everyN,
                alertEnabled = alertEnabled, kind = kind,
                everyDay = recurrence == "daily", dayNumber = if (recurrence == "once") startDay else null
            )
            repository.upsertTask(task)
            rescheduleAlerts()
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteTask(_selectedSpreadsheetId.value, taskId)
            rescheduleAlerts()
        }
    }

    /** Toggle completion of a task on a specific flock-day (used by the hold-to-complete control). */
    fun toggleTaskComplete(taskId: String, day: Int, done: Boolean) {
        viewModelScope.launch {
            repository.setTaskCompleted(_selectedSpreadsheetId.value, taskId, day, done)
        }
    }

    fun toggleTaskAlert(taskId: String, enabled: Boolean) {
        viewModelScope.launch {
            val t = _tasks.value.firstOrNull { it.taskId == taskId } ?: return@launch
            repository.upsertTask(t.copy(alertEnabled = enabled))
            rescheduleAlerts()
            _userMessage.value = if (enabled) "Alerts on for \"${t.label}\"" else "Alerts off for \"${t.label}\""
        }
    }

    /** Duplicate a task across a day-range as a daily task (quick "copy to other days"). */
    fun copyTaskToRange(taskId: String, fromDay: Int, toDay: Int) {
        val flock = _activeFlock.value ?: return
        viewModelScope.launch {
            val t = _tasks.value.firstOrNull { it.taskId == taskId } ?: return@launch
            val copy = t.copy(
                taskId = "tsk_" + System.currentTimeMillis(),
                startDay = fromDay.coerceIn(0, flock.harvestAge),
                endDay = toDay.coerceIn(0, flock.harvestAge),
                recurrence = "daily", everyDay = true, dayNumber = null, completedDays = ""
            )
            repository.upsertTask(copy)
            rescheduleAlerts()
            _userMessage.value = "Copied to days $fromDay–$toDay"
        }
    }

    private fun rescheduleAlerts() {
        val flock = _activeFlock.value ?: return
        val curDay = repository.calculateCurrentDay(flock.startDate, _farm.value.timeZone)
        TaskNotify.scheduleUpcoming(
            getApplication(),
            _selectedSpreadsheetId.value, flock.flockId,
            _tasks.value, curDay, flock.harvestAge
        )
    }

    private fun ingestNotificationCompletions() {
        viewModelScope.launch {
            val done = TaskNotify.drainCompletedInbox(getApplication())
            for ((sid, tid, day) in done) repository.setTaskCompleted(sid, tid, day, true)
        }
    }

    fun createFarm(name: String, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            _syncStatus.value = "syncing"
            val newFarm = FarmEntity(farmName = name)
            val newConfig = ConfigEntity()
            val newFeedTypes = listOf(
                FeedTypeEntity(code = "B1", name = "Pre-starter", bagKg = 60.0, phase = "starter", sortOrder = 1),
                FeedTypeEntity(code = "B2", name = "Starter", bagKg = 60.0, phase = "grower", sortOrder = 2),
                FeedTypeEntity(code = "B3", name = "Finisher", bagKg = 60.0, phase = "finisher", sortOrder = 3)
            )

            val res = syncManager.createFarmSpreadsheet(
                farmName = name,
                farm = newFarm,
                config = newConfig,
                feedTypes = newFeedTypes
            )

            res.onSuccess { newId ->
                _syncStatus.value = "synced"
                openFarm(newId)
                _userMessage.value = "Farm created: $name"
                onComplete(newId)
            }.onFailure { err ->
                _syncStatus.value = "offline"
                _userMessage.value = "Created local farm: ${err.message}"
            }
        }
    }

    fun shareFarm(spreadsheetId: String, email: String, isEditor: Boolean, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = syncManager.shareFarm(spreadsheetId, email, isEditor)
            res.onSuccess {
                _userMessage.value = "Farm shared with $email as ${if (isEditor) "Editor" else "Viewer"}"
                onResult(true, "Shared successfully")
            }.onFailure { err ->
                val msg = err.message ?: "Failed to share"
                _userMessage.value = msg
                onResult(false, msg)
            }
        }
    }

    fun updateFarmSettings(farm: FarmEntity, config: ConfigEntity) {
        viewModelScope.launch {
            repository.updateFarm(farm)
            repository.updateConfig(config)
            _farm.value = farm
            _config.value = config
            updateLockStatus()  // apply a changed cut-off time immediately, everywhere
            val flock = _activeFlock.value
            if (flock != null) {
                repository.recomputeFlock(_selectedSpreadsheetId.value, flock.flockId)
            }
            _userMessage.value = "Farm settings saved"
        }
    }

    fun saveFeedType(feedType: FeedTypeEntity) {
        viewModelScope.launch {
            repository.saveFeedType(feedType)
        }
    }

    fun deleteFeedType(code: String) {
        viewModelScope.launch {
            repository.deleteFeedType(_selectedSpreadsheetId.value, code)
        }
    }

    fun createFlock(
        name: String,
        breed: String,
        startDate: String,
        birdsPlaced: Int,
        receptionMort: Int = 0,
        targetWeight: Double = 3200.0,
        harvestAge: Int = 42,
        season: String = "Monsoon",
        startTime: String = "08:00",
        onComplete: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val id = repository.createFlock(
                spreadsheetId = _selectedSpreadsheetId.value,
                name = name,
                breed = breed,
                startDate = startDate,
                birdsPlaced = birdsPlaced,
                receptionMort = receptionMort,
                targetWeight = targetWeight,
                harvestAge = harvestAge,
                season = season,
                startTime = startTime
            )
            openFlock(id)
            _userMessage.value = "Batch $name created"
            onComplete(id)
        }
    }

    fun closeFlock(flockId: String) {
        viewModelScope.launch {
            repository.closeFlock(_selectedSpreadsheetId.value, flockId)
            _userMessage.value = "Flock archived"
        }
    }

    // Recycle bin flows
    val deletedFarms: StateFlow<List<FarmRegistryEntity>> = repository.deletedFarms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val deletedFlocks: StateFlow<List<FlockEntity>> = repository.deletedFlocks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteFlock(flockId: String) {
        viewModelScope.launch {
            repository.softDeleteFlock(_selectedSpreadsheetId.value, flockId)
            if (_activeFlock.value?.flockId == flockId) _activeFlock.value = null
            _userMessage.value = "Flock moved to Recycle Bin"
        }
    }

    fun restoreFlock(spreadsheetId: String, flockId: String) {
        viewModelScope.launch {
            repository.restoreFlock(spreadsheetId, flockId)
            _userMessage.value = "Flock restored"
        }
    }

    fun purgeFlock(spreadsheetId: String, flockId: String) {
        viewModelScope.launch {
            repository.deleteFlock(spreadsheetId, flockId)
            _userMessage.value = "Flock permanently deleted"
        }
    }

    fun restoreFarm(spreadsheetId: String) {
        viewModelScope.launch {
            repository.restoreFarm(spreadsheetId)
            if (!authState.value.isDemoMode) {
                runCatching { syncManager.setFarmDeletedInIndex(spreadsheetId, false) }
            }
            _userMessage.value = "Farm restored"
        }
    }

    fun purgeFarm(spreadsheetId: String) {
        viewModelScope.launch {
            repository.deleteFarm(spreadsheetId)
            if (!authState.value.isDemoMode) {
                runCatching { syncManager.removeFarmFromIndex(spreadsheetId) }
            }
            _userMessage.value = "Farm permanently deleted"
        }
    }

    fun toggleFlockLock(flockId: String, locked: Boolean) {
        viewModelScope.launch {
            repository.setFlockLocked(_selectedSpreadsheetId.value, flockId, locked)
            _userMessage.value = if (locked) "Flock locked (read-only)" else "Flock unlocked"
        }
    }

    fun toggleFarmLock(spreadsheetId: String, locked: Boolean) {
        viewModelScope.launch {
            repository.setFarmLocked(spreadsheetId, locked)
            _userMessage.value = if (locked) "Farm locked (read-only)" else "Farm unlocked"
        }
    }

    fun deleteFarm(spreadsheetId: String) {
        viewModelScope.launch {
            repository.softDeleteFarm(spreadsheetId)
            if (!authState.value.isDemoMode) {
                runCatching { syncManager.setFarmDeletedInIndex(spreadsheetId, true) }
            }
            if (_selectedSpreadsheetId.value == spreadsheetId) {
                _selectedSpreadsheetId.value = "local_default"
                _activeFlock.value = null
            }
            _appScreen.value = AppScreen.FARMS
            _userMessage.value = "Farm moved to Recycle Bin"
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun openSharedFarm(input: String) {
        val spreadsheetId = extractSpreadsheetId(input)
        if (spreadsheetId.isBlank()) return
        viewModelScope.launch {
            _syncStatus.value = "syncing"
            val validation = syncManager.validateCompatibility(spreadsheetId)
            if (validation.isFailure || validation.getOrNull() != true) {
                _syncStatus.value = "offline"
                _userMessage.value =
                    "Couldn't open shared farm: ${validation.exceptionOrNull()?.message ?: "not a FlockIt farm, or no access"}"
                return@launch
            }

            // Register a placeholder, then pull the real name + flocks + daily data.
            repository.registerFarm(
                spreadsheetId = spreadsheetId,
                farmName = "Farm " + spreadsheetId.take(6),
                role = "Editor",
                isOwner = false
            )
            repository.refreshFromCloud(spreadsheetId)
            val finalName = repository.getFarmFlow(spreadsheetId).firstOrNull()?.farmName ?: ("Farm " + spreadsheetId.take(6))

            // Record the accepted shared farm in this user's index so it loads
            // automatically on all their devices from now on.
            syncManager.addFarmToIndex(spreadsheetId, finalName, role = "editor")
            _syncStatus.value = "synced"
            _userMessage.value = "Opened shared farm: $finalName"
            openFarm(spreadsheetId)
        }
    }

    /** Accepts a raw spreadsheet ID or a full Google Sheets URL and returns the ID. */
    private fun extractSpreadsheetId(input: String): String {
        val t = input.trim()
        return Regex("/d/([a-zA-Z0-9-_]+)").find(t)?.groupValues?.get(1) ?: t
    }

    fun signInWithGoogle() {
        enableDemoMode()
    }

    fun toggleDemoMode() {
        if (authState.value.isDemoMode) {
            signOut()
        } else {
            enableDemoMode()
        }
    }

    fun handleSignInResult(account: GoogleSignInAccount) {
        authManager.handleSignInResult(account)
        _appScreen.value = AppScreen.FARMS
        _userMessage.value = "Signed in as ${account.email}"
        viewModelScope.launch {
            authManager.refreshAccessToken()
            _syncStatus.value = "syncing"
            val imported = runCatching { runFullSync() }.getOrDefault(0)
            _syncStatus.value = "synced"
            if (imported > 0) {
                _userMessage.value = "Loaded $imported farm(s) from your FlockIt account"
            }
        }
    }

    fun handleSignInError(message: String) {
        _userMessage.value = message
    }

    fun enableDemoMode() {
        authManager.enableDemoMode()
        _appScreen.value = AppScreen.FARMS
        _userMessage.value = "Using offline mode (local only)"
    }

    fun signOut() {
        authManager.signOut {
            _selectedSpreadsheetId.value = "local_default"
            _activeFlock.value = null
            _appScreen.value = AppScreen.FARMS
            _userMessage.value = "Signed out"
        }
    }
}
