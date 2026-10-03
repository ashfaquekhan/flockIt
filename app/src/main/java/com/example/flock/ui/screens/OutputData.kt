package com.example.flock.ui.screens

import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FarmEntity
import com.example.flock.data.FeedTypeEntity
import com.example.flock.data.FlockEntity
import com.example.flock.engine.CompanyStandard
import com.example.flock.engine.IbController
import com.example.flock.engine.PhysiologicalEngine
import com.example.flock.network.HourPoint
import com.example.flock.network.WeatherResult
import com.example.flock.ui.Fmt
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.max

/** Standard spacings: pans every 0.76 m (2.5 ft), nipples every 0.25 m; first pan ~5 ft from the front wall. */
const val PAN_SPACING_FT = 2.5
const val NIPPLE_SPACING_FT = 0.82
const val LINE_START_FT = 5.0
/** Ross: birds should not have to walk more than 2 m to feed or water. */
/** breaths a minute above which birds are panting (heat stress) */
const val PANT_ABOVE_PER_MIN = 60.0
const val ALLOWED_TRAVEL_M = 2.0

/** The five Output topics, in the order the farm reads them. */
enum class Topic(val emoji: String, val title: String) {
    VENT("🌀", "Ventilation"),
    ENV("🌡️", "Environment"),
    BIRDS("🐔", "Birds"),
    FEED("🥣", "Feed & Water"),
    STOCK("📦", "Stock")
}

/** level 1 = watch, 2 = act now. */
data class TopicAlert(val level: Int, val text: String)

/** One outside-temperature case for the ventilation plan (coolest / middle / hottest hour). */
data class VentScenario(
    val label: String, val emoji: String, val hour: Int, val outC: Double, val outRh: Double,
    val state: IbController.HouseState, val level: IbController.Level, val airFpm: Double, val mode: String
)

/**
 * Every number the Output tab shows, worked out once for the selected day, plus the alerts for
 * each topic. Present = logged/measured, Projected = estimated (no sample / not logged),
 * Ideal = Ross 308 breed objective, Commercial = the company's all-branches standard.
 */
