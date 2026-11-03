package com.example.flock.domain

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The birds' floor: how tightly they stand, how far they walk to feed and water, and — once birds are being lifted
 * or the house is roomier than the birds need — whether to keep the whole house, pen them with the barricade, or
 * close some feeder pans. Pure Kotlin, no Android.
 *
 * The rules it follows:
 *  - Density is kg of bird on each m² the birds HAVE (the floor up to the barricade), not on the whole house.
 *  - What the birds need: a density they stay comfortable at — 30 kg/m² on a hot day (32 °C or more outside),
 *    34 kg/m² on a warm one (27 °C or more), else the farm's cap (39 kg/m² as the EU maximum for good houses).
 *    Room ratio = floor a bird has ÷ floor it needs: under 0.95 tight, over 1.6 roomy (more space than the birds use:
 *    they spread out, walk further, and chicks wander from the heat).
 *  - Walking: a bird should not have more than [MAX_WALK_M] to the nearest open feeder pan or drinker line (Ross).
 *  - Feeder pans: 45–80 birds a pan for grown birds (Ross); with far fewer, feed sits stale and the pans never
 *    empty — close every other pan rather than pen the birds when it is warm (space sheds heat), pen them when it
 *    is cool (a smaller floor is easier to keep warm and the birds stay near the heaters).
 */
object SpacePlan {
    const val MAX_WALK_M = 2.0
    const val FT2_PER_M2 = 10.7639
    /** fewer birds than this on a pan and the feed in it goes stale (half of Ross's 45) */
    const val FEW_BIRDS_PER_PAN = 25.0

    enum class Room(val label: String) { TIGHT("Tight"), RIGHT("Right"), ROOMY("Roomy") }

    data class In(
        val live: Int, val avgKg: Double, val day: Int,
        val houseLenFt: Double, val widthFt: Double,
        /** the floor the birds have now (ft²) and the barricade, when one is set (ft from the front) */
        val areaFt2: Double, val penFt: Double?,
        val feederLines: Int, val pansPerLine: Int, val panSpacingFt: Double, val lineStartFt: Double,
        /** feeder line to feeder line, and drinker line to drinker line (ft) */
        val feederGapFt: Double, val drinkerGapFt: Double,
        val capKgM2: Double,
        /** the day's hottest and coolest outside temperature (°C) */
        val hotC: Double, val coolC: Double,
        val lifted: Boolean
    )

    data class Option(
        val key: String, val title: String, val penFt: Double, val areaFt2: Double, val kgM2: Double, val ft2PerBird: Double,
        val openPans: Int, val birdsPerPan: Double, val walkM: Double
    )

    data class Out(
        val kgM2: Double, val birdsM2: Double, val ft2PerBird: Double,
        /** the density the birds are comfortable at today (kg/m²) and the floor a bird needs for it (ft²) */
        val targetKgM2: Double, val needFt2: Double,
        val roomRatio: Double, val room: Room,
        val walkM: Double,
        val options: List<Option>, val best: Int, val why: String,
        /** a young flock (under 3 weeks, nothing lifted): it grows into the floor, so "roomy" and the ways to set the floor do not apply */
        val young: Boolean = false
    )

    fun targetKgM2(capKgM2: Double, hotC: Double): Double {
        val cap = if (capKgM2 > 0) capKgM2 else 39.0
        return when {
            hotC >= 32 -> min(cap, 30.0)
            hotC >= 27 -> min(cap, 34.0)
            else -> cap
        }
    }

    private fun walk(feederGapFt: Double, panGapFt: Double, drinkerGapFt: Double): Double =
        max(hypot(feederGapFt / 2, panGapFt / 2), drinkerGapFt / 2) / 3.2808

