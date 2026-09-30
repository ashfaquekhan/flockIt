package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun ts(size: Int, line: Int, w: FontWeight = FontWeight.Normal, ls: Double = 0.0) =
    TextStyle(fontFamily = FontFamily.Default, fontWeight = w, fontSize = size.sp, lineHeight = line.sp, letterSpacing = ls.sp)

/** Material 3 sizes, each one step (≈ 1 sp) larger so values read easily in the shed. */
val Typography = Typography(
    displayLarge = ts(58, 66), displayMedium = ts(46, 54), displaySmall = ts(37, 45),
    headlineLarge = ts(33, 41), headlineMedium = ts(29, 37), headlineSmall = ts(25, 33),
    titleLarge = ts(23, 29), titleMedium = ts(17, 25, FontWeight.Medium, 0.15), titleSmall = ts(15, 21, FontWeight.Medium, 0.1),
    bodyLarge = ts(17, 25, ls = 0.5), bodyMedium = ts(15, 21, ls = 0.25), bodySmall = ts(13, 17, ls = 0.4),
    labelLarge = ts(15, 21, FontWeight.Medium, 0.1), labelMedium = ts(13, 17, FontWeight.Medium, 0.5), labelSmall = ts(12, 16, FontWeight.Medium, 0.5)
)
