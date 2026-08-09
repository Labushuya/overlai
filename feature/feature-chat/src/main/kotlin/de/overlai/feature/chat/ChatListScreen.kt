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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.overlai.core.data.chat.ChatSession
import de.overlai.core.ui.components.ProviderModelChip
import de.overlai.llm.providers.ProviderRegistry

// CHANGE-MARKER: Chat-Organisation & Modell-UX (Phase 3, siehe CHANGELOG.md)
// Übersicht aller Chat-Sessions: öffnen (Tap), neu (FAB → geführtes NewChatSheet),
// umbenennen/löschen (Overflow-Menü ⋮). Je Zeile Titel + Anbieter/Modell-Chip.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel,
    newChatViewModel: NewChatViewModel,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()

    // Aktive Dialoge/Sheets als lokaler UI-State.
    var showNewChat by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ChatSession?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatSession?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Chats") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewChat = true }) {
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
                        onRename = { renameTarget = session },
                        onDelete = { deleteTarget = session },
                    )
                }
            }
        }
    }

    if (showNewChat) {
        NewChatSheet(
            viewModel = newChatViewModel,
            onDismiss = { showNewChat = false },
            onCreated = { id ->
                showNewChat = false
                onOpenSession(id)
            },
        )
    }

    renameTarget?.let { target ->
        RenameDialog(
            initial = target.title,
            onConfirm = { newTitle ->
                viewModel.rename(target.id, newTitle)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { target ->
        DeleteChatDialog(
            title = target.title,
            onConfirm = {
                viewModel.delete(target.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun DeleteChatDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chat löschen?") },
        text = {
            Text(
                "„$title“ und der gesamte Verlauf werden gelöscht. " +
                    "Das kann nicht rückgängig gemacht werden.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Löschen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

@Composable
private fun SessionRow(
    session: ChatSession,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val providerName = ProviderRegistry.byId(session.providerId)?.displayName ?: session.providerId
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(session.title, maxLines = 1) },
        supportingContent = {
            ProviderModelChip(
                providerName = providerName,
                modelId = session.modelId,
                modifier = Modifier.padding(top = 4.dp),
            )
        },
        trailingContent = {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Mehr")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Umbenennen") },
                    onClick = {
                        menuOpen = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Löschen") },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        },
        modifier = Modifier.fillMaxWidth().clickable { onOpen() },
    )
}

@Composable
private fun RenameDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chat umbenennen") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Titel") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
            ) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}
