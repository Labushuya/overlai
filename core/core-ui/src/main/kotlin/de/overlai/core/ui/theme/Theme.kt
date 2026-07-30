package de.overlai.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// CHANGE-MARKER v0.1.0: Initiales Projektgrundgerüst (siehe CHANGELOG.md)
// OverlAI Material-3-Theme. Nutzt Material You (dynamic color) ab Android 12,
// sonst die Marken-Fallback-Palette. Dark Mode folgt dem System.

// Marken-Fallback (falls kein dynamic color): ruhiges Indigo/Teal, "AI-assistant"-Ton.
private val BrandPrimary = Color(0xFF4C5BD4)
private val BrandSecondary = Color(0xFF00A6A6)
private val BrandTertiary = Color(0xFF7C4DFF)

private val LightColors =
    lightColorScheme(
        primary = BrandPrimary,
        secondary = BrandSecondary,
        tertiary = BrandTertiary,
    )

private val DarkColors =
    darkColorScheme(
        primary = BrandPrimary,
        secondary = BrandSecondary,
        tertiary = BrandTertiary,
    )

@Composable
fun OverlAiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color ist Android 12+ (API 31) — auf minSdk 26 also Feature-Detect.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColors
            else -> LightColors
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OverlAiTypography,
        content = content,
    )
}
