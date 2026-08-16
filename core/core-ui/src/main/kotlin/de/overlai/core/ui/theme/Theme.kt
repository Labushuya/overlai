package de.overlai.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import de.overlai.common.ThemeMode
import de.overlai.common.ThemePreferences

// CHANGE-MARKER: Marken-Palette v2 (siehe CHANGELOG.md)
// OverlAI Material-3-Theme mit VOLLEM Farbschema aus der neuen Palette:
// Tiefblau 142C96 (primär), Cyan-Blau 2B8AB6 (sekundär), Oliv-Gold 887B3F (Akzent/tertiär),
// helles Blau DBE1FA (helle Flächen). Alle Rollen/Flächen sind gesetzt, damit die Palette
// durchgängig sichtbar ist (kein M3-Default-Grau). Dynamic Color ist Opt-in (Default aus),
// sonst überschrieb die Systemfarbe die Marke. Nimmt einen aufgelösten ThemePreferences-Snapshot.

// --- Warm-Palette (P2.5): Creme/Taupe/Greige + abgeleitete Text-/Akzent-/Dark-Töne. ---
// Basis: EDD9CA (Creme, Flächen), B9A8A6 (Taupe, Primär/Akzent), D4C9C8 (Greige, Karten).
// Da alle drei hell sind, sind Text (Ink) + ein kräftigerer Akzent (Terrakotta) abgeleitet.
private val Cream = Color(0xFFEDD9CA) // background
private val CreamSurface = Color(0xFFF6ECE3) // etwas hellere surface über dem BG
private val Taupe = Color(0xFFB9A8A6) // primär: Buttons/aktive Zustände
private val TaupeDark = Color(0xFF6E5F5C) // Text auf Taupe / dunkle Variante
private val Greige = Color(0xFFD4C9C8) // Karten/Sekundärflächen
private val Terracotta = Color(0xFFA5735C) // kräftigerer warmer Akzent (Kontrast)
private val TerracottaDark = Color(0xFF5C3A2C) // dunkle Terracotta-Variante
private val WarmInk = Color(0xFF3A2F2A) // Haupttext (warmes Anthrazit-Braun)
private val WarmInkSoft = Color(0xFF6E5F5C) // sekundärer Text

// Dark: dieselbe warme Familie, dunkel.
private val DarkBrown = Color(0xFF241E1B) // dunkler background
private val DarkBrownSurface = Color(0xFF2E2724) // surface
private val DarkGreige = Color(0xFF3A322F) // Karten/surfaceVariant dunkel

private val LightColors =
    lightColorScheme(
        primary = Taupe,
        onPrimary = WarmInk,
        primaryContainer = Greige,
        onPrimaryContainer = TaupeDark,
        secondary = Terracotta,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFEBD3C6),
        onSecondaryContainer = TerracottaDark,
        tertiary = TaupeDark,
        onTertiary = Color.White,
        tertiaryContainer = Greige,
        onTertiaryContainer = TaupeDark,
        background = Cream,
        onBackground = WarmInk,
        surface = CreamSurface,
        onSurface = WarmInk,
        surfaceVariant = Greige,
        onSurfaceVariant = WarmInkSoft,
        outline = TaupeDark,
        outlineVariant = Color(0xFFC3B4B1),
    )

private val DarkColors =
    darkColorScheme(
        primary = Taupe,
        onPrimary = WarmInk,
        primaryContainer = Color(0xFF4A403D),
        onPrimaryContainer = Greige,
        secondary = Color(0xFFD8A488),
        onSecondary = TerracottaDark,
        secondaryContainer = Color(0xFF5C3A2C),
        onSecondaryContainer = Color(0xFFEBD3C6),
        tertiary = Greige,
        onTertiary = WarmInk,
        tertiaryContainer = Color(0xFF4A403D),
        onTertiaryContainer = Greige,
        background = DarkBrown,
        onBackground = Cream,
        surface = DarkBrownSurface,
        onSurface = Cream,
        surfaceVariant = DarkGreige,
        onSurfaceVariant = Color(0xFFCBBDB9),
        outline = Taupe,
        outlineVariant = Color(0xFF574D49),
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
    // Immer das Marken-Farbschema (Warm-Palette, P2.5) — bewusst KEIN Dynamic Color, damit
    // Fullscreen-App und Overlay-Panel garantiert identisch aussehen. prefs.useDynamicColor
    // wird daher ignoriert.
    val colorScheme = if (dark) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OverlAiTypography,
        shapes = OverlaiShapes,
        content = content,
    )
}
