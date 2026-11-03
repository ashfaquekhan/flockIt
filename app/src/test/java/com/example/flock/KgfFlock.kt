package com.example.flock

import com.example.flock.data.ConfigEntity
import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FarmEntity
import com.example.flock.data.FeedTypeEntity
import com.example.flock.data.FlockEntity
import com.example.flock.data.formatMoreSamples

/**
 * The farm's own running flock (KGF, September 2026) — the entries from its sheet to day 44 (read 2026-11-03, the
 * lifting on days 34, 40, 42 and 43 included), used to check the calculations and to back-test the projections
 * against what really happened.
 */
object KgfFlock {
    /** day, date, weighings (total g to birds), deaths, bags used by variety, bags received B1 / B2 / B3, saved groups */
    class D(val day: Int, val date: String, val groups: List<Pair<Double, Int>>, val deaths: Int, val used: Map<String, Double>,
            val recB1: Double, val recB2: Double, val recB3: Double, val saved: String, val lifted: Int = 0, val liftedKg: Double = 0.0)

    const val SID = "kgf"
    val days: List<D> = listOf(
        D(0, "2026-09-19", listOf(215.0 to 10, 225.0 to 10, 125.0 to 10, 330.0 to 10, 230.0 to 10), 0, mapOf(), 60.0, 0.0, 0.0, ""),
        D(1, "2026-09-20", listOf(665.0 to 10, 630.0 to 10, 690.0 to 10, 695.0 to 10, 670.0 to 10), 34, mapOf("B1" to 12.0), 0.0, 0.0, 0.0, ""),
        D(2, "2026-09-21", listOf(840.0 to 10, 845.0 to 10, 830.0 to 10, 865.0 to 10, 875.0 to 10), 42, mapOf("B1" to 4.0), 0.0, 0.0, 0.0, "W"),
        D(3, "2026-09-22", listOf(920.0 to 10, 980.0 to 10, 970.0 to 10, 910.0 to 10, 990.0 to 10), 36, mapOf("B1" to 2.0), 0.0, 0.0, 0.0, ""),
        D(4, "2026-09-23", listOf(1195.0 to 10, 1155.0 to 10, 1195.0 to 10, 1160.0 to 10, 1210.0 to 10), 18, mapOf("B1" to 6.0), 0.0, 0.0, 0.0, ""),
        D(5, "2026-09-24", listOf(1495.0 to 10), 25, mapOf("B1" to 6.0), 0.0, 0.0, 0.0, ""),
        D(6, "2026-09-25", listOf(1660.0 to 10, 1650.0 to 10, 1705.0 to 10, 1755.0 to 10, 1745.0 to 10), 10, mapOf("B1" to 6.0), 0.0, 0.0, 0.0, "W,M,F"),
        D(7, "2026-09-26", listOf(), 22, mapOf("B1" to 5.0), 0.0, 0.0, 0.0, "M,F"),
        D(8, "2026-09-27", listOf(2270.0 to 10, 2415.0 to 10, 2420.0 to 10, 2145.0 to 10, 2250.0 to 10), 22, mapOf("B1" to 12.0), 46.0, 14.0, 0.0, "W,M,F"),
        D(9, "2026-09-28", listOf(2255.0 to 10, 2400.0 to 10, 2670.0 to 10, 2845.0 to 10, 2690.0 to 10), 35, mapOf("B1" to 9.0), 0.0, 0.0, 0.0, "W,M,F"),
        D(10, "2026-09-29", listOf(2875.0 to 10, 3155.0 to 10, 3165.0 to 10, 3270.0 to 10, 3240.0 to 10), 16, mapOf("B1" to 17.0), 0.0, 0.0, 0.0, "W,M,F"),
        D(11, "2026-09-30", listOf(3400.0 to 10, 3530.0 to 10, 3600.0 to 10, 3400.0 to 10, 3330.0 to 10), 22, mapOf("B1" to 12.0), 0.0, 60.0, 0.0, "W,M,F"),
        D(12, "2026-10-01", listOf(4150.0 to 10, 4000.0 to 10, 4220.0 to 10, 4300.0 to 10, 4200.0 to 10), 15, mapOf("B1" to 10.0, "B2" to 6.0), 0.0, 0.0, 0.0, "W,M,F"),
        D(13, "2026-10-02", listOf(5130.0 to 10, 5140.0 to 10, 4810.0 to 10, 4850.0 to 10, 4720.0 to 10), 5, mapOf("B1" to 5.0, "B2" to 14.0), 0.0, 0.0, 0.0, "W,M,F"),
        D(14, "2026-10-03", listOf(5200.0 to 10, 5900.0 to 10, 5940.0 to 10, 5975.0 to 10, 5600.0 to 10), 9, mapOf("B2" to 21.0), 0.0, 61.0, 0.0, "W,M,F"),
        D(15, "2026-10-04", listOf(5900.0 to 10, 6200.0 to 10, 6300.0 to 10, 6400.0 to 10, 6160.0 to 10), 9, mapOf("B2" to 23.0), 0.0, 60.0, 0.0, "W,M,F"),
        D(16, "2026-10-05", listOf(7200.0 to 10, 7300.0 to 10, 7620.0 to 10, 7390.0 to 10, 7555.0 to 10, 7440.0 to 10, 7445.0 to 10, 7340.0 to 10, 7340.0 to 10, 7480.0 to 10, 6750.0 to 10, 6600.0 to 10), 15, mapOf("B2" to 37.0), 0.0, 0.0, 0.0, "W,M,F"),
        D(17, "2026-10-06", listOf(7755.0 to 10, 7935.0 to 10, 8020.0 to 10, 7810.0 to 10, 7580.0 to 10), 4, mapOf("B2" to 16.0), 0.0, 0.0, 0.0, "W,M,F"),
        D(18, "2026-10-07", listOf(8748.0 to 10, 8635.0 to 10, 8410.0 to 10, 8440.0 to 10, 8590.0 to 10), 6, mapOf("B2" to 24.0), 0.0, 60.0, 0.0, "W,M,F"),
        D(19, "2026-10-08", listOf(), 12, mapOf("B2" to 32.0), 0.0, 0.0, 0.0, "M,F"),
        D(20, "2026-10-09", listOf(10315.0 to 10, 10600.0 to 10, 11990.0 to 10, 11600.0 to 10, 12900.0 to 10, 11100.0 to 10), 14, mapOf("B2" to 29.0), 0.0, 65.0, 0.0, "W,M,F"),
        D(21, "2026-10-10", listOf(), 5, mapOf("B2" to 20.0), 0.0, 0.0, 0.0, "M,F"),
        D(22, "2026-10-11", listOf(11450.0 to 10, 12380.0 to 10, 11840.0 to 10, 12220.0 to 10, 11475.0 to 10, 11340.0 to 10), 4, mapOf("B2" to 40.0), 0.0, 0.0, 120.0, "W,M,F"),
        D(23, "2026-10-12", listOf(12140.0 to 10, 13335.0 to 10, 12950.0 to 10, 12980.0 to 10, 12445.0 to 10), 8, mapOf("B2" to 32.0), 0.0, 0.0, 9.0, "W,M,F"),
        D(24, "2026-10-13", listOf(13340.0 to 10, 14300.0 to 10, 14600.0 to 10, 13800.0 to 10, 13320.0 to 10, 12830.0 to 10), 5, mapOf("B2" to 14.0, "B3" to 17.0), 0.0, 0.0, 0.0, ""),
        D(25, "2026-10-14", listOf(), 5, mapOf("B2" to 12.0, "B3" to 20.0), 0.0, 0.0, 171.0, "M,F"),
        D(26, "2026-10-15", listOf(6905.0 to 5, 7750.0 to 5, 8125.0 to 5, 8035.0 to 5, 8225.0 to 5, 7060.0 to 5, 6875.0 to 5, 7015.0 to 5, 7600.0 to 5, 8125.0 to 5), 6, mapOf("B3" to 30.0), 0.0, 0.0, 0.0, "W,M,F"),
        D(27, "2026-10-16", listOf(), 5, mapOf("B3" to 34.0), 0.0, 0.0, 155.0, "M,F"),
        D(28, "2026-10-17", listOf(9325.0 to 5, 8680.0 to 5, 8320.0 to 5, 8265.0 to 5, 8175.0 to 5, 8690.0 to 5, 8550.0 to 5, 7725.0 to 5), 6, mapOf("B3" to 36.0), 0.0, 0.0, 60.0, "W"),
        D(29, "2026-10-18", listOf(17735.0 to 10, 17690.0 to 10, 18445.0 to 10, 18380.0 to 10, 18260.0 to 10, 18120.0 to 10), 6, mapOf("B3" to 32.0), 0.0, 0.0, 0.0, "W,M,F"),
        D(30, "2026-10-19", listOf(), 4, mapOf("B3" to 36.0), 0.0, 0.0, 0.0, "M,F"),
        D(31, "2026-10-20", listOf(19810.0 to 10, 20760.0 to 10, 19360.0 to 10, 20065.0 to 10, 18410.0 to 10), 7, mapOf("B3" to 36.0), 0.0, 0.0, 0.0, "W,M,F"),
        D(32, "2026-10-21", listOf(20540.0 to 10, 20995.0 to 10, 21095.0 to 10, 20575.0 to 10, 18920.0 to 10), 4, mapOf("B3" to 43.0), 0.0, 0.0, 0.0, "W,M,F"),
        D(33, "2026-10-22", listOf(21995.0 to 10, 22955.0 to 10, 21510.0 to 10, 23265.0 to 10, 20825.0 to 10), 8, mapOf("B3" to 43.0), 0.0, 0.0, 0.0, "W,M,F"),
        D(34, "2026-10-23", listOf(24250.0 to 10, 22560.0 to 10, 23380.0 to 10, 23245.0 to 10, 21395.0 to 10), 6, mapOf("B3" to 43.0), 0.0, 0.0, 0.0, "W,M,F", lifted = 1008, liftedKg = 2409.0),
        D(35, "2026-10-24", listOf(), 2, mapOf("B3" to 34.0), 0.0, 0.0, 60.0, "M,F"),
        D(36, "2026-10-25", listOf(24020.0 to 10, 24465.0 to 10, 24145.0 to 10, 24635.0 to 10, 24170.0 to 10), 2, mapOf("B3" to 38.0), 0.0, 0.0, 0.0, "W"),
        D(37, "2026-10-26", listOf(25405.0 to 10, 26240.0 to 10, 24755.0 to 10, 24880.0 to 10, 25560.0 to 10), 5, mapOf("B3" to 40.0), 0.0, 0.0, 0.0, "W,M,F"),
        D(38, "2026-10-27", listOf(), 11, mapOf("B3" to 40.0), 0.0, 0.0, 42.0, "M,F"),
        D(39, "2026-10-28", listOf(30840.0 to 10, 26895.0 to 10, 28110.0 to 10, 26020.0 to 10, 25735.0 to 10, 26015.0 to 10), 12, mapOf("B3" to 42.0), 0.0, 0.0, 125.0, "W,M,F"),
        D(40, "2026-10-29", listOf(29870.0 to 10, 27115.0 to 10, 29740.0 to 10, 28075.0 to 10, 26565.0 to 10), 12, mapOf("B3" to 37.0), 0.0, 0.0, 0.0, "W,M,F", lifted = 1901, liftedKg = 5357.7),
        D(41, "2026-10-30", listOf(), 5, mapOf("B3" to 28.0), 0.0, 0.0, 0.0, "M,F"),
        D(42, "2026-10-31", listOf(29415.0 to 10, 29910.0 to 10, 29500.0 to 10, 29445.0 to 10, 27760.0 to 10), 7, mapOf("B3" to 39.0), 0.0, 0.0, 0.0, "W,M,F", lifted = 2378, liftedKg = 7043.4),
        D(43, "2026-11-01", listOf(), 5, mapOf("B3" to 29.0), 0.0, 0.0, 40.0, "", lifted = 2589, liftedKg = 7835.7),
        D(44, "2026-11-02", listOf(), 8, mapOf("B3" to 17.0), 0.0, 0.0, 57.0, "M,F")
    )

