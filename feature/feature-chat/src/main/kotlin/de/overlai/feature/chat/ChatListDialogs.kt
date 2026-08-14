package de.overlai.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.overlai.core.data.chat.ChatSession
import de.overlai.core.data.chat.Project

// CHANGE-MARKER: Projekte/Gruppen (Phase 3 E2, siehe CHANGELOG.md)
// UI-State-Halter für die Dialoge/Sheets der Chat-Liste. Bündelt die einzelnen
// mutableState-Felder, damit der Screen und die Dialogs-Composable ohne lange
// Parameterlisten auskommen. Vom Screen via [rememberChatListDialogState] gehalten.
class ChatListDialogState {
    val showNewChat = mutableStateOf(false)
    val renameChat = mutableStateOf<ChatSession?>(null)
    val deleteChat = mutableStateOf<ChatSession?>(null)
    val moveChat = mutableStateOf<ChatSession?>(null)
    val newProject = mutableStateOf(false)
    val renameProject = mutableStateOf<Project?>(null)
}

@Composable
internal fun rememberChatListDialogState(): ChatListDialogState = remember { ChatListDialogState() }

// Rendert die je nach State aktiven Dialoge/Sheets. Nutzt Screen-VM + NewChat-VM.
@Composable
internal fun ChatListDialogs(
    viewModel: ChatListViewModel,
    newChatViewModel: NewChatViewModel,
    dialogs: ChatListDialogState,
    projects: List<Project>,
    onOpenSession: (String) -> Unit,
) {
    if (dialogs.showNewChat.value) {
        NewChatSheet(
            viewModel = newChatViewModel,
            onDismiss = { dialogs.showNewChat.value = false },
            onCreated = { id ->
                dialogs.showNewChat.value = false
                onOpenSession(id)
            },
        )
    }

    dialogs.renameChat.dialogTarget { target, close ->
        TextInputDialog("Chat umbenennen", target.title, "Speichern", {
            viewModel.rename(target.id, it)
            close()
        }, close)
    }

    dialogs.deleteChat.dialogTarget { target, close ->
        ConfirmDeleteDialog(
            title = "Chat löschen?",
            body =
                "„${target.title}“ und der gesamte Verlauf werden gelöscht. " +
                    "Das kann nicht rückgängig gemacht werden.",
            onConfirm = {
                viewModel.delete(target.id)
                close()
            },
            onDismiss = close,
        )
    }

    dialogs.moveChat.dialogTarget { target, close ->
        MoveToProjectSheet(projects = projects, onPick = {
            viewModel.moveChat(target.id, it)
            close()
        }, onDismiss = close)
    }

    if (dialogs.newProject.value) {
        val close = { dialogs.newProject.value = false }
        TextInputDialog("Neues Projekt", "", "Anlegen", {
            viewModel.createProject(it)
            close()
        }, close)
    }

    dialogs.renameProject.dialogTarget { target, close ->
        TextInputDialog("Projekt umbenennen", target.name, "Speichern", {
            viewModel.renameProject(target.id, it)
            close()
        }, close)
    }
}

// Kleiner Helfer: rendert [content] nur, wenn der State ein Ziel hält; liefert Ziel + Close.
@Composable
private inline fun <T> MutableState<T?>.dialogTarget(content: @Composable (T, () -> Unit) -> Unit) {
    value?.let { target -> content(target) { value = null } }
}

@Composable
internal fun TextInputDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Löschen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveToProjectSheet(
    projects: List<Project>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "In Projekt verschieben",
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            item(key = "none") {
                ListItem(
                    headlineContent = { Text("Ohne Projekt") },
                    modifier = Modifier.fillMaxWidth().clickable { onPick(null) },
                )
            }
            items(projects, key = { it.id }) { p ->
                ListItem(
                    headlineContent = { Text(p.name) },
                    modifier = Modifier.fillMaxWidth().clickable { onPick(p.id) },
                )
            }
        }
    }
}
