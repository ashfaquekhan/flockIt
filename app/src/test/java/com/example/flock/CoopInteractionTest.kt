package com.example.flock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.example.flock.ui.screens.Coop3D
import com.example.flock.ui.screens.CoopCam
import com.example.flock.ui.screens.CoopInput
import com.example.flock.ui.screens.CoopProj
import com.example.flock.ui.screens.CoopSim
import com.example.flock.ui.screens.FeederState
import com.example.flock.ui.screens.LightProgram
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The coop turns, tilts back, and answers taps on birds, feeder and floor. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h900dp-xhdpi")
class CoopInteractionTest {
    @get:Rule val rule = createComposeRule()

    // a zone where it is late morning now, so the birds are awake
    private val zone = java.time.ZoneOffset.ofHours(((11 - java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).hour + 36) % 24 - 12).coerceIn(-12, 14))
    private fun input(f: FeederState = FeederState(200.0, 400.0, 6.0, 0.0, 8.0, 1_000L, 0.1)) = CoopInput(
        age = 11, meanG = 360.0, cvPct = 8.0, live = 15300, entry = 15660, stage = "grower", light = LightProgram(18.0), feeder = f,
        zoneId = zone, airC = 28.0, rhPct = 60.0, feelsC = 28.0, chillC = 0.0, pressurePa = 22.5,
        litterC = 28.0, litterMoist = 25.0, bodyC = 41.0, waterC = 18.0 to 21.0, waterPh = 6.0 to 6.8, travelM = 2.0)

    @Test fun projectionRoundTrips() {
        for (yaw in listOf(0.0, 0.7, 2.4, -1.3)) for (elev in listOf(0.3, 0.6154797, 1.1)) {
            val cam = CoopCam().apply { this.yaw = yaw; this.elev = elev; zoom = 1.4 }
            val pr = CoopProj(1.5, 0.675, 1080f, 900f, cam)
            for ((x, z) in listOf(0.2 to 0.3, 1.1 to 0.9, 0.75 to 1.4)) {
                val (bx, bz) = pr.floorAt(pr.P(x, 0.0, z))
                assertEquals(x, bx, 1e-4); assertEquals(z, bz, 1e-4)
            }
        }
    }

    @Test fun grainsBringBirdsOver() {
        val sim = CoopSim().apply { sideFt = 5.0; x0Ft = 60.0; y0Ft = 10.0 }; val a = input()
        sim.setup(a)
        assertEquals("birds at the flock density", 1.31 * 25, sim.birds.size.toDouble(), 1.0)
        repeat(60) { sim.update(0.016, a, 1.0) }
        val b = sim.birds.first()                   // Pip, the curious one
        sim.dropGrains((b.x + 0.3).coerceAtMost(sim.side - 0.2), b.zz)
        val before = sim.grains.size
        var pecked = false
        repeat(900) { sim.update(0.016, a, 1.0); if (sim.birds.any { it.state == "peckGrain" }) pecked = true }
        assertTrue("a bird pecks at the grains", pecked)
        assertTrue("grains get eaten", sim.grains.size < before)
        sim.poke(b)
        assertEquals(b, sim.selected)
    }

    @Test fun turnTapAndReset() {
        rule.mainClock.autoAdvance = false
        rule.setContent { MyApplicationTheme { Box(Modifier.background(Color.Black)) { Coop3D(input()) } } }
        rule.mainClock.advanceTimeBy(1500)
        rule.onRoot().captureRoboImage("src/test/screenshots/coop_0_default.png")
        rule.onNodeWithTag("coop").performTouchInput { swipeLeft(startX = centerX + 250f, endX = centerX - 250f) }
        rule.mainClock.advanceTimeBy(900)
        rule.onRoot().captureRoboImage("src/test/screenshots/coop_1_turned.png")
        rule.onNodeWithTag("coop").performTouchInput { click(Offset(centerX, centerY + height * 0.12f)) }
        rule.mainClock.advanceTimeBy(2500)
        rule.onRoot().captureRoboImage("src/test/screenshots/coop_2_tapped.png")
        rule.onNodeWithTag("coop").performTouchInput { doubleClick(center) }
        rule.mainClock.advanceTimeBy(2000)
        rule.onRoot().captureRoboImage("src/test/screenshots/coop_3_reset.png")
        rule.onNodeWithTag("farmMap").performTouchInput { swipeRight(startX = width * 0.2f, endX = width * 0.6f) }
        rule.mainClock.advanceTimeBy(1500)
        rule.onRoot().captureRoboImage("src/test/screenshots/coop_4_moved.png")
    }
}
