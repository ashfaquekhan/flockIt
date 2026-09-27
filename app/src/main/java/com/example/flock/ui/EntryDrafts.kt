package com.example.flock.ui

import android.content.Context
import org.json.JSONObject

/**
 * Unsaved text in the day-entry form, kept on the phone per farm + flock + day, so figures typed
 * while waiting (e.g. for the mortality count) survive leaving and reopening the app. Cleared when
 * the day is saved. Never synced — the Google Sheet only ever receives saved values.
 */
object EntryDrafts {
    private const val PREFS = "entry_drafts"

    fun key(spreadsheetId: String, flockId: String, day: Int) = "$spreadsheetId|$flockId|$day"

    fun load(context: Context, key: String): Map<String, String> = try {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null)
        if (raw.isNullOrBlank()) emptyMap() else {
            val o = JSONObject(raw)
            o.keys().asSequence().associateWith { o.optString(it, "") }
        }
    } catch (e: Exception) { emptyMap() }

    fun save(context: Context, key: String, values: Map<String, String>) {
        val o = JSONObject()
        values.forEach { (k, v) -> if (v.isNotEmpty()) o.put(k, v) }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (o.length() == 0) prefs.remove(key) else prefs.putString(key, o.toString())
        prefs.apply()
    }

    fun clear(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key).apply()
    }
}
