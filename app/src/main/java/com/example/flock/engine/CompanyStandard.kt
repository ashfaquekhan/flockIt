package com.example.flock.engine

/**
 * The company's commercial standard ("All Branches" chart), days 1–55: feed phase, daily
 * mortality %, FCR, cFCR, feed per bird per day (g), cumulative feed (g), daily gain (g) and
 * body weight (g). FCR = cumulative feed ÷ body weight; cFCR = (2 − kg) × 0.25 + FCR.
 * The company allows a 5 % tolerance from day 28 onwards.
 */
object CompanyStandard {

    data class Row(
        val day: Int, val feed: String, val mortPct: Double, val fcr: Double, val cfcr: Double,
        val feedPerDay: Int, val cumFeed: Int, val gain: Int, val bw: Int
    )

    const val TOLERANCE_PCT = 5.0
    const val TOLERANCE_FROM_DAY = 28

    val ROWS: List<Row> = """
        1 B1 0.15 0.26 0.75 13 13 18 50
        2 B1 0.15 0.42 0.90 17 30 21 71
        3 B1 0.15 0.54 1.02 22 52 25 96
        4 B1 0.15 0.67 1.14 26 78 21 117
        5 B1 0.15 0.75 1.21 30 108 28 144
        6 B1 0.15 0.82 1.28 35 143 29 173
        7 B1 0.15 0.88 1.32 40 183 36 209
        8 B1 0.15 0.93 1.37 46 229 38 247
        9 B1 0.15 0.98 1.40 53 282 42 289
        10 B1 0.15 1.02 1.43 58 340 45 334
        11 B1 0.15 1.05 1.45 60 400 48 382
        12 B2 0.15 1.07 1.47 66 466 52 434
        13 B2 0.10 1.10 1.48 75 541 58 492
        14 B2 0.10 1.13 1.49 84 625 60 552
        15 B2 0.10 1.16 1.51 92 717 64 616
        16 B2 0.10 1.19 1.52 100 817 68 684
        17 B2 0.10 1.22 1.53 108 925 72 756
        18 B2 0.10 1.25 1.55 116 1041 75 831
        19 B2 0.10 1.28 1.56 126 1167 78 909
        20 B2 0.10 1.32 1.57 136 1303 80 989
        21 B2 0.10 1.35 1.58 145 1448 82 1071
        22 B2 0.10 1.39 1.60 152 1600 84 1155
        23 B2 0.10 1.41 1.60 156 1756 90 1245
        24 B3 0.10 1.43 1.60 162 1918 94 1339
        25 B3 0.10 1.45 1.59 164 2082 98 1437
        26 B3 0.10 1.46 1.58 168 2250 100 1537
        27 B3 0.10 1.48 1.57 170 2420 100 1637
        28 B3 0.10 1.49 1.56 172 2592 100 1737
        29 B3 0.15 1.51 1.55 174 2766 101 1838
        30 B3 0.15 1.52 1.53 176 2942 102 1940
        31 B3 0.15 1.53 1.52 178 3120 103 2043
        32 B3 0.15 1.54 1.50 180 3300 104 2147
        33 B3 0.15 1.55 1.48 180 3480 104 2251
        34 B3 0.15 1.55 1.47 180 3660 104 2355
        35 B3 0.15 1.56 1.45 180 3840 104 2459
        36 B3 0.15 1.57 1.43 180 4020 104 2563
        37 B3 0.15 1.57 1.40 180 4200 108 2671
        38 B3 0.15 1.58 1.38 180 4380 105 2776
        39 B3 0.15 1.58 1.36 180 4560 105 2881
        40 B3 0.15 1.58 1.34 180 4740 110 2991
        41 B3 0.15 1.58 1.31 180 4920 114 3105
        42 B3 0.15 1.58 1.28 180 5100 118 3223
        43 B3 0.15 1.58 1.25 180 5280 118 3341
        44 B3 0.15 1.58 1.21 180 5460 118 3459
        45 B3 0.15 1.58 1.18 180 5640 118 3577
        46 B3 0.15 1.58 1.15 180 5820 118 3695
        47 B3 0.15 1.58 1.13 180 6000 110 3805
        48 B3 0.15 1.58 1.10 180 6180 110 3915
        49 B3 0.15 1.58 1.07 180 6360 110 4025
        50 B3 0.15 1.58 1.05 180 6540 110 4135
        51 B3 0.15 1.58 1.02 180 6720 110 4245
        52 B3 0.15 1.58 1.00 180 6900 110 4355
        53 B3 0.15 1.58 0.96 180 7080 118 4473
        54 B3 0.15 1.58 0.94 180 7260 110 4583
        55 B3 0.15 1.58 0.91 180 7440 118 4701
    """.trimIndent().lines().filter { it.isNotBlank() }.map { l ->
        val p = l.trim().split(Regex("\\s+"))
        Row(p[0].toInt(), p[1], p[2].toDouble(), p[3].toDouble(), p[4].toDouble(), p[5].toInt(), p[6].toInt(), p[7].toInt(), p[8].toInt())
    }

    const val MAX_DAY = 55

    /** Row for a flock day (clamped to 1–55); null for day 0 (placement, no standard yet). */
    fun row(day: Int): Row? = if (day < 1) null else ROWS[day.coerceAtMost(MAX_DAY) - 1]

    fun bw(day: Int): Double? = row(day)?.bw?.toDouble()
    fun fcr(day: Int): Double? = row(day)?.fcr
    fun cfcr(day: Int): Double? = row(day)?.cfcr
    fun feedPerDay(day: Int): Double? = row(day)?.feedPerDay?.toDouble()
    fun cumFeed(day: Int): Double? = row(day)?.cumFeed?.toDouble()
    fun gain(day: Int): Double? = row(day)?.gain?.toDouble()
    fun feedPhase(day: Int): String = row(day.coerceAtLeast(1))?.feed ?: "B1"

    /**
     * Company feed per bird per day (g) for a bird of [bwG] grams: finds where that weight sits on
     * the company growth curve (fractional day) and reads the feed there, so heavy or light flocks
     * are fed for the size they are. Below the day-1 weight it gives the day-1 ration.
     */
    fun feedForWeight(bwG: Double): Double {
        if (bwG <= ROWS.first().bw) return ROWS.first().feedPerDay.toDouble()
        for (i in 1 until ROWS.size) {
            val a = ROWS[i - 1]; val b = ROWS[i]
            if (bwG <= b.bw) {
                val t = (bwG - a.bw) / (b.bw - a.bw).toDouble()
                return a.feedPerDay + t * (b.feedPerDay - a.feedPerDay)
            }
        }
        return ROWS.last().feedPerDay.toDouble()
    }

    /** Daily mortality standard (% of live birds per day). */
    fun dailyMortPct(day: Int): Double = row(day.coerceAtLeast(1))?.mortPct ?: 0.15

    /** Cumulative mortality standard (%) up to and including [day]. */
    fun cumMortPct(day: Int): Double = (1..day.coerceIn(0, MAX_DAY)).sumOf { ROWS[it - 1].mortPct }

    /** Whether the company's ±5 % tolerance applies on this day. */
    fun toleranceApplies(day: Int) = day >= TOLERANCE_FROM_DAY
}
