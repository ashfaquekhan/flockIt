package com.example.flock.engine

import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pure Kotlin mathematical and physiological derivation engine for Broiler grow-out management.
 * Disconnected from Android UI/Context to allow straightforward JVM unit testing.
 *
 * Core premise: Calendar age != physiological weight-age.
 * Sampled body weights map onto the breed's growth curve to derive "WEIGHT-AGE", which
 * subsequently drives temperature, ventilation, feed, water, and density targets.
 */
object PhysiologicalEngine {

    data class StandardPoint(
        val day: Int,
        val bwRoss: Double,
        val bwCobb: Double,
        val dFeedRoss: Double,
        val dFeedCobb: Double,
        val cumFeedRoss: Double,
        val cumFeedCobb: Double,
        val fcrRoss: Double,
        val fcrCobb: Double
    )

    // Ross 308 AP 2022 (as-hatched) & Cobb 500 Day 0 - 49
    val STANDARDS = listOf(
        StandardPoint(0, 44.0, 42.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
        StandardPoint(1, 62.0, 64.0, 12.0, 12.4, 12.0, 12.0, 0.194, 0.188),
        StandardPoint(2, 80.0, 86.0, 16.0, 17.2, 28.0, 29.0, 0.348, 0.337),
        StandardPoint(3, 101.0, 108.0, 20.0, 21.4, 48.0, 50.0, 0.472, 0.463),
        StandardPoint(4, 125.0, 130.0, 24.0, 25.0, 71.0, 75.0, 0.571, 0.577),
        StandardPoint(5, 151.0, 152.0, 27.0, 27.2, 98.0, 102.0, 0.652, 0.671),
        StandardPoint(6, 181.0, 174.0, 31.0, 29.8, 130.0, 132.0, 0.717, 0.759),
        StandardPoint(7, 214.0, 196.0, 35.0, 32.1, 165.0, 164.0, 0.772, 0.837),
        StandardPoint(8, 250.0, 241.0, 39.0, 37.6, 204.0, 202.0, 0.818, 0.838),
        StandardPoint(9, 289.0, 286.0, 44.0, 43.5, 248.0, 246.0, 0.857, 0.860),
        StandardPoint(10, 333.0, 331.0, 48.0, 47.7, 296.0, 294.0, 0.891, 0.888),
        StandardPoint(11, 379.0, 376.0, 53.0, 52.6, 349.0, 347.0, 0.921, 0.923),
        StandardPoint(12, 429.0, 421.0, 58.0, 56.9, 407.0, 404.0, 0.948, 0.960),
        StandardPoint(13, 483.0, 466.0, 63.0, 60.8, 469.0, 465.0, 0.972, 0.998),
        StandardPoint(14, 540.0, 511.0, 68.0, 64.3, 537.0, 529.0, 0.995, 1.035),
        StandardPoint(15, 601.0, 582.0, 73.0, 70.7, 610.0, 600.0, 1.016, 1.031),
        StandardPoint(16, 665.0, 652.0, 79.0, 77.5, 689.0, 678.0, 1.036, 1.040),
        StandardPoint(17, 732.0, 723.0, 84.0, 83.0, 773.0, 761.0, 1.056, 1.053),
        StandardPoint(18, 803.0, 793.0, 90.0, 88.9, 863.0, 850.0, 1.075, 1.072),
        StandardPoint(19, 876.0, 864.0, 96.0, 94.7, 959.0, 945.0, 1.094, 1.094),
        StandardPoint(20, 953.0, 934.0, 101.0, 99.0, 1060.0, 1044.0, 1.112, 1.118),
        StandardPoint(21, 1033.0, 1005.0, 107.0, 104.1, 1167.0, 1148.0, 1.130, 1.142),
        StandardPoint(22, 1115.0, 1098.0, 113.0, 111.3, 1280.0, 1259.0, 1.148, 1.147),
        StandardPoint(23, 1200.0, 1191.0, 119.0, 118.1, 1400.0, 1377.0, 1.166, 1.156),
        StandardPoint(24, 1287.0, 1284.0, 125.0, 124.7, 1525.0, 1502.0, 1.185, 1.170),
        StandardPoint(25, 1377.0, 1376.0, 131.0, 130.9, 1655.0, 1633.0, 1.203, 1.187),
        StandardPoint(26, 1468.0, 1469.0, 137.0, 137.1, 1792.0, 1770.0, 1.221, 1.205),
        StandardPoint(27, 1562.0, 1562.0, 142.0, 142.0, 1935.0, 1912.0, 1.239, 1.224),
        StandardPoint(28, 1657.0, 1655.0, 148.0, 147.8, 2083.0, 2060.0, 1.257, 1.245),
        StandardPoint(29, 1754.0, 1756.0, 154.0, 154.2, 2236.0, 2214.0, 1.275, 1.261),
        StandardPoint(30, 1853.0, 1856.0, 159.0, 159.3, 2396.0, 2373.0, 1.293, 1.279),
        StandardPoint(31, 1952.0, 1957.0, 164.0, 164.4, 2560.0, 2537.0, 1.312, 1.296),
        StandardPoint(32, 2053.0, 2058.0, 170.0, 170.4, 2730.0, 2707.0, 1.330, 1.315),
        StandardPoint(33, 2154.0, 2159.0, 175.0, 175.4, 2904.0, 2882.0, 1.349, 1.335),
        StandardPoint(34, 2257.0, 2259.0, 180.0, 180.2, 3084.0, 3062.0, 1.367, 1.355),
        StandardPoint(35, 2360.0, 2360.0, 184.0, 184.0, 3268.0, 3246.0, 1.386, 1.375),
        StandardPoint(36, 2463.0, 2458.0, 189.0, 188.6, 3457.0, 3435.0, 1.404, 1.397),
        StandardPoint(37, 2567.0, 2556.0, 193.0, 192.2, 3650.0, 3627.0, 1.423, 1.419),
        StandardPoint(38, 2671.0, 2654.0, 197.0, 195.7, 3848.0, 3823.0, 1.441, 1.440),
        StandardPoint(39, 2775.0, 2751.0, 201.0, 199.3, 4049.0, 4022.0, 1.460, 1.462),
        StandardPoint(40, 2879.0, 2849.0, 205.0, 202.9, 4254.0, 4225.0, 1.479, 1.483),
        StandardPoint(41, 2982.0, 2947.0, 209.0, 206.5, 4463.0, 4432.0, 1.497, 1.504),
        StandardPoint(42, 3086.0, 3045.0, 212.0, 209.2, 4674.0, 4641.0, 1.516, 1.524),
        StandardPoint(43, 3189.0, 3143.0, 215.0, 211.9, 4890.0, 4853.0, 1.535, 1.544),
        StandardPoint(44, 3291.0, 3241.0, 218.0, 214.7, 5108.0, 5068.0, 1.553, 1.564),
        StandardPoint(45, 3393.0, 3339.0, 221.0, 217.5, 5328.0, 5286.0, 1.572, 1.583),
        StandardPoint(46, 3493.0, 3436.0, 223.0, 219.4, 5552.0, 5505.0, 1.591, 1.602),
        StandardPoint(47, 3594.0, 3534.0, 226.0, 222.2, 5778.0, 5727.0, 1.609, 1.621),
        StandardPoint(48, 3693.0, 3632.0, 228.0, 224.2, 6005.0, 5951.0, 1.628, 1.638),
        StandardPoint(49, 3791.0, 3730.0, 230.0, 226.3, 6235.0, 6177.0, 1.646, 1.656)
    )

    // Standard curves
    val CURVE_TEMP_BY_BW = listOf(
        44.0 to 30.0, 100.0 to 28.0, 180.0 to 27.0, 290.0 to 26.0, 425.0 to 25.0,
        590.0 to 24.0, 790.0 to 23.0, 1015.0 to 22.0, 1260.0 to 21.0, 1530.0 to 20.0,
        9999.0 to 20.0
    )

    val CURVE_MINVENT_BY_AGE = listOf(
        7.0 to 0.10, 14.0 to 0.25, 21.0 to 0.35, 28.0 to 0.50, 35.0 to 0.65,
        42.0 to 0.70, 49.0 to 0.80, 56.0 to 0.90
    )

    val CURVE_RH_BY_AGE = listOf(
        0.0 to 65.0, 3.0 to 65.0, 7.0 to 60.0, 14.0 to 55.0, 28.0 to 55.0, 42.0 to 60.0
    )

    val CURVE_AIRSPEED_BY_AGE = listOf(
        0.0 to 30.0, 7.0 to 40.0, 14.0 to 60.0, 21.0 to 120.0, 28.0 to 235.0,
        35.0 to 395.0, 42.0 to 490.0
    )

    val CURVE_LIGHT_BY_AGE = listOf(
        0.0 to 23.0, 2.0 to 23.0, 7.0 to 18.0, 21.0 to 18.0, 35.0 to 18.0,
        39.0 to 23.0, 42.0 to 23.0
    )

    val CURVE_MAXMORT_BY_AGE = listOf(
        0.0 to 0.0, 3.0 to 0.4, 7.0 to 1.0, 14.0 to 1.7, 21.0 to 2.4,
        28.0 to 3.0, 35.0 to 3.7, 42.0 to 4.4
    )

    val CURVE_WATERLINE_BY_AGE = listOf(
        0.0 to 4.0, 7.0 to 6.0, 14.0 to 9.0, 21.0 to 12.0, 35.0 to 16.0, 42.0 to 18.0
    )

    val CURVE_DRINKERHT_BY_AGE = listOf(
        0.0 to 2.0, 7.0 to 5.0, 14.0 to 7.0, 21.0 to 9.0, 35.0 to 13.0, 42.0 to 15.0
    )

    fun isCobb(breed: String): Boolean = breed.contains("cobb", ignoreCase = true)

    /**
     * Piecewise-linear interpolation over a list of (X, Y) points, clamped at ends.
     */
    fun interpolate(table: List<Pair<Double, Double>>, x: Double): Double {
        if (table.isEmpty()) return 0.0
        if (x <= table.first().first) return table.first().second
        if (x >= table.last().first) return table.last().second
        for (i in 1 until table.size) {
            val (x0, y0) = table[i - 1]
            val (x1, y1) = table[i]
            if (x <= x1) {
                if (x1 == x0) return y0
                val t = (x - x0) / (x1 - x0)
                return y0 + t * (y1 - y0)
            }
        }
        return table.last().second
    }

    /**
     * Standard body weight (g) for a calendar day or fractional day on breed curve.
     */
    fun bwFromDay(day: Double, breed: String): Double {
        val table = STANDARDS.map { it.day.toDouble() to if (isCobb(breed)) it.bwCobb else it.bwRoss }
        return interpolate(table, day)
    }

    fun dailyFeedFromDay(day: Double, breed: String): Double {
        val table = STANDARDS.map { it.day.toDouble() to if (isCobb(breed)) it.dFeedCobb else it.dFeedRoss }
        return interpolate(table, day)
    }

    fun cumFeedFromDay(day: Double, breed: String): Double {
        val table = STANDARDS.map { it.day.toDouble() to if (isCobb(breed)) it.cumFeedCobb else it.cumFeedRoss }
        return interpolate(table, day)
    }

    fun stdFcrFromDay(day: Double, breed: String): Double {
        val table = STANDARDS.map { it.day.toDouble() to if (isCobb(breed)) it.fcrCobb else it.fcrRoss }
        return interpolate(table, day)
    }

    /**
     * Inverse mapping: find exact fractional "weight-age" (in days) matching a measured body weight (g).
     */
    fun weightAgeFromBW(bw: Double, breed: String): Double {
        if (bw <= 0.0) return 0.0
        val cobb = isCobb(breed)
        for (i in 1 until STANDARDS.size) {
            val prev = STANDARDS[i - 1]
            val curr = STANDARDS[i]
            val b0 = if (cobb) prev.bwCobb else prev.bwRoss
            val b1 = if (cobb) curr.bwCobb else curr.bwRoss
            if (bw <= b1) {
                if (bw <= b0) return prev.day.toDouble()
                return prev.day + (bw - b0) / (b1 - b0)
            }
        }
        return STANDARDS.last().day.toDouble()
    }

    /**
     * Stull (2011) wet-bulb temperature approximation. T in °C, RH in %.
     * Formula requires arctangent in degrees.
     */
    private fun atanDeg(x: Double): Double = Math.toDegrees(atan(x))

    fun wetBulb(tempC: Double, rh: Double): Double {
        return tempC * atanDeg(0.151977 * sqrt(rh + 8.313659)) +
                atanDeg(tempC + rh) - atanDeg(rh - 1.676331) +
                0.00391838 * rh.pow(1.5) * atanDeg(0.023101 * rh) - 4.686035
    }

    /**
     * Feed heat de-rate: clamp(1 - 0.012 * (meanTemp - 20), 0.75, 1.15)
     */
    fun computeFeedHeatDerate(meanTempC: Double, feedHeatK: Double = 0.012): Double {
        val factor = 1.0 - feedHeatK * (meanTempC - 20.0)
        return factor.coerceIn(0.75, 1.15)
    }

    /**
     * Water heat uplift: +6% per °C above 20°C
     */
    fun computeWaterUplift(meanTempC: Double, waterHeatK: Double = 0.06): Double {
        return 1.0 + waterHeatK * max(0.0, meanTempC - 20.0)
    }

    /**
     * cFCR = (2 - Current Body Weight in kg) * 0.25 + actual FCR
     */
    fun computeCorrectedFcr(currentBwKg: Double, actualFcr: Double, cFcrDivisor: Double = 0.25): Double {
        return (2.0 - currentBwKg) * cFcrDivisor + actualFcr
    }

    data class LocationSample(val totalWeightG: Double, val chickCount: Int) {
        val averageG: Double get() = if (chickCount > 0) totalWeightG / chickCount else 0.0
    }

    data class WeightSampleResult(
        val flockAvgG: Double,
        val cvPercent: Double,
        val hasSample: Boolean,
        val totalWeighed: Int
    )

    fun computeWeightSamples(samples: List<LocationSample>): WeightSampleResult {
        var totalW = 0.0
        var totalN = 0
        val locAverages = mutableListOf<Double>()
        for (s in samples) {
            if (s.totalWeightG > 0 && s.chickCount > 0) {
                totalW += s.totalWeightG
                totalN += s.chickCount
                locAverages.add(s.averageG)
            }
        }
        if (totalN == 0) {
            return WeightSampleResult(flockAvgG = 0.0, cvPercent = 0.0, hasSample = false, totalWeighed = 0)
        }
        val avg = totalW / totalN
        var cv = 0.0
        if (locAverages.size >= 2) {
            val mean = locAverages.average()
            val variance = locAverages.map { (it - mean) * (it - mean) }.average()
            val sd = sqrt(variance)
            cv = if (mean > 0) (sd / mean) * 100.0 else 0.0
        }
        return WeightSampleResult(
            flockAvgG = avg,
            cvPercent = cv,
            hasSample = true,
            totalWeighed = totalN
        )
    }

    data class VentPlan(
        val fansToRun: Int,
        val onSec: Int,
        val offSec: Int,
        val mode: Int, // 0 = Min-vent (cycling), 1 = Transitional, 2 = Tunnel cool
        val modeName: String,
        val airspeedFtMin: Int,
        val cfmDeliveredPerBird: Double
    )

    fun computeVentPlan(
        fanCount: Int,
        fanRatedCfm: Double,
        fanDerate: Double,
        usableWidthFt: Double,
        usableHeightFt: Double,
        avgKg: Double,
        incomingTempC: Double,
        setTempC: Double,
        liveBirds: Int,
        cfmPerBirdReq: Double,
        day: Int,
        cycleSec: Int = 300,
        minOnSec: Int = 30,
        tempBand: Double = 1.5,
        tunTrigBig: Double = 3.0,
        tunTrigYoung: Double = 4.5
    ): VentPlan {
        val effFanCfm = fanRatedCfm * (1.0 - fanDerate)
        val crossSection = usableWidthFt * usableHeightFt
        val targetAirspeed = interpolate(CURVE_AIRSPEED_BY_AGE, day.toDouble())
        val totalMinVentCfm = cfmPerBirdReq * liveBirds
        val minVentFans = min(fanCount, max(1, ceil(totalMinVentCfm / (effFanCfm * 0.8)).toInt()))
        val tunnelCfm = targetAirspeed * crossSection
        val tunnelFans = min(fanCount, max(minVentFans, ceil(tunnelCfm / effFanCfm).toInt()))

        val overTemp = incomingTempC - setTempC
        val trigger = if (avgKg * 1000.0 >= 1500.0) tunTrigBig else tunTrigYoung

        val mode: Int = when {
            overTemp > trigger && (avgKg * 1000.0 >= 1000.0) -> 2 // Tunnel
            overTemp > tempBand -> 1 // Transitional
            else -> 0 // Min-vent
        }

        val fans: Int
        val onSec: Int
        val offSec: Int

        when (mode) {
            2 -> {
                fans = tunnelFans
                onSec = cycleSec
                offSec = 0
            }
            1 -> {
                fans = min(fanCount, max(minVentFans + 1, ceil(tunnelFans * 0.4).toInt()))
                onSec = cycleSec
                offSec = 0
            }
            else -> {
                fans = minVentFans
                val rawOn = (totalMinVentCfm / (fans * effFanCfm)) * cycleSec
                val clampedOn = rawOn.coerceIn(minOnSec.toDouble(), cycleSec.toDouble()).roundToInt()
                onSec = clampedOn
                offSec = cycleSec - clampedOn
            }
        }

        val dutyFraction = if (onSec + offSec > 0) onSec.toDouble() / (onSec + offSec) else 1.0
        val deliveredCfmPerBird = if (liveBirds > 0) (fans * effFanCfm * dutyFraction) / liveBirds else 0.0

        val modeName = when (mode) {
            2 -> "Tunnel cool"
            1 -> "Transitional"
            else -> "Min-vent (cycling)"
        }

        return VentPlan(
            fansToRun = fans,
            onSec = onSec,
            offSec = offSec,
            mode = mode,
            modeName = modeName,
            airspeedFtMin = targetAirspeed.roundToInt(),
            cfmDeliveredPerBird = deliveredCfmPerBird
        )
    }

    data class AreaDensityResult(
        val occupiedFt2: Double,
        val barricadeFt: Int,
        val fullHouse: Boolean,
        val densityKgM2: Double,
        val ftPerBird: Double,
        val minFtPerBird: Double
    )

    fun computeAreaAndDensity(
        liveBirds: Int,
        avgKg: Double,
        usableLengthFt: Double,
        usableWidthFt: Double,
        broodDensity: Double = 3.7,
        densityCapDefault: Double = 39.0,
        day: Int
    ): AreaDensityResult {
        val grossUsableFt2 = usableLengthFt * usableWidthFt
        val usableM2 = grossUsableFt2 * 0.092903

        val areaBrood = if (broodDensity > 0) liveBirds / broodDensity else 0.0
        val areaCap = if (densityCapDefault > 0) (liveBirds * avgKg / densityCapDefault) / 0.092903 else 0.0
        val areaNeeded = max(areaBrood, areaCap)

        val barricadeFt = min(usableLengthFt, min(grossUsableFt2, areaNeeded) / max(1.0, usableWidthFt)).roundToInt()
        val fullHouse = day >= 11 || barricadeFt >= usableLengthFt.toInt()
        val occupiedFt2 = if (fullHouse) grossUsableFt2 else min(grossUsableFt2, areaNeeded)

        val ftPerBird = if (liveBirds > 0) occupiedFt2 / liveBirds else 0.0
        val minFtPerBird = if (densityCapDefault > 0) (avgKg / densityCapDefault) / 0.092903 else 0.0
        val densityKgM2 = if (usableM2 > 0) (liveBirds * avgKg) / usableM2 else 0.0

        return AreaDensityResult(
            occupiedFt2 = occupiedFt2,
            barricadeFt = barricadeFt,
            fullHouse = fullHouse,
            densityKgM2 = densityKgM2,
            ftPerBird = ftPerBird,
            minFtPerBird = minFtPerBird
        )
    }

    data class TargetRow(
        val parameter: String,
        val unit: String,
        val calendarValue: String,
        val groundValue: String,
        val provenance: String,
        val readDelta: String,
        val status: String // "good", "warn", "crit", or ""
    )

    data class TargetGroup(
        val title: String,
        val colorHex: Long,
        val rows: List<TargetRow>
    )

    fun buildTargetComparisons(
        breed: String,
        day: Int,
        weightAge: Double,
        measuredBw: Double?,
        isProjected: Boolean,
        fcr: Double?,
        cFcr: Double?,
        cv: Double?,
        setTemp: Double,
        meanTempC: Double,
        liveBirds: Int,
        densityKgM2: Double,
        densityCap: Double,
        usableM2: Double,
        bagKg: Double,
        cumMortPct: Double?,
        wfRatio: Double = 1.8
    ): List<TargetGroup> {
        val bwDate = bwFromDay(day.toDouble(), breed)
        val bwGround = measuredBw ?: bwFromDay(weightAge, breed)
        val stdFcr = stdFcrFromDay(day.toDouble(), breed)
        val feedDate = dailyFeedFromDay(day.toDouble(), breed)
        val heatFactor = computeFeedHeatDerate(meanTempC)
        val waterUplift = computeWaterUplift(meanTempC)
        val maxMortDate = interpolate(CURVE_MAXMORT_BY_AGE, day.toDouble())
        val densDate = if (usableM2 > 0) (liveBirds * (bwDate / 1000.0)) / usableM2 else 0.0

        val groups = mutableListOf<TargetGroup>()

        // 1. Growth & Conversion
        val growthRows = mutableListOf<TargetRow>()
        val bwDeltaStr = if (measuredBw != null && bwDate > 0) {
            val pct = ((measuredBw / bwDate) - 1.0) * 100.0
            String.format("%+.1f%% vs std", pct)
        } else if (isProjected) "no sample — projected" else "on track"

        val bwStatus = if (isProjected) "warn" else if (measuredBw != null) {
            val r = measuredBw / bwDate
            if (r < 0.90) "crit" else if (r < 0.98) "warn" else "good"
        } else ""

        growthRows.add(
            TargetRow(
                parameter = "Body weight",
                unit = "g",
                calendarValue = String.format("%.0f", bwDate),
                groundValue = String.format("%.0f", bwGround),
                provenance = if (isProjected) "projected" else "measured",
                readDelta = bwDeltaStr,
                status = bwStatus
            )
        )

        val waDelta = weightAge - day
        val waStatus = if (waDelta < -0.3) "warn" else "good"
        growthRows.add(
            TargetRow(
                parameter = "Weight-age",
                unit = "d",
                calendarValue = "$day d",
                groundValue = String.format("%.1f d", weightAge),
                provenance = "curve-inv",
                readDelta = String.format("%+.1f d vs calendar", waDelta),
                status = waStatus
            )
        )

        val fcrStr = if (fcr != null) String.format("%.3f", fcr) else "—"
        val fcrStatus = if (fcr != null) {
            if (fcr > stdFcr * 1.15) "crit" else if (fcr > stdFcr * 1.05) "warn" else "good"
        } else ""
        growthRows.add(
            TargetRow(
                parameter = "FCR",
                unit = "ratio",
                calendarValue = String.format("%.3f", stdFcr),
                groundValue = fcrStr,
                provenance = "measured",
                readDelta = if (fcr != null && fcr > stdFcr * 1.08) "above standard" else "on track",
                status = fcrStatus
            )
        )

        val cfcrStr = if (cFcr != null) String.format("%.3f", cFcr) else "—"
        growthRows.add(
            TargetRow(
                parameter = "cFCR → 2 kg",
                unit = "ratio",
                calendarValue = "—",
                groundValue = cfcrStr,
                provenance = "formula",
                readDelta = "(2−CBW)·0.25 + FCR",
                status = ""
            )
        )

        val cvStr = if (cv != null && cv > 0) String.format("%.1f%%", cv) else "—"
        val cvStatus = if (cv != null && cv > 0) {
            if (cv >= 12.0) "crit" else if (cv >= 10.0) "warn" else "good"
        } else ""
        growthRows.add(
            TargetRow(
                parameter = "Uniformity CV%",
                unit = "%",
                calendarValue = "<10% tgt",
                groundValue = cvStr,
                provenance = "measured",
                readDelta = if (cv != null && cv > 0) (if (cv < 10) "uniform" else "uneven") else "",
                status = cvStatus
            )
        )
        groups.add(TargetGroup("Growth & Conversion", 0xFF1B7A63, growthRows))

        // 2. Climate & Air
        val climateRows = mutableListOf<TargetRow>()
        val setTempCal = interpolate(CURVE_TEMP_BY_BW, bwDate)
        climateRows.add(
            TargetRow(
                parameter = "Set-point temp",
                unit = "°C",
                calendarValue = String.format("%.1f", setTempCal),
                groundValue = String.format("%.1f", setTemp),
                provenance = "BW curve",
                readDelta = "heavier → cooler",
                status = ""
            )
        )
        val rhIdeal = interpolate(CURVE_RH_BY_AGE, day.toDouble())
        climateRows.add(
            TargetRow(
                parameter = "Humidity ideal",
                unit = "%",
                calendarValue = String.format("%.0f", rhIdeal),
                groundValue = String.format("%.0f", rhIdeal),
                provenance = "age",
                readDelta = "comfort zone",
                status = ""
            )
        )
        val airSpeedIdeal = interpolate(CURVE_AIRSPEED_BY_AGE, day.toDouble())
        climateRows.add(
            TargetRow(
                parameter = "Air speed (bird)",
                unit = "ft/min",
                calendarValue = String.format("%.0f", airSpeedIdeal),
                groundValue = String.format("%.0f", airSpeedIdeal),
                provenance = "age",
                readDelta = "tunnel level",
                status = ""
            )
        )
        val minVentCal = interpolate(CURVE_MINVENT_BY_AGE, day.toDouble())
        val minVentGround = interpolate(CURVE_MINVENT_BY_AGE, weightAge)
        climateRows.add(
            TargetRow(
                parameter = "Min-vent air",
                unit = "cfm/bird",
                calendarValue = String.format("%.2f", minVentCal),
                groundValue = String.format("%.2f", minVentGround),
                provenance = "NPTC age",
                readDelta = "air-quality floor",
                status = ""
            )
        )
        climateRows.add(TargetRow("CO₂ max", "ppm", "3,500", "= same", "fixed", "house limit", ""))
        climateRows.add(TargetRow("NH₃ max", "ppm", "20", "= same", "fixed", "ammonia ceiling", ""))
        climateRows.add(TargetRow("Static pressure", "Pa", "25", "= same", "fixed", "±10 Pa band", ""))
        groups.add(TargetGroup("Climate & Air", 0xFF2E7DA6, climateRows))

        // 3. Feed
        val feedRows = mutableListOf<TargetRow>()
        val feedGround = dailyFeedFromDay(weightAge, breed) * heatFactor
        feedRows.add(
            TargetRow(
                parameter = "Feed / bird",
                unit = "g",
                calendarValue = String.format("%.0f", feedDate),
                groundValue = String.format("%.0f", feedGround),
                provenance = "heat+curve",
                readDelta = if (heatFactor < 0.95) "heat compression" else "standard",
                status = if (heatFactor < 0.90) "warn" else ""
            )
        )
        val totalFeedKg = (feedGround * liveBirds) / 1000.0
        val feedBags = ceil(totalFeedKg / bagKg).toInt()
        feedRows.add(
            TargetRow(
                parameter = "Total feed",
                unit = "kg",
                calendarValue = String.format("%.0f", (feedDate * liveBirds) / 1000.0),
                groundValue = String.format("%.0f", totalFeedKg),
                provenance = "derived",
                readDelta = "on $liveBirds live",
                status = ""
            )
        )
        feedRows.add(
            TargetRow(
                parameter = "Feed bags",
                unit = "${bagKg.toInt()} kg",
                calendarValue = "${ceil((feedDate * liveBirds) / 1000.0 / bagKg).toInt()}",
                groundValue = "$feedBags",
                provenance = "derived",
                readDelta = "daily allocation",
                status = ""
            )
        )
        groups.add(TargetGroup("Feed", 0xFFB26A1E, feedRows))

        // 4. Water
        val waterRows = mutableListOf<TargetRow>()
        val waterDate = feedDate * wfRatio
        val waterGround = feedGround * wfRatio * waterUplift
        waterRows.add(
            TargetRow(
                parameter = "Water / bird",
                unit = "mL",
                calendarValue = String.format("%.0f", waterDate),
                groundValue = String.format("%.0f", waterGround),
                provenance = "derived",
                readDelta = String.format("×%.2f heat uplift", waterUplift),
                status = ""
            )
        )
        val totalWaterL = (waterGround * liveBirds) / 1000.0
        waterRows.add(
            TargetRow(
                parameter = "Total water",
                unit = "L",
                calendarValue = String.format("%.0f", (waterDate * liveBirds) / 1000.0),
                groundValue = String.format("%.0f", totalWaterL),
                provenance = "derived",
                readDelta = "daily tank demand",
                status = ""
            )
        )
        val linePressure = interpolate(CURVE_WATERLINE_BY_AGE, day.toDouble()).roundToInt()
        waterRows.add(TargetRow("Line pressure", "in", "$linePressure", "$linePressure", "age", "nipple flow", ""))
        val drinkerHt = interpolate(CURVE_DRINKERHT_BY_AGE, day.toDouble()).roundToInt()
        waterRows.add(TargetRow("Drinker height", "in", "$drinkerHt", "$drinkerHt", "age", "eye level", ""))
        groups.add(TargetGroup("Water", 0xFF2C6FB0, waterRows))

        // 5. Space & Density
        val spaceRows = mutableListOf<TargetRow>()
        val densStatus = if (densityKgM2 > densityCap) "crit" else if (densityKgM2 > densityCap * 0.9) "warn" else "good"
        spaceRows.add(
            TargetRow(
                parameter = "Density",
                unit = "kg/m²",
                calendarValue = String.format("%.1f", densDate),
                groundValue = String.format("%.1f", densityKgM2),
                provenance = "measured",
                readDelta = String.format("cap %.1f", densityCap),
                status = densStatus
            )
        )
        groups.add(TargetGroup("Space & Density", 0xFF1B7A63, spaceRows))

        // 6. Flock Health
        val healthRows = mutableListOf<TargetRow>()
        val mortStr = if (cumMortPct != null) String.format("%.2f%%", cumMortPct) else "—"
        val mortStatus = if (cumMortPct != null) {
            if (cumMortPct > maxMortDate) "crit" else if (cumMortPct > maxMortDate * 0.85) "warn" else "good"
        } else ""
        healthRows.add(
            TargetRow(
                parameter = "Cum mortality",
                unit = "%",
                calendarValue = String.format("%.1f%% max", maxMortDate),
                groundValue = mortStr,
                provenance = "measured",
                readDelta = if (cumMortPct != null && cumMortPct > maxMortDate) "over ceiling" else "under ceiling",
                status = mortStatus
            )
        )
        val lightHours = interpolate(CURVE_LIGHT_BY_AGE, day.toDouble()).roundToInt()
        healthRows.add(TargetRow("Light hours", "h", "$lightHours", "$lightHours", "age", "photoperiod", ""))
        groups.add(TargetGroup("Flock Health", 0xFFAE3F72, healthRows))

        return groups
    }
}
