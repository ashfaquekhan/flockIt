package com.example.flock.engine

import com.example.flock.data.FarmEntity
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Level-based fan controller (IB-style: levels with diff / run / stop / fan grid, plus per-day
 * SET, HEAT, MIN, MAX and SAFE) and a simple heat + moisture balance of the house.
 *
 * The ladder is fixed for the whole flock and cumulative (each level keeps every fan of the level
 * below and adds one). Only SET, HEAT, MIN and MAX change with age. Level gaps equal the extra
 * wind-chill a bird feels when that fan starts, so one step never overshoots the next.
 */
object IbController {

    data class Level(
        val cont: List<Int>,   // fans running continuously
        val cyc: List<Int>,    // fans on the cycle timer
        val on: Int,           // timer ON seconds (cycle fans)
        val off: Int,          // timer OFF seconds
        val diff: Double       // °C above the previous level
    ) {
        val isTimer: Boolean get() = cyc.isNotEmpty()
        val fansOn: Int get() = cont.size + cyc.size
        val duty: Double get() = if (cyc.isEmpty()) 1.0 else on.toDouble() / (on + off)
        val avgFans: Double get() = cont.size + cyc.size * (if (cyc.isEmpty()) 0.0 else duty)
        fun avgCfm(fanCfm: Double): Double = avgFans * fanCfm
    }

    private val TIMERS = listOf(50 to 550, 50 to 400, 50 to 300, 60 to 240, 90 to 210, 120 to 180, 150 to 150, 190 to 110, 240 to 60)
    private val FAN_DIFFS = listOf(0.4, 0.6, 0.5, 0.5, 0.4, 0.4, 0.3, 0.3, 0.3)

    /**
     * 20 levels for 10 fans. 1–9: first fan on a growing timer · 10: first fan non-stop ·
     * 11: + second fan on a 120/180 s timer · 12–20: one more fan per level, never removing one.
     */
    fun ladder(fanCount: Int): List<Level> {
        val seq = PhysiologicalEngine.fanSequence(max(1, fanCount))
        val first = seq[0]
        val out = mutableListOf<Level>()
        TIMERS.forEachIndexed { i, (on, off) -> out.add(Level(emptyList(), listOf(first), on, off, if (i == 0) 0.1 else 0.2)) }
        out.add(Level(listOf(first), emptyList(), 0, 0, 0.2))
        if (seq.size >= 2) {
            out.add(Level(listOf(first), listOf(seq[1]), 120, 180, 0.3))
            for (n in 2..seq.size) out.add(Level(seq.take(n), emptyList(), 0, 0, FAN_DIFFS.getOrElse(n - 2) { 0.3 }))
        }
        return out
    }

    fun accu(levels: List<Level>): List<Double> {
        var a = 0.0
        return levels.map { a += it.diff; (a * 100).roundToInt() / 100.0 }
    }

    // ---------------- bird perception ----------------
    private val KCH = listOf(0.0 to 4.5, 7.0 to 4.0, 14.0 to 3.4, 21.0 to 2.9, 28.0 to 2.4, 35.0 to 2.1, 42.0 to 1.9)

    /** How much cooler a bird feels in moving air (°C). Rule of thumb: ∝ √speed, less for older birds, fades as air nears body heat. */
    /**
     * Sensors and birds sit about 1 ft above the litter, inside the slower air near the floor
     * (friction from litter, floor and the birds themselves), so they get ~80 % of the average
     * cross-section air speed.
     */
    const val FLOOR_AIR_FACTOR = 0.80

    fun chillC(fpmAverage: Double, day: Int, airC: Double): Double {
        val fpm = fpmAverage * FLOOR_AIR_FACTOR
        if (fpm <= 0) return 0.0
        val fT = ((41 - airC) / 11).coerceIn(0.0, 1.0)
        return PhysiologicalEngine.interpolate(KCH, day.toDouble()) * sqrt(fpm / 100) * fT
    }

    /** How much warmer (+) or cooler (−) air feels vs 65 % RH — from the Ross/Aviagen temperature-by-RH table. */
    fun rhOffsetC(rh: Double): Double = if (rh >= 65) 0.14 * (rh - 65) else 0.20 * (rh - 65)

    fun levelChill(l: Level, day: Int, airC: Double, fanCfm: Double, crossFt2: Double): Double {
        val base = chillC(l.cont.size * fanCfm / crossFt2, day, airC)
        if (l.cyc.isEmpty()) return base
        val burst = chillC(l.fansOn * fanCfm / crossFt2, day, airC)
        return l.duty * burst + (1 - l.duty) * base
    }

