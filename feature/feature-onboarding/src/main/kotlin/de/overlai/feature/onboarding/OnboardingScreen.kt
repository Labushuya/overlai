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

            state.providers.forEach { provider ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = provider.id == state.selectedProviderId,
                                onClick = { viewModel.onSelectProvider(provider.id) },
                            ).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = provider.id == state.selectedProviderId,
                        onClick = { viewModel.onSelectProvider(provider.id) },
                    )
                    Text(provider.displayName, modifier = Modifier.weight(1f))
                    if (provider.id in state.keyPresentFor) {
                        Text("✓ Key", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            OutlinedTextField(
                value = state.apiKeyInput,
                onValueChange = viewModel::onKeyInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API-Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )

            state.savedMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            Button(
                onClick = viewModel::onSaveKey,
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
                        TextButton(onClick = { viewModel.onRemoveKey(state.selectedProviderId) }) {
                            Text("Entfernen")
                        }
                    }
                }
            }

            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Fertig")
            }
        }
    }
}
