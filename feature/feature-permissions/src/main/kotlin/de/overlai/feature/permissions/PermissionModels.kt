package de.overlai.feature.permissions

// CHANGE-MARKER v0.1.0: Permission Hub (siehe CHANGELOG.md)
// Status einer einzelnen Berechtigung/Voraussetzung. Die UI rendert grün/rot
// und bietet bei NICHT-erfüllt einen "Fix"-Deep-Link. In M2 (lite) sind nur die
// MVP-relevanten Checks aktiv; M3 ergänzt Overlay/Bubble/Accessibility/Akku.
data class PermissionItem(
    val id: String,
    val title: String,
    val rationale: String,
    val granted: Boolean,
    // true = Fix ist ein System-Deep-Link; false = In-App (z.B. Onboarding).
    val fixIsSystemSetting: Boolean = true,
)

data class PermissionHubState(
    val items: List<PermissionItem> = emptyList(),
) {
    val allGranted: Boolean get() = items.all { it.granted }
}
