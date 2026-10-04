package com.example.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.example.R

/**
 * The app's typeface: Rubik — soft square corners, like the bars of the mark.
 * (SIL Open Font License 1.1; the licence texts are in design/fonts.)
 */
val AppFont = FontFamily(
    Font(R.font.rubik_regular, FontWeight.Normal), Font(R.font.rubik_medium, FontWeight.Medium),
    Font(R.font.rubik_semibold, FontWeight.SemiBold), Font(R.font.rubik_bold, FontWeight.Bold),
    Font(R.font.rubik_black, FontWeight.Black)
)

/** The face for numbers: JetBrains Mono — every digit the same width, so columns line up. */
val NumberFont = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal), Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold), Font(R.font.jetbrains_mono_extrabold, FontWeight.ExtraBold)
)

/** The same faces for text drawn straight onto a canvas (charts, the clock, the farm window). Loaded once by the theme. */
object AppFonts {
    @Volatile var mono: Typeface = Typeface.MONOSPACE
        private set
    @Volatile var text: Typeface = Typeface.DEFAULT
        private set
    @Volatile private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        try {
            ResourcesCompat.getFont(context, R.font.jetbrains_mono_medium)?.let { mono = it }
            ResourcesCompat.getFont(context, R.font.rubik_medium)?.let { text = it }
        } catch (e: Exception) {
            // the system faces stay in place
        }
        loaded = true
    }
}

private fun ts(size: Int, line: Int, w: FontWeight = FontWeight.Normal, ls: Double = 0.0) =
    TextStyle(fontFamily = AppFont, fontWeight = w, fontSize = size.sp, lineHeight = line.sp, letterSpacing = ls.sp)

/** Material 3 sizes, each one step (≈ 1 sp) larger so values read easily in the shed. Rubik runs a little wide, so the tracking is tighter than Material's. */
val Typography = Typography(
    displayLarge = ts(58, 66), displayMedium = ts(46, 54), displaySmall = ts(37, 45),
    headlineLarge = ts(33, 41), headlineMedium = ts(29, 37), headlineSmall = ts(25, 33),
    titleLarge = ts(23, 29), titleMedium = ts(17, 25, FontWeight.Medium, 0.0), titleSmall = ts(15, 21, FontWeight.Medium, 0.0),
    bodyLarge = ts(17, 25, ls = 0.2), bodyMedium = ts(15, 21, ls = 0.1), bodySmall = ts(13, 17, ls = 0.2),
    labelLarge = ts(15, 21, FontWeight.Medium, 0.0), labelMedium = ts(13, 17, FontWeight.Medium, 0.2), labelSmall = ts(12, 16, FontWeight.Medium, 0.2)
)
