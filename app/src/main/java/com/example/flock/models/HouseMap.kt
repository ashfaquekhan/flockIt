package com.example.flock.models

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The house floor, place by place (no sensors yet: a model, shown as projected) — the litter's moisture, the air's
 * humidity, the stocking density and the heat the birds give off — from first principles:
 *
 *  - Heat: a bird gives off 10.62 × kg^0.75 W ([sensible] of it as heat, the rest as moisture breathed out).
 *  - Air along the house: in tunnel air every m³ moved picks up the birds' heat and moisture as it goes, so the air
 *    warms by heat ÷ (ρ·cp·airflow) from the inlet end to the fans and gets wetter by moisture ÷ (ρ·airflow); with
 *    minimum ventilation the air mixes and only a [mix] share of that shows. The house's own average (the house model)
 *    stays the average of the floor.
 *  - Birds: spread over the floor between the barricades, more near the feeder and drinker lines, and on a warm day
 *    away from the warmer end ([migration] a °C over comfort).
 *  - Litter: the litter model's average for the house, wetter under the drinker lines (a bump [drinkerWet] points high,
 *    [drinkerSigmaM] wide), where birds crowd ([crowdWet] points for twice the average), and where the air is more
 *    humid; outside the barricades the floor has no droppings and stays drier.
 *  - Sensors: where there is a reading (temperature, humidity, litter moisture), the field is pulled to it nearby and
 *    the model's own estimate is kept further away (inverse-distance blend).
 * Pure Kotlin, no Android. Weights from [ModelParams] (model id "house_map").
 */
object HouseMap {
    const val VERSION = 1
    const val ID = "house_map"

    enum class Layer(val label: String, val unit: String, val decimals: Int) {
        LITTER("Litter", "% moisture", 1), HUMIDITY("Humidity", "% RH", 1), DENSITY("Density", "kg/m²", 1), HEAT("Heat", "W/m²", 0)
    }

    data class In(
        val lengthFt: Double, val widthFt: Double, val penStartFt: Double, val penEndFt: Double,
        /** across the house, ft from the side wall: the drinker lines and the feeder lines */
        val drinkerYFt: List<Double>, val feederYFt: List<Double>,
        val live: Int, val avgKg: Double,
        /** the house air at the hour (its average), the air moved (cfm), whether it runs end to end (tunnel) */
        val houseC: Double, val houseRh: Double, val airCfm: Double, val tunnel: Boolean,
        val comfortC: Double,
        /** the litter model's moisture for the house as a whole (%) */
        val litterMoistPct: Double,
        /** what is comfortable and the most: kg/m², and the air's humidity */
        val densityIdeal: Double, val densityMost: Double, val rhIdeal: Double, val rhMost: Double,
        val sources: Sources = Sources(null, null),
        val params: ModelParams = ModelParams.DEFAULTS
    )

    /** Where a layer's colour turns: up to [ideal] it is fine, to [most] past the ideal, beyond it past the most. */
    data class Limits(val ideal: Double, val most: Double)

    private const val M_PER_FT = 0.3048
    private const val RHO_CP = 1.2 * 1006.0          // J per m³ per K
    private const val CFM_TO_M3S = 0.000471947

    fun field(i: In): Field = Field(i)

    class Field internal constructor(val i: In) {
        private fun p(key: String, d: Double) = i.params.get(ID, key, d)
        private val sensible = p("sensible", 0.6)
        private val mix = p("mix", 0.2)
        private val migration = p("migration", 0.35)
        private val lineAttraction = p("line_attraction", 0.25)
        private val drinkerWet = p("drinker_wet_pts", 7.0)
        private val drinkerSigmaM = p("drinker_sigma_m", 0.6)
        private val crowdWet = p("crowd_wet_pts", 3.0)
        private val humidWet = p("humid_wet_pts_per_5", 1.0)
        private val fansAtBack = p("fans_at_back", 1.0) > 0.5

