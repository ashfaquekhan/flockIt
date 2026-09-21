package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF04231B),
    primaryContainer = AccentDarkWash,
    onPrimaryContainer = Color(0xFF8FD4BF),
    secondary = Color(0xFF7FA6BC),
    onSecondary = Color(0xFF06222E),
    secondaryContainer = Color(0xFF16242B),
    onSecondaryContainer = Color(0xFFC3E1EF),
    tertiary = Color(0xFFC79A5E),
    onTertiary = Color(0xFF2E1F08),
    tertiaryContainer = Color(0xFF272013),
    onTertiaryContainer = Color(0xFFEBD3AE),
    background = GroundDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = Surface2Dark,
    onSurfaceVariant = Ink2Dark,
    surfaceContainerHighest = Surface2Dark,
    outline = LineDark,
    outlineVariant = Color(0xFF343A40),
    error = Color(0xFFD9705F),
    onError = Color(0xFF3A0906),
    errorContainer = Color(0xFF33201C),
    onErrorContainer = Color(0xFFF6D2CB)
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
