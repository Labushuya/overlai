package de.overlai.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// CHANGE-MARKER v0.1.0: Onboarding/BYOK (siehe CHANGELOG.md)
// BYOK-Onboarding: Provider wählen, eigenen API-Key eingeben (maskiert), speichern.
// Der Key wird verschlüsselt (Keystore) abgelegt und verlässt das Gerät nie.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onDone: () -> Unit,
    onChooseModel: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Provider einrichten") }) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Wähle einen Provider und gib deinen eigenen API-Key ein. " +
                    "Der Key wird verschlüsselt auf dem Gerät gespeichert — kein Server, kein Backend.",
                style = MaterialTheme.typography.bodyMedium,
            )

            ProviderList(
                providers = state.providers,
                selectedId = state.selectedProviderId,
                activeId = state.activeProviderId,
                keyPresentFor = state.keyPresentFor,
                onSelect = viewModel::onSelectProvider,
            )

            HorizontalDivider()

            KeyEntrySection(
                state = state,
                onKeyInputChange = viewModel::onKeyInputChange,
                onSaveKey = viewModel::onSaveKey,
                onRemoveKey = { viewModel.onRemoveKey(state.selectedProviderId) },
            )

            // Modell-Katalog nur anbieten, wenn für den gewählten Provider ein Key da ist.
            if (state.selectedProviderId in state.keyPresentFor) {
                Text(
                    "Aktives Modell: ${state.activeModelId ?: "Standard (${state.selectedProvider.defaultModel})"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { onChooseModel(state.selectedProviderId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Modell wählen")
                }
            }

            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Fertig")
            }
        }
    }
}

// Provider-Auswahlliste (Radio + "✓ Key"-Marker + "● Aktiv" für den app-weit aktiven).
@Composable
private fun ProviderList(
    providers: List<de.overlai.llm.ProviderConfig>,
    selectedId: String,
    activeId: String,
    keyPresentFor: Set<String>,
    onSelect: (String) -> Unit,
) {
    providers.forEach { provider ->
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = provider.id == selectedId,
                        onClick = { onSelect(provider.id) },
                    ).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = provider.id == selectedId,
                onClick = { onSelect(provider.id) },
            )
            Text(provider.displayName, modifier = Modifier.weight(1f))
            if (provider.id == activeId) {
                Text(
                    "● Aktiv",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            if (provider.id in keyPresentFor) {
                Text("✓ Key", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// Key-Eingabe für den GEWÄHLTEN Provider — mit klarer Zuordnung + Hinweis-URL.
@Composable
private fun KeyEntrySection(
    state: OnboardingUiState,
    onKeyInputChange: (String) -> Unit,
    onSaveKey: () -> Unit,
    onRemoveKey: () -> Unit,
) {
    Text(
        "API-Key für ${state.selectedProvider.displayName}",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    if (state.selectedKeyHint.isNotEmpty()) {
        Text(
            "Key erstellen/kopieren unter: ${state.selectedKeyHint}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    OutlinedTextField(
        value = state.apiKeyInput,
        onValueChange = onKeyInputChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("${state.selectedProvider.displayName}-API-Key") },
        placeholder = { Text("Key hier einfügen") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )

    state.savedMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

    Button(
        onClick = onSaveKey,
        enabled = state.apiKeyInput.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Key speichern")
    }

    if (state.selectedProviderId in state.keyPresentFor) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Key hinterlegt.", modifier = Modifier.weight(1f).padding(start = 8.dp))
                TextButton(onClick = onRemoveKey) {
                    Text("Entfernen")
                }
            }
        }
    }
}
