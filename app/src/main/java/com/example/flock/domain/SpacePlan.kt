package com.example.flock.domain

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The birds' floor, pans and feedings, from first principles — for a full house, and for one half empty after lifting
 * (7,000 birds left in a 15,000-bird house) or one whose feed bags are few. Pure Kotlin, no Android.
 *
 * What it rests on:
 *  1. Floor. A bird needs floor for its weight: 30 kg/m² on a hot day (32 °C or more outside), 34 on a warm one
 *     (27 °C or more), else the farm's cap. Space sheds heat, so on a warm day the birds keep the whole house; on a cool
 *     day a roomy floor (more than 1.6 × what they need) can be penned with the barricade — less floor to keep warm, the
 *     birds stay near feed, water and heat.
 *  2. Pans. A pan serves 45–80 grown birds (Ross). With far fewer (under [FEW_BIRDS_PER_PAN]) the feed in it goes
 *     stale and the pans never empty, so pans are shut in a repeating series (1 on · 2 off …) — never so many that a
 *     bird has more than [MAX_WALK_M] to walk to an open pan. Pans past a barricade are shut too: the auger fills
 *     them whether or not a bird can reach them.
 *  3. Feedings. A line pour fills the open pans from the hopper outward: one bag fills ([In.pansPerLine] ÷
 *     [In.bagsFullLine]) open pans. Each feeding must reach the last open pan, so it pours at least one fill of the
 *     open pans; what it pours beyond that waits in the hopper and tops the pans up. The farm's feedings for the age
 *     ([In.feedingsWanted]) are kept while each still reaches the last pan, else fewer; when even one can't, more
 *     pans are shut. Fewer open pans → a smaller fill.
 */
