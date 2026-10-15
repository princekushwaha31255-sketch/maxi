package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MaxDarkColorScheme = darkColorScheme(
    primary = MaxElectricCyan,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2F2F2F),
    onPrimaryContainer = Color(0xFFECECEC),
    secondary = MaxElectricBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF262626),
    onSecondaryContainer = Color(0xFFECECEC),
    tertiary = MaxEmeraldSuccess,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF064E3B),
    onTertiaryContainer = Color(0xFFD1FAE5),
    background = MaxObsidian,
    onBackground = MaxTextPrimary,
    surface = MaxSurfaceDark,
    onSurface = MaxTextPrimary,
    surfaceVariant = MaxSurfaceVariantDark,
    onSurfaceVariant = MaxTextSecondary,
    error = MaxCoralError,
    onError = Color.White,
    errorContainer = Color(0xFF451A1A),
    onErrorContainer = Color(0xFFFEE2E2),
    outline = Color(0xFF333333)
)

private val MaxLightColorScheme = lightColorScheme(
    primary = MaxPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF4F4F4),
    onPrimaryContainer = Color(0xFF0D0D0D),
    secondary = MaxSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECECF1),
    onSecondaryContainer = Color(0xFF0D0D0D),
    tertiary = MaxEmeraldSuccess,
    onTertiary = Color.White,
    background = MaxBackgroundLight,
    onBackground = MaxTextPrimaryLight,
    surface = MaxSurfaceLight,
    onSurface = MaxTextPrimaryLight,
    surfaceVariant = MaxSurfaceVariantLight,
    onSurfaceVariant = MaxTextSecondaryLight,
    error = MaxCoralError,
    onError = Color.White,
    outline = Color(0xFFD9D9E3)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MaxDarkColorScheme else MaxLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
