package de.overlai.feature.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
// P2.1c-Politur: Zurück-Pfeil (wie alle Sub-Screens) + kräftigere Karten-Abgrenzung
// (Elevation + abgesetzte Container-Farbe), damit das UI nicht zur Fläche verschwimmt.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionHubScreen(
    state: PermissionHubState,
    onFix: (PermissionItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Berechtigungen & Status") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
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
                "OverlAI prüft hier alle Voraussetzungen. Roter Status = tippe auf „Fix“, " +
                    "um direkt zur richtigen Einstellung zu springen.",
                style = MaterialTheme.typography.bodyMedium,
            )
            state.items.forEach { item -> PermissionRow(item, onFix) }

            // Klarstellung des Nutzer-Schmerzpunkts — abgesetzt (tertiaryContainer).
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Hinweis zur Bubble", fontWeight = FontWeight.Bold)
                    Text(
                        "Die schwebende OverlAI-Bubble (ab Phase 2) nutzt „Über anderen Apps anzeigen“ " +
                            "— NICHT die System-„Bubbles“. Sie kann also nicht durch einen deaktivierten " +
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
    // Karte je Status klar abgesetzt: erfüllt = neutrale Fläche, offen = errorContainer
    // (rötlich, „Handlung nötig"), Info = neutral. Elevation hebt jede Karte vom Hintergrund ab.
    val container =
        when {
            item.isInfo -> MaterialTheme.colorScheme.surfaceVariant
            item.granted -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.errorContainer
        }
    val statusIcon =
        when {
            item.isInfo -> Icons.Filled.Info
            item.granted -> Icons.Filled.CheckCircle
            else -> Icons.Filled.Error
        }
    val statusTint =
        when {
            item.isInfo -> MaterialTheme.colorScheme.primary
            item.granted -> GRANTED_GREEN
            else -> MaterialTheme.colorScheme.error
        }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusTint,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.Medium)
                Text(item.rationale, style = MaterialTheme.typography.bodySmall)
            }
            // Info-Items haben keinen Fix (nur Erklärung); erfüllte auch nicht.
            if (!item.isInfo && !item.granted) {
                Button(onClick = { onFix(item) }) { Text("Fix") }
            }
        }
    }
}

// Grün gibt es in Material3 nicht als Rollenfarbe — fester Ton für „erfüllt".
private val GRANTED_GREEN = androidx.compose.ui.graphics.Color(0xFF2E7D32)
