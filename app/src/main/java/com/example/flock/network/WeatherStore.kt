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
            val f = part.split('|'); if (f.size < 3) return@mapNotNull null
            val t = f[1].toDoubleOrNull(); val rh = f[2].toDoubleOrNull()
            if (t != null && rh != null && f[0].length >= 16) HourPoint(f[0], t, rh, f.getOrNull(3)?.toIntOrNull(), f.getOrNull(4) != "n") else null
        }

    /** Lays [fresh] over what is stored (newer wins hour by hour), saves it and returns everything, oldest first. */
    fun merge(ctx: Context, lat: Double, lon: Double, fresh: List<HourPoint>): List<HourPoint> {
        if (fresh.isEmpty()) return load(ctx, lat, lon)
        val all = LinkedHashMap<String, HourPoint>()
        load(ctx, lat, lon).forEach { all[it.time] = it }
        fresh.forEach { all[it.time] = it }
        val cutoff = try { java.time.LocalDate.parse(fresh.last().date).minusDays(KEEP_DAYS).toString() } catch (e: Exception) { "" }
        val kept = all.values.filter { it.date >= cutoff }.sortedBy { it.time }
        prefs(ctx).edit().putString(key(lat, lon), kept.joinToString(";") { "${it.time}|${it.tempC}|${it.rhPct}|${it.code ?: ""}|${if (it.isDay) "d" else "n"}" }).apply()
        return kept
    }

    /** The first stored date for a location ("" when nothing is stored). */
    fun firstDate(ctx: Context, lat: Double, lon: Double): String = load(ctx, lat, lon).firstOrNull()?.date ?: ""
}

/** What the phone remembers about how the app is shown (not farm data): the weather switch and what the Output page shows. */
object ViewPrefs {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("flockit_view", Context.MODE_PRIVATE)
    fun weatherOn(ctx: Context): Boolean = prefs(ctx).getBoolean("weather_on", true)
    fun setWeatherOn(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("weather_on", on).apply()
    /** The Output sections hidden on this phone (keys); everything else shows. */
    fun hiddenSections(ctx: Context): Set<String> = prefs(ctx).getString("hidden_sections", null)?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    fun setHiddenSections(ctx: Context, keys: Set<String>) = prefs(ctx).edit().putString("hidden_sections", keys.joinToString(",")).apply()
    /** The numbers in the "At a glance" grid, in order (null: the default set). */
    fun glance(ctx: Context): List<String>? = prefs(ctx).getString("glance", null)?.split(",")?.filter { it.isNotBlank() }
    fun setGlance(ctx: Context, keys: List<String>) = prefs(ctx).edit().putString("glance", keys.joinToString(",")).apply()
    /** The Output sections open (unfolded) on this phone (null: the default set). */
    fun openSections(ctx: Context): Set<String>? = prefs(ctx).getString("open_sections", null)?.split(",")?.filter { it.isNotBlank() }?.toSet()
    fun setOpenSections(ctx: Context, keys: Set<String>) = prefs(ctx).edit().putString("open_sections", keys.joinToString(",")).apply()
}
