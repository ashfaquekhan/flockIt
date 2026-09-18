package com.example.flock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.ui.components.FarmSettingsDialog
import com.example.flock.ui.components.FlockManagementDialog
import com.example.flock.ui.components.RemindersBottomSheet
import com.example.flock.ui.components.TopFlockBar
import com.example.flock.ui.screens.EntriesScreen
import com.example.flock.ui.screens.FeedScreen
import com.example.flock.ui.screens.GraphsScreen
import com.example.flock.ui.screens.InsightsScreen
import com.example.flock.ui.screens.MedsScreen
import com.example.flock.ui.screens.TargetsScreen
import com.example.flock.ui.screens.TasksScreen
import com.example.flock.ui.screens.VentClimateScreen
import com.example.flock.ui.screens.WaterScreen
import com.example.ui.theme.BrandEmerald

enum class FlockNavSection(val label: String, val icon: ImageVector) {
    ENTRIES("Entries", Icons.Default.EditNote),
    VENT_CLIMATE("Vent/Climate", Icons.Default.Air),
    FEED("Feed", Icons.Default.Restaurant),
    WATER("Water", Icons.Default.Opacity),
    TASKS("Tasks", Icons.Default.CheckCircle),
    MEDS("Meds", Icons.Default.MedicalServices),
    TARGETS("Targets", Icons.Default.Balance),
    INSIGHTS("Insights", Icons.Default.TrendingUp),
    GRAPHS("Graphs", Icons.Default.ShowChart)
}

