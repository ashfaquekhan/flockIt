package com.example.flock.ui.screens

import androidx.compose.ui.graphics.Color
import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FarmEntity
import com.example.flock.data.FeedTypeEntity
import com.example.flock.data.parseFeedBreakdown
import com.example.flock.engine.CompanyStandard
import java.time.LocalTime
import java.time.ZoneId

// Shared day maths for the Output topics.

/** Company daily mortality standard (% of live birds per day). */
fun stdDailyMortPct(day: Int): Double = CompanyStandard.dailyMortPct(day)
fun stdCumMortPct(day: Int): Double = CompanyStandard.cumMortPct(day)

/** Feed used logged on a day row, per type code → bags. */
fun usedByType(r: DailyDataEntity): Map<String, Double> {
    val b = parseFeedBreakdown(r.feedUsedBreakdown)
    if (b.isNotEmpty()) return b.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
    return if (r.feedBagsUsed > 0) mapOf(r.feedUsedType.ifBlank { "B1" } to r.feedBagsUsed) else emptyMap()
}

/** Feed received (delivered) on a day row, per type code → bags. */
fun receivedByType(r: DailyDataEntity): Map<String, Double> {
    val m = HashMap<String, Double>()
    fun add(code: String, bags: Double) { if (bags > 0 && code.isNotBlank()) m[code] = (m[code] ?: 0.0) + bags }
    add(r.feedTypeB1, r.feedRecB1); add(r.feedTypeB2, r.feedRecB2); add(r.feedTypeB3, r.feedRecB3)
    return m
}

/** Distinct, stable colours for feed types (by their order in the farm's feed list). */
private val FEED_COLORS = listOf(
    Color(0xFF46B98C), Color(0xFF5B9BD5), Color(0xFFE0A33A), Color(0xFFB07CC6),
    Color(0xFFE06C75), Color(0xFF4FB3BF), Color(0xFF9CA3AF)
)

fun feedColor(code: String, feedTypes: List<FeedTypeEntity>): Color {
    val idx = feedTypes.sortedBy { it.sortOrder }.indexOfFirst { it.code == code }
    return if (idx >= 0) FEED_COLORS[idx % FEED_COLORS.size] else FEED_COLORS[(code.hashCode() and 0x7fffffff) % FEED_COLORS.size]
}

fun currentHour(farm: FarmEntity): Int = try { LocalTime.now(ZoneId.of(farm.timeZone)).hour } catch (e: Exception) { 12 }
