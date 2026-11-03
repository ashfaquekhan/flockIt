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
import com.example.flock.data.savedGroups
import com.example.flock.network.HourPoint
import com.example.flock.network.WeatherClient
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
    val indivWeights: String = "",
    /** sample locations beyond the fifth ("w:n;w:n") and how many locations the entry showed */
    val moreSamples: String = "", val locCount: Int = 0,
    val mortality: Int?, val feedBagsUsed: Double, val feedUsedType: String,
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
    val dieselCansUsed: Double = 0.0,
    // Important groups the user actually filled in this time: "W" weights, "M" mortality, "F" feed used.
    val entered: Set<String> = emptySet(),
    /** when what was entered happened (epoch ms) — the weighing, the lifting; null = now */
    val at: Long? = null
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

    private val _hourly = MutableStateFlow<List<HourPoint>>(emptyList())
    /** Hourly outside temperature/RH for the next ~48 h (drives the ventilation day plan). */
    val hourly: StateFlow<List<HourPoint>> = _hourly.asStateFlow()

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

    /** When the open farm was last loaded from its sheet (epoch ms; 0 = not yet) — shown by the loading bar. */
    private val _loadedAt = MutableStateFlow(0L)
    val loadedAt: StateFlow<Long> = _loadedAt.asStateFlow()

    /**
     * The function running now (saving a day, repairing the sheet, closing a batch …), shown over the page while it
     * runs; null when nothing runs. One at a time: a second button pressed meanwhile is turned away with a note.
     */
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private fun busy(label: String, block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
        val running = _busy.value
        if (running != null) { _userMessage.value = "Wait: ${running.replaceFirstChar { it.lowercase() }} is still running"; return }
        _busy.value = label
        viewModelScope.launch {
            try { block() } finally { _busy.value = null }
        }
    }

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
        // Backup copies were once imported as farms (duplicates) — drop them before anything else.
        val removed = syncManager.cleanupBackupFarms()
        if (removed > 0) _userMessage.value = "Removed $removed duplicate farm(s) that were backup copies"
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

    private var loadJob: Job? = null

    /**
     * Loads the open farm from its sheet (and the weather): pull-to-refresh, opening a farm or a flock, coming back to
     * the app. One load at a time; a load asked for while one runs is the same load. The repository keeps saves and
     * loads apart, so nothing typed on the phone is overwritten by an older sheet.
     */
    fun refreshCurrentFarm() {
        val id = _selectedSpreadsheetId.value
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _isRefreshing.value = true
            if (!authState.value.isDemoMode && authState.value.isSignedIn && id != "local_default") {
                _syncStatus.value = "syncing"
                val ok = runCatching { repository.refreshFromCloud(id) }.getOrDefault(false)
                _syncStatus.value = if (ok) "synced" else "offline"
                if (ok) _loadedAt.value = System.currentTimeMillis()
            }
            refreshWeather()
            _isRefreshing.value = false
        }
    }

    /** Back in the app (or on a timer while a flock is open): load again when the last load is older than [maxAgeMs]. */
    fun refreshIfStale(maxAgeMs: Long = 60_000L) {
        when (_appScreen.value) {
            AppScreen.FARMS -> if (System.currentTimeMillis() - lastFarmsSync > 5 * 60_000L) { lastFarmsSync = System.currentTimeMillis(); refreshFarms() }
            else -> {
                val id = _selectedSpreadsheetId.value
                val last = repository.lastLoadedAt[id] ?: 0L
                if (id != "local_default" && System.currentTimeMillis() - last > maxAgeMs) refreshCurrentFarm()
            }
        }
    }
    private var lastFarmsSync = 0L

    // No auto-selection of a farm/flock: the user opens one explicitly from the lists.

    /** Google Sign-In client for the real OAuth flow (launched from the Activity). */
    fun googleSignInClient() = authManager.getGoogleSignInClient()

    fun goToFarms() {
        _appScreen.value = AppScreen.FARMS
    }

    fun openFarm(spreadsheetId: String) {
        _appScreen.value = AppScreen.FLOCKS
        selectFarm(spreadsheetId)
    }

    fun goToFlocks() {
        _appScreen.value = AppScreen.FLOCKS
    }

    fun openFlock(flockId: String) {
        selectFlock(flockId)
        _appScreen.value = AppScreen.DASHBOARD
        // the latest from the sheet when a flock is opened (another phone's entries) — unless it was just loaded
        refreshIfStale(20_000L)
    }

    fun selectFarm(spreadsheetId: String) {
        val changed = _selectedSpreadsheetId.value != spreadsheetId
        _selectedSpreadsheetId.value = spreadsheetId
        if (changed) _loadedAt.value = repository.lastLoadedAt[spreadsheetId] ?: 0L
        loadFarmData(spreadsheetId)
        // the latest workspace from the sheet (another phone's changes), with the loading bar showing
        refreshIfStale(20_000L)
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
            // a flock has no fixed last day: make sure it has a row for every day it has lived (and tomorrow)
            repository.recomputeFlock(spreadsheetId, flockId)
            val curDay = repository.calculateCurrentDay(flock.startDate, _farm.value.timeZone)
            val clampedDay = curDay.coerceIn(0, repository.lastDayFor(flock, _farm.value.timeZone))
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
        val clamped = day.coerceIn(0, repository.lastDayFor(flock, _farm.value.timeZone))
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

            // no clock locks (v47): today and every past day can be entered and changed; only a day to come cannot
            val isHardLocked = isFuture
            val isApproaching = false
            val reason = if (isFuture) "Day $day has not come yet — nothing to enter." else ""

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
            if (!(farm.weatherLat == 0.0 && farm.weatherLon == 0.0)) {
                // the stored hours first (works offline), then the service: the flock's past days once, a few days after that
                val ctx = getApplication<Application>()
                val stored = com.example.flock.network.WeatherStore.load(ctx, farm.weatherLat, farm.weatherLon)
                if (stored.isNotEmpty() && _hourly.value.isEmpty()) _hourly.value = stored
                val start = _activeFlock.value?.startDate.orEmpty()
                val need = try {
                    val today = java.time.LocalDate.now(java.time.ZoneId.of(farm.timeZone))
                    val have = stored.firstOrNull()?.date
                    if (start.isNotEmpty() && (have == null || have > start))
                        java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(start), today).toInt() + 1 else 3
                } catch (e: Exception) { 3 }
                val fresh = WeatherClient.fetchHourly(farm.weatherLat, farm.weatherLon, need.coerceIn(3, 92))
                _hourly.value = com.example.flock.network.WeatherStore.merge(ctx, farm.weatherLat, farm.weatherLon, fresh)
            }
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
        busy("Backing up and repairing the sheet") {
            _syncStatus.value = "syncing"
            _userMessage.value = "Backing up & repairing the sheet…"
            val res = syncManager.backupAndRepairFarm(spreadsheetId)
            // the phone now holds the repaired data: recompute every flock from it
            if (res.isSuccess) runCatching { repository.refreshFromCloud(spreadsheetId) }
            _syncStatus.value = if (res.isSuccess) "synced" else "offline"
            _userMessage.value = if (res.isSuccess) "Sheet repaired: ${res.getOrNull()}"
                else "Repair failed: ${res.exceptionOrNull()?.message ?: "unknown"}"
        }
    }

    /** Sets the active flock's target weight (g) and planned harvest age (days). */
    fun updateFlockPlan(targetWeightG: Double, harvestAge: Int) {
        val flock = _activeFlock.value ?: return
        busy("Saving the target") {
            repository.updateFlockPlan(_selectedSpreadsheetId.value, flock.flockId, targetWeightG, harvestAge)
            _activeFlock.value = _flocks.value.firstOrNull { it.flockId == flock.flockId }
                ?: flock.copy(targetWeight = targetWeightG, harvestAge = harvestAge)
            _userMessage.value = "Target ${targetWeightG.toInt()} g at day $harvestAge saved"
        }
    }

    /** Clears every entry of the selected day (and unlocks it). */
    fun clearDay() {
        val flock = _activeFlock.value ?: return
        val day = _selectedDay.value
        busy("Clearing day $day") {
            val sid = _selectedSpreadsheetId.value
            val res = repository.revertDay(sid, flock.flockId, day, authState.value.email)
            EntryDrafts.clear(getApplication(), EntryDrafts.key(sid, flock.flockId, day))
            _userMessage.value = if (res.isSuccess) "Day $day cleared" else (res.exceptionOrNull()?.message ?: "Could not clear")
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

        busy("Saving day $day") {
            val sid = _selectedSpreadsheetId.value
            val result = repository.saveDayEntry(
                spreadsheetId = sid,
                flockId = flock.flockId,
                dayNumber = day,
                userEmail = userEmail,
                entered = inputs.entered,
                at = inputs.at
            ) { existing ->
                // nothing locks (v47): every value is as on the page; feed used not touched keeps what is there
                val keepW = false
                val keepF = "F" !in inputs.entered
                existing.copy(
                    w1 = if (keepW) existing.w1 else inputs.w1, n1 = if (keepW) existing.n1 else inputs.n1,
                    w2 = if (keepW) existing.w2 else inputs.w2, n2 = if (keepW) existing.n2 else inputs.n2,
                    w3 = if (keepW) existing.w3 else inputs.w3, n3 = if (keepW) existing.n3 else inputs.n3,
                    w4 = if (keepW) existing.w4 else inputs.w4, n4 = if (keepW) existing.n4 else inputs.n4,
                    w5 = if (keepW) existing.w5 else inputs.w5, n5 = if (keepW) existing.n5 else inputs.n5,
                    indivWeights = if (keepW) existing.indivWeights else inputs.indivWeights,
                    moreSamples = if (keepW) existing.moreSamples else inputs.moreSamples,
                    locCount = if (keepW) existing.locCount else inputs.locCount,
                    mortality = inputs.mortality ?: existing.mortality,
                    feedBagsUsed = if (day == 0) 0.0 else if (keepF) existing.feedBagsUsed else inputs.feedBagsUsed,
                    feedUsedType = if (keepF) existing.feedUsedType else inputs.feedUsedType,
                    feedUsedBreakdown = if (day == 0) "" else if (keepF) existing.feedUsedBreakdown else inputs.feedUsedBreakdown,
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
                EntryDrafts.clear(getApplication(), EntryDrafts.key(sid, flock.flockId, day))
                _userMessage.value = "Saved Day $day"
                onSuccess()
            }.onFailure { err ->
                _userMessage.value = err.message ?: "Failed to save entry"
                onError(err.message ?: "Failed to save entry")
            }
        }
    }

    /**
     * A feeding poured for the birds: [bags] at [at] (epoch ms). It goes on the day it was given, on the sheet, so
     * every phone on the farm sees it (it drives the hungry / thirsty likelihoods).
     */
    fun logFeeding(bags: Double, at: Long) {
        val flock = _activeFlock.value ?: return
        val zone = try { ZoneId.of(_farm.value.timeZone) } catch (e: Exception) { ZoneId.systemDefault() }
        val t = java.time.Instant.ofEpochMilli(at).atZone(zone)
        val day = try { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(flock.startDate), t.toLocalDate()).toInt() } catch (e: Exception) { return }
        val row = _dailyRows.value.firstOrNull { it.dayNumber == day } ?: return
        val list = com.example.flock.data.parseFeedings(row.feedings) + (String.format("%02d:%02d", t.hour, t.minute) to bags)
        busy("Saving the feeding") {
            repository.setFeedings(_selectedSpreadsheetId.value, flock.flockId, day, com.example.flock.data.formatFeedings(list), authState.value.email)
        }
    }

    /** Takes back the last feeding logged (the latest day that has one). */
    fun undoLastFeeding() {
        val flock = _activeFlock.value ?: return
        val row = _dailyRows.value.filter { it.feedings.isNotBlank() }.maxByOrNull { it.dayNumber } ?: return
        val list = com.example.flock.data.parseFeedings(row.feedings).sortedBy { it.first }.dropLast(1)
        busy("Taking back the feeding") {
            repository.setFeedings(_selectedSpreadsheetId.value, flock.flockId, row.dayNumber, com.example.flock.data.formatFeedings(list), authState.value.email)
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
        busy("Saving the task") {
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
        busy("Deleting the task") {
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
        busy("Copying the task") {
            val t = _tasks.value.firstOrNull { it.taskId == taskId } ?: return@busy
            val copy = t.copy(
                taskId = "tsk_" + System.currentTimeMillis(),
                startDay = fromDay.coerceIn(0, com.example.flock.engine.PhysiologicalEngine.MAX_FLOCK_DAY),
                endDay = toDay.coerceIn(0, com.example.flock.engine.PhysiologicalEngine.MAX_FLOCK_DAY),
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
            _tasks.value, curDay, repository.lastDayFor(flock, _farm.value.timeZone)
        )
    }

    private fun ingestNotificationCompletions() {
        viewModelScope.launch {
            val done = TaskNotify.drainCompletedInbox(getApplication())
            for ((sid, tid, day) in done) repository.setTaskCompleted(sid, tid, day, true)
        }
    }

    fun createFarm(name: String, onComplete: (String) -> Unit) {
        busy("Creating the farm") {
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
        busy("Sharing the farm") {
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
        busy("Saving the farm settings") {
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

    /** Save a farm setting changed from the Output page (e.g. the water refill multiplier). */
    fun updateFarm(farm: FarmEntity) {
        busy("Saving the setting") {
            repository.updateFarm(farm)
            _farm.value = farm
        }
    }

    fun saveFeedType(feedType: FeedTypeEntity) {
        busy("Saving the feed type") {
            repository.saveFeedType(feedType)
        }
    }

    fun deleteFeedType(code: String) {
        busy("Deleting the feed type") {
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
        busy("Creating the batch") {
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
        busy("Closing the batch and saving it to the sheet") {
            val synced = repository.closeFlock(_selectedSpreadsheetId.value, flockId)
            _userMessage.value = if (synced) "Batch closed and saved to the Google Sheet" else "Batch closed on this phone — it will reach the sheet when online"
        }
    }

    // Recycle bin flows
    val deletedFarms: StateFlow<List<FarmRegistryEntity>> = repository.deletedFarms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val deletedFlocks: StateFlow<List<FlockEntity>> = repository.deletedFlocks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteFlock(flockId: String) {
        busy("Moving the batch to the recycle bin") {
            repository.softDeleteFlock(_selectedSpreadsheetId.value, flockId)
            if (_activeFlock.value?.flockId == flockId) _activeFlock.value = null
            _userMessage.value = "Flock moved to Recycle Bin"
        }
    }

    fun restoreFlock(spreadsheetId: String, flockId: String) {
        busy("Restoring the batch") {
            repository.restoreFlock(spreadsheetId, flockId)
            _userMessage.value = "Flock restored"
        }
    }

    fun purgeFlock(spreadsheetId: String, flockId: String) {
        busy("Deleting the batch") {
            repository.deleteFlock(spreadsheetId, flockId)
            _userMessage.value = "Flock permanently deleted"
        }
    }

    fun restoreFarm(spreadsheetId: String) {
        busy("Restoring the farm") {
            repository.restoreFarm(spreadsheetId)
            if (!authState.value.isDemoMode) {
                runCatching { syncManager.setFarmDeletedInIndex(spreadsheetId, false) }
            }
            _userMessage.value = "Farm restored"
        }
    }

    fun purgeFarm(spreadsheetId: String) {
        busy("Deleting the farm") {
            repository.deleteFarm(spreadsheetId)
            if (!authState.value.isDemoMode) {
                runCatching { syncManager.removeFarmFromIndex(spreadsheetId) }
            }
            _userMessage.value = "Farm permanently deleted"
        }
    }

    fun toggleFlockLock(flockId: String, locked: Boolean) {
        busy(if (locked) "Locking the batch" else "Unlocking the batch") {
            repository.setFlockLocked(_selectedSpreadsheetId.value, flockId, locked)
            _userMessage.value = if (locked) "Flock locked (read-only)" else "Flock unlocked"
        }
    }

    fun toggleFarmLock(spreadsheetId: String, locked: Boolean) {
        busy(if (locked) "Locking the farm" else "Unlocking the farm") {
            repository.setFarmLocked(spreadsheetId, locked)
            _userMessage.value = if (locked) "Farm locked (read-only)" else "Farm unlocked"
        }
    }

    fun deleteFarm(spreadsheetId: String) {
        busy("Moving the farm to the recycle bin") {
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
        busy("Opening the shared farm") {
            _syncStatus.value = "syncing"
            val validation = syncManager.validateCompatibility(spreadsheetId)
            if (validation.isFailure || validation.getOrNull() != true) {
                _syncStatus.value = "offline"
                _userMessage.value =
                    "Couldn't open shared farm: ${validation.exceptionOrNull()?.message ?: "not a FlockIt farm, or no access"}"
                return@busy
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
