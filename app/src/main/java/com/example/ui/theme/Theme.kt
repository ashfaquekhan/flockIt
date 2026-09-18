package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4FB79A),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF16302A),
    onPrimaryContainer = Color(0xFF7FD0B8),
    secondary = Color(0xFF5AA6CC),
    onSecondary = Color(0xFF003549),
    secondaryContainer = Color(0xFF14262E),
    onSecondaryContainer = Color(0xFFBCE9FF),
    tertiary = Color(0xFFD08A3E),
    onTertiary = Color(0xFF442B00),
    tertiaryContainer = Color(0xFF2E2213),
    onTertiaryContainer = Color(0xFFFFDDB8),
    background = GroundDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = Surface2Dark,
    onSurfaceVariant = Ink2Dark,
    outline = LineDark,
    outlineVariant = Color(0xFF39413D),
    error = Color(0xFFE0705C),
    onError = Color(0xFF680003),
    errorContainer = Color(0xFF361E19),
    onErrorContainer = Color(0xFFFFDAD4)
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
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