class OutputData(
    val flock: FlockEntity?,
    val farm: FarmEntity,
    val e: DailyDataEntity,
    val rows: List<DailyDataEntity>,
    val feedTypes: List<FeedTypeEntity>,
    val weather: WeatherResult?,
    val hourly: List<HourPoint>,
    val isToday: Boolean
) {
    val day = e.dayNumber
    val breed = flock?.breed ?: "Ross308"
    val harvestAge = flock?.harvestAge ?: 42
    /** Weight-derived numbers: Present when a sample was entered today, else Projected. */
    val vk = if (e.projected) ValueKind.PREDICTED else ValueKind.PRESENT
    val byDay = rows.associateBy { it.dayNumber }
    val upto = rows.filter { it.dayNumber <= day }

    // ------------------------------- birds -------------------------------
    val placed = flock?.birdsPlaced ?: 0
    val reception = flock?.receptionMort ?: 0
    val live = e.liveBirds
    val liveSafe = live.coerceAtLeast(1)
    val mortToday = e.mortality
    /** Birds that started the flock: placed minus reception / transit deaths (those are not flock mortality). */
    val entryBirds = max(1, placed - reception)
    val mortTD = upto.sumOf { it.mortality }
    val mortTodayPct: Double? = if (live + mortToday > 0) mortToday * 100.0 / (live + mortToday) else null
    val mortTDPct: Double? = if (placed > 0) mortTD * 100.0 / entryBirds else e.cumMortPct
    val comDailyPct = CompanyStandard.dailyMortPct(day)
    val comCumPct = CompanyStandard.cumMortPct(day)
    val comMortBirdsToday = comDailyPct * (live + mortToday) / 100.0
    /** Industry benchmark mortality (shown as the Ideal). */
    val ceilingPct = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_MAXMORT_BY_AGE, day.toDouble())
    val lameTD = upto.sumOf { it.lameSeparated }
    val liftTD = upto.sumOf { it.birdsLifted }
    val liftKgTD = upto.sumOf { it.weightLifted }

    val bw: Double = e.avgWeight ?: PhysiologicalEngine.bwFromDay(e.weightAge, breed)
    val bwCom: Double? = CompanyStandard.bw(day)
    val bwIdeal = PhysiologicalEngine.bwFromDay(day.toDouble(), breed)
    private val prevSample = rows.filter { it.dayNumber < day && it.avgWeight != null }.maxByOrNull { it.dayNumber }
    val gainMeasured: Double? = if (e.avgWeight != null && prevSample?.avgWeight != null)
        (e.avgWeight - prevSample.avgWeight) / (day - prevSample.dayNumber) else null
    val gain: Double? = gainMeasured ?: e.gainPerBird.takeIf { it > 0 }
    val gainKind = if (gainMeasured != null) ValueKind.PRESENT else ValueKind.PREDICTED
    val gainCom: Double? = CompanyStandard.gain(day)
    val gainIdeal = bwIdeal - PhysiologicalEngine.bwFromDay(max(0, day - 1).toDouble(), breed)
    val fcrCom: Double? = CompanyStandard.fcr(day)
    val fcrIdeal = PhysiologicalEngine.stdFcrFromDay(day.toDouble(), breed)
    val cfcrCom: Double? = CompanyStandard.cfcr(day)
    val cfcrIdeal = PhysiologicalEngine.computeCorrectedFcr(bwIdeal / 1000.0, fcrIdeal)
    val epef: Double? = if (day >= 7 && e.fcr != null && e.fcr > 0 && e.livability != null)
        e.livability * (bw / 1000.0) * 100.0 / (day * e.fcr) else null
    val epefCom: Double? = if (day >= 7 && fcrCom != null && bwCom != null)
        (100 - comCumPct) * (bwCom / 1000.0) * 100.0 / (day * fcrCom) else null
    val epefIdeal: Double? = if (day >= 7) (100 - ceilingPct) * (bwIdeal / 1000.0) * 100.0 / (day * fcrIdeal) else null
    val w0 = byDay[0]?.avgWeight ?: PhysiologicalEngine.bwFromDay(0.0, breed)
    val sevenDayMultiple: Double? = byDay[7]?.avgWeight?.takeIf { day >= 7 }?.let { it / w0 }

    // ------------------------------- feed -------------------------------
    val bagKg = if (farm.feedBagKg > 0) farm.feedBagKg else 50.0
    fun kgPerBag(code: String) = feedTypes.firstOrNull { it.code == code }?.bagKg ?: bagKg
    fun usedKg(r: DailyDataEntity) = usedByType(r).entries.sumOf { (code, bags) -> bags * kgPerBag(code) }
    private fun recKg(r: DailyDataEntity) = receivedByType(r).entries.sumOf { (code, bags) -> bags * kgPerBag(code) }

    val giveKg = e.totalFeedKg
    val giveBags = giveKg / bagKg
    val givePerBird = e.feedPerBird
    val comPerBird: Double? = CompanyStandard.feedPerDay(max(1, day))
    val idealPerBird = PhysiologicalEngine.dailyFeedFromDay(max(1, day).toDouble(), breed)
    val phase = CompanyStandard.feedPhase(day)
    val nextPhaseDay: Int? = (day + 1..CompanyStandard.MAX_DAY).firstOrNull { CompanyStandard.feedPhase(it) != phase }

    /** Feed logged today = what the birds ate yesterday. */
    val usedBagsToday = usedByType(e).values.sum()
    val usedKgToday = usedKg(e)
    private val yLive = byDay[day - 1]?.liveBirds?.takeIf { it > 0 } ?: liveSafe
    val usedPerBirdY: Double? = if (usedKgToday > 0 && day >= 1) usedKgToday * 1000.0 / yLive else null
    val comPerBirdY: Double? = CompanyStandard.feedPerDay(day - 1)
    val idealPerBirdY = PhysiologicalEngine.dailyFeedFromDay(max(1, day - 1).toDouble(), breed)
    val usedBagsTD = upto.sumOf { usedByType(it).values.sum() }
    val usedKgTD = upto.sumOf { usedKg(it) }
    val planKgTD = rows.filter { it.dayNumber < day }.sumOf { it.totalFeedKg }
    /** Commercial / ideal feed the flock should have eaten up to yesterday, for the birds actually alive each day. */
    val comKgTD = rows.filter { it.dayNumber in 1 until day }.sumOf { (CompanyStandard.feedPerDay(it.dayNumber) ?: 0.0) * it.liveBirds / 1000.0 }
    val idealKgTD = rows.filter { it.dayNumber in 1 until day }.sumOf { PhysiologicalEngine.dailyFeedFromDay(it.dayNumber.toDouble(), breed) * it.liveBirds / 1000.0 }
    val cumPerBird: Double? = if (usedKgTD > 0) usedKgTD * 1000.0 / liveSafe else null
    val cumPerBirdCom: Double? = CompanyStandard.cumFeed(day - 1)
    val cumPerBirdIdeal: Double? = if (day >= 2) PhysiologicalEngine.cumFeedFromDay((day - 1).toDouble(), breed) else null
    val remainPlanKg = rows.filter { it.dayNumber in day..harvestAge }.sumOf { it.totalFeedKg }

    // ------------------------------- stock -------------------------------
    val recByCode = HashMap<String, Double>()
    val usedByCode = HashMap<String, Double>()
    init {
        upto.forEach { r ->
            receivedByType(r).forEach { (k, v) -> recByCode[k] = (recByCode[k] ?: 0.0) + v }
            usedByType(r).forEach { (k, v) -> usedByCode[k] = (usedByCode[k] ?: 0.0) + v }
        }
    }
    val stockCodes: List<String> = (feedTypes.sortedBy { it.sortOrder }.map { it.code } + recByCode.keys + usedByCode.keys)
        .distinct().filter { (recByCode[it] ?: 0.0) > 0 || (usedByCode[it] ?: 0.0) > 0 }
    fun stockBags(code: String) = (recByCode[code] ?: 0.0) - (usedByCode[code] ?: 0.0)
    val recBagsTD = recByCode.values.sum()
    val recKgTD = upto.sumOf { recKg(it) }
    val stockBagsTotal = recBagsTD - usedBagsTD
    val stockKgTotal = recKgTD - usedKgTD
    val lastsDays: Double? = if (giveKg > 0) stockKgTotal / giveKg else null
    val toOrderBags = max(0.0, (remainPlanKg - stockKgTotal) / bagKg)
    val dieselTD = upto.sumOf { it.dieselCansUsed }

    // ------------------------------- water -------------------------------
    val tankL = if (farm.drinkTankL > 0) farm.drinkTankL else 2000.0
    val refillF = farm.waterRefillFactor
    val waterTD = upto.sumOf { it.totalWaterL }
    val drinkerHtIn = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_DRINKERHT_BY_AGE, day.toDouble())
    val lines = max(1, farm.drinkerLines)

    // --------------------------- environment / heat ---------------------------
    val avgKg = bw / 1000.0
    val heatPerBirdW = 10.62 * Math.pow(avgKg.coerceAtLeast(0.04), 0.75)
    val sensibleFrac = (0.61 * (1 + 0.02 * (20 - e.tempIdeal)) - 0.000228 * e.tempIdeal * e.tempIdeal).coerceIn(0.15, 0.75)
    /** Moisture breathed out, g per bird per hour (latent heat ÷ 2,450 J/g). */
    val moistureGPerBirdHr = heatPerBirdW * (1 - sensibleFrac) * 3600.0 / 2450.0
    val lightIdealLux = if (day <= 7) "30.0–40.0" else "5.0–10.0"

    // ------------------------------- ventilation -------------------------------
    val plan = IbController.dayPlan(day, bw, live, farm)
    val hour = currentHour(farm)
    /** No house sensors yet, so nothing inside the house is estimated from the weather. */
    val now: IbController.HouseState? = null
    val fanCfm = plan.fanCfm
    val allFansCfm = farm.fanCount * fanCfm
    val houseVolFt3 = farm.usableLengthFt * farm.usableWidthFt * farm.heightFt
    val minLevel: IbController.Level = plan.minLv
    val minAvgCfm = minLevel.avgCfm(fanCfm)

    /** The controller level the house mostly sits at in a simulated state. */
    fun levelOf(s: IbController.HouseState): IbController.Level =
        plan.levels[(if (s.frac >= 0.5) s.level else s.level - 1).coerceIn(0, plan.levels.size - 1)]

    fun airFpm(fans: Double) = fans * fanCfm / plan.crossFt2 * IbController.FLOOR_AIR_FACTOR
    fun modeOf(s: IbController.HouseState): String {
        val base = when {
            s.level <= 9 -> "Minimum ventilation · 1 fan on timer"
            s.level <= 11 -> "Transition · fans building up"
            else -> "Tunnel ventilation"
        }
        return base + (if (s.padEff > 0) " + cooling pads" else "") + (if (s.heaterKw > 0.05) " + heaters" else "")
    }

    /** Coolest / middle / hottest hour of the selected day: forecast when available, else the season's typical day. */
    val scenarioSource: String
    val scenarios: List<VentScenario>
    init {
        val dayPts = emptyList<com.example.flock.network.HourPoint>()   // season's typical day, not the forecast
        val cases: List<Triple<Int, Double, Double>> = if (dayPts.size >= 12) {
            val lo = dayPts.minBy { it.tempC }
            val hi = dayPts.maxBy { it.tempC }
            val midT = (lo.tempC + hi.tempC) / 2
            val mid = dayPts.minBy { abs(it.tempC - midT) }
            listOf(Triple(lo.hour, lo.tempC, lo.rhPct), Triple(mid.hour, mid.tempC, mid.rhPct), Triple(hi.hour, hi.tempC, hi.rhPct))
        } else {
            when (farm.season.lowercase()) {
                "summer" -> listOf(Triple(5, 27.0, 60.0), Triple(10, 34.0, 40.0), Triple(15, 41.0, 25.0))
                "winter" -> listOf(Triple(5, 10.0, 80.0), Triple(10, 18.0, 60.0), Triple(15, 26.0, 45.0))
                else -> listOf(Triple(5, 24.0, 92.0), Triple(10, 28.0, 80.0), Triple(15, 32.0, 68.0))
            }
        }
        scenarioSource = if (dayPts.size >= 12) "forecast for this day" else "typical ${farm.season.lowercase()} day"
        val names = listOf("Coolest" to "", "Middle" to "", "Hottest" to "")
        scenarios = cases.mapIndexed { i, (h, t, rh) ->
            val s = IbController.simulate(plan, t, rh, h, live, bw, farm)
            VentScenario(names[i].first, names[i].second, h, t, rh, s, levelOf(s), airFpm(s.fans), modeOf(s))
        }
    }


    // ------------------------------- cooling grid & fan finder -------------------------------
    /** Hottest outside temperature of the day (forecast, else the season's typical day). */
    val hottestC = scenarios.last().outC
    val hottestHour = scenarios.last().hour
    /** Cooling grid rows: hottest − 8, − 4 and the hottest itself; columns: dry, medium, humid air. */
    val gridTemps = listOf(hottestC - 8, hottestC - 4, hottestC).map { (it * 2).roundToInt() / 2.0 }
    val gridRh = listOf(40.0, 65.0, 90.0)
    fun sim(outC: Double, outRh: Double, hr: Int = 14): IbController.HouseState = IbController.simulate(plan, outC, outRh, hr, live, bw, farm)
    /** Where the fan finder dials start: the weather now if we have it, else the hottest hour. */
    val dialStartC = hottestC
    val dialStartRh = scenarios.last().outRh

    // ------------------------------- house layout & feeding plan -------------------------------
    val feederLines = max(1, farm.feederLines)
    val drinkerLinesN = max(0, farm.drinkerLines)
    /** Feed pans on one line; the sensor (control) pans at the end are not counted. */
    val pansPerLine = max(1, farm.pansPerFeederLine)
    val sensorPans = max(0, farm.sensorPansPerLine)
    val panSpacingFt = if (farm.panSpacingFt > 0) farm.panSpacingFt else PAN_SPACING_FT
    /** A feeder line is as long as its pans need (hopper → pans → sensor pans → motor), not the house length. */
    val lineLenFt = (pansPerLine + sensorPans) * panSpacingFt
    val drinkerLenFt = if (farm.nipplesPerLine > 0) farm.nipplesPerLine * NIPPLE_SPACING_FT else lineLenFt
    val lineStartFt = LINE_START_FT
    /** Bags that fill one whole line from empty (farm setting, e.g. 3.3). */
    val bagsFullLine = max(0.1, farm.lineFillBags)
    val houseFloorFt2 = max(1.0, farm.usableLengthFt * farm.usableWidthFt)
    /** Brooding barricade: how far from the front the birds can go today. */
    val barricadeFtNow: Double = if (e.occupiedFt2 > 0) min(farm.usableLengthFt, e.occupiedFt2 / max(1.0, farm.usableWidthFt)) else farm.usableLengthFt
    val openFrac = (barricadeFtNow / max(1.0, farm.usableLengthFt)).coerceIn(0.05, 1.0)
    /** Pans inside the birds' area on each line. */
    val pansInArea = floor((barricadeFtNow - lineStartFt) / panSpacingFt).toInt().coerceIn(0, pansPerLine)
    /** Floor the birds have today (up to the barricade). */
    val areaInUseFt2 = max(1.0, barricadeFtNow * farm.usableWidthFt)
    /** Lines spread evenly across the width, drinkers between feeders: D F D F … D. */
    val lineOrder: List<Char> = buildList {
        val f = feederLines; val dl = drinkerLinesN
        if (dl >= f + 1) { repeat(f) { add('D'); add('F') }; add('D'); repeat(dl - f - 1) { add('D') } }
        else if (dl == f) repeat(f) { add('D'); add('F') }
        else { var dd = dl; repeat(f) { if (dd > 0) { add('D'); dd-- }; add('F') } }
    }
    val lineGapFt = farm.usableWidthFt / max(1, lineOrder.size)
    /** Feeder line to feeder line (farm setting, else the width shared by the lines). */
    val feederGapFt = if (farm.feederLineGapFt > 0) farm.feederLineGapFt else farm.usableWidthFt / feederLines
    /** Feedings by age: small and often for chicks (Ross: top up trays/paper often in days 0–4), 2–3 after the first week. */
    val feedingsWanted = when {
        day <= 3 -> 5
        day <= 7 -> 4
        day <= 21 -> 3
        else -> 2
    }
    /** Pans one bag fills on a line (farm setting: bags that fill one whole line). */
    val pansPerBag = pansPerLine / bagsFullLine
    /**
     * Birds one pan can serve: Ross gives 45–80 for grown birds; feeder space needed grows with body size
     * (≈ weight^⅓), so small birds can share a pan with more birds.
     */
    val birdsPerPanMax = if (bw > 3500) 45.0 else min(160.0, 80.0 * Math.cbrt(2000.0 / max(bw, 100.0)).coerceAtLeast(1.0))
    val birdsPerPanMin = 45.0

    /**
     * One on/off series of consecutive pans, repeated from the hopper end to the barricade on every line.
     * [travelM] = the furthest any bird is from an open pan: half the feeder-line gap across, half the
     * gap between open pans along. [cellFt2] / [cellBirds] = the floor one open pan serves in the repeat
     * (line gap × repeat length ÷ pans on) and the birds on it at today's density; [birdsPerPan] = every
     * bird shared over the open pans (the feeder-space check).
     */
    data class PanPattern(val on: Int, val off: Int, val openPerLine: Int, val lastIdx: Int, val travelM: Double, val birdsPerPan: Double,
                          val cellFt2: Double, val cellBirds: Double) {
        val label get() = if (off == 0) "All on" else "$on on · $off off"
        fun isOpen(i: Int) = (i % (on + off)) < on
    }
    val patterns: List<PanPattern> = listOf(1 to 0, 4 to 1, 3 to 1, 2 to 1, 3 to 2, 1 to 1, 2 to 3, 1 to 2, 1 to 3).map { (on, off) ->
        val open = (0 until pansInArea).filter { (it % (on + off)) < on }
        val gapM = (off + 1) * panSpacingFt * 0.3048
        val travel = Math.hypot(feederGapFt * 0.3048 / 2, gapM / 2)
        val n = feederLines * open.size
        val cell = feederGapFt * (on + off) * panSpacingFt / on
        PanPattern(on, off, open.size, open.lastOrNull() ?: -1, travel,
            if (n == 0) Double.MAX_VALUE else live.toDouble() / n, cell, cell * live / areaInUseFt2)
    }
    private fun safe(p: PanPattern) = p.openPerLine > 0 && p.travelM <= ALLOWED_TRAVEL_M && p.birdsPerPan <= birdsPerPanMax

    /** The day's feed in whole bags — never more than that (15.27 needed → 16). */
    val dayBags: Double = if (giveBags > 0) ceil(giveBags - 1e-6) else 0.0

    /**
     * One way to feed the day's bags: [feedings] a day, the same pour on every line each time, and the
     * most open safe series whose open pans that pour fills all the way to the last one (3 % slack for
     * the "3–3.3 bags a line" spread).
     */
    data class FeedOption(
        val feedings: Int, val bagsPerFeeding: Double, val bagsPerLine: Double, val kgPerLine: Double,
        val canFill: Double, val pattern: PanPattern, val safe: Boolean, val adLib: Boolean
    ) {
        /** Share of the open pans the pour fills (100 % = feed reaches the last open pan). */
        val fillPct get() = if (pattern.openPerLine > 0) min(100.0, canFill / pattern.openPerLine * 100) else 0.0
        /** Share of the pour that lands in the open pans at once; the rest waits in the hopper. */
        val usePct get() = if (canFill > 0) min(100.0, pattern.openPerLine / canFill * 100) else 0.0
    }
    val feedOptions: List<FeedOption> = (2..3).map { n ->
        val perLine = dayBags / (feederLines * n)
        val can = perLine * pansPerBag
        val fits = patterns.filter { safe(it) && it.openPerLine <= can * 1.03 }
        val p = fits.maxByOrNull { it.openPerLine }
        FeedOption(n, dayBags / n, perLine, perLine * bagKg, can, p ?: patterns.first(), p != null,
            p != null && p.off == 0 && can >= pansInArea)
    }
    /**
     * Recommended: a safe option; if the pour covers every pan (all on, the hopper tops the line up) the
     * fewer feedings; otherwise the one whose pour goes into the pans best (5 % steps), then more pans open.
     */
    val recommendedOption: Int = feedOptions.indices.sortedWith(compareBy<Int>(
        { if (feedOptions[it].safe) 0 else 1 },
        { if (feedOptions[it].adLib) 0 else 1 },
        { if (feedOptions[it].adLib) feedOptions[it].feedings.toDouble() else -floor(feedOptions[it].usePct / 5.0) },
        { feedOptions[it].pattern.birdsPerPan }
    )).first()
    val chosen: FeedOption = feedOptions[recommendedOption]
    val patternFits: Boolean = chosen.safe
    /** First week: trays and paper carry the chicks, pans in the brooding area stay all on. */
    val feedings: Int = if (!chosen.safe && day <= 7) feedingsWanted else chosen.feedings
    val feedPattern: PanPattern = if (chosen.safe) chosen.pattern else patterns.first()
    val planBags: Double = dayBags
    val bagsPerFeeding: Double = if (feedings > 0) dayBags / feedings else 0.0
    val bagsPerLinePerFeeding: Double = bagsPerFeeding / feederLines
    /** The rounding up to whole bags. */
    val extraBags = planBags - giveBags
    /** Pans one feeding fills per line, and the pans open per line. */
    val pansFilledPerLine = bagsPerLinePerFeeding * pansPerBag
    val pansOpenPerLine = max(0, feedPattern.openPerLine)
    val pansOpen = pansOpenPerLine * feederLines
    val birdsPerPan = if (pansOpen > 0) live.toDouble() / pansOpen else 0.0
    val openLenFt = min(lineLenFt, max(0.0, barricadeFtNow - lineStartFt))
    /** Bags that fill every open pan once; what a feeding pours beyond that waits in the hopper. */
    val bagsFillOpen = pansOpen / pansPerBag
    val hopperBagsPerFeeding = max(0.0, bagsPerFeeding - bagsFillOpen)
    val fillsPossible: Double = if (bagsFillOpen > 0) giveBags / bagsFillOpen else 0.0
    val reachOfOpen = if (pansOpenPerLine > 0) min(1.0, pansFilledPerLine / pansOpenPerLine) else 1.0
    val reachFt = reachOfOpen * (feedPattern.lastIdx + 1) * panSpacingFt
    fun feedTimesFor(n: Int): List<String> = when (n) {
        1 -> listOf("06:00")
        2 -> listOf("06:00", "17:00")
        3 -> listOf("06:00", "12:00", "18:00")
        4 -> listOf("06:00", "10:00", "14:00", "18:00")
        else -> listOf("06:00", "09:00", "12:00", "15:00", "18:00")
    }
    val feedTimes: List<String> = feedTimesFor(feedings)
    // ------------------------------- the farm's day (clock) and feed correction -------------------------------
    /** Outside temperature for each hour of this day: the forecast when it covers the day, else the season's typical day. */
    val dayTemps: List<Double> = run {
        val pts = hourly.filter { it.date == e.date }.associateBy { it.hour }
        if (pts.size >= 24) (0 until 24).map { pts[it]!!.tempC }
        else com.example.flock.domain.DaySchedule.seasonTemps(scenarios.first().outC, scenarios.last().outC)
    }
    /** Tank fills the day's water needs (rounded up), and the refills planned with the farm's multiplier. */
    val waterTanksNeeded: Double get() = max(1.0, ceil(e.totalWaterL / tankL))
    val waterRefills: Int get() = (waterTanksNeeded * (if (refillF > 0) refillF else 1.0)).roundToInt().coerceIn(1, 24)
    fun daySchedule(feedings: Int) = com.example.flock.domain.DaySchedule.plan(
        com.example.flock.domain.DaySchedule.Inputs(e.lightHours, 5.0, feedings, waterRefills, dayTemps))
    val correction = com.example.flock.domain.FeedCorrection.advise(day, if (e.sampleEntered) bw else null, bwCom, e.fcr, fcrCom)
    val correctedBags: Double get() = if (correction.pct == 0.0) dayBags else ceil(giveBags * (1 + correction.pct / 100) - 1e-6)

    /** Ross: let birds clear the pans once a day from day 10–12 — only workable once a day's ration can refill the empty lines. */
    val cleanOutOk = day >= 10 && giveBags >= bagsFillOpen

    // ------------------------------- first week equipment -------------------------------
    val chicksPlaced = entryBirds
    /** Ross: feeder trays 1 per 100 chicks; mini drinkers 12 per 1,000 chicks. */
    val traysIdeal = ceil(chicksPlaced / 100.0)
    val miniDrinkersIdeal = ceil(chicksPlaced * 12.0 / 1000.0)
    /** Estimated breast height (cm): ~4.0 cm for a 40 g chick, scaling with weight^(1/3). */
    fun breastCm(g: Double) = 4.0 * Math.cbrt(max(g, 30.0) / 40.0)
    val breastNowCm = breastCm(bw)
    /** First day the birds' breast reaches the pan lip (company curve weights). */
    val panReachDay: Int = (0..14).firstOrNull { d -> breastCm(CompanyStandard.bw(d) ?: w0) >= farm.panLipCm } ?: 14
    /** Share of trays to keep: all until day 3 (and until birds reach the pans), then out over ~3 days; none from day 7. */
    val trayKeepFrac: Double = run {
        val start = max(4, panReachDay)
        val end = max(7, start + 3)
        when {
            day < start -> 1.0
            day >= end -> 0.0
            else -> 1.0 - (day - start + 1).toDouble() / (end - start + 1)
        }
    }
    val traysKeepIdeal = ceil(traysIdeal * trayKeepFrac)
    val traysKeepYours = ceil(farm.manualFeeders * trayKeepFrac)
    /** Ross: supplementary drinkers for the first 3 days. */
    val drinkerKeepFrac = when { day <= 3 -> 1.0; day == 4 -> 0.5; else -> 0.0 }
    /** Ross: feed on paper covering ≥ 70 % of the brooding area; paper out by the end of day 4. */
    val paperFt2 = if (day <= 4) 0.70 * (if (e.occupiedFt2 > 0) e.occupiedFt2 else houseFloorFt2) else 0.0
    val nipples = farm.nipplesPerLine * max(1, farm.drinkerLines)
    val birdsPerNipple: Double? = if (nipples > 0) live.toDouble() / nipples else null
    /** Ross: 10–12 birds per nipple while brooding, 12 below 3 kg, 9 above 3 kg. */
    val birdsPerNippleMax = when { day <= 10 -> 12.0; bw > 3000 -> 9.0; else -> 12.0 }
    /** Ross nipple flow guide, mL/min by age. */
    val nippleFlow: Pair<Double, Double> = when {
        day <= 7 -> 20.0 to 29.0
        day <= 14 -> 30.0 to 39.0
        day <= 21 -> 40.0 to 49.0
        day <= 28 -> 50.0 to 69.0
        else -> 70.0 to 100.0
    }

    // ------------------------------- bird / litter temperatures -------------------------------
    /** Body (vent/cloacal) temperature: Ross 39.4–40.5 °C in the first 2 days; ~41–42 °C once grown. */
    val bodyTemp: Triple<Double, Double, Double> = when {
        day <= 2 -> Triple(39.4, 40.0, 40.5)
        day <= 10 -> Triple(40.0, 40.6, 41.2)
        else -> Triple(40.6, 41.2, 42.0)
    }
    /** Foot (leg skin) temperature: warm to the touch; thermal-camera studies put comfortable birds at ~32–34 °C. */
    /** minimum air per bird: the day's worked-out value, or the design rule for this weight when it is missing */
    val minVentCfmBird: Double get() = e.cfmPerBird.takeIf { it > 0 } ?: PhysiologicalEngine.designMinVentCfmPerBird(bw / 1000.0, farm.minVentFactor)
    /** resting breaths a minute when the house is at its ideal; chicks breathe faster than grown birds */
    val breathsIdeal: Pair<Double, Double> = if (day <= 7) 30.0 to 50.0 else 20.0 to 40.0
    val footTemp: Triple<Double, Double, Double> = if (day <= 7) Triple(29.0, 31.0, 33.0) else Triple(31.0, 33.0, 35.0)
    /** Litter / floor: Ross 28–32 °C at placement (floor 28–30 °C); afterwards it follows the house air. */
    val litterTemp: Triple<Double, Double, Double> = if (day <= 7) Triple(28.0, 30.0, 32.0) else Triple(e.tempMin, e.tempIdeal, e.tempMax)
    val lightLux: Pair<Double, Double> = if (day <= 7) 30.0 to 40.0 else 5.0 to 10.0

    // ------------------------------- stock by feed type -------------------------------
    /** Bags still needed from today to lifting, split by the feed phase each day falls in. */
    val needByCode: Map<String, Double> = rows.filter { it.dayNumber in day..harvestAge }
        .groupBy { CompanyStandard.feedPhase(it.dayNumber) }
        .mapValues { (code, rs) -> rs.sumOf { it.totalFeedKg } / kgPerBag(code) }
    val allCodes: List<String> = (stockCodes + needByCode.keys).distinct()
    fun orderBags(code: String) = max(0.0, (needByCode[code] ?: 0.0) - max(0.0, stockBags(code)))
    val godownFree: Double? = if (farm.godownBags > 0) farm.godownBags - stockBagsTotal else null

    // ------------------------------- alerts -------------------------------
    val alerts: Map<Topic, List<TopicAlert>> = buildMap {
        put(Topic.BIRDS, buildList {
            val m = mortTDPct
            if (m != null && m > comCumPct * 1.2) add(TopicAlert(2, "Mortality ${Fmt.pct(m)} is well above commercial ${Fmt.pct(comCumPct)}"))
            else if (m != null && m > comCumPct * 1.05) add(TopicAlert(1, "Mortality ${Fmt.pct(m)} is above commercial ${Fmt.pct(comCumPct)}"))
            val t = mortTodayPct
            if (t != null && t > comDailyPct * 2) add(TopicAlert(2, "Today's deaths ${Fmt.pct(t, 3)} — over twice the commercial ${Fmt.pct(comDailyPct, 3)}"))
            else if (t != null && t > comDailyPct * 1.3) add(TopicAlert(1, "Today's deaths ${Fmt.pct(t, 3)} above commercial ${Fmt.pct(comDailyPct, 3)}"))
            bwCom?.let { c ->
                val dev = (bw - c) / c * 100
                val tag = if (e.avgWeight == null) " (projected)" else ""
                if (dev < -10) add(TopicAlert(2, "Weight ${Fmt.n(bw, 1)} g is ${Fmt.n(-dev, 1)}% under commercial$tag"))
                else if (dev < -5) add(TopicAlert(1, "Weight ${Fmt.n(bw, 1)} g is ${Fmt.n(-dev, 1)}% under commercial$tag"))
            }
            val f = e.fcr
            if (f != null && fcrCom != null && day >= 7) {
                val dev = (f - fcrCom) / fcrCom * 100
                if (dev > 10) add(TopicAlert(2, "FCR ${Fmt.n(f, 3)} is ${Fmt.n(dev, 1)}% worse than commercial"))
                else if (dev > 5) add(TopicAlert(1, "FCR ${Fmt.n(f, 3)} is ${Fmt.n(dev, 1)}% worse than commercial"))
            }
            e.cv?.let { cv -> if (cv >= 12) add(TopicAlert(2, "Uniformity CV ${Fmt.n(cv)}% — very uneven, grade the flock")) else if (cv >= 10) add(TopicAlert(1, "Uniformity CV ${Fmt.n(cv)}% — uneven")) }
            e.densityKgM2?.let { d -> if (d > farm.densityCapDefault) add(TopicAlert(2, "Density ${Fmt.n(kgPerFt2(d), 3)} kg/ft² over the cap ${Fmt.n(kgPerFt2(farm.densityCapDefault), 3)}")) }
            if (e.minFtPerBird > 0 && e.ftPerBird < e.minFtPerBird) add(TopicAlert(1, "Floor space ${Fmt.n(e.ftPerBird, 3)} ft²/bird below ${Fmt.n(e.minFtPerBird, 3)} — open the barricade"))
        })
        put(Topic.FEED, buildList {
            val u = usedPerBirdY; val c = comPerBirdY
            if (u != null && c != null) {
                val dev = (u - c) / c * 100
                if (abs(dev) > 20) add(TopicAlert(2, "Birds ate ${Fmt.n(u, 1)} g yesterday vs commercial ${Fmt.n(c, 1)} g (${Fmt.signed(dev, 1)}%)"))
                else if (abs(dev) > 10) add(TopicAlert(1, "Birds ate ${Fmt.n(u, 1)} g yesterday vs commercial ${Fmt.n(c, 1)} g (${Fmt.signed(dev, 1)}%)"))
            } else if (day >= 2 && usedBagsToday == 0.0) add(TopicAlert(1, "Yesterday's feed use not entered yet"))
            e.waterPh?.let { ph -> if (ph < 6.0 || ph > 6.8) add(TopicAlert(1, "Water pH ${Fmt.n(ph)} outside 6.00–6.80")) }
            e.waterTempC?.let { t -> if (t > 25) add(TopicAlert(1, "Water ${Fmt.n(t, 1)} °C is warm (ideal 10.0–25.0 °C) — flush the lines")) }
            if (day > 7 && !patternFits) add(TopicAlert(2, "No safe pan series for ${Fmt.n(dayBags, 2)} bags"))
            if (birdsPerPan > birdsPerPanMax) add(TopicAlert(1, "${Fmt.n(birdsPerPan, 1)} birds per open pan — more than ${Fmt.n(birdsPerPanMax, 1)}; open more pans"))
            birdsPerNipple?.let { b -> if (b > birdsPerNippleMax) add(TopicAlert(1, "${Fmt.n(b, 1)} birds per nipple — more than ${Fmt.n(birdsPerNippleMax, 1)}")) }
            if (day <= 3 && farm.manualFeeders < traysIdeal) add(TopicAlert(1, "${farm.manualFeeders} feeder trays; Ross advises ${Fmt.n(traysIdeal, 1)} (1 per 100 chicks)"))
        })
        put(Topic.STOCK, buildList {
            stockCodes.forEach { code -> if (stockBags(code) < -0.001) add(TopicAlert(2, "$code stock is negative (${Fmt.n(stockBags(code))} bags) — a delivery is missing")) }
            if (stockCodes.isEmpty() && day >= 1) add(TopicAlert(1, "No feed deliveries logged yet"))
            godownFree?.let { f -> if (f < 0) add(TopicAlert(2, "Godown over capacity by ${Fmt.n(-f, 2)} bags")) }
        })
        put(Topic.VENT, buildList {
            scenarios.lastOrNull()?.let { s -> if (s.state.feltC > plan.comfort + 4) add(TopicAlert(1, "Hottest hour: birds may feel ${Fmt.n(s.state.feltC, 1)} °C even with ${Fmt.n(s.state.fans, 1)} fans")) }
            scenarios.firstOrNull()?.let { s ->
                val cap = farm.heaterCount * farm.heaterKw
                if (cap > 0 && s.state.heaterKw >= cap * 0.98) add(TopicAlert(1, "Coolest hour needs all heaters (${Fmt.n(cap, 1)} kW)"))
            }
            e.measuredAirspeed?.let { a -> if (a > PhysiologicalEngine.maxAirSpeedFpm(day)) add(TopicAlert(1, "Measured air speed ${Fmt.n(a, 1)} ft/min above the age cap ${Fmt.n(PhysiologicalEngine.maxAirSpeedFpm(day), 1)}")) }
        })
        put(Topic.ENV, buildList {
            e.measuredNh3?.let { v -> if (v > 20) add(TopicAlert(2, "Ammonia ${Fmt.n(v, 1)} ppm — critical")) else if (v > e.nh3Max) add(TopicAlert(1, "Ammonia ${Fmt.n(v, 1)} ppm above ${Fmt.n(e.nh3Max, 1)}")) }
            e.measuredCo2?.let { v -> if (v > 3500) add(TopicAlert(2, "CO₂ ${Fmt.n(v, 1)} ppm — critical")) else if (v > e.co2Max) add(TopicAlert(1, "CO₂ ${Fmt.n(v, 1)} ppm above ${Fmt.n(e.co2Max, 1)}")) }
            e.measuredO2?.let { v -> if (v < 19.6) add(TopicAlert(1, "Oxygen ${Fmt.n(v)}% below 19.60%")) }
            e.measuredPressure?.let { v -> if (v < 15 || v > 45) add(TopicAlert(1, "Static pressure ${Fmt.n(v, 1)} Pa outside 15.0–45.0")) }
        })
    }

    /** Growth stage name for the day. */
    val stage = when { day <= 3 -> "Day-old chick"; day <= 10 -> "Starter chick"; day <= 21 -> "Grower"; day <= 35 -> "Finisher"; else -> "Market weight" }
    /** shown on the Output page; the store's own alerts sit on the Stock page */
    val allAlerts: List<TopicAlert> get() = alerts.filterKeys { it != Topic.STOCK }.values.flatten()
    val stockAlerts: List<TopicAlert> get() = alerts[Topic.STOCK].orEmpty()

    fun worst(t: Topic): Int = alerts[t].orEmpty().maxOfOrNull { it.level } ?: 0
}
