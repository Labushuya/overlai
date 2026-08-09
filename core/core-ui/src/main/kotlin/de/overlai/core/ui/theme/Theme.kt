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

// CHANGE-MARKER: Marken-Palette (P2.1c-Politur, siehe CHANGELOG.md)
// OverlAI Material-3-Theme mit VOLLEM Marken-Farbschema aus der Logo-Palette
// (Gold D0BB3E, Grün-Grau 485956, Off-White EBEFEE) — nicht mehr nur 3 Rollen auf
// M3-Default-Grau ("schwarze Masse"). Alle Flächen/Container sind gesetzt, damit die
// Palette durchgängig sichtbar ist. Dynamic Color ist jetzt Opt-in (Default aus), sonst
// überschrieb die Systemfarbe die Marke. Nimmt einen aufgelösten ThemePreferences-Snapshot.

// --- Logo-Palette ---
private val BrandGold = Color(0xFFD0BB3E) // primär: Akzent, Buttons, aktive Zustände
private val BrandGoldDark = Color(0xFF8A7A1F) // dunklerer Gold-Ton (Text auf Gold, dark scheme)
private val BrandGreen = Color(0xFF485956) // sekundär: Grün-Grau, ruhige Flächen
private val BrandGreenDark = Color(0xFF2B3634) // dunklere Variante
private val BrandGreenLight = Color(0xFFCDD6D3) // heller Grün-Grau-Ton (Container hell)
private val BrandOffWhite = Color(0xFFEBEFEE) // helle Flächen (background/surface)
private val BrandInk = Color(0xFF1B211F) // Text/dunkle Flächen (kein reines Schwarz)

private val LightColors =
    lightColorScheme(
        primary = BrandGold,
        onPrimary = BrandInk,
        primaryContainer = Color(0xFFF3E9B8),
        onPrimaryContainer = BrandGoldDark,
        secondary = BrandGreen,
        onSecondary = BrandOffWhite,
        secondaryContainer = BrandGreenLight,
        onSecondaryContainer = BrandGreenDark,
        tertiary = BrandGreenDark,
        onTertiary = BrandOffWhite,
        tertiaryContainer = BrandGreenLight,
        onTertiaryContainer = BrandGreenDark,
        background = BrandOffWhite,
        onBackground = BrandInk,
        surface = Color(0xFFF6F8F7),
        onSurface = BrandInk,
        surfaceVariant = BrandGreenLight,
        onSurfaceVariant = BrandGreenDark,
        outline = BrandGreen,
        outlineVariant = Color(0xFFAEBAB6),
    )

private val DarkColors =
    darkColorScheme(
        primary = BrandGold,
        onPrimary = BrandInk,
        primaryContainer = BrandGoldDark,
        onPrimaryContainer = Color(0xFFF3E9B8),
        secondary = BrandGreenLight,
        onSecondary = BrandGreenDark,
        secondaryContainer = BrandGreen,
        onSecondaryContainer = BrandOffWhite,
        tertiary = BrandGreenLight,
        onTertiary = BrandGreenDark,
        tertiaryContainer = BrandGreen,
        onTertiaryContainer = BrandOffWhite,
        background = BrandInk,
        onBackground = BrandOffWhite,
        surface = Color(0xFF242C2A),
        onSurface = BrandOffWhite,
        surfaceVariant = BrandGreenDark,
        onSurfaceVariant = BrandGreenLight,
        outline = BrandGreenLight,
        outlineVariant = BrandGreen,
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
