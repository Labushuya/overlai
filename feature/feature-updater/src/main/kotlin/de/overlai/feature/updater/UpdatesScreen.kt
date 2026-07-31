package de.overlai.feature.updater

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// CHANGE-MARKER v0.2.1: In-App-Updater-UI (siehe CHANGELOG.md)
// Zeigt aktuelle Version, prüft auf Updates und führt Download + Installation.
// Macht transparent, dass der System-Install-Dialog unvermeidbar ist (Sideload).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    viewModel: UpdateViewModel,
    onBack: () -> Unit,
    onFixInstallPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Updates & Version") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val s = state) {
                is UpdateViewModel.UiState.Idle -> {
                    VersionRow(s.current)
                    Button(onClick = viewModel::check, modifier = Modifier.fillMaxWidth()) {
                        Text("Auf Updates prüfen")
                    }
                }
                UpdateViewModel.UiState.Checking -> Loading("Prüfe auf Updates …")
                is UpdateViewModel.UiState.UpToDate -> {
                    VersionRow(s.current)
                    Text("Du hast die aktuelle Version.", color = MaterialTheme.colorScheme.primary)
                    Button(onClick = viewModel::check, modifier = Modifier.fillMaxWidth()) {
                        Text("Erneut prüfen")
                    }
                }
                is UpdateViewModel.UiState.Available -> {
                    Text("Update verfügbar: ${s.manifest.versionName}", fontWeight = FontWeight.Bold)
                    s.manifest.releaseNotes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    InstallHint()
                    Button(
                        onClick = { viewModel.download(s.manifest) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Herunterladen") }
                }
                UpdateViewModel.UiState.Downloading -> Loading("Lade herunter & prüfe Integrität …")
                is UpdateViewModel.UiState.Ready -> {
                    Text("Bereit zur Installation: ${s.versionName}", fontWeight = FontWeight.Bold)
                    InstallHint()
                    Button(onClick = { viewModel.install(s.apk) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Installieren")
                    }
                }
                is UpdateViewModel.UiState.NeedsInstallPermission -> {
                    Text(
                        "Zum Installieren muss „Unbekannte Apps installieren“ für OverlAI erlaubt sein.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = onFixInstallPermission, modifier = Modifier.fillMaxWidth()) {
                        Text("Berechtigung erteilen")
                    }
                    Button(onClick = { viewModel.install(s.apk) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Erneut versuchen")
                    }
                }
                is UpdateViewModel.UiState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Text(s.message, modifier = Modifier.padding(12.dp))
                    }
                    Button(onClick = viewModel::check, modifier = Modifier.fillMaxWidth()) {
                        Text("Erneut prüfen")
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionRow(version: String) {
    Text("Installierte Version: $version", style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun Loading(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator()
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun InstallHint() {
    Text(
        "Android zeigt beim Installieren einen Bestätigungsdialog — das lässt sich nicht umgehen.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
