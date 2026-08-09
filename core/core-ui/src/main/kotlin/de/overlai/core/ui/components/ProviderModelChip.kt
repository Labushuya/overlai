package de.overlai.core.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// CHANGE-MARKER: Chat-Organisation & Modell-UX (Phase 3, siehe CHANGELOG.md)
// Kompakter, informativer Chip, der Anbieter (+ optional Modell) einer Chat-Session zeigt.
// Minimal platzeinnehmend: kleines Icon + "Anbieter · Modell". Vorlage: AssistChip-Muster
// aus ProviderHubScreen, aber als eigenständiges, leichtes Surface (kein Klick nötig).
@Composable
fun ProviderModelChip(
    providerName: String,
    modelId: String?,
    modifier: Modifier = Modifier,
) {
    val label = providerName + (modelId?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.SmartToy,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp).size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
