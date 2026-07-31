package de.overlai.common

// CHANGE-MARKER v0.2.1: Theme-Präferenzen (siehe CHANGELOG.md)
// Theme-Modell in core-common (NICHT core-data), damit OverlAiTheme in core-ui
// den Typ nutzen kann, ohne dass core-ui von core-data abhängt (Zyklus-Vermeidung).
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class ThemePreferences(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    // Default true = heutiges Verhalten (Material You ab Android 12); Bestands-User
    // sehen beim Update keine Farbänderung.
    val useDynamicColor: Boolean = true,
)
