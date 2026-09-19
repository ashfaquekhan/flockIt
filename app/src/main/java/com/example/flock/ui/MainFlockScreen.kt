package com.example.flock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.flock.ui.components.TopFlockBar
import com.example.flock.ui.screens.EntriesScreen
import com.example.flock.ui.screens.OutputScreen
import com.example.flock.ui.screens.TasksScreen
import com.example.ui.theme.BrandEmerald
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class FlockNavTab(val label: String, val icon: ImageVector) {
    ENTRY("ENTRY", Icons.Default.EditNote),
    OUTPUT("OUTPUT", Icons.Default.Dashboard),
    TASKS("TASKS", Icons.Default.CheckCircle)
}

/**
 * The single-flock dashboard: ENTRY / OUTPUT / TASKS. Reached by opening a flock from the
 * Flocks screen. The top bar's farm/flock taps navigate back up the stack.
 */
@Composable
fun MainFlockScreen(
    viewModel: FlockViewModel,
    onNavFarms: () -> Unit,
    onNavFlocks: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableStateOf(FlockNavTab.ENTRY) }

    val activeFlock by viewModel.activeFlock.collectAsState()
    val farm by viewModel.farm.collectAsState()
    val feedTypes by viewModel.feedTypes.collectAsState()

    val selectedDay by viewModel.selectedDay.collectAsState()
    val currentDayEntry by viewModel.currentDayEntry.collectAsState()
    val dailyRows by viewModel.dailyRows.collectAsState()
    val feedStockSummary by viewModel.feedStockSummary.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val lockStatus by viewModel.lockStatus.collectAsState()
    val weather by viewModel.weather.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()

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
                    val maxDay = activeFlock?.harvestAge ?: 42
                    if (selectedDay < maxDay) viewModel.selectDay(selectedDay + 1)
                },
                onFarmClick = onNavFarms,
                onFlockClick = onNavFlocks,
                onSettingsClick = onOpenSettings,
                onWeatherClick = { viewModel.refreshWeather() }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                FlockNavTab.values().forEach { tab ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BrandEmerald,
                            selectedTextColor = BrandEmerald,
                            indicatorColor = BrandEmerald.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
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
                    onSave = { inputs -> viewModel.saveDayEntry(inputs) }
                )
                FlockNavTab.OUTPUT -> OutputScreen(
                    flock = activeFlock,
                    farm = farm,
                    entry = currentDayEntry,
                    dailyRows = dailyRows,
                    feedStockSummary = feedStockSummary
                )
                FlockNavTab.TASKS -> TasksScreen(
                    tasks = tasks,
                    dayNumber = selectedDay,
                    onAddTask = { block, label, time, everyDay -> viewModel.addTask(block, label, time, everyDay) },
                    onDeleteTask = { taskId -> viewModel.deleteTask(taskId) }
                )
            }
        }
    }
}