    val flock = FlockEntity(spreadsheetId = SID, flockId = "FL260921072834", name = "September2026", breed = "Ross308", startDate = "2026-09-19",
        startTime = "07:30", birdsPlaced = 15664, receptionMort = 47, targetWeight = 3200.0, harvestAge = 42, season = "Monsoon", status = "active")
    val farm = FarmEntity(spreadsheetId = SID).copy(farmName = "KGF", timeZone = "Asia/Kolkata", lengthFt = 320.0, widthFt = 40.0, heightFt = 7.5,
        usableLengthFt = 299.0, usableWidthFt = 39.0, fanCount = 10, fanRatedCfm = 25000.0, fanDerate = 0.2, heaterCount = 4, heaterKw = 25.0, hasEC = true,
        drinkerLines = 5, drinkTankL = 2000.0, feederLines = 4, pansPerFeederLine = 114, feedBagKg = 60.0, season = "Monsoon",
        weatherLat = 21.16, weatherLon = 84.08, waterRefillFactor = 2.0, lineFillBags = 3.0, sensorPansPerLine = 2, panSpacingFt = 2.5)
    val config = ConfigEntity(spreadsheetId = SID)
    val feedTypes = listOf(FeedTypeEntity(SID, "B1", "Pre-starter", 60.0, "starter", 1), FeedTypeEntity(SID, "B2", "Starter", 60.0, "grower", 2),
        FeedTypeEntity(SID, "B3", "Finisher", 60.0, "finisher", 3))
    val kgOf = feedTypes.associate { it.code to it.bagKg }

