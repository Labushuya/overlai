package de.overlai.feature.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.overlai.llm.ModelInfo

// CHANGE-MARKER v0.4.0: Modell-Katalog-UI (siehe CHANGELOG.md)
// Durchscrollbarer, durchsuchbarer Modell-Katalog eines Providers. Antippen setzt
// das Modell aktiv. "Nur kostenlose"-Filter nur bei OpenRouter (nur dort verlässlich).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelCatalogScreen(
    viewModel: ModelCatalogViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Modell · ${state.providerName}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val phase = state.phase) {
                ModelCatalogViewModel.Phase.Loading ->
                    Centered { CircularProgressIndicator() }
                ModelCatalogViewModel.Phase.Loaded, ModelCatalogViewModel.Phase.Empty ->
                    LoadedContent(state, viewModel)
                ModelCatalogViewModel.Phase.Error.NoKey ->
                    ErrorContent("Kein API-Key hinterlegt. Bitte zuerst einen Key eintragen.", onBack, "Zurück")
                ModelCatalogViewModel.Phase.Error.Unauthorized ->
                    ErrorContent("API-Key ungültig. Bitte Key prüfen.", onBack, "Zurück")
                ModelCatalogViewModel.Phase.Error.RateLimited ->
                    ErrorContent("Rate-Limit/Guthaben — später erneut versuchen.", viewModel::load, "Erneut prüfen")
                is ModelCatalogViewModel.Phase.Error.Network ->
                    ErrorContent("Netzwerkfehler: ${phase.message}", viewModel::load, "Erneut prüfen")
                ModelCatalogViewModel.Phase.Error.NoEndpoint ->
                    ManualIdFallback(viewModel, onBack)
            }
        }
    }
}

@Composable
private fun LoadedContent(
    state: ModelCatalogViewModel.UiState,
    viewModel: ModelCatalogViewModel,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onSearch,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            label = { Text("Modell suchen") },
            singleLine = true,
        )
        if (state.showFreeFilter) {
            FilterChip(
                selected = state.freeOnly,
                onClick = viewModel::onToggleFreeOnly,
                label = { Text("Nur kostenlose") },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        val visible = state.visible
        if (visible.isEmpty()) {
            Text(
                if (state.all.isEmpty()) "Provider lieferte keine Modelle." else "Keine Treffer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(visible, key = { it.id }) { model ->
                    ModelRow(model, selected = model.id == state.selectedModelId, onSelect = viewModel::onSelect)
                }
            }
        }
    }
}

@Composable
private fun ModelRow(
    model: ModelInfo,
    selected: Boolean,
    onSelect: (String) -> Unit,
) {
    ListItem(
        headlineContent = { Text(model.displayName) },
        supportingContent = { Text(model.id, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            when {
                selected -> Text("✓", color = MaterialTheme.colorScheme.primary)
                model.free -> AssistChip(onClick = { onSelect(model.id) }, label = { Text("Free") })
                else -> null
            }
        },
        modifier = Modifier.clickable { onSelect(model.id) },
    )
}

@Composable
private fun ManualIdFallback(
    viewModel: ModelCatalogViewModel,
    onBack: () -> Unit,
) {
    var manualId by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Dieser Provider bietet keinen Modell-Katalog. Gib die Modell-ID direkt ein.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = manualId,
            onValueChange = { manualId = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Modell-ID") },
            singleLine = true,
        )
        Button(
            onClick = {
                viewModel.onSelect(manualId.trim())
                onBack()
            },
            enabled = manualId.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Übernehmen")
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onAction: () -> Unit,
    actionLabel: String,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}
