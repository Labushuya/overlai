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
import androidx.compose.material3.HorizontalDivider
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

// CHANGE-MARKER: Bubble-UX-Block (P2.1c, siehe CHANGELOG.md)
// Ein/Aus-Schalter der Overlay-Bubble. Bewusst stateless: der Screen kennt nur den
// aktuellen Zustand + die Permission-Lage und meldet Wünsche per Callback nach oben.
// Permission-Check und Service-Start/Stop passieren in :app (kein feature->feature).
// P2.1c: Toggle ist UNABHÄNGIG von der Berechtigung — der Wunsch „Bubble an" wird immer
// gespeichert; fehlt die Berechtigung, erscheint nur ein Hinweis mit Weg ins Berechtigungs-
// Menü. Erteilen/Entziehen der Berechtigung schaltet die Bubble also nicht mehr um.
// Ein einfacher An/Aus-Zugang (Zustand + Umschalt-Callback) — bündelt Parameter.
data class AccessToggle(
    val enabled: Boolean,
    val onToggle: (Boolean) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlaySettingsScreen(
    enabled: Boolean,
    hasOverlayPermission: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpenPermissions: () -> Unit,
    onBack: () -> Unit,
    notification: AccessToggle,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Zugänge") },
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
                // Immer bedienbar — die Berechtigung ist davon entkoppelt (siehe Header).
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                )
            }

            // Nur ein Hinweis (kein Sperren des Toggles): die Bubble erscheint erst, wenn die
            // Berechtigung im Berechtigungs-Menü erteilt ist. Der Toggle-Wunsch bleibt gewahrt.
            if (enabled && !hasOverlayPermission) {
                Text(
                    "Die Bubble erscheint erst, wenn die Berechtigung „Über anderen Apps anzeigen\" " +
                        "erteilt ist. Alle Bubble-Berechtigungen findest du im Berechtigungs-Menü.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = onOpenPermissions) {
                    Text("Zum Berechtigungs-Menü")
                }
            }

            HorizontalDivider()

            // P2.4: Benachrichtigungs-Zugang — persistente Notification mit Direktantwort.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Benachrichtigungs-Zugang", fontWeight = FontWeight.Bold)
                    Text(
                        "Dauerhafte Benachrichtigung: Chat direkt öffnen und per Direktantwort " +
                            "schreiben. Braucht die Benachrichtigungs-Berechtigung.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = notification.enabled,
                    onCheckedChange = notification.onToggle,
                )
            }
        }
    }
}
