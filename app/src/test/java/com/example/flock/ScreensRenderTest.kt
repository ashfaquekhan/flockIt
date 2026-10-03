package com.example.flock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FarmEntity
import com.example.flock.data.FarmRegistryEntity
import com.example.flock.data.FeedStockSummary
import com.example.flock.data.FeedTypeEntity
import com.example.flock.data.FlockEntity
import com.example.flock.data.TaskEntity
import com.example.flock.engine.CompanyStandard
import com.example.flock.engine.PhysiologicalEngine
import com.example.flock.network.WeatherResult
import com.example.flock.ui.LockStatus
import com.example.flock.ui.components.TopFlockBar
import com.example.flock.ui.screens.EntriesScreen
import com.example.flock.ui.screens.FarmsScreen
import com.example.flock.ui.screens.FlocksScreen
import com.example.flock.ui.screens.OutputScreen
import com.example.flock.ui.screens.TasksScreen
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders each screen as it appears in the app, at a small and a large phone size. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreensRenderTest {
    @get:Rule val rule = createComposeRule()

    private val farm = FarmEntity(spreadsheetId = "t", farmName = "Maa Tarini Farm", feedBagKg = 50.0, feederLines = 4, feederLineBags = 3,
        pansPerFeederLine = 112, sensorPansPerLine = 2, lineFillBags = 3.3, feederLineGapFt = 9.75, manualFeeders = 150, manualDrinkers = 180, nipplesPerLine = 290, godownBags = 600.0,
        timeZone = java.time.ZoneOffset.ofHours(((11 - java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).hour + 36) % 24 - 12).coerceIn(-12, 14)).id)
    private val flock = FlockEntity(spreadsheetId = "t", flockId = "f", name = "Batch #3", startDate = java.time.LocalDate.now().minusDays(11).toString(), birdsPlaced = 15700, receptionMort = 40)
    private val feedTypes = listOf(
        FeedTypeEntity(spreadsheetId = "t", code = "B1", name = "Starter", bagKg = 50.0, sortOrder = 1),
        FeedTypeEntity(spreadsheetId = "t", code = "B2", name = "Grower", bagKg = 50.0, sortOrder = 2),
        FeedTypeEntity(spreadsheetId = "t", code = "B3", name = "Finisher", bagKg = 50.0, sortOrder = 3)
    )
    private val lock = LockStatus(isPastDay = false, isToday = true, isFuture = false, isHardLocked = false, cutoffTime = "11:00", isCutoffApproaching = false)
    private val weather = WeatherResult(tempC = 30.5, rhPercent = 74.0, windKmh = 9.0, locationName = "Jujomura", isLive = true)

    private fun rows(upTo: Int): List<DailyDataEntity> {
        var live = 15700 - 40
        return (0..upTo).map { d ->
            val mort = if (d == 0) 0 else (live * CompanyStandard.dailyMortPct(d) * 1.2 / 100).toInt()
            live -= mort
            val bw = (CompanyStandard.bw(d) ?: 42.0) * 0.97
            val open = if (d >= 11) 1.0 else 0.3 + 0.7 * d / 11.0
            val feed = CompanyStandard.feedForWeight(bw) * live / 1000.0
            val bags = if (d >= 1) (CompanyStandard.feedPerDay(d - 1) ?: 13.0) * live / 1000.0 / 50.0 else 0.0
            DailyDataEntity(spreadsheetId = "t", flockId = "f", dayNumber = d, date = java.time.LocalDate.now().minusDays((upTo - d).toLong()).toString(),
                mortality = mort, liveBirds = live, avgWeight = if (d % 3 == 0 && d > 0) bw else null, sampleEntered = d % 3 == 0 && d > 0,
                projected = d % 3 != 0, cv = 8.4, weightAge = PhysiologicalEngine.weightAgeFromBW(bw, "Ross308"),
                fcr = CompanyStandard.fcr(d)?.times(1.03), cFcr = CompanyStandard.cfcr(d)?.times(1.03),
                cumMortPct = (15660 - live) * 100.0 / 15660, livability = live * 100.0 / 15660,
                feedBagsUsed = bags, feedUsedType = CompanyStandard.feedPhase(maxOf(1, d - 1)), feedUsedBreakdown = "",
                feedRecB1 = if (d == 0) 300.0 else 0.0, totalFeedKg = feed, feedPerBird = feed * 1000 / live,
                totalWaterL = feed * 1.8, waterPerBird = feed * 1800 / live, waterHighL = feed * 2.1, waterLowL = feed * 1.5,
                occupiedFt2 = 299.0 * 39 * open, densityKgM2 = live * bw / 1000 / (299 * 39 * 0.0929 * open), ftPerBird = 299.0 * 39 * open / live, minFtPerBird = 0.8,
                tempIdeal = 28.0, tempMin = 26.5, tempMax = 29.5, rhIdeal = 60.0, lightHours = 18.0)
        }
    }

    @Composable
    private fun Dash(tab: Int, content: @Composable () -> Unit) {
        val r = rows(11)
        Scaffold(
            topBar = { TopFlockBar(farm.farmName, flock, 11, 11, "30 Sep 2026", lock, weather, "synced", {}, {}, {}, {}, {}) },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    listOf("Entry" to Icons.Default.Edit, "Output" to Icons.Default.Insights, "Stock" to Icons.Default.Inventory2, "Tasks" to Icons.Default.Checklist).forEachIndexed { i, (l, ic) ->
                        NavigationBarItem(selected = i == tab, onClick = {}, icon = { Icon(ic, null) }, label = { Text(l) })
                    }
                }
            }
        ) { pad -> Box(Modifier.fillMaxSize().padding(pad).background(MaterialTheme.colorScheme.background)) { content() } }
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { MyApplicationTheme { content() } }
        rule.mainClock.advanceTimeBy(1200)
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/screen_$name.png")
    }

    @Composable private fun entry() = Dash(0) {
        val r = rows(11)
        EntriesScreen(r.last(), 11, "30 Sep", "29 Sep", feedTypes, lock, true, "k", listOf(null, null, null, null, null), emptyList(), {}, {}, {})
    }
    @Composable private fun output() = Dash(1) {
        val r = rows(11)
        OutputScreen(flock, farm, r.last(), r, FeedStockSummary(0.0, 0.0, 0.0, emptyMap()), feedTypes, weather, emptyList(), true, {})
    }
    @Composable private fun stock() = Dash(2) {
        val r = rows(11)
        com.example.flock.ui.screens.StockScreen(flock, farm, r.last(), r, feedTypes)
    }
    @Composable private fun tasks() = Dash(3) {
        TasksScreen(listOf(TaskEntity(spreadsheetId = "t", taskId = "1", flockId = "f", block = "Morning", label = "Check drinkers", time = "06:00")),
            11, 42, { _, _, _, _, _, _, _, _, _, _ -> }, {}, { _, _, _ -> }, { _, _ -> }, { _, _, _ -> })
    }
    @Composable private fun flocks() = FlocksScreen(farm.farmName, farm.timeZone, listOf(flock, flock.copy(flockId = "g", name = "Batch #2", status = "closed", startDate = "2026-07-01")),
        false, {}, {}, {}, { _, _, _, _, _, _, _ -> }, {}, { _, _ -> })
    @Composable private fun farms() = FarmsScreen(listOf(FarmRegistryEntity(spreadsheetId = "t", farmName = "Maa Tarini Farm")), "me@x.com", false, false,
        {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    @Config(qualifiers = "w360dp-h780dp-xhdpi") @Test fun entrySmall() = shoot("entry_small") { entry() }
    @Config(qualifiers = "w412dp-h915dp-xxhdpi") @Test fun entryLarge() = shoot("entry_large") { entry() }
    @Config(qualifiers = "w360dp-h780dp-xhdpi") @Test fun outputSmall() = shoot("output_small") { output() }
    @Config(qualifiers = "w412dp-h915dp-xxhdpi") @Test fun outputLarge() = shoot("output_large") { output() }
    @Config(qualifiers = "w360dp-h780dp-xhdpi") @Test fun tasksSmall() = shoot("tasks_small") { tasks() }
    @Config(qualifiers = "w360dp-h780dp-xhdpi") @Test fun flocksSmall() = shoot("flocks_small") { flocks() }
    @Config(qualifiers = "w360dp-h780dp-xhdpi") @Test fun farmsSmall() = shoot("farms_small") { farms() }
    /** Small phone with the system text size at 130 %. */
    @Config(qualifiers = "w360dp-h780dp-xhdpi") @Test fun outputBigText() = shoot("output_bigtext") {
        val dens = androidx.compose.ui.platform.LocalDensity.current
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(dens.density, 1.3f)) { output() }
    }
    /** Changing the day keeps the card being read in place (cards above it change height between days). */
    @Config(qualifiers = "w360dp-h780dp-xhdpi") @Test fun dayChangeKeepsSection() {
        val all = rows(11)
        var day by mutableStateOf(11)
        rule.mainClock.autoAdvance = false
        rule.setContent { MyApplicationTheme { Box(Modifier.fillMaxSize().background(Color.Black)) {
            OutputScreen(flock, farm, all[day], all.take(day + 1), FeedStockSummary(0.0, 0.0, 0.0, emptyMap()), feedTypes, weather, emptyList(), day == 11, {})
        } } }
        rule.mainClock.advanceTimeBy(1000)
        // the feeding plan is in the Feed & water tab: scroll the tab bar into view and open it
        var g0 = 0
        while (rule.onNodeWithTag("tab_out_2").getUnclippedBoundsInRoot().top.value > 500f && g0++ < 40) {
            rule.onRoot().performTouchInput { swipe(Offset(8f, height * 0.7f), Offset(8f, height * 0.5f), 1200) }
            rule.mainClock.advanceTimeBy(1500)
        }
        rule.onNodeWithTag("tab_out_2").performClick()
        rule.mainClock.advanceTimeBy(600)
        // scroll with the finger (in the page margin, clear of the map and the clocks) until the plan is near the top
        var guard = 0
        // slow, short swipes (no fling) until the plan's title sits in the upper half of the screen
        while (rule.onAllNodesWithText("Feeding plan")[0].getUnclippedBoundsInRoot().top.value > 350f && guard++ < 80) {
            rule.onRoot().performTouchInput { swipe(Offset(8f, height * 0.7f), Offset(8f, height * 0.5f), 1200) }
            rule.mainClock.advanceTimeBy(1500)
        }
        val before = rule.onAllNodesWithText("Feeding plan")[0].getUnclippedBoundsInRoot().top
        day = 4          // a first-week day: alerts and growth cards above change height
        rule.mainClock.advanceTimeBy(900)
        val after = rule.onAllNodesWithText("Feeding plan")[0].getUnclippedBoundsInRoot().top
        println("feeding plan top: day 11 ${before.value} dp, day 4 ${after.value} dp")
        assertTrue("the title is on screen, not clipped at the top", before.value in 40f..350f)
        assertEquals(before.value, after.value, 30f)
        day = 11
        rule.mainClock.advanceTimeBy(900)
        assertEquals(before.value, rule.onAllNodesWithText("Feeding plan")[0].getUnclippedBoundsInRoot().top.value, 30f)
    }
    @Config(qualifiers = "w360dp-h6000dp-xhdpi") @Test fun outputLong() = shoot("output_long") { output() }
    /** System text at 130 %: nothing may break mid-word. */
    @Config(qualifiers = "w360dp-h7000dp-xhdpi") @Test fun outputLongBigText() = shoot("output_long_bigtext") {
        val dens = androidx.compose.ui.platform.LocalDensity.current
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(dens.density, 1.3f)) { output() }
    }
    @Config(qualifiers = "w360dp-h5200dp-xhdpi") @Test fun outputLongVent() = shootTab(1, "output_long_vent")
    @Config(qualifiers = "w360dp-h7200dp-xhdpi") @Test fun outputLongFeed() = shootTab(2, "output_long_feed")
    @Config(qualifiers = "w360dp-h1500dp-xhdpi") @Test fun stockSmall() = shoot("stock_small") { stock() }
    /** The quarter-width window and its stats grid. */
    @Config(qualifiers = "w360dp-h1500dp-xhdpi") @Test fun outputQuarter() {
        rule.mainClock.autoAdvance = false
        rule.setContent { MyApplicationTheme { output() } }
        rule.mainClock.advanceTimeBy(800)
        rule.onNodeWithTag("coopWidth_2").performClick()
        rule.mainClock.advanceTimeBy(1200)
        rule.onRoot().captureRoboImage("src/test/screenshots/screen_output_quarter.png")
    }
    /** The loading animation frame by frame: the mark put together part by part (stem, arms, comb, beak, eye). */
    @Config(qualifiers = "w520dp-h120dp-xhdpi") @Test fun loaderFrames() {
        rule.setContent { MyApplicationTheme { androidx.compose.foundation.layout.Row(Modifier.fillMaxSize().background(Color.Black),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            listOf(0.12f, 0.3f, 0.5f, 0.68f, 0.84f, 0.94f, 1f).forEach { com.example.flock.ui.components.FlockMark(progress = it, size = 64.dp) }
        } } }
        rule.onRoot().captureRoboImage("src/test/screenshots/loader_frames.png")
    }
    /** The ⓘ by a card opens its explanation. */
    @Config(qualifiers = "w360dp-h2400dp-xhdpi") @Test fun infoDialog() {
        rule.mainClock.autoAdvance = false
        rule.setContent { MyApplicationTheme { output() } }
        rule.mainClock.advanceTimeBy(800)
        rule.onNodeWithTag("info_clock").performClick()
        rule.mainClock.advanceTimeBy(800)
        rule.onAllNodesWithText("How it is worked out")[0].assertExists()
        com.github.takahirom.roborazzi.captureScreenRoboImage("src/test/screenshots/screen_info_clock.png")
    }

    private fun shootTab(tab: Int, name: String) {
        rule.mainClock.autoAdvance = false
        rule.setContent { MyApplicationTheme { output() } }
        rule.mainClock.advanceTimeBy(800)
        rule.onNodeWithTag("tab_out_$tab").performClick()
        rule.mainClock.advanceTimeBy(800)
        rule.onRoot().captureRoboImage("src/test/screenshots/screen_$name.png")
    }
}
