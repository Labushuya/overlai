package de.overlai.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.overlai.llm.providers.ProviderRegistry

// CHANGE-MARKER: Modellwechsel im Chat (P2.5 E2, siehe CHANGELOG.md)
// Kompaktes Modell-/Provider-Wechsel-Sheet für den OFFENEN Chat (Header-Chip → hier).
// Erst Anbieter (nur mit Key wählbar), dann dessen lazy geladene Modelle — Tap wechselt
// Provider+Modell der Session (ChatViewModel.changeModel, baut Session neu). Spiegelt das
// NewChatSheet-Muster, wirkt aber auf die bestehende Session statt eine neue anzulegen.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelSwitchSheet(
    state: ModelSwitchState,
    onDismiss: () -> Unit,
    onSelectProvider: (String) -> Unit,
    onClearProvider: () -> Unit,
    onPickModel: (providerId: String, modelId: String?) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            val selected = state.selectedProviderId
            if (selected == null) {
                ProviderStep(state = state, onPick = onSelectProvider)
            } else {
                ModelStep(
                    providerId = selected,
                    state = state,
                    onBack = onClearProvider,
                    onPick = { modelId -> onPickModel(selected, modelId) },
                )
            }
        }
    }
}

@Composable
private fun ProviderStep(
    state: ModelSwitchState,
    onPick: (String) -> Unit,
) {
    Text(
        "Anbieter wählen",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    HorizontalDivider()
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(ProviderRegistry.all, key = { it.id }) { provider ->
            val hasKey = provider.id in state.keyPresentFor
            val noKeyHint: (@Composable () -> Unit)? =
                if (hasKey) {
                    null
                } else {
                    { Text("Kein API-Key") }
                }
            ListItem(
                headlineContent = { Text(provider.displayName) },
                supportingContent = noKeyHint,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = hasKey) { onPick(provider.id) }
                        .padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun ModelStep(
    providerId: String,
    state: ModelSwitchState,
    onBack: () -> Unit,
    onPick: (String?) -> Unit,
) {
    val provider = ProviderRegistry.byId(providerId)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück zur Anbieterwahl")
        }
        Text(
            provider?.displayName ?: providerId,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
    HorizontalDivider()
    when {
        state.modelsError != null ->
            Text(
                state.modelsError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
        state.models == null ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                Text("Lade Modelle …", style = MaterialTheme.typography.bodyMedium)
            }
        state.models.isEmpty() ->
            // Kein Katalog: Provider-Default-Modell übernehmen (null → Provider-Default).
            ListItem(
                headlineContent = { Text(provider?.defaultModel ?: "Standard-Modell") },
                supportingContent = { Text("Kein Katalog — Standard-Modell des Anbieters") },
                modifier = Modifier.fillMaxWidth().clickable { onPick(provider?.defaultModel) },
            )
        else ->
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(state.models, key = { it.id }) { model ->
                    ListItem(
                        headlineContent = { Text(model.displayName) },
                        supportingContent = {
                            val ctx = model.context?.let { " · ${it / 1000}k Kontext" } ?: ""
                            Text(model.id + ctx, style = MaterialTheme.typography.bodySmall)
                        },
                        modifier = Modifier.fillMaxWidth().clickable { onPick(model.id) },
                    )
                }
            }
    }
}