        val start = i.penStartFt.coerceIn(0.0, i.lengthFt)
        val end = i.penEndFt.coerceIn(start + 1.0, i.lengthFt)
        private val penM2 = (end - start) * i.widthFt * M_PER_FT * M_PER_FT
        val heatPerBirdW = 10.62 * max(0.04, i.avgKg).pow(0.75)
        private val flowM3s = max(0.5, i.airCfm * CFM_TO_M3S)
        /** how much the air warms (°C) and wets (kg water a kg of air) from one end of the birds' floor to the other */
        val riseC = i.live * heatPerBirdW * sensible / (RHO_CP * flowM3s) * (if (i.tunnel) 1.0 else mix)
        private val riseW = i.live * heatPerBirdW * (1 - sensible) / 2.45e6 / (1.2 * flowM3s) * (if (i.tunnel) 1.0 else mix)

        /** 0 at the inlet end of the birds' floor, 1 at the fan end */
        private fun along(xFt: Double): Double {
            val f = ((xFt - start) / (end - start)).coerceIn(0.0, 1.0)
            return if (fansAtBack) f else 1 - f
        }

        private fun psat(c: Double) = 610.94 * exp(17.625 * c / (c + 243.04))
        private fun wOf(c: Double, rh: Double): Double { val pv = rh / 100 * psat(c); return 0.622 * pv / (101325 - pv) }
        private fun rhOf(c: Double, w: Double) = (w * 101325 / (0.622 + w) / psat(c) * 100).coerceIn(5.0, 100.0)
        private val wMid = wOf(i.houseC, i.houseRh)

        fun airC(xFt: Double): Double = blend(SensorReading.Kind.AIR_C, xFt, i.widthFt / 2, i.houseC + riseC * (along(xFt) - 0.5))
        fun rh(xFt: Double): Double {
            val c = i.houseC + riseC * (along(xFt) - 0.5)
            return blend(SensorReading.Kind.RH_PCT, xFt, i.widthFt / 2, rhOf(c, wMid + riseW * (along(xFt) - 0.5)))
        }

        private fun nearLine(yFt: Double, lines: List<Double>): Double =
            lines.maxOfOrNull { exp(-((yFt - it) * M_PER_FT).pow(2) / (2 * 0.5 * 0.5)) } ?: 0.0
        /** how much birds favour a spot (1 = the average spot), before evening out over the floor */
        private fun pull(xFt: Double, yFt: Double): Double {
            val hot = ((i.houseC - i.comfortC) / 3.0).coerceIn(0.0, 1.0)
            val tempPull = exp(-migration * hot * (airC(xFt) - i.houseC))
            return (1 + lineAttraction * (nearLine(yFt, i.feederYFt) + nearLine(yFt, i.drinkerYFt))) * tempPull
        }
        private val pullMean: Double = run {
            var s = 0.0; var n = 0
            for (a in 0 until 24) for (b in 0 until 12) { s += pull(start + (a + 0.5) / 24 * (end - start), (b + 0.5) / 12 * i.widthFt); n++ }
            max(1e-6, s / n)
        }
        private fun inPen(xFt: Double) = xFt in start..end

        fun birdsM2(xFt: Double, yFt: Double): Double = if (!inPen(xFt)) 0.0 else i.live / max(1.0, penM2) * pull(xFt, yFt) / pullMean
        fun kgM2(xFt: Double, yFt: Double): Double = birdsM2(xFt, yFt) * i.avgKg
        fun heatWm2(xFt: Double, yFt: Double): Double = birdsM2(xFt, yFt) * heatPerBirdW

        private fun litterRaw(xFt: Double, yFt: Double): Double {
            val drink = i.drinkerYFt.maxOfOrNull { exp(-((yFt - it) * M_PER_FT).pow(2) / (2 * drinkerSigmaM * drinkerSigmaM)) } ?: 0.0
            val crowd = pull(xFt, yFt) / pullMean - 1
            return drinkerWet * drink + crowdWet * crowd + humidWet * (rh(xFt) - i.houseRh) / 5
        }
        // the litter model's figure stays the average of the birds' floor
        private val litterShift: Double = run {
            var s = 0.0; var n = 0
            for (a in 0 until 24) for (b in 0 until 24) { s += litterRaw(start + (a + 0.5) / 24 * (end - start), (b + 0.5) / 24 * i.widthFt); n++ }
            s / max(1, n)
        }
        fun litterMoist(xFt: Double, yFt: Double): Double {
            val v = if (inPen(xFt)) i.litterMoistPct + litterRaw(xFt, yFt) - litterShift else min(i.litterMoistPct, 15.0) * 0.9
            return blend(SensorReading.Kind.LITTER_MOIST_PCT, xFt, yFt, v).coerceIn(8.0, 60.0)
        }

