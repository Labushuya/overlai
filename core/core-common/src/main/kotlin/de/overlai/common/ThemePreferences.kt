package de.overlai.common

// CHANGE-MARKER v0.2.1: Theme-Präferenzen (siehe CHANGELOG.md)
// Theme-Modell in core-common (NICHT core-data), damit OverlAiTheme in core-ui
// den Typ nutzen kann, ohne dass core-ui von core-data abhängt (Zyklus-Vermeidung).
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class ThemePreferences(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    // Default false (P2.1c): das OverlAI-Marken-Farbschema (Gold/Grün-Grau/Off-White) greift
    // sofort, statt von der dynamischen Systemfarbe („schwarze Masse") überschrieben zu werden.
    // Material You bleibt als bewusstes Opt-in erhalten.
    val useDynamicColor: Boolean = false,
)
