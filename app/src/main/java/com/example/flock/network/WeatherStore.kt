package com.example.flock.network

import android.content.Context

/**
 * The hourly weather at the farm's location, kept on the phone: every refresh adds the newest hours (and the
 * past days the weather service still has), so the days of a running flock stay available — also offline —
 * for the house and bird model. About 80 days are kept, per location.
 */
object WeatherStore {
    private const val KEEP_DAYS = 80L
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("flockit_weather", Context.MODE_PRIVATE)
    private fun key(lat: Double, lon: Double) = String.format(java.util.Locale.US, "%.2f,%.2f", lat, lon)

    fun load(ctx: Context, lat: Double, lon: Double): List<HourPoint> =
        (prefs(ctx).getString(key(lat, lon), "") ?: "").split(';').mapNotNull { part ->
            val f = part.split('|'); if (f.size != 3) return@mapNotNull null
            val t = f[1].toDoubleOrNull(); val rh = f[2].toDoubleOrNull()
            if (t != null && rh != null && f[0].length >= 16) HourPoint(f[0], t, rh) else null
        }

    /** Lays [fresh] over what is stored (newer wins hour by hour), saves it and returns everything, oldest first. */
    fun merge(ctx: Context, lat: Double, lon: Double, fresh: List<HourPoint>): List<HourPoint> {
        if (fresh.isEmpty()) return load(ctx, lat, lon)
        val all = LinkedHashMap<String, HourPoint>()
        load(ctx, lat, lon).forEach { all[it.time] = it }
        fresh.forEach { all[it.time] = it }
        val cutoff = try { java.time.LocalDate.parse(fresh.last().date).minusDays(KEEP_DAYS).toString() } catch (e: Exception) { "" }
        val kept = all.values.filter { it.date >= cutoff }.sortedBy { it.time }
        prefs(ctx).edit().putString(key(lat, lon), kept.joinToString(";") { "${it.time}|${it.tempC}|${it.rhPct}" }).apply()
        return kept
    }

    /** The first stored date for a location ("" when nothing is stored). */
    fun firstDate(ctx: Context, lat: Double, lon: Double): String = load(ctx, lat, lon).firstOrNull()?.date ?: ""
}

/** What the phone remembers about how the app is shown (not farm data): the weather switch and the basic / advanced view. */
object ViewPrefs {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("flockit_view", Context.MODE_PRIVATE)
    fun weatherOn(ctx: Context): Boolean = prefs(ctx).getBoolean("weather_on", true)
    fun setWeatherOn(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("weather_on", on).apply()
    fun basic(ctx: Context): Boolean = prefs(ctx).getBoolean("basic_view", true)
    fun setBasic(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("basic_view", on).apply()
}
