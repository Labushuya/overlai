package de.overlai.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// CHANGE-MARKER v0.5.2: Overlay-Bubble (M3, siehe CHANGELOG.md)
// Ein/Aus-Schalter der Overlay-Bubble. Bewusst stateless: der Screen kennt nur den
// aktuellen Zustand + die Permission-Lage und meldet Wünsche per Callback nach oben.
// Permission-Check und Service-Start/Stop passieren in :app (kein feature->feature).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlaySettingsScreen(
    enabled: Boolean,
    hasOverlayPermission: Boolean,
    onToggle: (Boolean) -> Unit,
    onRequestPermission: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Overlay-Bubble") },
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Bubble anzeigen", fontWeight = FontWeight.Bold)
                    Text(
                        "Blendet OverlAI als schwebende Bubble über anderen Apps ein.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    // Ohne Overlay-Berechtigung lässt sich die Bubble nicht einschalten.
                    enabled = hasOverlayPermission,
                )
            }

            if (!hasOverlayPermission) {
                Text(
                    "Dafür braucht OverlAI die Berechtigung „Über anderen Apps anzeigen\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = onRequestPermission) {
                    Text("Berechtigung erteilen")
                }
            }
        }
    }
}
