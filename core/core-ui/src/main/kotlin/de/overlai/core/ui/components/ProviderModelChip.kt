package de.overlai.core.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// CHANGE-MARKER: Chat-Organisation & Modell-UX (Phase 3, siehe CHANGELOG.md)
// Kompakter, informativer Chip, der Anbieter (+ optional Modell) einer Chat-Session zeigt.
// Minimal platzeinnehmend: kleines Icon + "Anbieter · Modell".
// P2.5-E2: optional klickbar (onClick != null) → nutzt die Surface(onClick)-Überladung
// (echte Klickfläche + Ripple, zuverlässig treffbar auch in der TopAppBar-Titelzeile) und
// zeigt einen Chevron als Hinweis, dass der Chip antippbar ist (Modell wechseln).
@Composable
fun ProviderModelChip(
    providerName: String,
    modelId: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val label = providerName + (modelId?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
    val content: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // Mind. 48dp hohe Klickfläche (Material-Touch-Target), wenn klickbar.
            modifier =
                Modifier
                    .then(if (onClick != null) Modifier.heightIn(min = 48.dp) else Modifier)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.SmartToy,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp).size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onClick != null) {
                // Dekorativ: die Aktion trägt der klickbare Surface (semantics unten), nicht das Icon.
                Icon(
                    imageVector = Icons.Filled.UnfoldMore,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 4.dp).size(16.dp),
                )
            }
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = RoundedCornerShape(8.dp),
            // Button-Rolle + sprechende Aktion für TalkBack am interaktiven Element.
            modifier =
                modifier.semantics {
                    role = Role.Button
                    contentDescription = "$label — Modell wechseln"
                },
            content = content,
        )
    } else {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = RoundedCornerShape(8.dp),
            modifier = modifier,
            content = content,
        )
    }
}
