package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Expo styleguide, dark: a near-black screen, slate panels, one blue accent
private val DarkColorScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF0D1520),
    primaryContainer = AccentDarkWash,
    onPrimaryContainer = Color(0xFFC2E6FF),
    secondary = Color(0xFFB0B4BA),
    onSecondary = Color(0xFF111113),
    secondaryContainer = Color(0x21FFFFFF),
    onSecondaryContainer = InkDark,
    tertiary = BrandBeak,
    onTertiary = Color(0xFF2E1F08),
    tertiaryContainer = Color(0xFF331E0B),
    onTertiaryContainer = Color(0xFFFFE0C2),
    background = GroundDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = Surface2Dark,
    onSurfaceVariant = Ink2Dark,
    surfaceContainerLowest = GroundDark,
    surfaceContainerLow = SunkDark,
    surfaceContainer = Color(0xFF18191B),
    surfaceContainerHigh = Color(0xFF212225),
    surfaceContainerHighest = Color(0xFF272A2D),
    surfaceBright = Color(0xFF272A2D),
    surfaceDim = GroundDark,
    surfaceTint = SurfaceDark,
    outline = LineDark,
    outlineVariant = Color(0xFF363A3F),
    inverseSurface = InkDark,
    inverseOnSurface = Color(0xFF111113),
    error = BrandComb,
    onError = Color(0xFF3A0906),
    errorContainer = Color(0xFF3B1219),
    onErrorContainer = Color(0xFFFFD1D9)
)

private val LightColorScheme = lightColorScheme(
    primary = BrandEmerald,
    onPrimary = Color.White,
    primaryContainer = BrandWashLight,
    onPrimaryContainer = BrandDarkEmerald,
    secondary = DomainVent,
    onSecondary = Color.White,
    secondaryContainer = DomainVentWash,
    onSecondaryContainer = Color(0xFF001F2A),
    tertiary = DomainFeed,
    onTertiary = Color.White,
    tertiaryContainer = DomainFeedWash,
    onTertiaryContainer = Color(0xFF2E1500),
    background = GroundLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = Surface2Light,
    onSurfaceVariant = Ink2Light,
    outline = LineLight,
    outlineVariant = Color(0xFFD3CEC1),
    error = StatusCrit,
    onError = Color.White,
    errorContainer = StatusCritWash,
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun FlockItTheme(
    // Minimalist dark theme is the app's identity — force dark regardless of system setting.
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    // the faces for text drawn on canvases (charts, clock, farm window)
    AppFonts.load(androidx.compose.ui.platform.LocalContext.current)
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

@Deprecated("Use FlockItTheme instead", ReplaceWith("FlockItTheme(darkTheme, content)"))
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    FlockItTheme(darkTheme = darkTheme, content = content)
}
