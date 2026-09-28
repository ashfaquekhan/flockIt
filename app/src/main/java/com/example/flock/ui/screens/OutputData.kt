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
import kotlin.math.max

/** The five Output topics, in the order the farm reads them. */
enum class Topic(val emoji: String, val title: String) {
    VENT("🌬️", "Ventilation"),
    ENV("🌡️", "Environment"),
    BIRDS("🐔", "Birds"),
    FEED("🌾", "Feed & Water"),
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
    val mortTD = upto.sumOf { it.mortality } + reception
    val mortTodayPct: Double? = if (live + mortToday > 0) mortToday * 100.0 / (live + mortToday) else null
    val mortTDPct: Double? = if (placed > 0) mortTD * 100.0 / placed else e.cumMortPct
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
        val names = listOf("Coolest" to "🌙", "Middle" to "⛅", "Hottest" to "☀️")
        scenarios = cases.mapIndexed { i, (h, t, rh) ->
            val s = IbController.simulate(plan, t, rh, h, live, bw, farm)
            VentScenario(names[i].first, names[i].second, h, t, rh, s, levelOf(s), airFpm(s.fans), modeOf(s))
        }
    }

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
        })
        put(Topic.STOCK, buildList {
            stockCodes.forEach { code -> if (stockBags(code) < -0.001) add(TopicAlert(2, "$code stock is negative (${Fmt.n(stockBags(code))} bags) — a delivery is missing")) }
            lastsDays?.takeIf { stockKgTotal > 0 }?.let { d ->
                if (stockCodes.isNotEmpty() && d < 1) add(TopicAlert(2, "Feed in store lasts only ${Fmt.n(d, 1)} day"))
                else if (stockCodes.isNotEmpty() && d < 2) add(TopicAlert(1, "Feed in store lasts ${Fmt.n(d, 1)} days — order now"))
            }
            if (stockCodes.isNotEmpty() && stockBags(phase) <= 0.0) add(TopicAlert(1, "No $phase (current phase) in store"))
            if (stockCodes.isEmpty() && day >= 1) add(TopicAlert(1, "No feed deliveries logged yet"))
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

    fun worst(t: Topic): Int = alerts[t].orEmpty().maxOfOrNull { it.level } ?: 0
}
