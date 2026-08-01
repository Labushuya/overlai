package de.overlai.feature.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.overlai.core.ui.components.KeyStatusBadge
import de.overlai.llm.Capability
import de.overlai.llm.ModelInfo
import de.overlai.llm.ProviderConfig

// CHANGE-MARKER v0.4.7: Provider-Hub-UI (siehe CHANGELOG.md)
// Ein Screen für Provider + Keys + Modelle: Akkordeon-Karten, genau eine offen.
// Aufgeklappt: Key-Verwaltung (maskiert + Ändern/Löschen), Capability-Badges,
// live Modell-Katalog. Modell-Tap macht Modell + Provider aktiv. Ersetzt
// OnboardingScreen + ModelCatalogScreen (dient Onboarding UND Settings).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderHubScreen(
    viewModel: ProviderHubViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Provider & Modelle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        ) {
            items(state.providers, key = { it.id }) { provider ->
                ProviderCard(
                    provider = provider,
                    state = state,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: ProviderConfig,
    state: ProviderHubViewModel.UiState,
    viewModel: ProviderHubViewModel,
) {
    val expanded = state.expandedProviderId == provider.id
    val hasKey = provider.id in state.keyPresentFor
    val active = state.activeProviderId == provider.id
    val activeModel = state.activeModelByProvider[provider.id]

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        // Kopf — immer sichtbar, klickbar zum Auf-/Zuklappen.
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.onToggleExpand(provider.id) }
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(provider.displayName, fontWeight = FontWeight.SemiBold)
                KeyStatusBadge(active = active, hasKey = hasKey)
                if (active && activeModel != null) {
                    Text(
                        "Modell: $activeModel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Zuklappen" else "Aufklappen",
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HorizontalDivider()
                KeySection(provider, state, viewModel, hasKey)
                CapabilityBadges(provider)
                ModelSection(provider, state, viewModel, hasKey)
            }
        }
    }
}