object SpacePlan {
    const val MAX_WALK_M = 2.0
    const val FT2_PER_M2 = 10.7639
    /** fewer birds than this on a pan and the feed in it goes stale (half of Ross's 45) */
    const val FEW_BIRDS_PER_PAN = 25.0
    /** the pan load the series aims for (Ross's lower figure) */
    const val AIM_BIRDS_PER_PAN = 45.0

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
        val lifted: Boolean,
        /** the day's bags, the bags that fill one whole line from empty, the most birds a pan serves at this size */
        val dayBags: Double = 0.0, val bagsFullLine: Double = 3.0, val birdsPerPanMax: Double = 80.0,
        /** the feedings a day the farm gives at this age */
        val feedingsWanted: Int = 2
    )

    data class Option(
        val key: String, val title: String, val penFt: Double, val areaFt2: Double, val kgM2: Double, val ft2PerBird: Double,
        val openPans: Int, val birdsPerPan: Double, val walkM: Double,
        /** the pan series (on, off) in the floor used, and the pans per line shut past the barricade */
        val on: Int = 1, val off: Int = 0, val pansInPen: Int = 0, val shutPastPen: Int = 0,
        /** bags that fill every open pan once (all lines), feedings a day, bags a feeding, bags a line a feeding */
        val fillBags: Double = 0.0, val feedings: Int = 2, val bagsPerFeeding: Double = 0.0, val bagsPerLine: Double = 0.0,
        /** true when each feeding reaches the last open pan */
        val feedReaches: Boolean = true
    ) {
        val openPerLine get() = if (on + off == 0) 0 else (0 until pansInPen).count { it % (on + off) < on }
        val series get() = if (off == 0) "all on" else "$on on · $off off"
    }

    data class Out(
        val kgM2: Double, val birdsM2: Double, val ft2PerBird: Double,
        /** the density the birds are comfortable at today (kg/m²) and the floor a bird needs for it (ft²) */
        val targetKgM2: Double, val needFt2: Double,
        val roomRatio: Double, val room: Room,
        val walkM: Double,
        val options: List<Option>, val best: Int, val why: String,
        /** a young flock (under 3 weeks, nothing lifted): it grows into the floor, so "roomy" and the ways to set the floor do not apply */
        val young: Boolean = false,
        /** the plan in three short steps: floor, pans, feed */
        val steps: List<String> = emptyList()
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

    private val SERIES = listOf(1 to 0, 4 to 1, 3 to 1, 2 to 1, 3 to 2, 1 to 1, 2 to 3, 1 to 2, 1 to 3)

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
        val lines = max(1, i.feederLines)
        val pansPerBag = max(1, i.pansPerLine) / max(0.1, i.bagsFullLine)
        fun pansUpTo(penFt: Double) = floor((penFt - i.lineStartFt) / i.panSpacingFt).toInt().coerceIn(1, max(1, i.pansPerLine))

        /** One way to set the floor: the barricade at [penFt] and the series ([on], [off]) in it, with its feedings. */
        fun option(key: String, title: String, penFt: Double, on: Int, off: Int): Option {
            val inPen = if (penFt >= i.houseLenFt - 0.5) max(1, i.pansPerLine) else pansUpTo(penFt)
            val perLine = (0 until inPen).count { it % (on + off) < on }
            val open = max(1, lines * perLine)
            val a = (if (penFt >= i.houseLenFt - 0.5) i.houseLenFt else penFt) * i.widthFt
            val fill = open / pansPerBag
            // the farm's feedings while each pour still reaches the last open pan, else fewer
            val wanted = max(1, i.feedingsWanted)
            val feedings = (wanted downTo 1).firstOrNull { i.dayBags / it >= fill * 0.97 } ?: 1
            return Option(key, title, penFt, a, live * i.avgKg / (a / FT2_PER_M2), a / live, open, live.toDouble() / open,
                walk(i.feederGapFt, (off + 1) * i.panSpacingFt, i.drinkerGapFt), on, off, inPen, max(0, i.pansPerLine - inPen),
                fill, feedings, i.dayBags / feedings, i.dayBags / feedings / lines, i.dayBags <= 0 || i.dayBags / feedings >= fill * 0.97)
        }
        /** the series for a floor: pans nearest [AIM_BIRDS_PER_PAN] birds each, the walk within 2 m, the pans not too full */
        fun seriesFor(penFt: Double): Pair<Int, Int>? = SERIES.drop(1).map { (on, off) -> option("x", "", penFt, on, off) }
            .filter { it.walkM <= MAX_WALK_M && it.birdsPerPan <= i.birdsPerPanMax && it.feedReaches }
            .minByOrNull { abs(it.birdsPerPan - AIM_BIRDS_PER_PAN) }?.let { it.on to it.off }

        val opts = mutableListOf<Option>()
        // A: the whole house, every pan open
        opts += option("house", "Whole house, every pan", i.houseLenFt, 1, 0)
        val allPans = opts[0].openPans
        val few = live.toDouble() / allPans < FEW_BIRDS_PER_PAN
        // B: the whole house with some pans shut
        if (few && !young) seriesFor(i.houseLenFt)?.let { (on, off) ->
            opts += option("pans", "Whole house, $on pan on · $off off", i.houseLenFt, on, off)
        }
        // C: the barricade where the birds have a comfortable floor (34 kg/m² at most; 5 ft steps; never under 40 % of the house)
        val penTarget = min(target, 34.0)
        val penNeed = ceil(live * i.avgKg / penTarget * FT2_PER_M2 / max(1.0, i.widthFt) / 5.0) * 5.0
        val pen = penNeed.coerceIn(i.houseLenFt * 0.4, i.houseLenFt)
        if (pen < i.houseLenFt - 10 && !young) {
            val inPen = pansUpTo(pen)
            val penFew = live.toDouble() / (lines * inPen) < FEW_BIRDS_PER_PAN
            val (on, off) = if (penFew) seriesFor(pen) ?: (1 to 0) else 1 to 0
            opts += option("pen", "Barricade at ${pen.toInt()} ft" + if (off > 0) ", $on on · $off off" else "", pen, on, off)
        }

        val idx = { k: String -> opts.indexOfFirst { it.key == k }.takeIf { it >= 0 } }
        val (best, why) = when {
            room == Room.TIGHT && i.penFt != null -> (idx("house") ?: 0) to "Too tight: open the barricade (to the whole house)."
            room == Room.TIGHT -> (idx("house") ?: 0) to "Too tight: the birds need more floor than the house gives — lift or thin soon."
            brooding -> (idx("house") ?: 0) to "Brooding: open the floor day by day as planned."
            young -> (idx("house") ?: 0) to "Growing birds: they grow into the floor — nothing to change."
            i.hotC >= 30 && few && idx("pans") != null -> idx("pans")!! to
                "Warm day: keep the whole house (space sheds heat) and shut some pans, so the feed stays fresh and the pans empty between feedings."
            i.hotC >= 30 -> (idx("house") ?: 0) to "Warm day: keep the whole house — space sheds heat."
            room == Room.ROOMY && i.coolC < 20 && idx("pen") != null -> idx("pen")!! to
                "Cool and roomy: pen the birds — a smaller floor keeps warm, the birds stay near feed, water and heat."
            room == Room.ROOMY && few && idx("pans") != null -> idx("pans")!! to
                "Roomy: keep the floor, shut some pans so each open one has birds enough to empty it."
            few && idx("pans") != null -> idx("pans")!! to "Few birds a pan: shut some pans so each open one is emptied."
            else -> (idx("house") ?: 0) to "The floor is right for the birds: nothing to change."
        }
        return Out(kgM2, live / (area / FT2_PER_M2), ft2PerBird, target, need, ratio, room, opts[0].walkM, opts, best, why, young,
            if (young) emptyList() else steps(opts[best], i))
    }

    private fun n1(x: Double) = String.format(java.util.Locale.US, "%.1f", x)
    private fun n2(x: Double) = String.format(java.util.Locale.US, "%.2f", x)

    /** The chosen way, in three steps the farm can follow: floor, pans, feed. */
    private fun steps(o: Option, i: In): List<String> {
        val pen = o.penFt < i.houseLenFt - 0.5
        val floor = if (pen) "Floor: barricade at ${o.penFt.toInt()} ft from the front — ${n1(o.kgM2)} kg/m², ${n2(o.ft2PerBird)} ft² a bird."
            else "Floor: the whole house — ${n1(o.kgM2)} kg/m², ${n2(o.ft2PerBird)} ft² a bird."
        val pans = (if (pen) "Pans: shut the ${o.shutPastPen} past the barricade on every line; in the pen " else "Pans: ") +
            "${o.series} — ${o.openPerLine} open a line, ${n1(o.birdsPerPan)} birds a pan, the furthest walk ${n2(o.walkM)} m."
        val feed = if (i.dayBags <= 0) "Feed: nothing planned today."
            else if (!o.feedReaches) "Feed: ${n1(i.dayBags)} bags can't fill these ${o.openPans} pans even once (${n1(o.fillBags)} bags a fill) — shut more pans."
            else "Feed: ${o.feedings} feeding${if (o.feedings > 1) "s" else ""} of ${n1(o.bagsPerFeeding)} bags (${n2(o.bagsPerLine)} a line) — each reaches the last open pan " +
                "(${n1(o.fillBags)} bags fill them)" + if (o.bagsPerFeeding > o.fillBags * 1.15) "; the rest waits in the hoppers and tops the pans up." else "; the birds clear the pans between feedings."
        return listOf(floor, pans, feed)
    }
}
