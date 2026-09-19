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
import com.example.flock.network.WeatherResult
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

    private val _syncStatus = MutableStateFlow("synced")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

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
    }

    fun selectFarm(spreadsheetId: String) {
        _selectedSpreadsheetId.value = spreadsheetId
        loadFarmData(spreadsheetId)
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

            // Collect Tasks for current day
            launch {
                repository.getTasksFlow(spreadsheetId, flockId, _selectedDay.value).collect { t ->
                    _tasks.value = t
                }
            }
        }
    }

    fun selectDay(day: Int) {
        val flock = _activeFlock.value ?: return
        val clamped = day.coerceIn(0, flock.harvestAge)
        _selectedDay.value = clamped
        _currentDayEntry.value = _dailyRows.value.firstOrNull { it.dayNumber == clamped }
        updateLockStatus()

        viewModelScope.launch {
            repository.getTasksFlow(_selectedSpreadsheetId.value, flock.flockId, clamped).collect { t ->
                _tasks.value = t
            }
        }
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

            val cutoffPassed = isToday && now.isAfter(cutoff)
            val isHardLocked = isPast || isFuture || cutoffPassed

            val isApproaching = isToday && !cutoffPassed && now.isAfter(cutoff.minusMinutes(45))

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

    fun addTask(block: String, label: String, time: String, everyDay: Boolean) {
        val flock = _activeFlock.value ?: return
        val day = _selectedDay.value
        val spreadsheetId = _selectedSpreadsheetId.value
        val taskId = "tsk_" + System.currentTimeMillis()

        viewModelScope.launch {
            val task = TaskEntity(
                spreadsheetId = spreadsheetId,
                taskId = taskId,
                flockId = flock.flockId,
                block = block,
                label = label,
                time = time,
                everyDay = everyDay,
                dayNumber = if (everyDay) null else day
            )
            repository.addTask(task)
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteTask(_selectedSpreadsheetId.value, taskId)
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

    fun deleteFlock(flockId: String) {
        viewModelScope.launch {
            repository.deleteFlock(_selectedSpreadsheetId.value, flockId)
            if (_activeFlock.value?.flockId == flockId) _activeFlock.value = null
            _userMessage.value = "Flock deleted"
        }
    }

    fun toggleFlockLock(flockId: String, locked: Boolean) {
        viewModelScope.launch {
            repository.setFlockLocked(_selectedSpreadsheetId.value, flockId, locked)
            _userMessage.value = if (locked) "Flock locked (read-only)" else "Flock unlocked"
        }
    }

    fun deleteFarm(spreadsheetId: String) {
        viewModelScope.launch {
            repository.deleteFarm(spreadsheetId)
            if (_selectedSpreadsheetId.value == spreadsheetId) {
                _selectedSpreadsheetId.value = "local_default"
                _activeFlock.value = null
            }
            _appScreen.value = AppScreen.FARMS
            _userMessage.value = "Farm deleted"
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun openSharedFarm(spreadsheetId: String) {
        viewModelScope.launch {
            _syncStatus.value = "syncing"
            val validation = syncManager.validateCompatibility(spreadsheetId)
            val isSuccess = validation.isSuccess && (validation.getOrNull() == true)
            val farmName = "Farm " + spreadsheetId.take(8)

            repository.registerFarm(
                spreadsheetId = spreadsheetId,
                farmName = farmName,
                role = "Editor",
                isOwner = false
            )
            openFarm(spreadsheetId)

            if (isSuccess) {
                _syncStatus.value = "synced"
                _userMessage.value = "Opened shared farm: $farmName"
            } else {
                _syncStatus.value = "offline"
                val errorMsg = validation.exceptionOrNull()?.message ?: "Local cache only"
                _userMessage.value = "Opened farm ($errorMsg)"
            }
        }
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