@Composable
private fun KeySection(
    provider: ProviderConfig,
    state: ProviderHubViewModel.UiState,
    viewModel: ProviderHubViewModel,
    hasKey: Boolean,
) {
    val editing = state.editingKeyFor == provider.id
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("API-Key", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
        val hint = keyHint(provider.id)
        if (hint.isNotEmpty()) {
            Text(
                "Key erstellen unter: $hint",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when {
            // Klartext-Eingabe (Neu oder Ändern).
            editing -> {
                OutlinedTextField(
                    value = state.keyInput,
                    onValueChange = viewModel::onKeyInput,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("${provider.displayName}-API-Key") },
                    placeholder = { Text("Key hier einfügen") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.onSaveKey(provider.id) },
                        enabled = state.keyInput.isNotBlank(),
                    ) { Text("Speichern") }
                    TextButton(onClick = viewModel::onCancelKeyEntry) { Text("Abbrechen") }
                }
            }
            // Key vorhanden: maskiert + Ändern/Löschen.
            hasKey -> {
                val tail = state.keyLast4[provider.id] ?: ""
                Text(
                    "••••••••$tail",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.onStartKeyEntry(provider.id) }) { Text("Ändern") }
                    TextButton(onClick = { viewModel.onDeleteKey(provider.id) }) { Text("Löschen") }
                }
            }
            // Kein Key: Hinweis + Direkt-Eingabe.
            else -> {
                Text(
                    "Kein Key hinterlegt. Modelle erscheinen nach dem Speichern.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = state.keyInput,
                    onValueChange = viewModel::onKeyInput,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("${provider.displayName}-API-Key") },
                    placeholder = { Text("Key hier einfügen") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Button(
                    onClick = { viewModel.onSaveKey(provider.id) },
                    enabled = state.keyInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Speichern") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CapabilityBadges(provider: ProviderConfig) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        provider.capabilities.forEach { cap ->
            AssistChip(onClick = {}, label = { Text(capabilityLabel(cap)) })
        }
    }
}

@Composable
private fun ModelSection(
    provider: ProviderConfig,
    state: ProviderHubViewModel.UiState,
    viewModel: ProviderHubViewModel,
    hasKey: Boolean,
) {
    if (!hasKey) return
    Text("Modelle", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
    when (val m = state.models[provider.id]) {
        null, ProviderHubViewModel.ModelListState.Idle, ProviderHubViewModel.ModelListState.Loading ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                Text("Lade Modelle …", style = MaterialTheme.typography.bodyMedium)
            }
        ProviderHubViewModel.ModelListState.Empty ->
            Text("Provider lieferte keine Modelle.", style = MaterialTheme.typography.bodyMedium)
        is ProviderHubViewModel.ModelListState.Loaded ->
            ModelList(provider, m.models, state, viewModel)
        ProviderHubViewModel.ModelListState.Error.Unauthorized ->
            ModelError("API-Key ungültig. Bitte Key prüfen.")
        ProviderHubViewModel.ModelListState.Error.RateLimited ->
            RetryError("Rate-Limit/Guthaben — später erneut.", provider, viewModel)
        ProviderHubViewModel.ModelListState.Error.NoEndpoint ->
            ManualIdEntry(provider, viewModel)
        is ProviderHubViewModel.ModelListState.Error.Network ->
            RetryError("Netzwerkfehler: ${m.message}", provider, viewModel)
    }
}

@Composable
private fun ModelList(
    provider: ProviderConfig,
    models: List<ModelInfo>,
    state: ProviderHubViewModel.UiState,
    viewModel: ProviderHubViewModel,
) {
    val activeModel = state.activeModelByProvider[provider.id]
    val showFree = provider.id == "openrouter"
    if (showFree) {
        FilterChip(
            selected = state.freeOnly,
            onClick = viewModel::onToggleFreeOnly,
            label = { Text("Nur kostenlose") },
        )
    }
    val visible = if (state.freeOnly) models.filter { it.free } else models
    Column {
        visible.forEach { model ->
            ModelRow(
                model = model,
                selected = model.id == activeModel,
                onSelect = { viewModel.onSelectModel(provider.id, model.id) },
            )
        }
    }
}

@Composable
private fun ModelRow(
    model: ModelInfo,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(model.displayName) },
        supportingContent = {
            val ctx = model.context?.let { " · ${it / 1000}k Kontext" } ?: ""
            Text(model.id + ctx, style = MaterialTheme.typography.bodySmall)
        },
        trailingContent = {
            when {
                selected -> Text("✓", color = MaterialTheme.colorScheme.primary)
                model.free -> AssistChip(onClick = onSelect, label = { Text("Free") })
                else -> null
            }
        },
        modifier = Modifier.clickable { onSelect() },
    )
}

@Composable
private fun ManualIdEntry(
    provider: ProviderConfig,
    viewModel: ProviderHubViewModel,
) {
    var manualId by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Kein Modell-Katalog verfügbar. Modell-ID direkt eingeben.",
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
            onClick = { viewModel.onSelectModel(provider.id, manualId.trim()) },
            enabled = manualId.isNotBlank(),
        ) { Text("Übernehmen") }
    }
}

@Composable
private fun ModelError(message: String) {
    Card {
        Text(message, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun RetryError(
    message: String,
    provider: ProviderConfig,
    viewModel: ProviderHubViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = { viewModel.loadModels(provider.id) }) { Text("Erneut laden") }
    }
}

// Doku-URL je Provider (Key erstellen). Aus dem alten OnboardingViewModel übernommen.
private fun keyHint(providerId: String): String =
    when (providerId) {
        "openai" -> "platform.openai.com/api-keys"
        "anthropic" -> "console.anthropic.com/settings/keys"
        "grok" -> "console.x.ai"
        "deepseek" -> "platform.deepseek.com/api_keys"
        "kimi" -> "platform.moonshot.ai"
        "openrouter" -> "openrouter.ai/keys"
        "gemini" -> "aistudio.google.com/apikey"
        else -> ""
    }

private fun capabilityLabel(cap: Capability): String =
    when (cap) {
        Capability.CHAT -> "Chat"
        Capability.VISION -> "Bild"
        Capability.WEB_SEARCH_NATIVE -> "Websuche"
        Capability.WEB_SEARCH_EXTERNAL -> "Websuche (extern)"
        Capability.TRANSCRIPTION -> "Transkription"
        Capability.TOOL_USE -> "Tools"
    }
