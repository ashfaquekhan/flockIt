package com.example.flock.ui

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Number formatting used across the app: exact values with up to [maxDec] decimals (trailing
 * zeros dropped), thousands separators, never silently rounded to an integer.
 */
object Fmt {
    private val sym = DecimalFormatSymbols(Locale.US)
    private val cache = HashMap<String, DecimalFormat>()

    private fun df(maxDec: Int, minDec: Int): DecimalFormat {
        val key = "$maxDec/$minDec"
        return cache.getOrPut(key) {
            val frac = if (maxDec > 0) "." + "0".repeat(minDec) + "#".repeat((maxDec - minDec).coerceAtLeast(0)) else ""
            DecimalFormat("#,##0$frac", sym)
        }
    }

    /** 23.3666 → "23.37", 24.0 → "24", null → "—". */
    fun n(v: Double?, maxDec: Int = 2, minDec: Int = 0): String =
        if (v == null || v.isNaN() || v.isInfinite()) "—" else df(maxDec, minDec).format(v)

    fun i(v: Int?): String = if (v == null) "—" else df(0, 0).format(v)

    fun pct(v: Double?, maxDec: Int = 2): String = if (v == null) "—" else n(v, maxDec) + "%"

    /** Signed difference: "+1.25", "−0.5", "0". */
    fun signed(v: Double?, maxDec: Int = 2): String = when {
        v == null || v.isNaN() -> "—"
        v > 0 -> "+" + n(v, maxDec)
        v < 0 -> "−" + n(-v, maxDec)
        else -> "0"
    }
}