    /** Day rows 0 … [lastDay] with the entries filled in, up to [upTo] (later days are empty). */
    fun rows(lastDay: Int = 42, upTo: Int = 19): List<DailyDataEntity> {
        val by = days.filter { it.day <= upTo }.associateBy { it.day }
        val start = java.time.LocalDate.parse(flock.startDate)
        return (0..lastDay).map { n ->
            val d = by[n]
            val base = DailyDataEntity(spreadsheetId = SID, flockId = flock.flockId, dayNumber = n, date = start.plusDays(n.toLong()).toString())
            if (d == null) base else {
                val g = d.groups
                base.copy(
                    w1 = g.getOrNull(0)?.first, n1 = g.getOrNull(0)?.second, w2 = g.getOrNull(1)?.first, n2 = g.getOrNull(1)?.second,
                    w3 = g.getOrNull(2)?.first, n3 = g.getOrNull(2)?.second, w4 = g.getOrNull(3)?.first, n4 = g.getOrNull(3)?.second,
                    w5 = g.getOrNull(4)?.first, n5 = g.getOrNull(4)?.second,
                    moreSamples = formatMoreSamples(g.drop(5).map { Pair<Double?, Int?>(it.first, it.second) }),
                    mortality = d.deaths, feedBagsUsed = d.used.values.sum(), feedUsedType = d.used.maxByOrNull { it.value }?.key ?: "B1",
                    feedUsedBreakdown = d.used.entries.joinToString(";") { "${it.key}=${it.value}" },
                    feedRecB1 = d.recB1, feedRecB2 = d.recB2, feedRecB3 = d.recB3, savedFields = d.saved, sampleEntered = g.isNotEmpty(),
                    birdsLifted = d.lifted, weightLifted = d.liftedKg
                )
            }
        }
    }
}
