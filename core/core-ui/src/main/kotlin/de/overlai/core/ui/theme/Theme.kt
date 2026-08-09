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

// CHANGE-MARKER: Marken-Palette v2 (siehe CHANGELOG.md)
// OverlAI Material-3-Theme mit VOLLEM Farbschema aus der neuen Palette:
// Tiefblau 142C96 (primär), Cyan-Blau 2B8AB6 (sekundär), Oliv-Gold 887B3F (Akzent/tertiär),
// helles Blau DBE1FA (helle Flächen). Alle Rollen/Flächen sind gesetzt, damit die Palette
// durchgängig sichtbar ist (kein M3-Default-Grau). Dynamic Color ist Opt-in (Default aus),
// sonst überschrieb die Systemfarbe die Marke. Nimmt einen aufgelösten ThemePreferences-Snapshot.

// --- Palette (Basis + abgeleitete Töne) ---
private val DeepBlue = Color(0xFF142C96) // primär: Buttons, aktive Zustände
private val DeepBlueLight = Color(0xFFDDE1F6) // heller Container zu DeepBlue
private val CyanBlue = Color(0xFF2B8AB6) // sekundär: Akzent
private val CyanBlueLight = Color(0xFFD3E7F1) // heller Container zu CyanBlue
private val CyanBlueDark = Color(0xFF0E4A66) // dunkle Variante (Text auf hellem Cyan)
private val OliveGold = Color(0xFF887B3F) // tertiär: warmer Akzent-Gegenpol
private val OliveGoldLight = Color(0xFFEDE8CF) // heller Container zu OliveGold
private val OliveGoldDark = Color(0xFF5A521F) // dunkle Variante
private val PaleBlue = Color(0xFFDBE1FA) // helle Flächen (background)
private val Ink = Color(0xFF121629) // Text/dunkle Flächen (kein reines Schwarz, blaustichig)

private val LightColors =
    lightColorScheme(
        primary = DeepBlue,
        onPrimary = Color.White,
        primaryContainer = DeepBlueLight,
        onPrimaryContainer = DeepBlue,
        secondary = CyanBlue,
        onSecondary = Color.White,
        secondaryContainer = CyanBlueLight,
        onSecondaryContainer = CyanBlueDark,
        tertiary = OliveGold,
        onTertiary = Color.White,
        tertiaryContainer = OliveGoldLight,
        onTertiaryContainer = OliveGoldDark,
        background = PaleBlue,
        onBackground = Ink,
        surface = Color(0xFFF3F5FC),
        onSurface = Ink,
        surfaceVariant = DeepBlueLight,
        onSurfaceVariant = CyanBlueDark,
        outline = CyanBlue,
        outlineVariant = Color(0xFFAEB8DA),
    )

private val DarkColors =
    darkColorScheme(
        // hellere Blau-Variante für Kontrast auf dunkel
        primary = Color(0xFFAEBCF5),
        onPrimary = DeepBlue,
        primaryContainer = DeepBlue,
        onPrimaryContainer = DeepBlueLight,
        secondary = Color(0xFF8FCBE6),
        onSecondary = CyanBlueDark,
        secondaryContainer = CyanBlueDark,
        onSecondaryContainer = CyanBlueLight,
        tertiary = Color(0xFFD6C878),
        onTertiary = OliveGoldDark,
        tertiaryContainer = OliveGoldDark,
        onTertiaryContainer = OliveGoldLight,
        background = Ink,
        onBackground = PaleBlue,
        surface = Color(0xFF1C2138),
        onSurface = PaleBlue,
        surfaceVariant = Color(0xFF2A3052),
        onSurfaceVariant = Color(0xFFC3CBEC),
        outline = CyanBlue,
        outlineVariant = Color(0xFF3A426A),
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
