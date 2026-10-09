package com.example.flock

import com.example.flock.data.ConfigEntity
import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FarmEntity
import com.example.flock.data.FeedTypeEntity
import com.example.flock.data.FlockEntity
import com.example.flock.data.formatMoreSamples

/**
 * The farm's own running flock (KGF, September 2026) as it stood on day 19 — the entries from its sheet, used to
 * check the calculations and to back-test the projections against what really happened.
 */
object KgfFlock {
    /** day, date, weighings (total g to birds), deaths, bags used by variety, bags received B1 / B2 / B3, saved groups */
    class D(val day: Int, val date: String, val groups: List<Pair<Double, Int>>, val deaths: Int, val used: Map<String, Double>,
            val recB1: Double, val recB2: Double, val recB3: Double, val saved: String)

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
        D(19, "2026-10-08", listOf(), 12, mapOf("B2" to 32.0), 0.0, 0.0, 0.0, "M,F")
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

    /** Day rows 0 … [lastDay] with the entries filled in (days after day 19 are empty). */
    fun rows(lastDay: Int = 42): List<DailyDataEntity> {
        val by = days.associateBy { it.day }
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
                    feedRecB1 = d.recB1, feedRecB2 = d.recB2, feedRecB3 = d.recB3, savedFields = d.saved, sampleEntered = g.isNotEmpty()
                )
            }
        }
    }
}
