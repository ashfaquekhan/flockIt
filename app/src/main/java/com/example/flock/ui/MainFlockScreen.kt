package com.example.flock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.flock.data.FarmRegistryEntity
import com.example.flock.ui.components.FarmSettingsDialog
import com.example.flock.ui.components.FarmsListDialog
import com.example.flock.ui.components.FlockManagementDialog
import com.example.flock.ui.components.NewFarmDialog
import com.example.flock.ui.components.ShareFarmDialog
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

@Composable
fun MainFlockScreen(
    viewModel: FlockViewModel,
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableStateOf(FlockNavTab.ENTRY) }

    var showFlockDialog by remember { mutableStateOf(false) }
    var showFarmSettingsDialog by remember { mutableStateOf(false) }
    var showFarmsListDialog by remember { mutableStateOf(false) }
    var showNewFarmDialog by remember { mutableStateOf(false) }
    var sharingFarm by remember { mutableStateOf<FarmRegistryEntity?>(null) }

    val activeFlock by viewModel.activeFlock.collectAsState()
    val flocks by viewModel.flocks.collectAsState()
    val farm by viewModel.farm.collectAsState()
    val config by viewModel.config.collectAsState()
    val feedTypes by viewModel.feedTypes.collectAsState()
    val farms by viewModel.farms.collectAsState()
    val selectedSpreadsheetId by viewModel.selectedSpreadsheetId.collectAsState()
    val authState by viewModel.authState.collectAsState()

    val selectedDay by viewModel.selectedDay.collectAsState()
    val currentDayEntry by viewModel.currentDayEntry.collectAsState()
    val dailyRows by viewModel.dailyRows.collectAsState()
    val feedStockSummary by viewModel.feedStockSummary.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val lockStatus by viewModel.lockStatus.collectAsState()
    val weather by viewModel.weather.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Date calculations for dayDate and yesterdayDate
    val (dayDateStr, yesterdayDateStr) = remember(activeFlock, selectedDay, farm.timeZone) {
        try {
            val start = activeFlock?.startDate?.let { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }
                ?: LocalDate.now()
            val targetDate = start.plusDays(selectedDay.toLong())
            val yDate = targetDate.minusDays(1)
            val dtf = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.US)
            Pair(targetDate.format(dtf), yDate.format(dtf))
        } catch (e: Exception) {
            Pair("Day $selectedDay", "Day ${if (selectedDay > 0) selectedDay - 1 else 0}")
        }
    }

    val currentFlockDay = remember(activeFlock, farm.timeZone) {
        try {
            if (activeFlock == null) 0
            else {
                val zone = ZoneId.of(farm.timeZone)
                val today = LocalDate.now(zone)
                val start = LocalDate.parse(activeFlock!!.startDate, DateTimeFormatter.ISO_LOCAL_DATE)
                java.time.temporal.ChronoUnit.DAYS.between(start, today).toInt().coerceAtLeast(0)
            }
        } catch (e: Exception) {
            0
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("main_flock_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                onFarmClick = { showFarmsListDialog = true },
                onFlockClick = { showFlockDialog = true },
                onSettingsClick = { showFarmSettingsDialog = true },
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
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label
                            )
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
                FlockNavTab.ENTRY -> {
                    EntriesScreen(
                        entry = currentDayEntry,
                        dayNumber = selectedDay,
                        dayDate = dayDateStr,
                        yesterdayDate = yesterdayDateStr,
                        feedTypes = feedTypes,
                        lockStatus = lockStatus,
                        onSave = { w1, n1, w2, n2, w3, n3, w4, n4, w5, n5,
                                   mortality, feedBagsUsed, feedUsedType,
                                   birdsLifted, weightLifted, lameSeparated,
                                   feedRecB1, feedTypeB1, feedRecB2, feedTypeB2, feedRecB3, feedTypeB3,
                                   broodingLength, actualFans, actualFanTime, outTemp, outRH, notes ->
                            viewModel.saveDayEntry(
                                w1, n1, w2, n2, w3, n3, w4, n4, w5, n5,
                                mortality, feedBagsUsed, feedUsedType,
                                birdsLifted, weightLifted, lameSeparated,
                                feedRecB1, feedTypeB1, feedRecB2, feedTypeB2, feedRecB3, feedTypeB3,
                                broodingLength, actualFans, actualFanTime, outTemp, outRH, notes
                            )
                        }
                    )
                }
                FlockNavTab.OUTPUT -> {
                    OutputScreen(
                        flock = activeFlock,
                        farm = farm,
                        entry = currentDayEntry,
                        dailyRows = dailyRows,
                        feedStockSummary = feedStockSummary
                    )
                }
                FlockNavTab.TASKS -> {
                    TasksScreen(
                        tasks = tasks,
                        dayNumber = selectedDay,
                        onAddTask = { block, label, time, everyDay ->
                            viewModel.addTask(block, label, time, everyDay)
                        },
                        onDeleteTask = { taskId ->
                            viewModel.deleteTask(taskId)
                        }
                    )
                }
            }
        }
    }

    // DIALOGS
    if (showFarmsListDialog) {
        FarmsListDialog(
            farms = farms,
            selectedSpreadsheetId = selectedSpreadsheetId,
            authState = authState,
            onDismiss = { showFarmsListDialog = false },
            onSelectFarm = { viewModel.selectFarm(it) },
            onCreateNewFarm = { showNewFarmDialog = true },
            onOpenSharedFarm = { viewModel.openSharedFarm(it) },
            onShareFarm = { sharingFarm = it },
            onSignIn = { viewModel.signInWithGoogle() },
            onSignOut = { viewModel.signOut() },
            onToggleDemoMode = { viewModel.toggleDemoMode() }
        )
    }

    if (showFarmSettingsDialog) {
        FarmSettingsDialog(
            farm = farm,
            config = config,
            feedTypes = feedTypes,
            onDismiss = { showFarmSettingsDialog = false },
            onSaveFarmSettings = { f, c -> viewModel.updateFarmSettings(f, c) },
            onSaveFeedType = { ft -> viewModel.saveFeedType(ft) },
            onDeleteFeedType = { code -> viewModel.deleteFeedType(code) }
        )
    }

    if (showFlockDialog) {
        FlockManagementDialog(
            flocks = flocks,
            activeFlockId = activeFlock?.flockId,
            onSelectFlock = { viewModel.selectFlock(it) },
            onCreateFlock = { name, breed, startDate, placed, targetWeight, harvestAge, season ->
                viewModel.createFlock(name, breed, startDate, placed, 0, targetWeight, harvestAge, season)
            },
            onDeleteFlock = { viewModel.closeFlock(it) },
            onDismiss = { showFlockDialog = false }
        )
    }

    if (showNewFarmDialog) {
        NewFarmDialog(
            onDismiss = { showNewFarmDialog = false },
            onCreateFarm = { farmName ->
                viewModel.createFarm(farmName) {}
            }
        )
    }

    sharingFarm?.let { farmToShare ->
        ShareFarmDialog(
            farmName = farmToShare.farmName,
            spreadsheetId = farmToShare.spreadsheetId,
            onDismiss = { sharingFarm = null },
            onShare = { email, isEditor ->
                viewModel.shareFarm(farmToShare.spreadsheetId, email, isEditor) { _, _ -> }
            }
        )
    }
}
