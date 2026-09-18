package com.example.flock.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FarmEntity
import com.example.flock.data.FlockDatabase
import com.example.flock.data.FlockEntity
import com.example.flock.data.FlockRepository
import com.example.flock.data.RoutineEntity
import com.example.flock.network.WeatherResult
import com.example.flock.notifications.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

data class ReminderItem(
    val id: String,
    val title: String,
    val detail: String,
    val time: String,
    val kind: String, // "task", "med", "water"
    val isDone: Boolean,
    val isDue: Boolean
)

data class LockStatus(
    val isPastDay: Boolean,
    val isToday: Boolean,
    val isFuture: Boolean,
    val isHardLocked: Boolean,
    val cutoffTime: String,
    val isCutoffApproaching: Boolean
)

class FlockViewModel(application: Application) : AndroidViewModel(application) {

    private val database = FlockDatabase.getDatabase(application, viewModelScope)
    private val repository = FlockRepository(database)

    init {
        NotificationHelper.createNotificationChannel(application)
    }

    val flocks: StateFlow<List<FlockEntity>> = repository.allFlocks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val farm: StateFlow<FarmEntity> = repository.farmFlow
        .combine(MutableStateFlow(FarmEntity())) { f, def -> f ?: def }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FarmEntity())

    private val _selectedFlockId = MutableStateFlow<String?>(null)
    val selectedFlockId = _selectedFlockId.asStateFlow()

    private val _selectedDay = MutableStateFlow(0)
    val selectedDay = _selectedDay.asStateFlow()

    private val _weather = MutableStateFlow<WeatherResult?>(null)
    val weather = _weather.asStateFlow()

    private val _dailyRows = MutableStateFlow<List<DailyDataEntity>>(emptyList())
    val dailyRows = _dailyRows.asStateFlow()

    private val _currentDayEntry = MutableStateFlow<DailyDataEntity?>(null)
    val currentDayEntry = _currentDayEntry.asStateFlow()

    private val _routines = MutableStateFlow<List<RoutineEntity>>(emptyList())
    val routines = _routines.asStateFlow()

    private val _dismissedIds = MutableStateFlow<Set<String>>(emptySet())
    val dismissedIds = _dismissedIds.asStateFlow()

    private val _reminders = MutableStateFlow<List<ReminderItem>>(emptyList())
    val reminders = _reminders.asStateFlow()

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
    val lockStatus = _lockStatus.asStateFlow()

    private val _activeFlock = MutableStateFlow<FlockEntity?>(null)
    val activeFlock = _activeFlock.asStateFlow()

    init {
        viewModelScope.launch {
            flocks.collect { flockList ->
                if (flockList.isNotEmpty() && _selectedFlockId.value == null) {
                    val active = flockList.firstOrNull { it.status == "active" } ?: flockList.first()
                    selectFlock(active.flockId)
                }
            }
        }
    }

    fun selectFlock(flockId: String) {
        _selectedFlockId.value = flockId
        viewModelScope.launch {
            val flock = flocks.value.firstOrNull { it.flockId == flockId }
            _activeFlock.value = flock

            // Compute current real day of flock
            val todayDay = if (flock != null) calculateCurDay(flock.startDate, flock.harvestAge) else 0
            _selectedDay.value = todayDay

            loadDayData(flockId, todayDay)
            refreshWeather()
        }
    }

    fun selectDay(day: Int) {
        _selectedDay.value = day
        val flockId = _selectedFlockId.value ?: return
        viewModelScope.launch {
            loadDayData(flockId, day)
        }
    }

    private suspend fun loadDayData(flockId: String, day: Int) {
        val flock = _activeFlock.value ?: flocks.value.firstOrNull { it.flockId == flockId }
        val rows = repository.getDailyDataFlow(flockId).firstOrNull() ?: emptyList()
        _dailyRows.value = rows

        val entry = rows.firstOrNull { it.dayNumber == day }
        _currentDayEntry.value = entry

        // Update Lock Status
        if (entry != null && flock != null) {
            updateLockStatus(entry.date, flock.startDate)
        }

        // Routines for day
        val routineList = repository.getRoutinesFlow(flockId, day).firstOrNull() ?: emptyList()
        _routines.value = routineList

        // Dismissals for today
        val todayIso = getTodayIso()
        val dismissals = repository.getDismissalsFlow(flockId, todayIso).firstOrNull() ?: emptyList()
        val dismissedSet = dismissals.map { it.itemId }.toSet()
        _dismissedIds.value = dismissedSet

        // Build live reminders
        buildReminders(routineList, dismissedSet, entry)
    }

    private fun updateLockStatus(entryDateIso: String, flockStartDateIso: String) {
        val todayIso = getTodayIso()
        val isPastDay = entryDateIso < todayIso
        val isToday = entryDateIso == todayIso
        val isFuture = entryDateIso > todayIso

        val now = Calendar.getInstance()
        val curMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val cutoffMinutes = 11 * 60 // 11:00 AM

        val isPastCutoff = isToday && curMinutes >= cutoffMinutes
        val isHardLocked = isPastDay || isPastCutoff || isFuture
        val isApproaching = isToday && !isPastCutoff && (cutoffMinutes - curMinutes <= 90)

        _lockStatus.value = LockStatus(
            isPastDay = isPastDay,
            isToday = isToday,
            isFuture = isFuture,
            isHardLocked = isHardLocked,
            cutoffTime = "11:00",
            isCutoffApproaching = isApproaching
        )
    }

    private fun buildReminders(
        routineList: List<RoutineEntity>,
        dismissedSet: Set<String>,
        entry: DailyDataEntity?
    ) {
        val items = mutableListOf<ReminderItem>()
        val now = Calendar.getInstance()
        val curMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        // Routine items with alarm enabled
        for (r in routineList) {
            if (r.alarmOn) {
                val rMin = parseTimeToMinutes(r.time)
                val isDue = rMin != null && curMinutes >= rMin
                items.add(
                    ReminderItem(
                        id = r.routineId,
                        title = r.title,
                        detail = r.detail.ifBlank { "Scheduled routine" },
                        time = r.time.ifBlank { "Anytime" },
                        kind = r.type,
                        isDone = dismissedSet.contains(r.routineId),
                        isDue = isDue
                    )
                )
            }
        }

        // Dynamic water refills across the day
        val refills = entry?.tankRefills ?: 1
        val startH = 7
        val endH = 23
        for (k in 1..refills) {
            val h = (startH + (endH - startH) * (k - 1) / max(1, refills))
            val timeStr = String.format("%02d:00", h)
            val refillId = "water_refill_$k"
            val rMin = h * 60
            val isDue = curMinutes >= rMin
            items.add(
                ReminderItem(
                    id = refillId,
                    title = "Water tank refill #$k",
                    detail = "~2,000 L drinking tank refill",
                    time = timeStr,
                    kind = "water",
                    isDone = dismissedSet.contains(refillId),
                    isDue = isDue
                )
            )
        }

        items.sortBy { it.time }
        _reminders.value = items
    }

    fun saveCurrentDay(
        w1: String, n1: String,
        w2: String, n2: String,
        w3: String, n3: String,
        w4: String, n4: String,
        w5: String, n5: String,
        mortality: String,
        feedBagsUsed: String,
        birdsLifted: String,
        weightLifted: String,
        lameSeparated: String,
        feedRecB1: String, feedTypeB1: String,
        feedRecB2: String, feedTypeB2: String,
        actualFans: String,
        actualFanTime: String,
        outTemp: String,
        outRH: String,
        broodingLength: String,
        notes: String
    ) {
        val flockId = _selectedFlockId.value ?: return
        val day = _selectedDay.value

        viewModelScope.launch {
            repository.saveDayEntry(flockId, day) { current ->
                current.copy(
                    w1 = w1.toDoubleOrNull(),
                    n1 = n1.toIntOrNull(),
                    w2 = w2.toDoubleOrNull(),
                    n2 = n2.toIntOrNull(),
                    w3 = w3.toDoubleOrNull(),
                    n3 = n3.toIntOrNull(),
                    w4 = w4.toDoubleOrNull(),
                    n4 = n4.toIntOrNull(),
                    w5 = w5.toDoubleOrNull(),
                    n5 = n5.toIntOrNull(),
                    mortality = mortality.toIntOrNull() ?: current.mortality,
                    feedBagsUsed = feedBagsUsed.toDoubleOrNull() ?: current.feedBagsUsed,
                    birdsLifted = birdsLifted.toIntOrNull() ?: current.birdsLifted,
                    weightLifted = weightLifted.toDoubleOrNull() ?: current.weightLifted,
                    lameSeparated = lameSeparated.toIntOrNull() ?: current.lameSeparated,
                    feedRecB1 = feedRecB1.toDoubleOrNull() ?: current.feedRecB1,
                    feedTypeB1 = feedTypeB1,
                    feedRecB2 = feedRecB2.toDoubleOrNull() ?: current.feedRecB2,
                    feedTypeB2 = feedTypeB2,
                    actualFans = actualFans.toIntOrNull(),
                    actualFanTime = actualFanTime.toIntOrNull(),
                    outTemp = outTemp.toDoubleOrNull(),
                    outRH = outRH.toDoubleOrNull(),
                    broodingLength = broodingLength.toDoubleOrNull(),
                    notes = notes
                )
            }
            loadDayData(flockId, day)
        }
    }

    fun toggleReminder(itemId: String, markDone: Boolean) {
        val flockId = _selectedFlockId.value ?: return
        val todayIso = getTodayIso()
        viewModelScope.launch {
            if (markDone) {
                repository.dismissReminder(flockId, todayIso, itemId)
            } else {
                repository.undismissReminder(flockId, todayIso, itemId)
            }
            loadDayData(flockId, _selectedDay.value)
        }
    }

    fun toggleAlarm(routineId: String, currentAlarmOn: Boolean) {
        viewModelScope.launch {
            repository.setAlarm(routineId, !currentAlarmOn)
            val flockId = _selectedFlockId.value ?: return@launch
            loadDayData(flockId, _selectedDay.value)
        }
    }

    fun addRoutine(title: String, time: String, type: String, applyAllDays: Boolean) {
        val flockId = _selectedFlockId.value ?: return
        viewModelScope.launch {
            val newRoutine = RoutineEntity(
                routineId = "rt_" + System.currentTimeMillis(),
                flockId = if (applyAllDays) "" else flockId,
                dayNumber = if (applyAllDays) null else _selectedDay.value,
                type = type,
                title = title,
                detail = "",
                time = time,
                alarmOn = true
            )
            repository.addRoutine(newRoutine)
            loadDayData(flockId, _selectedDay.value)
        }
    }

    fun deleteRoutine(routineId: String) {
        val flockId = _selectedFlockId.value ?: return
        viewModelScope.launch {
            repository.deleteRoutine(routineId)
            loadDayData(flockId, _selectedDay.value)
        }
    }

    fun createNewFlock(
        name: String,
        breed: String,
        startDate: String,
        birdsPlaced: Int,
        targetWeight: Double,
        harvestAge: Int,
        season: String
    ) {
        viewModelScope.launch {
            val newId = repository.createFlock(
                name = name,
                breed = breed,
                startDate = startDate,
                birdsPlaced = birdsPlaced,
                targetWeight = targetWeight,
                harvestAge = harvestAge,
                season = season
            )
            selectFlock(newId)
        }
    }

    fun deleteFlock(flockId: String) {
        viewModelScope.launch {
            repository.deleteFlock(flockId)
            val remaining = flocks.value.filter { it.flockId != flockId }
            if (remaining.isNotEmpty()) {
                selectFlock(remaining.first().flockId)
            } else {
                _selectedFlockId.value = null
                _activeFlock.value = null
            }
        }
    }

    fun updateFarm(farmEntity: FarmEntity) {
        viewModelScope.launch {
            repository.updateFarm(farmEntity)
            val flockId = _selectedFlockId.value ?: return@launch
            repository.recomputeFlock(flockId)
            loadDayData(flockId, _selectedDay.value)
            refreshWeather()
        }
    }

    fun refreshWeather() {
        viewModelScope.launch {
            val farmData = repository.getFarm()
            val result = repository.fetchWeather(farmData)
            _weather.value = result
        }
    }

    private fun calculateCurDay(startDateIso: String, harvestAge: Int): Int {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val start = try { sdf.parse(startDateIso) ?: Date() } catch (e: Exception) { Date() }
        val today = Date()
        val diffDays = ((today.time - start.time) / (1000 * 60 * 60 * 24)).toInt()
        return diffDays.coerceIn(0, harvestAge)
    }

    private fun getTodayIso(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    private fun parseTimeToMinutes(timeStr: String): Int? {
        val parts = timeStr.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h * 60 + m
    }
}
