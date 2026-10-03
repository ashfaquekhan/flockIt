package com.example.flock

import com.example.flock.domain.FeedCorrection
import com.example.flock.domain.FeedCorrection.Why
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedCorrectionTest {
    @Test fun noneInTheFirstWeek() {
        val a = FeedCorrection.advise(6, 100.0, 180.0, 1.5, 1.0)
        assertEquals(0.0, a.pct, 0.0); assertEquals(Why.FIRST_WEEK, a.why)
    }
    @Test fun noneWithoutASample() {
        assertEquals(Why.NO_STANDARD, FeedCorrection.advise(20, null, 900.0, 1.4, 1.3).why)
    }
    @Test fun underWeightGetsHalfTheGapCapped() {
        val small = FeedCorrection.advise(20, 960.0, 1000.0, null, null)    // 4 % under
        assertEquals(Why.UNDER_WEIGHT, small.why); assertEquals(2.0, small.pct, 1e-9)
        val big = FeedCorrection.advise(20, 800.0, 1000.0, null, null)      // 20 % under
        assertEquals(FeedCorrection.MAX_UP_PCT, big.pct, 1e-9)
    }
    @Test fun smallGapsAreLeftAlone() {
        val a = FeedCorrection.advise(20, 980.0, 1000.0, 1.32, 1.30)
        assertEquals(Why.ON_TRACK, a.why); assertEquals(0.0, a.pct, 0.0)
    }
    @Test fun highFcrOnWeightTrimsALittle() {
        val a = FeedCorrection.advise(25, 1010.0, 1000.0, 1.50, 1.30)       // FCR 15 % over
        assertEquals(Why.HIGH_FCR, a.why); assertEquals(-FeedCorrection.MAX_DOWN_PCT, a.pct, 1e-9)
    }
    @Test fun highFcrUnderWeightIsNotCut() {
        val a = FeedCorrection.advise(25, 990.0, 1000.0, 1.50, 1.30)        // 1 % under, FCR high
        assertEquals(Why.ON_TRACK, a.why)
    }
}