    // ---------------- daily settings ----------------
    data class DayPlan(
        val day: Int,
        val levels: List<Level>,
        val accu: List<Double>,
        val minLevel: Int,      // 1-based
        val maxLevel: Int,
        val safeLevel: Int,
        val set: Double,
        val heat: Double,
        val vent: Double,
        val high: Double,
        val low: Double,
        val target: Double,     // house dry-bulb the MIN level should hold
        val comfort: Double,    // still-air bird comfort at 65 % RH
        val needCfm: Double,    // minimum-ventilation need (Ross × 1.3 × calibration)
        val maxFans: Int,
        val fanCfm: Double,
        val crossFt2: Double
    ) {
        fun start(level: Int): Double = set + accu[level - 1]
        val minLv: Level get() = levels[minLevel - 1]
    }

    fun fanCfm(farm: FarmEntity): Double = farm.fanRatedCfm * (1.0 - farm.fanDerate)
    fun crossFt2(farm: FarmEntity): Double = max(1.0, farm.usableWidthFt * farm.heightFt)

    /**
     * Fans allowed at this age: the young-bird air-speed cap plus 2. The top levels only switch on
     * when the house is hot, so the extra fans help in summer and never run in cool weather.
     */
    fun maxFans(day: Int, farm: FarmEntity): Int {
        val byAge = (PhysiologicalEngine.maxAirSpeedFpm(day) * crossFt2(farm) / max(1.0, fanCfm(farm))).roundToInt()
        return (byAge + 2).coerceIn(1, max(1, farm.fanCount))
    }

