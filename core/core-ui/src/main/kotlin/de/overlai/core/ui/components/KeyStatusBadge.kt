package de.overlai.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// CHANGE-MARKER v0.4.7: Provider-Hub-UI (siehe CHANGELOG.md)
// Einheitliche Status-Anzeige für einen Provider. Bisher war der Key-/Aktiv-Status
// an vier Stellen unterschiedlich dargestellt ("✓ Key"-Text, "Bereit"-Chip, Card,
// ✅/❌) — diese Komponente ist die eine Wahrheit dafür.
@Composable
fun KeyStatusBadge(
    active: Boolean,
    hasKey: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (active) {
            Text(
                "● Aktiv",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (hasKey) {
            Text(
                "✓ Key",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                "Kein Key",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
