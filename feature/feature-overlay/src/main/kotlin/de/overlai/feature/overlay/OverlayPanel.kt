package de.overlai.feature.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.overlai.core.ui.theme.OverlAiTheme
import de.overlai.llm.Role

// CHANGE-MARKER v0.5.2: Overlay-Bubble Chat (M3.2, siehe CHANGELOG.md)
// Das aufgeklappte Panel: ein kompakter Chat über anderen Apps. Nutzt den vom Service
// gehaltenen OverlayChatState (überdauert das Auf-/Zuklappen) und die ConversationEngine
// dahinter. Bewusst schmal gehalten — es schwebt über der Fremd-App, kein Vollbild-Chat.
@Composable
internal fun OverlayPanel(
    chat: OverlayChatState,
    onClose: () -> Unit,
) {
    OverlAiTheme {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("OverlAI", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Neuer Chat: Verlauf leeren (+ laufenden Stream stoppen).
                        IconButton(onClick = { chat.reset() }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Neuer Chat")
                        }
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, contentDescription = "Schließen")
                        }
                    }
                }

                MessageList(chat)
                Composer(chat)
            }
        }
    }
}

@Composable
private fun MessageList(chat: OverlayChatState) {
    val listState = rememberLazyListState()
    // Bei neuer Nachricht/Delta ans Ende scrollen.
    LaunchedEffect(chat.messages.size, chat.messages.lastOrNull()?.text) {
        if (chat.messages.isNotEmpty()) listState.animateScrollToItem(chat.messages.lastIndex)
    }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        // heightIn: das Panel wächst mit dem Verlauf, bleibt aber gedeckelt (Overlay).
        modifier = Modifier.fillMaxWidth().heightIn(min = 0.dp, max = 280.dp).padding(vertical = 8.dp),
    ) {
        items(chat.messages) { msg -> MessageBubble(msg) }
    }
}

@Composable
private fun MessageBubble(msg: OverlayChatState.UiMessage) {
    val isUser = msg.role == Role.USER
    val bubbleColor =
        if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = bubbleColor,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.padding(horizontal = 2.dp),
        ) {
            Text(
                text = msg.text.ifEmpty { "…" },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun Composer(chat: OverlayChatState) {
    var input by remember { mutableStateOf("") }
    val streaming by chat.isStreaming

    fun submit() {
        chat.send(input)
        input = ""
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text("Frag OverlAI…") },
            enabled = !streaming,
            singleLine = true,
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { submit() }),
            keyboardOptions =
                androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
            modifier = Modifier.weight(1f),
        )
        if (streaming) {
            // Während des Streamens wird der Senden- zum Stopp-Button.
            IconButton(onClick = { chat.cancelStream() }) {
                Icon(Icons.Filled.Stop, contentDescription = "Stopp")
            }
        } else {
            IconButton(
                onClick = ::submit,
                enabled = input.isNotBlank(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Senden")
            }
        }
    }
}
