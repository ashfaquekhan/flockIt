package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Primary Domain Palette
val BrandEmerald = Color(0xFF24463C)   // dark glass green for filled buttons (white text)
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
val GroundDark = Color(0xFF040404)   // matte black
val SurfaceDark = Color(0xFF0B0B0C)
val Surface2Dark = Color(0xFF141416)
val SunkDark = Color(0xFF000000)
val InkDark = Color(0xFFF2F2F0)      // primary text — high contrast
val Ink2Dark = Color(0xFFB9BCBF)     // secondary text — clearly visible
val MutedDark = Color(0xFF757C83)    // captions / hints
val LineDark = Color(0x55FFFFFF)     // thin white outline     // hairlines / outlines

// Calm accent for dark mode (not flashy) — a muted teal-emerald
val AccentDark = Color(0xFFD9E8E1)   // light, near-white accent
val AccentDarkWash = Color(0x1FFFFFFF)

// Value provenance — keep the three kinds visually distinct so they're never confused:
val ValuePresent = Color(0xFF8FE3BE)     // measured / actual (you entered it) — green
val ValuePresentWash = Color(0x148FE3BE)
val ValuePredicted = Color(0xFFF2CF8A)   // projected estimate (no sample yet) — amber
val ValuePredictedWash = Color(0x14F2CF8A)
val ValueIdeal = Color(0xFFA9CCF0)       // breed-standard target — blue
val ValueIdealWash = Color(0x14A9CCF0)
val ValueCommercial = Color(0xFFCDB6F7)  // company / commercial standard — violet
val ValueCommercialWash = Color(0x14CDB6F7)
val ValueMin = Color(0xFF9CEBF2)        // lower limit — ice cyan
val ValueMinWash = Color(0x149CEBF2)
val ValueMax = Color(0xFFF7A6BF)        // upper limit — rose
val ValueMaxWash = Color(0x14F7A6BF)

// Glass panels on matte black: transparent fill, thin white outline, faint top highlight.
val GlassFill = Color(0x0DFFFFFF)
val GlassFillTop = Color(0x1AFFFFFF)
val GlassLine = Color(0x4DFFFFFF)
