package com.example.flock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FarmEntity
import com.example.flock.data.FeedTypeEntity
import com.example.flock.engine.CompanyStandard
import com.example.flock.engine.PhysiologicalEngine
import com.example.flock.ui.screens.KpiScorecard
import com.example.flock.ui.screens.PerformanceCharts
import com.example.flock.ui.screens.TodayTiles
import com.example.flock.ui.screens.VentSimpleCard
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the new Output visuals with a plausible day-24 flock, to eyeball layout and charts. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class OutputVisualsScreenshotTest {

    @get:Rule val rule = createComposeRule()

    private val farm = FarmEntity(spreadsheetId = "t")
    private val feedTypes = listOf(
        FeedTypeEntity(spreadsheetId = "t", code = "B1", name = "Starter", bagKg = 50.0, sortOrder = 1),
        FeedTypeEntity(spreadsheetId = "t", code = "B2", name = "Grower", bagKg = 50.0, sortOrder = 2),
        FeedTypeEntity(spreadsheetId = "t", code = "B3", name = "Finisher", bagKg = 50.0, sortOrder = 3)
    )

    private fun rows(upTo: Int): List<DailyDataEntity> {
        val placed = 15700
        var live = placed
        return (0..upTo).map { d ->
            val mort = if (d == 0) 0 else (live * CompanyStandard.dailyMortPct(d) * 1.2 / 100).toInt()
            live -= mort
            val sampled = d % 3 == 0 && d > 0
            val bw = (CompanyStandard.bw(d) ?: 42.0) * 0.97
            val fcr = CompanyStandard.fcr(d)?.let { it * 1.03 }
            // feed logged on day d = yesterday's use (a bit above company)
            val bags = if (d >= 1) (CompanyStandard.feedPerDay(d - 1) ?: 13.0) * 1.04 * live / 1000.0 / 50.0 else 0.0
            val code = CompanyStandard.feedPhase(maxOf(1, d - 1))
            DailyDataEntity(
                spreadsheetId = "t", flockId = "f", dayNumber = d, date = "",
                mortality = mort, liveBirds = live,
                avgWeight = if (sampled) bw else null, sampleEntered = sampled, projected = !sampled,
                weightAge = PhysiologicalEngine.weightAgeFromBW(bw, "Ross308"),
                idealWeight = PhysiologicalEngine.bwFromDay(d.toDouble(), "Ross308"),
                fcr = if (d >= 3) fcr else null,
                cFcr = if (d >= 3 && fcr != null) PhysiologicalEngine.computeCorrectedFcr(bw / 1000, fcr) else null,
                cumMortPct = (placed - live) * 100.0 / placed,
                maxMortPct = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_MAXMORT_BY_AGE, d.toDouble()),
                feedBagsUsed = bags, feedUsedType = code, feedUsedBreakdown = if (bags > 0) "$code=$bags" else "",
                totalFeedKg = (CompanyStandard.feedPerDay(maxOf(1, d)) ?: 13.0) * live / 1000.0,
                totalWaterL = (CompanyStandard.feedPerDay(maxOf(1, d)) ?: 13.0) * 1.8 * live / 1000.0,
                setTemp = 24.0, tempIdeal = 24.0
            )
        }
    }

    private fun shoot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent {
            MyApplicationTheme {
                Box(Modifier.background(MaterialTheme.colorScheme.background).padding(12.dp)) { content() }
            }
        }
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$name.png")
    }

    @Test fun scorecard() {
        val r = rows(24)
        shoot("output_scorecard") {
            androidx.compose.foundation.layout.Column {
                TodayTiles(r.last(), farm, 15700)
                KpiScorecard(r.last(), r, "Ross308", farm, feedTypes)
            }
        }
    }

    @Config(qualifiers = "w412dp-h1400dp-xxhdpi")
    @Test fun charts() {
        val r = rows(24)
        shoot("output_charts") { PerformanceCharts(r, "Ross308", 24, 42, farm, feedTypes) }
    }

    @Test fun ventilation() {
        val r = rows(24)
        shoot("output_vent") { VentSimpleCard(r.last(), farm, null, emptyList(), isToday = false) }
    }
}
