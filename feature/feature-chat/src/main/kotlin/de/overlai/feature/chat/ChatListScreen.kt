package de.overlai.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.overlai.core.data.chat.ChatSession
import de.overlai.llm.providers.ProviderRegistry

// CHANGE-MARKER: Multi-Chat-Persistenz (P2.1b, siehe CHANGELOG.md)
// Übersicht aller Chat-Sessions: öffnen (Tap), neu (FAB), löschen (Icon). Je Zeile Titel
// + Provider-Badge. Startziel des Chat-Tabs.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Chats") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.newChat(onCreated = onOpenSession) }) {
                Icon(Icons.Filled.Add, contentDescription = "Neuer Chat")
            }
        },
    ) { padding ->
        if (sessions.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Noch keine Chats.", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Tippe auf +, um einen neuen Chat zu starten.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(sessions, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        onOpen = {
                            viewModel.open(session.id)
                            onOpenSession(session.id)
                        },
                        onDelete = { viewModel.delete(session.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: ChatSession,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val providerName = ProviderRegistry.byId(session.providerId)?.displayName ?: session.providerId
    ListItem(
        headlineContent = { Text(session.title, maxLines = 1) },
        supportingContent = {
            Text(
                providerName + (session.modelId?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Chat löschen")
            }
        },
        modifier = Modifier.fillMaxWidth().clickable { onOpen() },
    )
}
