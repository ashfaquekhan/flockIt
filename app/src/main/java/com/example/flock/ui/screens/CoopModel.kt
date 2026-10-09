package com.example.flock.ui.screens

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/** One feeding poured into the lines: when, and how many bags. */
data class FeedEvent(val t: Long, val bags: Double)

/**
 * Feedings entered on the Output page ("bags + Enter"), kept on this phone per flock. They drive the
 * feeder in the coop animation; the day's feed used is still entered on the Entry tab as before.
 */
object FeedLog {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("flockit_feedlog", Context.MODE_PRIVATE)

    fun load(ctx: Context, flockId: String): List<FeedEvent> =
        (prefs(ctx).getString(flockId, "") ?: "").split(";").mapNotNull { part ->
            val kv = part.split(":"); if (kv.size != 2) return@mapNotNull null
            val t = kv[0].toLongOrNull(); val b = kv[1].toDoubleOrNull()
            if (t != null && b != null && b > 0) FeedEvent(t, b) else null
        }.sortedBy { it.t }

    private fun save(ctx: Context, flockId: String, list: List<FeedEvent>) {
        // keep the last 7 days
        val cutoff = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        prefs(ctx).edit().putString(flockId, list.filter { it.t >= cutoff }.joinToString(";") { "${it.t}:${it.bags}" }).apply()
    }

    fun add(ctx: Context, flockId: String, bags: Double, t: Long = System.currentTimeMillis()): List<FeedEvent> {
        val list = load(ctx, flockId) + FeedEvent(t, bags)
        save(ctx, flockId, list); return list
    }

    fun undoLast(ctx: Context, flockId: String): List<FeedEvent> {
        val list = load(ctx, flockId).dropLast(1)
        save(ctx, flockId, list); return list
    }
}

/** Lighting programme: [lightHours] of light a day, the dark period ending at [darkEndHour] (05:00). */
data class LightProgram(val lightHours: Double, val darkEndHour: Double = 5.0) {
    val darkHours get() = (24.0 - lightHours).coerceIn(0.0, 24.0)
    val darkStartHour get() = ((darkEndHour - darkHours) % 24 + 24) % 24
    /** 0 = dark, 1 = full light, with 20-minute dimming at dusk and dawn. */
    fun level(hour: Double): Double {
        if (darkHours < 0.01) return 1.0
        val sinceDarkStart = ((hour - darkStartHour) % 24 + 24) % 24
        val ramp = 1.0 / 3.0
        return when {
            sinceDarkStart < ramp -> 1.0 - sinceDarkStart / ramp               // dusk
            sinceDarkStart < darkHours -> 0.0                                    // night
            sinceDarkStart < darkHours + ramp -> (sinceDarkStart - darkHours) / ramp // dawn
            else -> 1.0
        }
    }
    fun isLight(hour: Double) = level(hour) > 0.5
}

/** What the feeder and the birds' hunger look like right now. */
data class FeederState(
    val levelKg: Double,        // feed left in the lines and pans
    val capacityKg: Double,     // feed that fills every open pan
    val hoursToEmpty: Double?,  // at the current eating rate (null when empty)
    val emptyForH: Double,      // hours since the pans ran empty (0 while there is feed)
    val givenTodayBags: Double,
    val lastFedAt: Long?,
    val hunger: Double          // 0 fed … 1 very hungry, grows gradually once the pans are empty
) {
    /** Share of the open pans holding feed (feed beyond that waits in the hopper). */
    val fillFrac get() = if (capacityKg > 0) (levelKg / capacityKg).coerceAtMost(1.0) else 0.0
    val hopperKg get() = max(0.0, levelKg - capacityKg)
}

/** The feedings logged in the last day and a half, on a clock where today's midnight (farm time) is 0. */
fun loggedFeedings(events: List<FeedEvent>, now: Long, zone: ZoneId, kgPerBag: Double): List<com.example.flock.domain.FlockNeeds.Feeding> {
    val midnight = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
    return events.filter { it.t <= now && it.t >= now - 36L * 3600_000 }
        .map { com.example.flock.domain.FlockNeeds.Feeding((it.t - midnight) / 3600_000.0, it.bags * kgPerBag) }
}

/**
 * What the farm window shows of the feed: feed in the pans and how hungry the birds are, taken from the
 * likelihood model ([com.example.flock.domain.FlockNeeds]) — no clock time for "empty" is claimed any more.
 */
fun feederState(events: List<FeedEvent>, zone: ZoneId, needs: com.example.flock.domain.FlockNeeds.State, capacityKg: Double): FeederState {
    val today = LocalDate.now(zone)
    val givenToday = events.filter { Instant.ofEpochMilli(it.t).atZone(zone).toLocalDate() == today }.sumOf { it.bags }
    return FeederState(needs.feedLeftKg, capacityKg, null, needs.emptyForH, givenToday, events.lastOrNull()?.t, needs.hungry)
}
