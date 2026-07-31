package de.overlai.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// CHANGE-MARKER v0.2.1: Über & Datenschutz (siehe CHANGELOG.md)
// Kurzinfo: BYOK-Trust, Version, Links zu Repo/PRIVACY/SECURITY (per ACTION_VIEW,
// kein In-App-Markdown-Renderer). versionName wird von :app reingereicht.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    versionName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Über & Datenschutz") },
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Deine Keys bleiben auf dem Gerät", fontWeight = FontWeight.Bold)
                    Text(
                        "OverlAI speichert API-Keys verschlüsselt lokal (Android Keystore). " +
                            "Anfragen gehen direkt an den Provider — kein Backend, keine Telemetrie.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text(versionName) },
            )
            TextButton(onClick = { open("https://github.com/Labushuya/overlai") }) {
                Text("GitHub-Repository")
            }
            TextButton(onClick = { open("https://github.com/Labushuya/overlai/blob/main/PRIVACY.md") }) {
                Text("Datenschutz (PRIVACY.md)")
            }
            TextButton(onClick = { open("https://github.com/Labushuya/overlai/blob/main/SECURITY.md") }) {
                Text("Sicherheit (SECURITY.md)")
            }
            Text(
                "Lizenz: MIT",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