@Composable
fun MainFlockScreen(
    viewModel: FlockViewModel,
    modifier: Modifier = Modifier
) {
    var currentSection by remember { mutableStateOf(FlockNavSection.ENTRIES) }
    var showFlockDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showRemindersSheet by remember { mutableStateOf(false) }

    val activeFlock by viewModel.activeFlock.collectAsState()
    val flocks by viewModel.flocks.collectAsState()
    val farm by viewModel.farm.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    val currentEntry by viewModel.currentDayEntry.collectAsState()
    val dailyRows by viewModel.dailyRows.collectAsState()
    val lockStatus by viewModel.lockStatus.collectAsState()
    val weather by viewModel.weather.collectAsState()
    val routines by viewModel.routines.collectAsState()
    val dismissedIds by viewModel.dismissedIds.collectAsState()
    val reminders by viewModel.reminders.collectAsState()

    val maxDay = activeFlock?.harvestAge ?: 42
    val pendingRemindersCount = reminders.count { !it.isDone }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("main_flock_screen"),
        topBar = {
            TopFlockBar(
                flock = activeFlock,
                selectedDay = selectedDay,
                maxDay = maxDay,
                lockStatus = lockStatus,
                weather = weather,
                reminderCount = pendingRemindersCount,
                onPrevDay = { if (selectedDay > 0) viewModel.selectDay(selectedDay - 1) },
                onNextDay = { if (selectedDay < maxDay) viewModel.selectDay(selectedDay + 1) },
                onFlockClick = { showFlockDialog = true },
                onSettingsClick = { showSettingsDialog = true },
                onBellClick = { showRemindersSheet = true }
            )
        },
        bottomBar = {
            // Scrollable Tab navigation row for the 9 distinct sections
            ScrollableTabRow(
                selectedTabIndex = currentSection.ordinal,
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = BrandEmerald,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        Modifier.tabIndicatorOffset(tabPositions[currentSection.ordinal]),
                        color = BrandEmerald,
                        height = 3.dp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("flock_navigation_bar")
            ) {
                FlockNavSection.values().forEach { section ->
                    val isSelected = currentSection == section
                    Tab(
                        selected = isSelected,
                        onClick = { currentSection = section },
                        icon = {
                            Icon(
                                imageVector = section.icon,
                                contentDescription = section.label,
                                tint = if (isSelected) BrandEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        text = {
                            Text(
                                text = section.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) BrandEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        },
                        modifier = Modifier.testTag("nav_tab_${section.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentSection) {
                FlockNavSection.ENTRIES -> {
                    EntriesScreen(
                        entry = currentEntry,
                        lockStatus = lockStatus,
                        onSave = { w1, n1, w2, n2, w3, n3, w4, n4, w5, n5, mort, feedBags, liftB, liftW, lame, fb1, ft1, fb2, ft2, aFans, aTime, oTemp, oRh, bLen, notes ->
                            viewModel.saveCurrentDay(
                                w1, n1, w2, n2, w3, n3, w4, n4, w5, n5,
                                mort, feedBags, liftB, liftW, lame,
                                fb1, ft1, fb2, ft2,
                                aFans, aTime, oTemp, oRh, bLen, notes
                            )
                        }
                    )
                }
                FlockNavSection.VENT_CLIMATE -> {
                    VentClimateScreen(
                        entry = currentEntry,
                        farm = farm
                    )
                }
                FlockNavSection.FEED -> {
                    FeedScreen(
                        entry = currentEntry,
                        farm = farm
                    )
                }
                FlockNavSection.WATER -> {
                    WaterScreen(
                        entry = currentEntry,
                        farm = farm
                    )
                }
                FlockNavSection.TASKS -> {
                    TasksScreen(
                        routines = routines,
                        dismissedIds = dismissedIds,
                        onToggleAlarm = { rId, cur -> viewModel.toggleAlarm(rId, cur) },
                        onToggleDone = { rId, done -> viewModel.toggleReminder(rId, done) },
                        onAddTask = { title, time, allDays -> viewModel.addRoutine(title, time, "task", allDays) },
                        onDeleteTask = { rId -> viewModel.deleteRoutine(rId) }
                    )
                }
                FlockNavSection.MEDS -> {
                    MedsScreen(
                        entry = currentEntry,
                        routines = routines,
                        dismissedIds = dismissedIds,
                        onToggleAlarm = { rId, cur -> viewModel.toggleAlarm(rId, cur) },
                        onToggleDone = { rId, done -> viewModel.toggleReminder(rId, done) },
                        onAddMed = { title, time, allDays -> viewModel.addRoutine(title, time, "med", allDays) },
                        onDeleteMed = { rId -> viewModel.deleteRoutine(rId) }
                    )
                }
                FlockNavSection.TARGETS -> {
                    TargetsScreen(
                        entry = currentEntry,
                        farm = farm,
                        breed = activeFlock?.breed ?: "Ross308"
                    )
                }
                FlockNavSection.INSIGHTS -> {
                    InsightsScreen(
                        entry = currentEntry,
                        flock = activeFlock,
                        farm = farm
                    )
                }
                FlockNavSection.GRAPHS -> {
                    GraphsScreen(
                        dailyRows = dailyRows,
                        selectedDay = selectedDay,
                        onSelectDay = { day -> viewModel.selectDay(day) }
                    )
                }
            }
        }
    }

    // Dialogs & Bottom Sheet
    if (showFlockDialog) {
        FlockManagementDialog(
            flocks = flocks,
            activeFlockId = activeFlock?.flockId,
            onSelectFlock = { id ->
                viewModel.selectFlock(id)
                showFlockDialog = false
            },
            onCreateFlock = { name, breed, start, placed, target, harvest, season ->
                viewModel.createNewFlock(name, breed, start, placed, target, harvest, season)
            },
            onDeleteFlock = { id ->
                viewModel.deleteFlock(id)
            },
            onDismiss = { showFlockDialog = false }
        )
    }

    if (showSettingsDialog) {
        FarmSettingsDialog(
            farm = farm,
            onSaveFarm = { updated ->
                viewModel.updateFarm(updated)
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showRemindersSheet) {
        RemindersBottomSheet(
            reminders = reminders,
            entry = currentEntry,
            onDismissRequest = { showRemindersSheet = false },
            onToggleReminder = { id, done ->
                viewModel.toggleReminder(id, done)
            }
        )
    }
}