        /** a reading near the spot pulls the model's value to it; far from every reading the model's value stays */
        private fun blend(kind: SensorReading.Kind, xFt: Double, yFt: Double, model: Double): Double {
            val rs = i.sources.readings(kind)
            if (rs.isEmpty()) return model
            var wSum = 0.0; var dSum = 0.0
            rs.forEach { r ->
                val d2 = ((r.xFt - xFt) * M_PER_FT).pow(2) + ((r.yFt - yFt) * M_PER_FT).pow(2)
                val w = 1.0 / (d2 + 1.0)
                wSum += w; dSum += w * (r.value - model)
            }
            return model + dSum / wSum * (wSum / (wSum + 0.05))
        }

        fun value(layer: Layer, xFt: Double, yFt: Double): Double = when (layer) {
            Layer.LITTER -> litterMoist(xFt, yFt)
            Layer.HUMIDITY -> rh(xFt)
            Layer.DENSITY -> kgM2(xFt, yFt)
            Layer.HEAT -> heatWm2(xFt, yFt)
        }

        fun limits(layer: Layer): Limits = when (layer) {
            Layer.LITTER -> Limits(com.example.flock.domain.Litter.IDEAL_MOIST_HI, com.example.flock.domain.Litter.MOIST_MAX)
            Layer.HUMIDITY -> Limits(i.rhIdeal, i.rhMost)
            Layer.DENSITY -> Limits(i.densityIdeal, i.densityMost)
            Layer.HEAT -> Limits(i.densityIdeal / max(0.04, i.avgKg) * heatPerBirdW, i.densityMost / max(0.04, i.avgKg) * heatPerBirdW)
        }

        /** the layer on a grid of [nx] × [ny] cells over the whole house (for the map), and its range on the birds' floor */
        fun grid(layer: Layer, nx: Int = 60, ny: Int = 12): Array<DoubleArray> =
            Array(nx) { a -> DoubleArray(ny) { b -> value(layer, (a + 0.5) / nx * i.lengthFt, (b + 0.5) / ny * i.widthFt) } }

        fun range(layer: Layer): Pair<Double, Double> {
            var lo = Double.MAX_VALUE; var hi = -Double.MAX_VALUE
            for (a in 0 until 30) for (b in 0 until 12) {
                val v = value(layer, start + (a + 0.5) / 30 * (end - start), (b + 0.5) / 12 * i.widthFt)
                lo = min(lo, v); hi = max(hi, v)
            }
            return lo to hi
        }

        /** the inputs and a summary of the outputs, for the model log */
        fun record(flockId: String, day: Int, atMs: Long): ModelRecord = ModelRecord(ID, VERSION, i.params.version, flockId, day, atMs,
            mapOf("lengthFt" to i.lengthFt, "widthFt" to i.widthFt, "penStartFt" to start, "penEndFt" to end, "live" to i.live, "avgKg" to i.avgKg,
                "houseC" to i.houseC, "houseRh" to i.houseRh, "airCfm" to i.airCfm, "tunnel" to i.tunnel, "comfortC" to i.comfortC,
                "litterMoistPct" to i.litterMoistPct, "outsideC" to i.sources.outsideC, "outsideRh" to i.sources.outsideRh, "sensors" to i.sources.sensors.size),
            Layer.values().associate { l -> range(l).let { (lo, hi) -> "${l.name.lowercase()}Range" to listOf(lo, hi) } } +
                mapOf("riseC" to riseC, "heatPerBirdW" to heatPerBirdW))
    }
}
