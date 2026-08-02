package de.overlai.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.overlai.conversation.ChatUiMessage
import de.overlai.llm.Role

// CHANGE-MARKER v0.1.0: Chat-UI (siehe CHANGELOG.md)
// Kompakter Chat-Screen (Material 3). Streaming-Antworten wachsen live; ein
// fehlender API-Key wird als klarer Hinweis mit Onboarding-Verweis gezeigt.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("OverlAI · ${state.providerName}") }) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!state.hasApiKey) {
                MissingKeyBanner(onOpenOnboarding)
            }
            state.error?.let { ErrorBanner(it) }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.messages) { msg -> MessageBubble(msg) }
            }

            InputRow(
                input = state.input,
                enabled = !state.isStreaming,
                onChange = viewModel::onInputChange,
                onSend = viewModel::onSend,
            )
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatUiMessage) {
    val isUser = msg.role == Role.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        if (isUser) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                ),
        ) {
            Text(
                text = msg.text.ifEmpty { if (msg.streaming) "…" else "" },
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MissingKeyBanner(onOpenOnboarding: () -> Unit) {
    Card(
        onClick = onOpenOnboarding,
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Kein API-Key hinterlegt. Tippe hier, um einen Provider einzurichten.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "→ Onboarding öffnen",
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            message,
            modifier = Modifier.padding(12.dp),
            textAlign = TextAlign.Start,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputRow(
    input: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Frag OverlAI …") },
            enabled = enabled,
        )
        IconButton(onClick = onSend, enabled = enabled && input.isNotBlank()) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Senden")
        }
    }
}
