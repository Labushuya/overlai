package de.overlai.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import de.overlai.common.ThemeMode
import de.overlai.common.ThemePreferences

// CHANGE-MARKER v0.2.1: Theme-Präferenzen (siehe CHANGELOG.md)
// OverlAI Material-3-Theme. Nimmt jetzt einen aufgelösten ThemePreferences-Snapshot
// (nicht mehr rohe Booleans) — der Aufrufer (MainActivity/Entry-Activities) sammelt
// die Präferenz aus dem SettingsStore und reicht sie herein.

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
    prefs: ThemePreferences = ThemePreferences(),
    systemInDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val dark =
        when (prefs.mode) {
            ThemeMode.SYSTEM -> systemInDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    val context = LocalContext.current
    // Memoisiert: dynamic*ColorScheme allokiert; nur bei Änderung von prefs/dark neu.
    val colorScheme =
        remember(prefs, dark) {
            when {
                prefs.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                dark -> DarkColors
                else -> LightColors
            }
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OverlAiTypography,
        content = content,
    )
}
