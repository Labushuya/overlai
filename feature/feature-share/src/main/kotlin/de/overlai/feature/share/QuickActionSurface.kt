package de.overlai.feature.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// CHANGE-MARKER v0.1.0: Entry-Points (siehe CHANGELOG.md)
// Kompakte Quick-Action-Surface über der Host-App: Aktion wählen, Ergebnis
// (streamend) lesen, kopieren/einfügen. Bewusst klein — verdeckt die Host-App nicht.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickActionSurface(
    viewModel: QuickActionViewModel,
    onCopy: (String) -> Unit,
    onInsert: ((String) -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("OverlAI", style = MaterialTheme.typography.titleMedium)

            if (!state.hasApiKey) {
                Text(
                    "Kein API-Key hinterlegt — bitte in OverlAI einrichten.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Aktions-Chips.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction.entries.forEach { action ->
                    AssistChip(
                        onClick = { viewModel.run(action) },
                        label = { Text(action.label) },
                    )
                }
            }

            if (state.isLoading) {
                CircularProgressIndicator()
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (state.resultText.isNotEmpty()) {
                Text(
                    state.resultText,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onCopy(state.resultText) }) { Text("Kopieren") }
                    // Insert-in-Place ist host-abhängig -> nur anbieten, wenn möglich.
                    if (state.canReplaceInHost && onInsert != null) {
                        TextButton(onClick = { onInsert(state.resultText) }) { Text("Einfügen") }
                    }
                    TextButton(onClick = onDismiss) { Text("Schließen") }
                }
            }
        }
    }
}
