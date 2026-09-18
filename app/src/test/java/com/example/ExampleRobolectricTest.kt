package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.flock.engine.PhysiologicalEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Flock Manager", appName)
    }

    @Test
    fun `physiological engine computes weight age correctly`() {
        // Day 21 Ross308 standard weight is 1033g
        val weightAge = PhysiologicalEngine.weightAgeFromBW(1033.0, "Ross308")
        assertEquals(21.0, weightAge, 0.1)

        // Day 42 Ross308 standard weight is 3086g
        val weightAge42 = PhysiologicalEngine.weightAgeFromBW(3086.0, "Ross308")
        assertEquals(42.0, weightAge42, 0.1)
    }

    @Test
    fun `physiological engine computes 5 location samples and CV`() {
        val samples = listOf(
            PhysiologicalEngine.LocationSample(6042.0, 6), // 1007g
            PhysiologicalEngine.LocationSample(5982.0, 6), // 997g
            PhysiologicalEngine.LocationSample(5994.0, 6), // 999g
            PhysiologicalEngine.LocationSample(6582.0, 6), // 1097g
            PhysiologicalEngine.LocationSample(6594.0, 6)  // 1099g
        )
        val result = PhysiologicalEngine.computeWeightSamples(samples)
        assertTrue(result.hasSample)
        assertEquals(30, result.totalWeighed)
        assertTrue(result.flockAvgG > 1000.0 && result.flockAvgG < 1060.0)
        assertTrue(result.cvPercent > 0.0)
    }

    @Test
    fun `corrected FCR adjusted to 2kg standard`() {
        val cFcr = PhysiologicalEngine.computeCorrectedFcr(2.0, 1.450)
        assertEquals(1.450, cFcr, 0.001)

        // Birds lighter than 2kg get uplifted standard FCR slope
        val cFcrLighter = PhysiologicalEngine.computeCorrectedFcr(1.5, 1.450)
        assertEquals(1.575, cFcrLighter, 0.001)
    }
}
