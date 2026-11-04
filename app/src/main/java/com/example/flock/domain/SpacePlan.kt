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
        val feedingsWanted: Int = 2,
        /** the front barricade, ft from the front wall (0: none); [penFt] is the back one */
        val penStartFt: Double = 0.0,
        /** bags that fill a line with every pan shut: the tube alone, filled with the pans (0: not counted) */
        val bagsShutLine: Double = 0.0
    )

    data class Option(
        val key: String, val title: String, val penFt: Double, val areaFt2: Double, val kgM2: Double, val ft2PerBird: Double,
        val openPans: Int, val birdsPerPan: Double, val walkM: Double,
        /** the pan series (on, off) in the floor used, and the pans per line shut past the barricade */
        val on: Int = 1, val off: Int = 0, val pansInPen: Int = 0, val shutPastPen: Int = 0,
        /** bags that fill every open pan once (all lines), feedings a day, bags a feeding, bags a line a feeding */
        val fillBags: Double = 0.0, val feedings: Int = 2, val bagsPerFeeding: Double = 0.0, val bagsPerLine: Double = 0.0,
        /** true when each feeding reaches the last open pan */
        val feedReaches: Boolean = true,
        /** where the birds' floor starts (the front barricade, ft from the front wall) */
        val penStartFt: Double = 0.0
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
        // filling a line takes the tube as well as the open pans: tube + (whole line − tube) × open ÷ all pans
        val tube = i.bagsShutLine.coerceIn(0.0, i.bagsFullLine * 0.9)
        val pansPerBag = max(1, i.pansPerLine) / max(0.05, i.bagsFullLine - tube)
        val n = max(1, i.pansPerLine)
        /** the first pan inside a floor starting at [startFt], and the pans up to [endFt] (pans counted from the hopper) */
        fun firstPan(startFt: Double) = if (startFt <= i.lineStartFt) 0 else ceil((startFt - i.lineStartFt) / i.panSpacingFt).toInt().coerceIn(0, n - 1)
        fun pansIn(startFt: Double, endFt: Double): Int {
            val end = if (endFt >= i.houseLenFt - 0.5) n else floor((endFt - i.lineStartFt) / i.panSpacingFt).toInt().coerceIn(1, n)
            return (end - firstPan(startFt)).coerceAtLeast(1)
        }
        fun pansUpTo(penFt: Double) = pansIn(0.0, penFt)

        /** One way to set the floor: from [startFt] to the barricade at [penFt], the series ([on], [off]) in it, its feedings. */
        fun option(key: String, title: String, penFt: Double, on: Int, off: Int, startFt: Double = 0.0): Option {
            val inPen = pansIn(startFt, penFt)
            val perLine = (0 until inPen).count { it % (on + off) < on }
            val open = max(1, lines * perLine)
            val a = ((if (penFt >= i.houseLenFt - 0.5) i.houseLenFt else penFt) - startFt).coerceAtLeast(10.0) * i.widthFt
            val fill = open / pansPerBag + lines * tube
            // the farm's feedings while each pour still reaches the last open pan, else fewer
            val wanted = max(1, i.feedingsWanted)
            val feedings = (wanted downTo 1).firstOrNull { i.dayBags / it >= fill * 0.97 } ?: 1
            return Option(key, title, penFt, a, live * i.avgKg / (a / FT2_PER_M2), a / live, open, live.toDouble() / open,
                walk(i.feederGapFt, (off + 1) * i.panSpacingFt, i.drinkerGapFt), on, off, inPen, max(0, i.pansPerLine - inPen),
                fill, feedings, i.dayBags / feedings, i.dayBags / feedings / lines, i.dayBags <= 0 || i.dayBags / feedings >= fill * 0.97, startFt)
        }
        /** the series for a floor: pans nearest [AIM_BIRDS_PER_PAN] birds each, the walk within 2 m, the pans not too full */
        fun seriesFor(penFt: Double, startFt: Double = 0.0): Pair<Int, Int>? = SERIES.drop(1).map { (on, off) -> option("x", "", penFt, on, off, startFt) }
            .filter { it.walkM <= MAX_WALK_M && it.birdsPerPan <= i.birdsPerPanMax && it.feedReaches }
            .minByOrNull { abs(it.birdsPerPan - AIM_BIRDS_PER_PAN) }?.let { it.on to it.off }

        // v54: one way only — the floor between the barricades the farm sets (the whole house when none is set); the app
        // suggests no barricade of its own and has no modes. On that floor: every pan on, or a series shut when there are
        // few birds a pan (never past a 2 m walk); then the feedings each pour still fills to the last open pan.
        val setEnd = i.penFt ?: i.houseLenFt
        val isSet = i.penStartFt > 0.5 || setEnd < i.houseLenFt - 0.5
        val inFloor = pansIn(i.penStartFt, setEnd)
        val few = live.toDouble() / (lines * inFloor) < FEW_BIRDS_PER_PAN
        val (on, off) = if (few && !young) seriesFor(setEnd, i.penStartFt) ?: (1 to 0) else 1 to 0
        val title = (if (isSet) "Barricades ${i.penStartFt.toInt()}–${setEnd.toInt()} ft" else "Whole house") + if (off > 0) ", $on on · $off off" else ", every pan"
        val opts = listOf(option(if (isSet) "set" else if (off > 0) "pans" else "house", title, setEnd, on, off, i.penStartFt))
        val best = 0
        val why = when {
            room == Room.TIGHT && isSet -> "Too tight: move the barricades out"
            room == Room.TIGHT -> "Too tight: lift or thin soon"
            brooding -> "Brooding: trays, paper and every pan"
            young -> "Growing birds: every pan"
            off > 0 -> "Few birds a pan: shut some pans"
            else -> "Every pan on"
        }
        return Out(kgM2, live / (area / FT2_PER_M2), ft2PerBird, target, need, ratio, room, opts[0].walkM, opts, best, why, young,
            if (young) emptyList() else steps(opts[best], i))
    }

    private fun n1(x: Double) = String.format(java.util.Locale.US, "%.1f", x)
    private fun n2(x: Double) = String.format(java.util.Locale.US, "%.2f", x)

    /** The chosen way, in three short steps the farm can follow: floor, pans, feed. */
    private fun steps(o: Option, i: In): List<String> {
        val pen = o.penFt < i.houseLenFt - 0.5 || o.penStartFt > 0.5
        val floor = (if (o.penStartFt > 0.5) "Barricades at ${o.penStartFt.toInt()} and ${o.penFt.toInt()} ft"
            else if (pen) "Barricade at ${o.penFt.toInt()} ft" else "Whole house") + " · ${n1(o.kgM2)} kg/m²"
        val pans = "${o.series} · ${o.openPerLine} a line" + (if (pen) " · shut ${o.shutPastPen} outside" else "") +
            " · ${n1(o.birdsPerPan)} birds a pan · walk ${n2(o.walkM)} m"
        val feed = if (i.dayBags <= 0) "nothing planned"
            else if (!o.feedReaches) "${n1(i.dayBags)} bags can't fill ${o.openPans} pans · shut more"
            else "${o.feedings} × ${n1(o.bagsPerFeeding)} bags · ${n2(o.bagsPerLine)} a line · ${n1(o.fillBags)} fill the pans"
        return listOf(floor, pans, feed)
    }
}
