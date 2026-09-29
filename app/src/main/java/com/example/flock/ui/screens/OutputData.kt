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
    val gain: Double = gainMeasured ?: e.gainPerBird
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
    val now: IbController.HouseState? =
        if (isToday && weather != null) IbController.simulate(plan, weather.tempC, weather.rhPercent, hour, live, bw, farm) else null
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
        val dayPts = hourly.filter { it.date == e.date }
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
    val dialStartC = weather?.tempC ?: hottestC
    val dialStartRh = weather?.rhPercent ?: scenarios.last().outRh

    // ------------------------------- house layout & feeding plan -------------------------------
    val feederLines = max(1, farm.feederLines)
    val drinkerLinesN = max(0, farm.drinkerLines)
    val pansPerLine = max(1, farm.pansPerFeederLine)
    val panSpacingFt = PAN_SPACING_FT
    /** A feeder line is as long as its pans need (hopper → motor), not the house length. */
    val lineLenFt = pansPerLine * panSpacingFt
    val drinkerLenFt = if (farm.nipplesPerLine > 0) farm.nipplesPerLine * NIPPLE_SPACING_FT else lineLenFt
    val lineStartFt = LINE_START_FT
    /** Bags that fill one whole line from empty (farm setting). */
    val bagsFullLine = max(0.1, farm.feederLineBags.toDouble())
    val houseFloorFt2 = max(1.0, farm.usableLengthFt * farm.usableWidthFt)
    /** Brooding barricade: how far from the front the birds can go today. */
    val barricadeFtNow: Double = if (e.occupiedFt2 > 0) min(farm.usableLengthFt, e.occupiedFt2 / max(1.0, farm.usableWidthFt)) else farm.usableLengthFt
    val openFrac = (barricadeFtNow / max(1.0, farm.usableLengthFt)).coerceIn(0.05, 1.0)
    /** Pans inside the birds' area on each line. */
    val pansInArea = floor((barricadeFtNow - lineStartFt) / panSpacingFt).toInt().coerceIn(0, pansPerLine)
    /** Lines spread evenly across the width, drinkers between feeders: D F D F … D. */
    val lineOrder: List<Char> = buildList {
        val f = feederLines; val dl = drinkerLinesN
        if (dl >= f + 1) { repeat(f) { add('D'); add('F') }; add('D'); repeat(dl - f - 1) { add('D') } }
        else if (dl == f) repeat(f) { add('D'); add('F') }
        else { var dd = dl; repeat(f) { if (dd > 0) { add('D'); dd-- }; add('F') } }
    }
    val lineGapFt = farm.usableWidthFt / max(1, lineOrder.size)
    val feederGapFt = farm.usableWidthFt / feederLines
    /** Ross: 45–80 birds per pan (the lower figure above 3.5 kg); birds should not walk more than 2 m to feed. */
    val birdsPerPanMax = if (bw > 3500) 45.0 else 80.0
    val birdsPerPanMin = 45.0
    val isHotDay = hottestC >= 33.0 || scenarios.last().state.feltC > plan.comfort + 4
    /** Feedings the age and heat call for (farm practice + Ross: small and often while young, cool hours when hot). */
    val feedingsWanted = when {
        day <= 3 -> 5
        day <= 7 -> 4
        day <= 21 -> if (isHotDay) 2 else 3
        else -> 2
    }
    val feedings = feedingsWanted
    val bagsPerFeeding = giveBags / feedings
    val bagsPerLinePerFeeding = bagsPerFeeding / feederLines
    private val phaseBagKg = kgPerBag(phase)
    /** Feed in a pan when full, from the line setting minus what the auger tube holds (~0.95 kg per metre). */
    val tubeKgPerFt = 0.95 * 0.3048
    val panKg = max(0.3, (bagsFullLine * phaseBagKg - tubeKgPerFt * lineLenFt) / pansPerLine)

    /** One on/off arrangement of the pans inside the birds' area (same on every line). */
    data class PanPattern(val on: Int, val off: Int, val openPerLine: Int, val lastIdx: Int, val chargeKg: Double,
                          val travelM: Double, val birdsPerPan: Double) {
        val label get() = if (off == 0) "All pans open" else "$on on · $off off"
        fun isOpen(i: Int) = (i % (on + off)) < on
    }
    val patterns: List<PanPattern> = listOf(1 to 0, 2 to 1, 1 to 1, 1 to 2).map { (on, off) ->
        val open = (0 until pansInArea).filter { (it % (on + off)) < on }
        val last = open.lastOrNull() ?: -1
        val tubeFt = (last + 1) * panSpacingFt
        val charge = feederLines * (tubeKgPerFt * tubeFt + panKg * open.size)
        val gapM = (off + 1) * panSpacingFt * 0.3048
        val travel = Math.hypot(feederGapFt * 0.3048 / 2, gapM / 2)
        PanPattern(on, off, open.size, last, charge, travel, if (open.isEmpty()) Double.MAX_VALUE else live.toDouble() / (feederLines * open.size))
    }
    /** Patterns that keep every bird within 2 m of feed and each pan within its bird limit. */
    val safePatterns = patterns.filter { it.openPerLine > 0 && it.travelM <= ALLOWED_TRAVEL_M && it.birdsPerPan <= birdsPerPanMax }
    /**
     * EACH: one feeding reaches the last open pan even from empty. CHARGE: fill the lines once (within
     * today's ration), then top up before the pans run empty. MANUAL: no safe pattern fits today's ration.
     */
    val patternMode: String
    val feedPattern: PanPattern
    init {
        val perFeedKg = giveKg / feedings
        val each = safePatterns.firstOrNull { it.chargeKg <= perFeedKg }
        val charge = safePatterns.firstOrNull { it.chargeKg <= giveKg }
        when {
            day <= 3 -> { patternMode = "TRAYS"; feedPattern = patterns.first() }
            each != null -> { patternMode = "EACH"; feedPattern = each }
            charge != null -> { patternMode = "CHARGE"; feedPattern = charge }
            else -> { patternMode = "MANUAL"; feedPattern = safePatterns.lastOrNull() ?: patterns.first() }
        }
    }
    val pansOpenPerLine = max(0, feedPattern.openPerLine)
    val pansOpen = pansOpenPerLine * feederLines
    val birdsPerPan = if (pansOpen > 0) live.toDouble() / pansOpen else 0.0
    /** Length of line inside the birds' area. */
    val openLenFt = min(lineLenFt, max(0.0, barricadeFtNow - lineStartFt))
    /** Bags to fill every open pan (and the tube up to the last one) from empty. */
    val bagsFillOpen = feedPattern.chargeKg / phaseBagKg
    val fillsPossible: Double = if (bagsFillOpen > 0) giveBags / bagsFillOpen else 0.0
    /** Share of the open pans one feeding reaches if they were empty, and how far along the line that is. */
    val reachOfOpen = if (feedPattern.chargeKg > 0) (giveKg / feedings) / feedPattern.chargeKg else 1.0
    val reachFt = min(1.0, reachOfOpen) * (feedPattern.lastIdx + 1) * panSpacingFt
    /** Ross: let birds clear the pans once a day from day 10–12 — only workable once a day's ration can refill the empty lines. */
    val cleanOutOk = day >= 10 && giveBags >= bagsFillOpen
    val feedTimes: String = when {
        isHotDay && feedings == 2 -> "05:00 · 18:30"
        isHotDay && feedings == 3 -> "04:30 · 18:00 · 21:30"
        feedings == 1 -> "05:00"
        feedings == 2 -> "06:00 · 17:00"
        feedings == 3 -> "06:00 · 12:00 · 18:00"
        feedings == 4 -> "06:00 · 10:00 · 14:00 · 18:00"
        else -> "06:00 · 09:00 · 12:00 · 15:00 · 18:00 (+ night top-up)"
    }
    val feedingReason: String = when {
        day <= 3 -> "Chicks eat from trays and paper: top up little and often (Ross). Keep the pans flooded."
        isHotDay -> "Hot day (up to ${Fmt.n(hottestC, 1)} °C): feed in the cool hours; no feeding for ~5.0 h before the hottest hour (${String.format("%02d:00", hottestHour)})."
        day <= 21 -> "Comfortable day: $feedings feedings for young birds."
        else -> "$feedings feedings in the cooler morning and evening."
    }

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
            nextPhaseDay?.let { d -> if (d - day in 1..2) add(TopicAlert(1, "Feed changes to ${CompanyStandard.feedPhase(d)} on day $d")) }
            if (patternMode == "MANUAL") add(TopicAlert(2, "No safe pan pattern fits today's ${Fmt.n(giveBags, 2)} bags — pour the far pans by hand or move the barricade in"))
            else if (patternMode == "CHARGE") add(TopicAlert(1, "Fill the lines once with ${Fmt.n(bagsFillOpen, 2)} bags (${feedPattern.label.lowercase()}), then top up before the pans run empty"))
            if (birdsPerPan > birdsPerPanMax) add(TopicAlert(1, "${Fmt.n(birdsPerPan, 1)} birds per open pan — more than ${Fmt.n(birdsPerPanMax, 1)}; open more pans"))
            birdsPerNipple?.let { b -> if (b > birdsPerNippleMax) add(TopicAlert(1, "${Fmt.n(b, 1)} birds per nipple — more than ${Fmt.n(birdsPerNippleMax, 1)}")) }
            if (day <= 3 && farm.manualFeeders < traysIdeal) add(TopicAlert(1, "${farm.manualFeeders} feeder trays; Ross advises ${Fmt.n(traysIdeal, 1)} (1 per 100 chicks)"))
        })
        put(Topic.STOCK, buildList {
            stockCodes.forEach { code -> if (stockBags(code) < -0.001) add(TopicAlert(2, "$code stock is negative (${Fmt.n(stockBags(code))} bags) — a delivery is missing")) }
            lastsDays?.takeIf { stockKgTotal > 0 }?.let { d ->
                if (stockCodes.isNotEmpty() && d < 1) add(TopicAlert(2, "Feed in store lasts only ${Fmt.n(d, 1)} day"))
                else if (stockCodes.isNotEmpty() && d < 2) add(TopicAlert(1, "Feed in store lasts ${Fmt.n(d, 1)} days — order now"))
            }
            if (stockCodes.isNotEmpty() && stockBags(phase) <= 0.0) add(TopicAlert(1, "No $phase (current phase) in store"))
            if (stockCodes.isEmpty() && day >= 1) add(TopicAlert(1, "No feed deliveries logged yet"))
            godownFree?.let { f -> if (f < 0) add(TopicAlert(2, "Godown over capacity by ${Fmt.n(-f, 2)} bags")) }
        })
        put(Topic.VENT, buildList {
            now?.let { s ->
                if (s.feltC > plan.comfort + 4) add(TopicAlert(2, "Birds feel ${Fmt.n(s.feltC, 1)} °C now — ${Fmt.n(s.feltC - plan.comfort, 1)} °C over comfort"))
                else if (s.feltC > plan.comfort + 2) add(TopicAlert(1, "Birds feel ${Fmt.n(s.feltC, 1)} °C now — warm"))
                else if (s.feltC < plan.comfort - 3) add(TopicAlert(1, "Birds feel ${Fmt.n(s.feltC, 1)} °C now — cold"))
            }
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
            now?.let { s -> if (s.houseRh >= 80) add(TopicAlert(1, "House humidity about ${Fmt.n(s.houseRh, 1)}% — wet litter risk")) }
            weather?.let { w -> if (w.tempC >= 35) add(TopicAlert(1, "Hot outside: ${Fmt.n(w.tempC, 1)} °C")) }
        })
    }

    /** Growth stage name for the day. */
    val stage = when { day <= 3 -> "Day-old chick"; day <= 10 -> "Starter chick"; day <= 21 -> "Grower"; day <= 35 -> "Finisher"; else -> "Market weight" }
    val allAlerts: List<TopicAlert> get() = alerts.values.flatten()

    fun worst(t: Topic): Int = alerts[t].orEmpty().maxOfOrNull { it.level } ?: 0
}
