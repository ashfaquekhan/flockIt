package com.example.flock.ui

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Number formatting used across the app: every measured or computed value is shown as a decimal
 * with a fixed number of places (at least one), with thousands separators — never rounded to an
 * integer. Only head counts (birds) use [i].
 */
object Fmt {
    private val sym = DecimalFormatSymbols(Locale.US)
    private val cache = HashMap<Int, DecimalFormat>()

    private fun df(dec: Int): DecimalFormat =
        cache.getOrPut(dec) { DecimalFormat("#,##0." + "0".repeat(dec), sym) }

    /** 23.3666 → "23.37", 24.0 → "24.00", n(v, 0) → "24.0", null → "—". */
    fun n(v: Double?, dec: Int = 2): String =
        if (v == null || v.isNaN() || v.isInfinite()) "—" else df(dec.coerceAtLeast(1)).format(v)

    /** Whole counts (birds, heads) only. */
    fun i(v: Int?): String = if (v == null) "—" else DecimalFormat("#,##0", sym).format(v)

    fun pct(v: Double?, dec: Int = 2): String = if (v == null) "—" else n(v, dec) + "%"

    /** Signed difference: "+1.25", "−0.50", "0.00". */
    fun signed(v: Double?, dec: Int = 2): String = when {
        v == null || v.isNaN() -> "—"
        v > 0 -> "+" + n(v, dec)
        v < 0 -> "−" + n(-v, dec)
        else -> n(0.0, dec)
    }
}