    fun dayPlan(day: Int, bwG: Double, birds: Int, farm: FarmEntity): DayPlan {
        val levels = ladder(farm.fanCount)
        val acc = accu(levels)
        val eff = fanCfm(farm)
        val cross = crossFt2(farm)
        val kg = bwG / 1000.0
        val need = PhysiologicalEngine.designMinVentCfmPerBird(kg, farm.minVentFactor) * birds
        var mn = levels.indexOfFirst { it.avgCfm(eff) >= need * 0.97 }
        if (mn < 0) mn = levels.size - 1
        val mf = maxFans(day, farm)
        var mx = 0
        levels.forEachIndexed { i, l ->
            if ((l.cyc.isEmpty() && l.cont.size <= mf) || (l.cont.isEmpty() && l.cyc.size <= mf)) mx = i
        }
        mx = max(mx, mn)
        val comfort = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_TEMP_BY_BW, bwG)
        val target = comfort + levelChill(levels[mn], day, comfort, eff, cross)
        val set = r1(target - acc[mn])
        val heat = min(r1(target - PhysiologicalEngine.heatOnOffsetC(day)), r1(set - 0.2))
        // SAFE (sensor-failure fallback) = MIN: run the minimum-ventilation rate, not a guessed
        // temperature level — as the farm's own controller sheet already does.
        val safe = mn + 1
        return DayPlan(
            day = day, levels = levels, accu = acc, minLevel = mn + 1, maxLevel = mx + 1, safeLevel = safe,
            set = set, heat = heat, vent = r1(set + acc[0]), high = r1(max(target + 4, 33.0)), low = r1(heat - 2),
            target = r1(target), comfort = comfort, needCfm = need, maxFans = mf, fanCfm = eff, crossFt2 = cross
        )
    }

    private fun r1(v: Double) = (v * 10).roundToInt() / 10.0

    // ---------------- house balance ----------------
    private fun ws(t: Double): Double { val p = 0.61094 * exp(17.625 * t / (t + 243.04)); return 0.622 * p / (101.325 - p) }
    private fun wFromTwb(t: Double, twb: Double) = ((2501 - 2.326 * twb) * ws(twb) - 1.006 * (t - twb)) / (2501 + 1.86 * t - 4.186 * twb)
    private fun rhOf(t: Double, w: Double): Double {
        val p = w * 101.325 / (0.622 + w); val ps = 0.61094 * exp(17.625 * t / (t + 243.04))
        return (100 * p / ps).coerceIn(5.0, 100.0)
    }
    private fun fs(t: Double) = (0.61 * (1 + 0.02 * (20 - t)) - 0.000228 * t * t).coerceIn(0.15, 0.75)
    private const val K = 0.5698 // W per K per cfm of airflow

    data class HouseState(
        val level: Int,         // 1-based level the controller sits at
        val frac: Double,       // share of time spent one level higher (switching)
        val fans: Double,       // time-averaged fans running
        val houseC: Double,
        val houseRh: Double,
        val feltC: Double,
        val chillC: Double,
        val heaterKw: Double,
        val padEff: Double,     // 0 = pads off
        val setOffset: Double,  // humidity compensation applied to SET/HEAT
        val comfort: Double
    )

    /**
     * Steady house state for an outside temperature/RH at an hour of the day.
     * [rhCompensation] shifts SET by the bird's humidity feel (auto RH compensation).
     */
    fun simulate(
        plan: DayPlan, outC: Double, outRh: Double, hour: Int, birds: Int, bwG: Double,
        farm: FarmEntity, rhCompensation: Boolean = true
    ): HouseState {
        if (!rhCompensation) return core(plan, outC, outRh, hour, birds, bwG, farm, 0.0)
        var off = 0.0
        var st = core(plan, outC, outRh, hour, birds, bwG, farm, off)
        repeat(4) {
            off = (-rhOffsetC(st.houseRh)).coerceIn(if (plan.day <= 10) -2.0 else -3.5, 4.0)
            st = core(plan, outC, outRh, hour, birds, bwG, farm, off)
        }
        return st
    }

    private fun core(
        plan: DayPlan, outC: Double, outRh: Double, hour: Int, birds: Int, bwG: Double,
        farm: FarmEntity, off: Double
    ): HouseState {
        val L = plan.levels
        val st = plan.accu.map { plan.set + off + it }
        val mn = plan.minLevel - 1
        val mx = plan.maxLevel - 1
        val heatSet = plan.heat + off
        val twb = PhysiologicalEngine.wetBulb(outC, outRh)
        val wo = wFromTwb(outC, twb)
        val q = birds * 10.62 * Math.pow(max(bwG, 30.0) / 1000.0, 0.75)
        val floorFt2 = max(1.0, farm.lengthFt * farm.widthFt)
        val ua = 0.1758 * floorFt2 * min(1.0, 0.3 + 0.7 * plan.day / 11.0)
        val sol = 2.34 * floorFt2 * max(0.0, sin(PI * (hour - 6.5) / 13))
        val heatCap = max(0.0, farm.heaterCount * farm.heaterKw * 1000.0)
        val eff = plan.fanCfm

        fun th(c: Double, tin: Double, extra: Double = 0.0): Double {
            var t = tin
            repeat(40) { t = (q * fs(t) + sol + extra + K * c * tin + ua * outC) / (K * c + ua) }
            return t
        }
        fun mix(i: Int, f: Double) = L[i].avgCfm(eff) * (1 - f) + L[i + 1].avgCfm(eff) * f
        data class R(val i: Int, val f: Double, val t: Double, val c: Double)
        fun solve(tin: Double): R {
            val ts = L.map { th(it.avgCfm(eff), tin) }
            for (i in mn..mx) if ((i == mn || st[i] <= ts[i]) && (i == mx || ts[i] < st[i + 1])) return R(i, 0.0, ts[i], L[i].avgCfm(eff))
            for (i in mn until mx) if (ts[i] >= st[i + 1] && ts[i + 1] < st[i + 1]) {
                var lo = 0.0; var hi = 1.0
                repeat(40) { val f = (lo + hi) / 2; if (th(mix(i, f), tin) > st[i + 1]) lo = f else hi = f }
                val f = (lo + hi) / 2
                return R(i, f, st[i + 1], mix(i, f))
            }
            return R(mx, 0.0, ts[mx], L[mx].avgCfm(eff))
        }
        fun rhHouse(t: Double, c: Double, wi: Double) = rhOf(t, wi + q * (1 - fs(t)) / 2.45e6 / (max(c, 300.0) * 0.000471947 * 1.15))

        var r = solve(outC)
        var heat = 0.0
        var win = wo
        var pad = 0.0
        if (r.i == mn && r.f == 0.0 && r.t < heatSet) {
            var t = heatSet
            var need = K * r.c * (t - outC) + ua * (t - outC) - q * fs(t) - sol
            if (need > heatCap) { t = th(r.c, outC, heatCap); need = heatCap }
            r = r.copy(t = t); heat = max(0.0, need)
        }
        if (farm.hasEC && plan.day >= 14 && r.i == mx && r.t > plan.set + off + 6.5) {
            val tgt = plan.set + off + 6.5
            var best = Triple(0.0, r.t, wo)
            var k = 0
            while (k <= 75) {
                val e = k / 100.0
                val tin = outC - e * (outC - twb)
                val wi = wFromTwb(tin, twb)
                val t = th(L[mx].avgCfm(eff), tin)
                best = Triple(e, t, wi)
                if (t <= tgt) break
                k += 3
            }
            if (best.first > 0 && rhHouse(best.second, L[mx].avgCfm(eff), best.third) <= 80) {
                r = r.copy(t = best.second); win = best.third; pad = best.first
            }
        }
        val rh = rhHouse(r.t, r.c, win)
        val cross = plan.crossFt2
        val ch = levelChill(L[r.i], plan.day, r.t, eff, cross) * (1 - r.f) +
            (if (r.f > 0) levelChill(L[r.i + 1], plan.day, r.t, eff, cross) * r.f else 0.0)
        val fans = L[r.i].avgFans * (1 - r.f) + (if (r.f > 0) L[r.i + 1].avgFans * r.f else 0.0)
        return HouseState(
            level = r.i + 1, frac = r.f, fans = fans, houseC = r.t, houseRh = rh,
            feltC = r.t + rhOffsetC(rh) - ch, chillC = ch, heaterKw = heat / 1000.0, padEff = pad,
            setOffset = off, comfort = plan.comfort
        )
    }
}
