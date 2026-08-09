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
import androidx.compose.material.icons.filled.Bolt
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.overlai.llm.providers.ProviderRegistry

// CHANGE-MARKER: Chat-Organisation & Modell-UX (Phase 3, siehe CHANGELOG.md)
// Geführter "Neuer Chat"-Flow als ModalBottomSheet: Schnellstart (globaler Default, ein Tap)
// ODER gechaint Anbieter → Modell. Nichts weggenommen — der bisherige Ein-Tap-Weg bleibt oben.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatSheet(
    viewModel: NewChatViewModel,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            val selected = state.selectedProviderId
            if (selected == null) {
                ProviderStep(
                    state = state,
                    onQuickStart = { viewModel.quickStart(onCreated) },
                    onPick = { viewModel.selectProvider(it) },
                )
            } else {
                ModelStep(
                    providerId = selected,
                    state = state,
                    onBack = { viewModel.clearProvider() },
                    onPick = { modelId -> viewModel.create(selected, modelId, onCreated) },
                )
            }
        }
    }
}

@Composable
private fun ProviderStep(
    state: NewChatViewModel.UiState,
    onQuickStart: () -> Unit,
    onPick: (String) -> Unit,
) {
    Text(
        "Neuer Chat",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
    // Schnellstart: bisheriger Default-Weg, ein Tap.
    ListItem(
        headlineContent = { Text("Schnellstart") },
        supportingContent = { Text("Zuletzt genutzter Anbieter & Modell") },
        leadingContent = { Icon(Icons.Filled.Bolt, contentDescription = null) },
        modifier = Modifier.fillMaxWidth().clickable { onQuickStart() },
    )
    HorizontalDivider()
    Text(
        "Oder Anbieter wählen",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
    LazyColumn {
        items(state.providers, key = { it.id }) { p ->
            val hasKey = p.id in state.keyPresentFor
            ListItem(
                headlineContent = { Text(p.displayName) },
                supportingContent =
                    if (!hasKey) {
                        { Text("Kein API-Key", color = MaterialTheme.colorScheme.error) }
                    } else {
                        null
                    },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = hasKey) { onPick(p.id) },
            )
        }
    }
}

@Composable
private fun ModelStep(
    providerId: String,
    state: NewChatViewModel.UiState,
    onBack: () -> Unit,
    onPick: (String?) -> Unit,
) {
    val providerName = ProviderRegistry.byId(providerId)?.displayName ?: providerId
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
        }
        Text(providerName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
    when (val models = state.models) {
        NewChatViewModel.ModelsState.Idle, NewChatViewModel.ModelsState.Loading ->
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            ) { CircularProgressIndicator() }
        NewChatViewModel.ModelsState.Empty ->
            Text(
                "Keine Modelle gefunden.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        is NewChatViewModel.ModelsState.Error ->
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    models.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                // Provider-Default trotzdem anbietbar (modelId = null → config.defaultModel).
                ListItem(
                    headlineContent = { Text("Standard-Modell verwenden") },
                    modifier = Modifier.fillMaxWidth().clickable { onPick(null) },
                )
            }
        is NewChatViewModel.ModelsState.Loaded ->
            LazyColumn {
                items(models.models, key = { it.id }) { m ->
                    ListItem(
                        headlineContent = { Text(m.displayName, maxLines = 1) },
                        supportingContent = m.context?.let { ctx -> { Text("${ctx / 1000}k Kontext") } },
                        modifier = Modifier.fillMaxWidth().clickable { onPick(m.id) },
                    )
                }
            }
    }
}
