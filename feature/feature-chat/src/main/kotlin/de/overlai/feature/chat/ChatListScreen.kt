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
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.overlai.core.data.chat.ChatSession
import de.overlai.core.data.chat.Project
import de.overlai.core.ui.components.ProviderModelChip
import de.overlai.llm.providers.ProviderRegistry

// CHANGE-MARKER: Projekte/Gruppen (Phase 3 E2, siehe CHANGELOG.md)
// Übersicht aller Chats, gruppiert nach Projekt (+ „Ohne Projekt"). Chat: öffnen (Tap),
// neu (FAB), umbenennen/löschen/verschieben (⋮). Projekt: anlegen (Header), umbenennen/
// löschen (⋮ am Section-Header).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel,
    newChatViewModel: NewChatViewModel,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val dialogs = rememberChatListDialogState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Chats") },
                actions = {
                    IconButton(onClick = { dialogs.newProject.value = true }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "Neues Projekt")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { dialogs.showNewChat.value = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Neuer Chat")
            }
        },
    ) { padding ->
        if (groups.isEmpty()) {
            EmptyState(Modifier.padding(padding))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                groups.forEach { group ->
                    item(key = "hdr-${group.project?.id ?: "none"}") {
                        GroupHeader(
                            project = group.project,
                            onRename = { dialogs.renameProject.value = group.project },
                            onDelete = { group.project?.let { viewModel.deleteProject(it.id) } },
                        )
                    }
                    items(group.chats, key = { it.id }) { session ->
                        SessionRow(
                            session = session,
                            onOpen = {
                                viewModel.open(session.id)
                                onOpenSession(session.id)
                            },
                            onRename = { dialogs.renameChat.value = session },
                            onDelete = { dialogs.deleteChat.value = session },
                            onMove = { dialogs.moveChat.value = session },
                        )
                    }
                }
            }
        }
    }

    ChatListDialogs(
        viewModel = viewModel,
        newChatViewModel = newChatViewModel,
        dialogs = dialogs,
        projects = projects,
        onOpenSession = onOpenSession,
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
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
}

@Composable
private fun GroupHeader(
    project: Project?,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = {
            Text(
                project?.name ?: "Ohne Projekt",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent =
            if (project != null) {
                {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Projekt-Menü")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Umbenennen") }, onClick = {
                            menuOpen = false
                            onRename()
                        })
                        DropdownMenuItem(text = { Text("Projekt löschen") }, onClick = {
                            menuOpen = false
                            onDelete()
                        })
                    }
                }
            } else {
                null
            },
    )
}

@Composable
private fun SessionRow(
    session: ChatSession,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
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
                DropdownMenuItem(text = { Text("Umbenennen") }, onClick = {
                    menuOpen = false
                    onRename()
                })
                DropdownMenuItem(text = { Text("In Projekt verschieben") }, onClick = {
                    menuOpen = false
                    onMove()
                })
                DropdownMenuItem(text = { Text("Löschen") }, onClick = {
                    menuOpen = false
                    onDelete()
                })
            }
        },
        modifier = Modifier.fillMaxWidth().clickable { onOpen() },
    )
}
