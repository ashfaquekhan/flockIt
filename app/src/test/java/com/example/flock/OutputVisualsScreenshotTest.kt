package com.example.flock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FarmEntity
import com.example.flock.data.FeedTypeEntity
import com.example.flock.data.FlockEntity
import com.example.flock.engine.CompanyStandard
import com.example.flock.engine.PhysiologicalEngine
import com.example.flock.network.HourPoint
import com.example.flock.network.WeatherResult
import com.example.flock.ui.screens.OutputData
import com.example.flock.ui.screens.TagLegend
import com.example.flock.ui.screens.Topic
import com.example.flock.ui.screens.TopicTiles
import com.example.flock.ui.screens.TopicView
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.PI
import kotlin.math.sin

/** Renders every Output topic with a plausible day-24 flock, to eyeball layout and charts. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h5200dp-xxhdpi", sdk = [34])
class OutputVisualsScreenshotTest {

    @get:Rule val rule = createComposeRule()

    private val farm = FarmEntity(spreadsheetId = "t", feedBagKg = 50.0, feederLines = 4, feederLineBags = 3, pansPerFeederLine = 60,
        manualFeeders = 150, manualDrinkers = 180, nipplesPerLine = 290, godownBags = 600.0)
    private val flock = FlockEntity(spreadsheetId = "t", flockId = "f", name = "F", startDate = "2026-09-04", birdsPlaced = 15700, receptionMort = 40)
    private val feedTypes = listOf(
        FeedTypeEntity(spreadsheetId = "t", code = "B1", name = "Starter", bagKg = 50.0, sortOrder = 1),
        FeedTypeEntity(spreadsheetId = "t", code = "B2", name = "Grower", bagKg = 50.0, sortOrder = 2),
        FeedTypeEntity(spreadsheetId = "t", code = "B3", name = "Finisher", bagKg = 50.0, sortOrder = 3)
    )
    private fun date(d: Int) = java.time.LocalDate.parse("2026-09-04").plusDays(d.toLong()).toString()

    private fun open(d: Int) = if (d >= 11) 1.0 else 0.3 + 0.7 * d / 11.0

    private fun rows(upTo: Int): List<DailyDataEntity> {
        val placed = 15700
        var live = placed - 40
        return (0..upTo).map { d ->
            val mort = if (d == 0) 0 else (live * CompanyStandard.dailyMortPct(d) * 1.2 / 100).toInt()
            live -= mort
            val sampled = (d % 3 == 0 || d == 7) && d > 0
            val bw = (CompanyStandard.bw(d) ?: 42.0) * 0.97
            val fcr = CompanyStandard.fcr(d)?.let { it * 1.03 }
            val bags = if (d >= 1) (CompanyStandard.feedPerDay(d - 1) ?: 13.0) * 1.04 * live / 1000.0 / 50.0 else 0.0
            val code = CompanyStandard.feedPhase(maxOf(1, d - 1))
            val feed = CompanyStandard.feedForWeight(bw) * live / 1000.0
            DailyDataEntity(
                spreadsheetId = "t", flockId = "f", dayNumber = d, date = date(d),
                mortality = mort, liveBirds = live,
                w1 = if (sampled) bw * 20 * 0.95 else null, n1 = if (sampled) 20 else null,
                w2 = if (sampled) bw * 20 * 1.04 else null, n2 = if (sampled) 20 else null,
                w3 = if (sampled) bw * 20 else null, n3 = if (sampled) 20 else null,
                avgWeight = if (sampled) bw else null, sampleEntered = sampled, projected = !sampled, cv = if (sampled) 8.4 else null,
                weightAge = PhysiologicalEngine.weightAgeFromBW(bw, "Ross308"),
                idealWeight = PhysiologicalEngine.bwFromDay(d.toDouble(), "Ross308"),
                fcr = if (d >= 3) fcr else null,
                cFcr = if (d >= 3 && fcr != null) PhysiologicalEngine.computeCorrectedFcr(bw / 1000, fcr) else null,
                cumMortPct = (placed - live) * 100.0 / placed, livability = live * 100.0 / placed,
                maxMortPct = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_MAXMORT_BY_AGE, d.toDouble()),
                feedBagsUsed = bags, feedUsedType = code, feedUsedBreakdown = if (bags > 0) "$code=$bags" else "",
                feedRecB1 = when (d) { 0 -> 200.0; 10 -> 450.0; 21 -> 420.0; else -> 0.0 },
                feedTypeB1 = when (d) { 10 -> "B2"; 21 -> "B3"; else -> "B1" },
                totalFeedKg = feed, feedPerBird = feed * 1000 / live,
                totalWaterL = feed * 1.8, waterPerBird = feed * 1.8 * 1000 / live, waterHighL = feed * 1.8 * 1.18, waterLowL = feed * 1.8 * 0.85,
                drinkerFlowLHrLine = feed * 1.8 / 5 / 16, drinkerPressureIn = 8.0,
                densityKgM2 = live * bw / 1000 / (299 * 39 * 0.0929 * open(d)), ftPerBird = 299.0 * 39 * open(d) / live, minFtPerBird = 0.8, occupiedFt2 = 299.0 * 39 * open(d),
                barricadeFt = if (open(d) < 1.0) (299 * open(d)).toInt() else 0,
                setTemp = 24.0, tempIdeal = 24.0, tempMin = 22.5, tempMax = 25.5, rhIdeal = 60.0, lightHours = 18.0,
                measuredNh3 = if (d == 24) 12.0 else null, dieselCansUsed = if (d % 2 == 0) 1.5 else 0.0
            )
        }
    }

    private val weather = WeatherResult(tempC = 30.5, rhPercent = 74.0, windKmh = 9.0, locationName = "Test", isLive = true)
    private val hourly = (0 until 48).map { h ->
        val day = if (h < 24) date(24) else date(25)
        val t = 28.0 + 4.0 * sin(PI * ((h % 24) - 9) / 12)
        HourPoint("${day}T${String.format("%02d", h % 24)}:00", t, 95.0 - (t - 24) * 3.5)
    }

    private fun shoot(name: String, t: Topic, day: Int = 24) {
        val r = rows(day)
        val data = OutputData(flock, farm, r.last(), r, feedTypes, weather, hourly, isToday = true)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MyApplicationTheme {
                Box(Modifier.background(MaterialTheme.colorScheme.background).padding(12.dp)) {
                    Column {
                        TagLegend()
                        TopicTiles(data, t) {}
                        TopicView(data, t)
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(700)
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/topic_$name.png")
    }

    @Test fun vent() = shoot("vent", Topic.VENT)
    @Test fun env() = shoot("env", Topic.ENV)
    @Test fun birds() = shoot("birds", Topic.BIRDS)
    @Test fun feed() = shoot("feed", Topic.FEED)
    @Test fun stock() = shoot("stock", Topic.STOCK)
    @Test fun feed9() = shoot("feed9", Topic.FEED, 9)
    @Test fun vent9() = shoot("vent9", Topic.VENT, 9)
}
