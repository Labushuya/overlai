package de.overlai.feature.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// CHANGE-MARKER v0.1.0: Permission Hub (siehe CHANGELOG.md)
// Permission Hub (lite). Live grün/rot je Voraussetzung + "Fix"-Button, der in
// genau die richtige Systemseite deeplinkt. Löst den Nutzer-Schmerzpunkt
// "Bubble unsichtbar, weil eine versteckte Einstellung aus war".
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionHubScreen(
    state: PermissionHubState,
    onFix: (PermissionItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Berechtigungen & Status") }) },
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
                "OverlAI prüft hier alle Voraussetzungen. Roter Status = tippe auf „Fix", " +
                    "um direkt zur richtigen Einstellung zu springen.",
                style = MaterialTheme.typography.bodyMedium,
            )
            state.items.forEach { item -> PermissionRow(item, onFix) }

            // Klarstellung des Nutzer-Schmerzpunkts.
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Hinweis zur Bubble", fontWeight = FontWeight.Bold)
                    Text(
                        "Die schwebende OverlAI-Bubble (ab Phase 2) nutzt „Über anderen Apps anzeigen" " +
                            "— NICHT die System-„Bubbles". Sie kann also nicht durch einen deaktivierten " +
                            "Bubble-Schalter unsichtbar werden.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    item: PermissionItem,
    onFix: (PermissionItem) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (item.granted) "✅" else "❌",
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.Medium)
                Text(item.rationale, style = MaterialTheme.typography.bodySmall)
            }
            if (!item.granted) {
                TextButton(onClick = { onFix(item) }) { Text("Fix") }
            }
        }
    }
}
