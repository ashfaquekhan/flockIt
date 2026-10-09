package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Primary Domain Palette
val BrandEmerald = Color(0xFF0C0D0E)   // no colour fills: widgets sit on the screen colour
val BrandDarkEmerald = Color(0xFF0F5C4A)
val BrandWashLight = Color(0xFFE4F0EB)
val BrandWashDark = Color(0xFF16302A)

// Status Colors
val StatusGood = Color(0xFF2F7D66)
val StatusGoodWash = Color(0xFFE4F0EB)
val StatusWarn = Color(0xFFB7841B)
val StatusWarnWash = Color(0xFFF6ECD3)
val StatusCrit = Color(0xFFBB3B2B)
val StatusCritWash = Color(0xFFF7E3DE)
val StatusProjected = Color(0xFFBB3B2B)

// Operational Domain Colors
val DomainVent = Color(0xFF2E7DA6)
val DomainVentWash = Color(0xFFE1EEF4)
val DomainFeed = Color(0xFFB26A1E)
val DomainFeedWash = Color(0xFFF5E9D8)
val DomainWater = Color(0xFF2C6FB0)
val DomainWaterWash = Color(0xFFE2ECF6)
val DomainTask = Color(0xFF6A57B8)
val DomainTaskWash = Color(0xFFEAE6F5)
val DomainMed = Color(0xFFAE3F72)
val DomainMedWash = Color(0xFFF5E2ED)
val DomainInsight = Color(0xFF3C7D3A)
val DomainInsightWash = Color(0xFFE7F1E4)

// Light Backgrounds & Neutrals
val GroundLight = Color(0xFFF2EFE8)
val SurfaceLight = Color(0xFFFDFCF9)
val Surface2Light = Color(0xFFF7F4ED)
val SunkLight = Color(0xFFECE8DE)
val InkLight = Color(0xFF1E241F)
val Ink2Light = Color(0xFF4A5149)
val MutedLight = Color(0xFF7C8378)
val LineLight = Color(0xFFE2DED3)

// Dark Backgrounds & Neutrals — neutral charcoal, calm and clearly legible
val GroundDark = Color(0xFF0C0D0E)   // the screen: near-black (Expo styleguide "screen")
val SurfaceDark = Color(0xFF151618)  // panels: a few percent lighter than the screen
val Surface2Dark = Color(0xFF212225) // elements inside panels (slate 3)
val SunkDark = Color(0xFF111113)     // slate 1
val InkDark = Color(0xFFEDEEF0)      // primary text (slate 12)
val Ink2Dark = Color(0xFFB0B4BA)     // secondary text (slate 11)
val MutedDark = Color(0xFF777B84)    // captions / hints (slate 10)
val LineDark = Color(0xFF43484E)     // outlines of fields (slate 7)

// Calm accent for dark mode (not flashy) — a muted teal-emerald
val AccentDark = Color(0xFF70B8FF)   // the one accent: link blue (blue 11)
// the mark's own colours: the comb and the beak (used sparingly, as markers)
val BrandComb = Color(0xFFE5534B)
val BrandBeak = Color(0xFFF0A23A)
val AccentDarkWash = Color(0xFF0D2847) // blue 3

// Value provenance — keep the three kinds visually distinct so they're never confused:
val ValuePresent = Color(0xFF3DD68C)     // measured / actual (you entered it) — green 11
val ValuePresentWash = Color(0x143DD68C)
val ValuePredicted = Color(0xFFFFCA16)   // projected estimate (no sample yet) — amber 11
val ValuePredictedWash = Color(0x14FFCA16)
val ValueIdeal = Color(0xFF70B8FF)       // breed-standard target — blue 11
val ValueIdealWash = Color(0x1470B8FF)
val ValueCommercial = Color(0xFFD19DFF)  // company / commercial standard — purple 11
val ValueCommercialWash = Color(0x14D19DFF)
val ValueMin = Color(0xFF4CCCE6)        // lower limit — cyan 11
val ValueMinWash = Color(0x144CCCE6)
val ValueMax = Color(0xFFFF9592)        // upper limit — red 11
val ValueMaxWash = Color(0x14FF9592)

// Panels: see-through (a few percent of white over the screen), a hairline instead of an outline.
val GlassFill = Color(0x08FFFFFF)
val GlassFillTop = Color(0x0FFFFFFF)
/** the line round buttons and form sections: present, not loud */
val GlassLine = Color(0x29FFFFFF)
/** the hairline round a card */
val Hairline = Color(0x14FFFFFF)
/** a faint fill behind a value (no outline) and a stronger one for the chosen of several */
val SoftFill = Color(0x0DFFFFFF)
val SoftFillStrong = Color(0x21FFFFFF)
/** links and the chosen thing */
val LinkBlue = Color(0xFF70B8FF)
val AccentBlue = Color(0xFF0090FF)
