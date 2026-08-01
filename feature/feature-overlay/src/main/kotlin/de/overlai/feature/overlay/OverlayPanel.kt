package de.overlai.feature.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.overlai.core.ui.theme.OverlAiTheme

// CHANGE-MARKER v0.5.2: Overlay-Bubble (M3, siehe CHANGELOG.md)
// Das aufgeklappte Panel. M3-Skelett: bewusst statischer Platzhalter — hier wird in
// M3.2 der Mini-Chat einziehen (eigene List<ChatMessage> + ConversationEngine.stream()
// via Hilt-@EntryPoint, analog feature-share/ShareDependencies). Noch KEIN LLM.
@Composable
internal fun OverlayPanel(onClose: () -> Unit) {
    OverlAiTheme {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "OverlAI",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Schließen",
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text =
                        "Die Bubble läuft. Der KI-Chat über anderen Apps folgt im " +
                            "nächsten Schritt (M3.2).",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}
