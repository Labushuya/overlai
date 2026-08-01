package de.overlai.feature.settings

// CHANGE-MARKER v0.2.1: Einstellungs-Hub (siehe CHANGELOG.md)
// Route-Konstanten des Einstellungs-Hubs. Werden in :app im NavGraph verdrahtet;
// SettingsListScreen emittiert nur diese Strings (kein feature->feature-Import).
object SettingsRoutes {
    const val HOME = "settings/home"
    const val PROVIDER = "settings/provider"
    const val PERMISSIONS = "settings/permissions"
    const val UPDATES = "settings/updates"
    const val ABOUT = "settings/about"
    const val APPEARANCE = "settings/appearance"
    const val OVERLAY = "settings/overlay"
}
