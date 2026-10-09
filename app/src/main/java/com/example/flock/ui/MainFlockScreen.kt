package com.example.flock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.example.flock.ui.components.FlockPullIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.flock.data.locationsShown
import com.example.flock.data.sampleList
import com.example.flock.ui.components.TopFlockBar
import com.example.flock.ui.components.WeatherForecastDialog
import com.example.flock.ui.screens.EntriesScreen
import com.example.flock.ui.screens.OutputScreen
import com.example.flock.ui.screens.StockScreen
import com.example.flock.ui.screens.TasksScreen
import com.example.ui.theme.BrandEmerald
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class FlockNavTab(val label: String, val icon: ImageVector) {
    ENTRY("ENTRY", Icons.Default.EditNote),
    OUTPUT("OUTPUT", Icons.Default.Dashboard),
    STOCK("STOCK", Icons.Default.Inventory2),
    TASKS("TASKS", Icons.Default.CheckCircle)
}

/**
 * The single-flock dashboard: ENTRY / OUTPUT / STOCK / TASKS. Reached by opening a flock from the
 * Flocks screen. The top bar's farm/flock taps navigate back up the stack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainFlockScreen(
    viewModel: FlockViewModel,
    onNavFarms: () -> Unit,
    onNavFlocks: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableStateOf(FlockNavTab.ENTRY) }
    // how the app is shown on this phone: the short or the full view, and whether the weather acts on the house and birds
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var basicView by remember { mutableStateOf(com.example.flock.network.ViewPrefs.basic(ctx)) }
    var weatherOn by remember { mutableStateOf(com.example.flock.network.ViewPrefs.weatherOn(ctx)) }
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val activeFlock by viewModel.activeFlock.collectAsState()
    val farm by viewModel.farm.collectAsState()
    val feedTypes by viewModel.feedTypes.collectAsState()

    val selectedDay by viewModel.selectedDay.collectAsState()
    val currentDayEntry by viewModel.currentDayEntry.collectAsState()
    val dailyRows by viewModel.dailyRows.collectAsState()
    val feedStockSummary by viewModel.feedStockSummary.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val lockStatus by viewModel.lockStatus.collectAsState()
    val cutoffLockEnabled by viewModel.cutoffLockEnabled.collectAsState()
    val weather by viewModel.weather.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val showForecast by viewModel.showForecast.collectAsState()
    val forecast by viewModel.forecast.collectAsState()
    val hourly by viewModel.hourly.collectAsState()
    val spreadsheetId by viewModel.selectedSpreadsheetId.collectAsState()
    // Location bird counts from the most recent earlier weighing (carried forward as defaults).
    // Points noted on recent days, most used first, offered as one-tap chips (no retyping, no near-duplicates).
    val recentNotes = remember(dailyRows, selectedDay) {
        dailyRows.filter { it.dayNumber in (selectedDay - 14) until selectedDay }
            .flatMap { com.example.flock.ui.screens.parseNoteItems(it.notes) }
            .groupBy { com.example.flock.ui.screens.noteKey(it) }
            .entries.sortedByDescending { it.value.size }
            .map { it.value.last() }
    }
    val previousCounts = remember(dailyRows, selectedDay) {
        // every location of the last weighing (so today's entry opens with the same number of locations)
        dailyRows.filter { it.dayNumber < selectedDay && it.sampleList().any { s -> s.second != null } }
            .maxByOrNull { it.dayNumber }
            ?.let { r -> r.sampleList().map { it.second }.take(maxOf(r.locationsShown(), 1)) } ?: List(5) { null }
    }

    val (dayDateStr, yesterdayDateStr) = remember(activeFlock, selectedDay, farm.timeZone) {
        try {
            val start = activeFlock?.startDate?.let { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }
                ?: LocalDate.now()
            val targetDate = start.plusDays(selectedDay.toLong())
            val dtf = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.US)
            Pair(targetDate.format(dtf), targetDate.minusDays(1).format(dtf))
        } catch (e: Exception) {
            Pair("Day $selectedDay", "Day ${if (selectedDay > 0) selectedDay - 1 else 0}")
        }
    }

    val currentFlockDay = remember(activeFlock, farm.timeZone) {
        try {
            if (activeFlock == null) 0
            else {
                val zone = ZoneId.of(farm.timeZone)
                val start = LocalDate.parse(activeFlock!!.startDate, DateTimeFormatter.ISO_LOCAL_DATE)
                java.time.temporal.ChronoUnit.DAYS.between(start, LocalDate.now(zone)).toInt().coerceAtLeast(0)
            }
        } catch (e: Exception) { 0 }
    }

    // no fixed last day: the planned harvest age, or tomorrow once the flock has run past it (up to the oldest a broiler is kept)
    val lastDay = maxOf(activeFlock?.harvestAge ?: 42, currentFlockDay + 1).coerceIn(1, com.example.flock.engine.PhysiologicalEngine.MAX_FLOCK_DAY)

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("main_flock_screen"),
        topBar = {
            TopFlockBar(
                farmName = farm.farmName,
                flock = activeFlock,
                selectedDay = selectedDay,
                currentFlockDay = currentFlockDay,
                dayDate = dayDateStr,
                lockStatus = lockStatus,
                weather = weather,
                syncStatus = syncStatus,
                onPrevDay = { if (selectedDay > 0) viewModel.selectDay(selectedDay - 1) },
                onNextDay = {
                    if (selectedDay < lastDay) viewModel.selectDay(selectedDay + 1)
                },
                onSelectDay = { viewModel.selectDay(it) },
                onFarmClick = onNavFarms,
                onFlockClick = onNavFlocks,
                onWeatherClick = { viewModel.openWeatherForecast() },
                lastDay = lastDay,
                basicView = basicView,
                onBasicView = { basicView = it; com.example.flock.network.ViewPrefs.setBasic(ctx, it) }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
                modifier = Modifier.testTag("bottom_nav_bar").drawBehind { drawLine(com.example.ui.theme.GlassLine, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 1.dp.toPx()) }
            ) {
                FlockNavTab.values().forEach { tab ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentTab = tab },
                        icon = {
                            // the chosen tab is marked with a small bar in the beak's colour
                            androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                Icon(tab.icon, contentDescription = tab.label)
                                Box(Modifier.padding(top = 2.dp).size(width = 18.dp, height = 3.dp)
                                    .background(if (selected) com.example.ui.theme.BrandBeak else androidx.compose.ui.graphics.Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(2.dp)))
                            }
                        },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = androidx.compose.ui.graphics.Color(0xFFF2F2F0),
                            selectedTextColor = androidx.compose.ui.graphics.Color(0xFFF2F2F0),
                            indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unselectedIconColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.45f),
                            unselectedTextColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshCurrentFarm() },
            state = pullState,
            indicator = { FlockPullIndicator(pullState, isRefreshing) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentTab) {
                FlockNavTab.ENTRY -> EntriesScreen(
                    entry = currentDayEntry,
                    dayNumber = selectedDay,
                    dayDate = dayDateStr,
                    yesterdayDate = yesterdayDateStr,
                    feedTypes = feedTypes,
                    lockStatus = lockStatus,
                    cutoffLockEnabled = cutoffLockEnabled,
                    draftKey = EntryDrafts.key(spreadsheetId, activeFlock?.flockId ?: "", selectedDay),
                    previousCounts = previousCounts,
                    recentNotes = recentNotes,
                    onSave = { inputs -> viewModel.saveDayEntry(inputs) },
                    onToggleLockTimer = { viewModel.toggleCutoffLock() },
                    onRevertDay = { viewModel.revertDay() },
                    onClearDay = { viewModel.clearDay() },
                    farm = farm,
                    basic = basicView,
                    lastFeedType = dailyRows.filter { it.dayNumber < selectedDay && it.feedBagsUsed > 0 }.maxByOrNull { it.dayNumber }
                        ?.let { r -> com.example.flock.data.FlockCalc.usedSplit(r).maxByOrNull { it.value }?.key }
                )
                FlockNavTab.OUTPUT -> OutputScreen(
                    flock = activeFlock,
                    farm = farm,
                    entry = currentDayEntry,
                    dailyRows = dailyRows,
                    feedStockSummary = feedStockSummary,
                    feedTypes = feedTypes,
                    weather = weather,
                    hourly = hourly,
                    isToday = selectedDay == currentFlockDay,
                    onCloseBatch = activeFlock?.let { f -> { viewModel.closeFlock(f.flockId) } },
                    onFarmChange = { viewModel.updateFarm(it) },
                    onFlockPlan = { w, h -> viewModel.updateFlockPlan(w, h) },
                    basic = basicView,
                    weatherOn = weatherOn,
                    onWeatherOn = { weatherOn = it; com.example.flock.network.ViewPrefs.setWeatherOn(ctx, it) }
                )
                FlockNavTab.STOCK -> StockScreen(
                    flock = activeFlock,
                    farm = farm,
                    entry = currentDayEntry,
                    dailyRows = dailyRows,
                    feedTypes = feedTypes
                )
                FlockNavTab.TASKS -> TasksScreen(
                    allTasks = tasks,
                    dayNumber = selectedDay,
                    harvestAge = lastDay,
                    onSaveTask = { id, block, label, time, s, e, rec, n, alert, kind ->
                        viewModel.saveTask(id, block, label, time, s, e, rec, n, alert, kind)
                    },
                    onDeleteTask = { taskId -> viewModel.deleteTask(taskId) },
                    onToggleComplete = { id, day, done -> viewModel.toggleTaskComplete(id, day, done) },
                    onToggleAlert = { id, en -> viewModel.toggleTaskAlert(id, en) },
                    onCopyToRange = { id, f, t -> viewModel.copyTaskToRange(id, f, t) },
                    // the day clock sits under the tasks
                    footer = {
                        com.example.flock.ui.screens.DayClockSection(activeFlock, farm, currentDayEntry, dailyRows, feedTypes, weather, hourly,
                            selectedDay == currentFlockDay, weatherOn)
                    }
                )
            }
        }
    }

    if (showForecast) {
        WeatherForecastDialog(forecast = forecast, onDismiss = { viewModel.closeWeatherForecast() })
    }
}
