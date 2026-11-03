package com.example.flock.domain

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * How far a bird walks to feed and to water, against how far it should. Pure Kotlin, no Android.
 *
 *  - The furthest walk to a row of points (pans or nipples on lines): the bird furthest away stands halfway between two
 *    lines and halfway between two open points on them — hypot(line gap ÷ 2, point gap ÷ 2).
 *  - Trays and chick drinkers spread over the brooding floor: one every (floor ÷ count) m², the furthest bird at the
 *    middle of a square of them — side ÷ √2. The nearer of the two counts.
 *  - Limits by age: chicks within 1 m of feed and water in the first days (Aviagen: no more than 1 m to water in the
 *    first 24 h), 3 m at most for grown birds (the management guides: no more than 3 m to feed or water). In between
 *    they widen with the bird. "Safe" is where every bird eats and drinks without thinking twice; past "most" the
 *    furthest birds eat and drink less.
 */
object Travel {
    data class Limits(val safeM: Double, val maxM: Double)

    fun limits(day: Int): Limits = when {
        day <= 3 -> Limits(1.0, 1.5)
        day <= 10 -> Limits(1.5, 2.5)
        else -> Limits(2.0, 3.0)
    }

    /** the furthest walk to the nearest point: lines [acrossM] apart, points [alongM] apart along each line */
    fun furthest(acrossM: Double, alongM: Double): Double = hypot(acrossM / 2, alongM / 2)

    /** the furthest walk to [count] trays or drinkers spread evenly over [floorM2]; null with none */
    fun spread(floorM2: Double, count: Int): Double? = if (count <= 0 || floorM2 <= 0) null else sqrt(floorM2 / count) / sqrt(2.0)

    /** the nearer of the lines and the spread points */
    fun nearest(lines: Double, spread: Double?): Double = if (spread == null) lines else min(lines, spread)

    enum class Grade { SAFE, MOST, TOO_FAR }

    fun grade(walkM: Double, l: Limits): Grade = when {
        walkM <= l.safeM -> Grade.SAFE
        walkM <= l.maxM -> Grade.MOST
        else -> Grade.TOO_FAR
    }

    /** 100 up to safe, 50 at most, 0 as far again past it */
    fun score(walkM: Double, l: Limits): Double = when {
        walkM <= l.safeM -> 100.0
        else -> max(0.0, 100 - 50 * (walkM - l.safeM) / max(0.01, l.maxM - l.safeM))
    }
}