    fun plan(i: In): Out {
        val live = max(1, i.live)
        val area = max(1.0, i.areaFt2)
        val houseFt2 = max(1.0, i.houseLenFt * i.widthFt)
        val kgM2 = live * i.avgKg / (area / FT2_PER_M2)
        val ft2PerBird = area / live
        val target = targetKgM2(i.capKgM2, i.hotC)
        val need = i.avgKg / target * FT2_PER_M2
        val ratio = ft2PerBird / max(1e-6, need)
        val brooding = i.day < 11 && !i.lifted
        val young = i.day < 21 && !i.lifted
        val room = when {
            ratio < 0.95 -> Room.TIGHT
            ratio > 1.6 && !young -> Room.ROOMY
            else -> Room.RIGHT
        }
        val allPans = max(1, i.feederLines * i.pansPerLine)
        val walkAll = walk(i.feederGapFt, i.panSpacingFt, i.drinkerGapFt)

        val opts = mutableListOf<Option>()
        // A: the whole house, every pan open
        opts += Option("house", "Whole house, every pan", i.houseLenFt, houseFt2, live * i.avgKg / (houseFt2 / FT2_PER_M2), houseFt2 / live,
            allPans, live.toDouble() / allPans, walkAll)
        // B: the barricade where the birds have a comfortable floor (34 kg/m² at most, not the cap; in 5 ft steps, never
        // under 40 % of the house)
        val penTarget = min(target, 34.0)
        val penNeed = ceil(live * i.avgKg / penTarget * FT2_PER_M2 / max(1.0, i.widthFt) / 5.0) * 5.0
        val pen = penNeed.coerceIn(i.houseLenFt * 0.4, i.houseLenFt)
        if (pen < i.houseLenFt - 10 && !young) {
            val pans = max(1, i.feederLines * floor((pen - i.lineStartFt) / i.panSpacingFt).toInt().coerceIn(1, i.pansPerLine))
            val a = pen * i.widthFt
            opts += Option("pen", "Barricade at ${pen.toInt()} ft", pen, a, live * i.avgKg / (a / FT2_PER_M2), a / live, pans, live.toDouble() / pans, walkAll)
        }
        // C: the whole house with some pans shut — the series that keeps the walk short and brings the pans nearest 45 birds
        val shut = listOf(1 to 1, 2 to 1, 1 to 2).map { (on, off) ->
            val open = i.feederLines * ((0 until i.pansPerLine).count { it % (on + off) < on })
            Triple(on to off, open, walk(i.feederGapFt, (off + 1) * i.panSpacingFt, i.drinkerGapFt))
        }.filter { it.third <= MAX_WALK_M && it.second > 0 }
            .minByOrNull { kotlin.math.abs(live.toDouble() / it.second - 45.0) }
        if (shut != null && live.toDouble() / allPans < FEW_BIRDS_PER_PAN && !young) {
            val (pat, open, w) = shut
            opts += Option("pans", "Whole house, ${pat.first} pan on · ${pat.second} off", i.houseLenFt, houseFt2,
                live * i.avgKg / (houseFt2 / FT2_PER_M2), houseFt2 / live, open, live.toDouble() / open, w)
        }

        val idx = { k: String -> opts.indexOfFirst { it.key == k }.takeIf { it >= 0 } }
        val few = live.toDouble() / allPans < FEW_BIRDS_PER_PAN
        val (best, why) = when {
            room == Room.TIGHT && i.penFt != null -> (idx("house") ?: 0) to "Too tight: open the barricade (to the whole house)."
            room == Room.TIGHT -> (idx("house") ?: 0) to "Too tight: the birds need more floor than the house gives — lift or thin soon."
            brooding -> (idx("house") ?: 0) to "Brooding: open the floor day by day as planned."
            young -> (idx("house") ?: 0) to "Growing birds: they grow into the floor — nothing to change."
            i.hotC >= 30 && few && idx("pans") != null -> idx("pans")!! to
                "Warm day: keep the whole house (space sheds heat) and shut some pans, so the feed stays fresh and the pans empty once a day."
            i.hotC >= 30 -> (idx("house") ?: 0) to "Warm day: keep the whole house — space sheds heat."
            room == Room.ROOMY && i.coolC < 20 && idx("pen") != null -> idx("pen")!! to
                "Cool and roomy: pen the birds — a smaller floor keeps warm, the birds stay near feed, water and heat."
            room == Room.ROOMY && few && idx("pans") != null -> idx("pans")!! to
                "Roomy: keep the floor, shut some pans so each open one has birds enough to empty it."
            else -> (idx("house") ?: 0) to "The floor is right for the birds: nothing to change."
        }
        return Out(kgM2, live / (area / FT2_PER_M2), ft2PerBird, target, need, ratio, room, walkAll, opts, best, why, young)
    }
}
