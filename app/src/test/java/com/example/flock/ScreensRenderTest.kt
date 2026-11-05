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
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
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
            // feed entered on day d is what the birds ate the day before: the chart's row d; this flock eats a little under it
            val bags = if (d >= 1) (CompanyStandard.feedPerDay(d) ?: 13.0) * (0.95 + 0.02 * Math.sin(d * 1.3)) * live / 1000.0 / 50.0 else 0.0
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

    /** A warm, humid day at the farm, hour by hour, for the day the fixture's last row carries. */
    private fun hourly(): List<com.example.flock.network.HourPoint> {
        val date = java.time.LocalDate.now().toString()
        return (0 until 24).map { h ->
            val f = (1 - Math.cos((h - 3) / 24.0 * 2 * Math.PI)) / 2          // coolest at 03:00, hottest at 15:00
            com.example.flock.network.HourPoint(String.format("%sT%02d:00", date, h), 25.0 + 9.0 * f, 92.0 - 30.0 * f)
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
        EntriesScreen(r.last(), 11, "30 Sep", "29 Sep", feedTypes, lock, "k", listOf(50, 50, 50, 50, 50, 50), emptyList(), {}, {}, farm = farm)
    }
    @Composable private fun output(weatherOn: Boolean = true) = Dash(1) {
        val r = rows(11)
        OutputScreen(flock, farm, r.last(), r, FeedStockSummary(0.0, 0.0, 0.0, emptyMap()), feedTypes, weather, hourly(), true, {}, weatherOn = weatherOn)
    }
    @Composable private fun entryNewDay() = Dash(0) {
        val r = rows(11)
        // a day with no feed entered yet: its feed row starts with the variety entered last (B3 here)
        EntriesScreen(r.last().copy(feedBagsUsed = 0.0, feedUsedBreakdown = "", savedFields = ""), 11, "30 Sep", "29 Sep", feedTypes, lock, "k2", listOf(50, 50, 50, 50, 50, 50), emptyList(), {}, {}, farm = farm, lastFeedType = "B3")
    }
    @Composable private fun stock() = Dash(2) {
        val r = rows(11)
        com.example.flock.ui.screens.StockScreen(flock, farm, r.last(), r, feedTypes)
    }
    @Composable private fun tasks() = Dash(3) {
        TasksScreen(listOf(TaskEntity(spreadsheetId = "t", taskId = "1", flockId = "f", block = "Morning", label = "Check drinkers", time = "06:00")),
            11, 42, { _, _, _, _, _, _, _, _, _, _ -> }, {}, { _, _, _ -> }, { _, _ -> }, { _, _, _ -> },
            footer = { val r = rows(11); com.example.flock.ui.screens.DayClockSection(flock, farm, r.last(), r, feedTypes, weather, hourly(), true, true) })
    }
    @Composable private fun flocks() = FlocksScreen(farm.farmName, farm.timeZone, listOf(flock, flock.copy(flockId = "g", name = "Batch #2", status = "closed", startDate = "2026-07-01")),
        false, {}, {}, {}, { _, _, _, _, _, _, _ -> }, {}, { _, _ -> })
    @Composable private fun farms() = FarmsScreen(listOf(FarmRegistryEntity(spreadsheetId = "t", farmName = "Maa Tarini Farm")), "me@x.com", false, false,
        {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    @Config(qualifiers = "w360dp-h780dp-xhdpi") @Test fun entrySmall() = shoot("entry_small") { entry() }
    @Config(qualifiers = "w412dp-h915dp-xxhdpi") @Test fun entryLarge() = shoot("entry_large") { entry() }
    /** Six locations (as the last weighing) with the sampling map under them. */
    @Config(qualifiers = "w360dp-h1500dp-xhdpi") @Test fun entryLong() = shoot("entry_long") { entry() }
    /** The app opening: the mark alone in the middle of a black screen. */
    @Config(qualifiers = "w360dp-h780dp-xhdpi") @Test fun opening() {
        rule.mainClock.autoAdvance = false
        rule.setContent { MyApplicationTheme { Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = androidx.compose.ui.Alignment.Center) {
            com.example.flock.ui.components.FlockOpening() } } }
        rule.mainClock.advanceTimeBy(1400)
        rule.onRoot().captureRoboImage("src/test/screenshots/screen_opening.png")
    }
    @Config(qualifiers = "w360dp-h780dp-xhdpi") @Test fun outputSmall() = shoot("output_small") { output() }
    @Config(qualifiers = "w412dp-h915dp-xxhdpi") @Test fun outputLarge() = shoot("output_large") { output() }
    @Config(qualifiers = "w360dp-h780dp-xhdpi") @Test fun tasksSmall() = shoot("tasks_small") { tasks() }
    @Config(qualifiers = "w360dp-h780dp-xhdpi") @Test fun flocksSmall() = shoot("flocks_small") { flocks() }
    @Config(qualifiers = "w360dp-h780dp-xhdpi") @Test fun farmsSmall() = shoot("farms_small") { farms() }
    @Config(qualifiers = "w360dp-h780dp-xhdpi") @Test fun signInSmall() = shoot("signin_small") { com.example.flock.ui.screens.SignInScreen({}, {}) }
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
        // scroll with the finger (in the page margin, clear of the map and the clocks) until the feed card is near the top
        var guard = 0
        // slow, short swipes (no fling) until the feed card is the card at the top of the screen (the one being read)
        while (rule.onNodeWithTag("feed_conclusion").getUnclippedBoundsInRoot().top.value > 60f && guard++ < 160) {
            rule.onRoot().performTouchInput { swipe(Offset(8f, height * 0.7f), Offset(8f, height * 0.6f), 1200) }
            rule.mainClock.advanceTimeBy(1500)
        }
        val before = rule.onNodeWithTag("feed_conclusion").getUnclippedBoundsInRoot().top
        day = 4          // a first-week day: alerts and growth cards above change height
        rule.mainClock.advanceTimeBy(900)
        val after = rule.onNodeWithTag("feed_conclusion").getUnclippedBoundsInRoot().top
        println("feed card top: day 11 ${before.value} dp, day 4 ${after.value} dp")
        assertTrue("the feed card is at the top", before.value in -120f..60f)
        assertEquals(before.value, after.value, 40f)
        day = 11
        rule.mainClock.advanceTimeBy(900)
        assertEquals(before.value, rule.onNodeWithTag("feed_conclusion").getUnclippedBoundsInRoot().top.value, 40f)
    }
    @Config(qualifiers = "w360dp-h9000dp-xhdpi") @Test fun outputLong() = shoot("output_long") { output() }
    /** One view, no folds: the window, the time card, the flock table, then the feed. */
    @Config(qualifiers = "w360dp-h3600dp-xhdpi") @Test fun outputOne() = shoot("output_one") { output() }
    @Config(qualifiers = "w360dp-h4400dp-xhdpi") @Test fun outputOneBigText() = shoot("output_one_bigtext") {
        val dens = androidx.compose.ui.platform.LocalDensity.current
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(dens.density, 1.3f)) { output() }
    }
    /** Every card is open on the page (no folds, no "choose what shows"), each in its group, at 130 % text. */
    @Config(qualifiers = "w360dp-h11000dp-xhdpi") @Test fun outputAllOpenBigText() {
        rule.mainClock.autoAdvance = false
        rule.setContent { MyApplicationTheme {
            val dens = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(dens.density, 1.3f)) { output() }
        } }
        rule.mainClock.advanceTimeBy(1200)
        // v53: the farm's order — birds (window, seven numbers, radar), feed and floor, air, lifting, water, graphs — each with its farm view
        listOf("glance", "radar_birds", "time_rail", "section_tabs", "feed_conclusion", "space_why", "coop_layers", "coop_facts",
            "tile_live", "tile_cv", "tile_pans", "tile_barricades", "farm_iso_feed", "farm_iso_air", "meat_bar", "tile_water",
            "walk_feed", "walk_water", "chart", "flock_book").forEach { rule.onNodeWithTag(it).assertExists() }
        listOf("jump_birds", "jump_feed", "jump_air", "jump_lift", "jump_water", "jump_graphs").forEach { rule.onNodeWithTag(it).assertExists() }
        // gone by request: the other radars, the feeding log, the nipple-line drawing, the named birds, the old walk and floor cards
        listOf("radar_feed", "radar_air", "radar_floor", "feed_log", "nipple_view", "plan_step_0", "farm_iso_water", "space_by_auto",
            "chart_cursor", "chart_window").forEach {
            assertTrue(it, rule.onAllNodesWithTag(it).fetchSemanticsNodes().isEmpty()) }
        assertTrue(rule.onAllNodesWithText("Pip").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithText("Choose what shows", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithText("At a glance").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithText("Half", substring = true).fetchSemanticsNodes().isEmpty())
        // v52: no labels on the arrows (green good, red bad — no middle grade), no sentence over the radar;
        // v56: one FCR and one cFCR (the company's), no target or harvest day, no "fans by outside air" grid
        listOf("a little off", "far off", "Birds against the company", "cFCR, breed", "dead counted", "Reaches target", "Fans by outside air").forEach {
            assertTrue(it, rule.onAllNodesWithText(it, substring = true).fetchSemanticsNodes().isEmpty()) }
        rule.onRoot().captureRoboImage("src/test/screenshots/screen_output_all_open_bigtext.png")
    }
    /** v53's sections on the farm's flock after lifting (day 44): feed and floor, air (litter view), lifting, water. */
    @Config(qualifiers = "w360dp-h5600dp-xhdpi") @Test fun newCards() {
        val r = KgfFlock.computed(44)
        val d = com.example.flock.ui.screens.OutputData(KgfFlock.flock, KgfFlock.farm, r.last(), r, KgfFlock.feedTypes, null, emptyList(), false, false)
        val needs = d.needsAt(12.0, d.plannedFeedings)
        rule.mainClock.autoAdvance = false
        rule.setContent { MyApplicationTheme { androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().background(Color.Black).padding(14.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            com.example.flock.ui.screens.FeedSection(d, d.recommendedOption, {}, needs) { _, _ -> }
            com.example.flock.ui.screens.AirSection(d, 14, needs, false, {}, com.example.flock.ui.screens.houseMapOf(d, 14))
            com.example.flock.ui.screens.LiftSection(d)
            com.example.flock.ui.screens.WaterSection(d, needs) { }
        } } }
        rule.mainClock.advanceTimeBy(1600)
        rule.onNodeWithTag("tile_barricades").assertExists()
        rule.onNodeWithTag("farm_iso_feed").assertExists()
        rule.onNodeWithTag("barricade_slider").assertExists()
        rule.onNodeWithTag("air_view_litter").performClick()
        rule.mainClock.advanceTimeBy(800)
        rule.onNodeWithTag("litter_view").assertExists()
        rule.onNodeWithTag("farm_iso_litter").assertExists()
        rule.onRoot().captureRoboImage("src/test/screenshots/screen_new_cards.png")
    }
    /** Two phones changed the same entries: both values side by side, a tap keeps one (v55). */
    @Config(qualifiers = "w360dp-h780dp-xhdpi") @Test fun clashWindow() {
        val clashes = listOf(com.example.flock.ui.Clash(12, "mortality", "Deaths", "14", "12"),
            com.example.flock.ui.Clash(12, "w1", "Weight, location 1", "612.5", "598"))
        var kept: Pair<String, Boolean>? = null
        rule.setContent { MyApplicationTheme { com.example.flock.ui.components.ClashDialog(clashes, { c, mine -> kept = c.column to mine }, {}) } }
        rule.onAllNodesWithText("Day 12 · Deaths")[0].assertExists()
        com.github.takahirom.roborazzi.captureScreenRoboImage("src/test/screenshots/screen_clash.png")
        rule.onNodeWithTag("clash_theirs_12_mortality").performClick()
        assertEquals("mortality" to false, kept)
    }
    /** Every symbol in one sheet, with its name: the set to look at together. */
    @Config(qualifiers = "w360dp-h1400dp-xhdpi") @Test fun gameIconSheet() {
        rule.setContent { MyApplicationTheme { androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().background(Color.Black).padding(10.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            com.example.flock.ui.screens.GIcon.values().toList().chunked(5).forEach { row ->
                androidx.compose.foundation.layout.Row(Modifier.fillMaxSize().weight(1f)) {
                    row.forEach { ic ->
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            com.example.flock.ui.screens.GameIcon(ic, 40.dp)
                            Text(ic.name.lowercase(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
                        }
                    }
                    repeat(5 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        } } }
        rule.onRoot().captureRoboImage("src/test/screenshots/game_icons.png")
    }
    /** The whole house in each of its views: the feed plan's pans and barricades, the air, the fans, the pads, the litter, the water lines. */
    @Config(qualifiers = "w360dp-h1500dp-xhdpi") @Test fun farmViews() {
        val r = KgfFlock.computed(44)
        val d = com.example.flock.ui.screens.OutputData(KgfFlock.flock, KgfFlock.farm, r.last(), r, KgfFlock.feedTypes, null, emptyList(), false, false)
        val feed = com.example.flock.ui.screens.isoFarmOf(d, d.patternFor(d.recommendedOption), 40.0, 250.0, true, 14)
        val air = com.example.flock.ui.screens.isoFarmOf(d, d.feedPattern, d.penStartFt, d.barricadeFtNow, false, 14, com.example.flock.ui.screens.houseMapOf(d, 14))
        println("plan: ${d.floorPlan?.title}")
        rule.mainClock.autoAdvance = false
        rule.setContent { MyApplicationTheme { androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().background(Color.Black).padding(14.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            com.example.flock.ui.screens.FarmIso(feed, com.example.flock.ui.screens.IsoView.FEED)
            com.example.flock.ui.screens.FarmIso(air, com.example.flock.ui.screens.IsoView.AIR, layer = com.example.flock.models.HouseMap.Layer.HEAT)
            com.example.flock.ui.screens.FarmIso(air, com.example.flock.ui.screens.IsoView.FANS)
            com.example.flock.ui.screens.FarmIso(air.copy(padWet = 1.0, padLayout = "c"), com.example.flock.ui.screens.IsoView.PADS)
            com.example.flock.ui.screens.FarmIso(air, com.example.flock.ui.screens.IsoView.LITTER, layer = com.example.flock.models.HouseMap.Layer.LITTER)
            com.example.flock.ui.screens.FarmIso(air, com.example.flock.ui.screens.IsoView.WATER)
        } } }
        rule.mainClock.advanceTimeBy(700)
        rule.onRoot().captureRoboImage("src/test/screenshots/farm_views.png")
        // pinched in on the feed view: the pans and their rims, the zoom shown (a tap fits it again)
        rule.onNodeWithTag("farm_iso_feed").performTouchInput {
            down(0, Offset(width * 0.45f, height * 0.5f)); down(1, Offset(width * 0.55f, height * 0.5f))
            moveTo(0, Offset(width * 0.15f, height * 0.5f)); moveTo(1, Offset(width * 0.85f, height * 0.5f)); up(0); up(1)
        }
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithTag("iso_zoom_reset").assertExists()
        rule.onRoot().captureRoboImage("src/test/screenshots/farm_views_zoomed.png")
    }
    /** Farm settings: the barricades the farm has, how its floor is set, its pads — and the Save button reads. */
    @Config(qualifiers = "w360dp-h1500dp-xhdpi") @Test fun farmSettingsSaveHasWords() {
        var saved: FarmEntity? = null
        rule.setContent { MyApplicationTheme { com.example.flock.ui.components.FarmSettingsDialog(farm, com.example.flock.data.ConfigEntity(spreadsheetId = "t"), feedTypes,
            {}, { f, _ -> saved = f }, {}, {}) } }
        rule.onAllNodesWithText("Save settings")[0].assertExists()
        // v54: no barricade count, no modes; the pans counted all together with the sensor pans among them
        assertTrue(rule.onAllNodesWithText("Barricades you have", substring = true).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("total_pans").performScrollTo().performTextReplacement("116")
        rule.onAllNodesWithText("114 feed pans")[0].assertExists()
        rule.onNodeWithTag("pad_layout_c").performScrollTo().performClick()
        com.github.takahirom.roborazzi.captureScreenRoboImage("src/test/screenshots/screen_farm_settings.png")
        rule.onNodeWithTag("farm_settings_save").performClick()
        assertEquals("c", saved?.padLayout); assertEquals(114, saved?.pansPerFeederLine); assertEquals(2, saved?.sensorPansPerLine)
    }
    /** The house map's views on the farm window: the litter layer draws, with its key. */
    @Config(qualifiers = "w360dp-h1600dp-xhdpi") @Test fun coopLayers() {
        rule.mainClock.autoAdvance = false
        rule.setContent { MyApplicationTheme { output() } }
        rule.mainClock.advanceTimeBy(800)
        rule.onNodeWithTag("coop_layer_0").performClick()
        rule.mainClock.advanceTimeBy(800)
        rule.onAllNodesWithText("past the ideal", substring = true)[0].assertExists()
        rule.onRoot().captureRoboImage("src/test/screenshots/screen_coop_litter.png")
    }
    /** The model weights file reads; a number and a true/false become weights. */
    @Test fun modelParamsRead() {
        val p = com.example.flock.models.ModelStore.parse("""{"version": 3, "models": {"house_map": {"drinker_wet_pts": 9.5, "fans_at_back": false}}}""")!!
        assertEquals(3, p.version)
        assertEquals(9.5, p.get("house_map", "drinker_wet_pts", 7.0), 0.0)
        assertEquals(0.0, p.get("house_map", "fans_at_back", 1.0), 0.0)
        assertEquals(0.6, p.get("house_map", "sensible", 0.6), 0.0)
    }
    /** The tabs at the top go to a section and light the one being read. */
    @Config(qualifiers = "w360dp-h780dp-xhdpi") @Test fun tabsGoToASection() {
        rule.mainClock.autoAdvance = false
        rule.setContent { MyApplicationTheme { output() } }
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithTag("jump_water").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.mainClock.advanceTimeBy(1500)
        val top = rule.onNodeWithTag("tile_water").getUnclippedBoundsInRoot().top.value
        println("water tile at $top dp after the tap")
        assertTrue("the water section is on screen", top in 0f..780f)
        rule.onRoot().captureRoboImage("src/test/screenshots/screen_tabs_water.png")
    }
    /** The time rail (down the right edge) moves the page to another time of the day; touched it opens out; Now brings the clock back. */
    @Config(qualifiers = "w360dp-h2400dp-xhdpi") @Test fun timeSliderPicksATime() {
        rule.mainClock.autoAdvance = false
        rule.setContent { MyApplicationTheme { output() } }
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithTag("time_rail").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(18 * 60f) }
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithTag("time_shown").assertTextEquals("18:00")
        // touched, the rail opens out with the hours and the time on its handle
        rule.onNodeWithTag("time_rail").performTouchInput { down(Offset(width / 2f, height * 0.25f)) }
        rule.mainClock.advanceTimeBy(300)
        rule.onRoot().captureRoboImage("src/test/screenshots/screen_time_rail_open.png")
        rule.onNodeWithTag("time_rail").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithTag("time_shown").assertTextEquals("06:00")
        // back to the present: the Now button at the bottom right, shown only while another time is picked
        rule.onRoot().captureRoboImage("src/test/screenshots/screen_time_now_button.png")
        rule.onNodeWithTag("rail_now").performClick()
        rule.mainClock.advanceTimeBy(600)
        assertTrue(rule.onAllNodesWithTag("time_now").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithTag("rail_now").fetchSemanticsNodes().isEmpty())
    }
    /** The graphs: one chart picked from the row, lifting days marked; opened full screen from its corner. */
    @Config(qualifiers = "w360dp-h780dp-xhdpi") @Test fun chartOpensFullScreen() {
        val r = KgfFlock.computed(44)
        val d = com.example.flock.ui.screens.OutputData(KgfFlock.flock, KgfFlock.farm, r.last(), r, KgfFlock.feedTypes, null, emptyList(), false, false)
        rule.mainClock.autoAdvance = false
        rule.setContent { MyApplicationTheme { Box(Modifier.fillMaxSize().background(Color.Black).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            com.example.flock.ui.screens.GraphsCard(d) } } }
        rule.mainClock.advanceTimeBy(800)
        rule.onNodeWithTag("chart_pick_birds").performClick()
        rule.mainClock.advanceTimeBy(400)
        rule.onRoot().captureRoboImage("src/test/screenshots/screen_graphs.png")
        rule.onNodeWithTag("chart_zoom").performClick()
        rule.mainClock.advanceTimeBy(800)
        rule.onNodeWithTag("chart_zoomed").assertExists()
        com.github.takahirom.roborazzi.captureScreenRoboImage("src/test/screenshots/screen_chart_zoomed.png")
    }
    /** The flock book: one line a day, lifting days marked; full screen too. */
    @Config(qualifiers = "w360dp-h900dp-xhdpi") @Test fun flockBook() {
        val r = KgfFlock.computed(44)
        val d = com.example.flock.ui.screens.OutputData(KgfFlock.flock, KgfFlock.farm, r.last(), r, KgfFlock.feedTypes, null, emptyList(), false, false)
        var picked = -1
        rule.mainClock.autoAdvance = false
        rule.setContent { MyApplicationTheme { Box(Modifier.fillMaxSize().background(Color.Black).padding(14.dp)) { com.example.flock.ui.screens.FlockBookCard(d) { picked = it } } } }
        rule.mainClock.advanceTimeBy(800)
        rule.onNodeWithTag("book_day_43").assertExists()
        rule.onAllNodesWithText("43 L")[0].assertExists()
        rule.onRoot().captureRoboImage("src/test/screenshots/screen_flock_book.png")
        rule.onNodeWithTag("book_day_43").performClick()
        assertEquals(43, picked)
    }
    /** The day clock is under the tasks now, and no longer on the Output page; the likelihoods card is. */
    @Config(qualifiers = "w360dp-h2400dp-xhdpi") @Test fun clockLivesUnderTheTasks() {
        rule.mainClock.autoAdvance = false
        var showTasks by mutableStateOf(false)
        rule.setContent { MyApplicationTheme { if (showTasks) tasks() else output() } }
        rule.mainClock.advanceTimeBy(1000)
        assertTrue(rule.onAllNodesWithText("Day clock").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("glance").assertExists()
        assertTrue(rule.onAllNodesWithText("Hungry", substring = true).fetchSemanticsNodes().isNotEmpty())
        showTasks = true
        rule.mainClock.advanceTimeBy(1000)
        rule.onAllNodesWithText("Day clock")[0].assertExists()
        rule.onRoot().captureRoboImage("src/test/screenshots/screen_tasks_clock.png")
    }
    /** The day slider is one piece — its track carries the periods — and it still chooses a day by tap, drag or an accessibility action. */
    @Config(qualifiers = "w360dp-h260dp-xhdpi") @Test fun daySliderChoosesADay() {
        var picked = -1
        rule.setContent { MyApplicationTheme { TopFlockBar(farm.farmName, flock, 11, 20, "30 Sep 2026", lock, weather, "synced", {}, {}, { picked = it }, {}, {}) } }
        assertTrue(rule.onAllNodesWithText("Brooding", substring = true).fetchSemanticsNodes().isEmpty())     // no separate legend
        rule.onNodeWithTag("day_slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(30f) }
        assertEquals(30, picked)
        // a tap two thirds along the track lands near day 28 of 42
        rule.onNodeWithTag("day_slider").performTouchInput { click(Offset(width * 2f / 3f, height / 2f)) }
        assertTrue("picked $picked", picked in 26..30)
        rule.onRoot().captureRoboImage("src/test/screenshots/top_bar.png")
    }
    /** The slider's periods: brooding, growing, finishing to the planned harvest, and the days run past it. */
    @Test fun sliderPeriodsFollowThePlannedHarvest() {
        val p = com.example.flock.ui.components.dayPeriods(42, 42)
        assertEquals(listOf("Brooding" to (0 to 10), "Growing" to (11 to 27), "Finishing" to (28 to 42)), p.map { it.name to (it.from to it.to) })
        val over = com.example.flock.ui.components.dayPeriods(47, 42)
        assertEquals("Past harvest" to (43 to 47), over.last().name to (over.last().from to over.last().to))
        assertEquals(listOf("Brooding" to (0 to 10), "Growing" to (11 to 21)), com.example.flock.ui.components.dayPeriods(21, 21).map { it.name to (it.from to it.to) })
    }
    /** The weather switch off: the ideal house, as before. */
    @Config(qualifiers = "w360dp-h2600dp-xhdpi") @Test fun outputWeatherOff() = shoot("output_weather_off") { output(weatherOn = false) }
    @Config(qualifiers = "w360dp-h3200dp-xhdpi") @Test fun entryNewDayLong() {
        shoot("entry_new_day") { entryNewDay() }
        // a day not saved yet does not lock: no lock banner, no cut-off
        assertTrue(rule.onAllNodesWithText("locked", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithText("cut-off", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        // a new day's feed row starts with the variety entered last: B3 shows in the feed row as well as in the third delivery
        assertTrue(rule.onAllNodesWithText("B3", substring = true).fetchSemanticsNodes().size >= 2)
        // v52: plain headings, no numbers; no separate box for the time — it is picked after the hold, at Save
        rule.onAllNodesWithText("Weight samples")[0].assertExists()
        assertTrue(rule.onAllNodesWithText("1. Weight", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithTag("entry_time").fetchSemanticsNodes().isEmpty())
        rule.onAllNodesWithTag("hold_button")[0].performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1200)
        rule.onAllNodesWithTag("hold_button")[0].performTouchInput { up() }
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithTag("entry_time").assertExists()
        rule.onNodeWithTag("save_confirm").assertExists()
        com.github.takahirom.roborazzi.captureScreenRoboImage("src/test/screenshots/screen_entry_save_time.png")
    }
    /** A dial turned by hand holds for 30 seconds, then the dials go back to the weather. */
    @Config(qualifiers = "w360dp-h12000dp-xhdpi") @Test fun dialsGoBackToTheWeather() {
        rule.mainClock.autoAdvance = false
        rule.setContent { MyApplicationTheme { output() } }
        rule.mainClock.advanceTimeBy(800)
        // the dials are under the Fans view of the air section
        rule.onNodeWithTag("air_view_fans").performClick()
        rule.mainClock.advanceTimeBy(800)
        rule.onAllNodesWithText("The weather at the farm now · turn a dial to try another")[0].assertExists()
        rule.onAllNodesWithText("+")[0].performClick()
        rule.mainClock.advanceTimeBy(1500)
        assertTrue(rule.onAllNodesWithText("Set by hand", substring = true).fetchSemanticsNodes().isNotEmpty())
        rule.mainClock.advanceTimeBy(31_000)
        rule.onAllNodesWithText("The weather at the farm now · turn a dial to try another")[0].assertExists()
    }
    /** System text at 130 %: nothing may break mid-word. */
    @Config(qualifiers = "w360dp-h11000dp-xhdpi") @Test fun outputLongBigText() = shoot("output_long_bigtext") {
        val dens = androidx.compose.ui.platform.LocalDensity.current
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(dens.density, 1.3f)) { output() }
    }
    /** A saved day locks: its entries can't be changed (notes and feed received can), until it is unlocked. */
    @Config(qualifiers = "w360dp-h3200dp-xhdpi") @Test fun savedDayLocksUntilUnlocked() {
        val r = rows(11)
        val at = java.time.LocalDate.now().atTime(8, 15).atZone(java.time.ZoneId.of(farm.timeZone)).toInstant().toEpochMilli()
        val saved = r.last().copy(savedFields = "W,M,F", updatedAt = at + 60_000, weighedAt = at, w1 = 600.0, n1 = 2, notes = "- litter turned")
        var got: com.example.flock.ui.DailyInputs? = null
        rule.mainClock.autoAdvance = false
        rule.setContent { MyApplicationTheme { Dash(0) { EntriesScreen(saved, 11, "30 Sep", "29 Sep", feedTypes, lock, "k3", listOf(50), emptyList(), { got = it }, {}, farm = farm) } } }
        rule.mainClock.advanceTimeBy(1000)
        rule.onAllNodesWithText("Saved and locked", substring = true)[0].assertExists()
        rule.onNodeWithTag("mortality_input").assertIsNotEnabled()
        rule.onNodeWithTag("notes_input").assertIsEnabled()
        rule.onAllNodesWithText("Hold to save notes and feed received")[0].assertExists()
        rule.onRoot().captureRoboImage("src/test/screenshots/screen_entry_locked.png")
        // the hold: a fill and a count while held; saved with the time it was entered at, not now
        rule.onAllNodesWithTag("hold_button")[0].performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(500)
        rule.onAllNodesWithText("Keep holding", substring = true)[0].assertExists()
        rule.onRoot().captureRoboImage("src/test/screenshots/screen_entry_holding.png")
        rule.mainClock.advanceTimeBy(700)
        rule.onAllNodesWithTag("hold_button")[0].performTouchInput { up() }
        rule.mainClock.advanceTimeBy(300)
        assertEquals(at, got?.at)
        // unlocked, the entries open again
        rule.onNodeWithTag("unlock_day").performClick(); rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithTag("unlock_confirm").performClick(); rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithTag("mortality_input").assertIsEnabled()
        rule.onAllNodesWithText("Unlocked", substring = true)[0].assertExists()
        // updating it: after the hold the time offered is the one first saved (08:15), and that is what is saved
        got = null
        rule.onAllNodesWithTag("hold_button")[0].performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1200)
        rule.onAllNodesWithTag("hold_button")[0].performTouchInput { up() }
        rule.mainClock.advanceTimeBy(400)
        rule.onAllNodesWithText("as first saved", substring = true)[0].assertExists()
        rule.onNodeWithTag("entry_time").assertTextEquals("08:15")
        rule.onNodeWithTag("save_confirm").performClick()
        rule.mainClock.advanceTimeBy(400)
        assertEquals(at, got?.at)
    }
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
        rule.onNodeWithTag("info_glance").performClick()
        rule.mainClock.advanceTimeBy(800)
        rule.onAllNodesWithText("How it is worked out")[0].assertExists()
        com.github.takahirom.roborazzi.captureScreenRoboImage("src/test/screenshots/screen_info_glance.png")
    }
}
