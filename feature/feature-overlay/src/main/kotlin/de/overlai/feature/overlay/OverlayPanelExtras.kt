package de.overlai.feature.overlay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.overlai.core.data.chat.Project

// CHANGE-MARKER: Overlay-Parität (Phase 3 E3c, siehe CHANGELOG.md)
// Kompakte Panel-Bausteine für Usage-Anzeige + Handover + Umbenennen/Verschieben-Dialoge.
// Eigenständig nachgebaut, weil die feature-chat-Composables `internal` (anderes Modul) sind.

// Usage-/Aktionsleiste über dem Verlauf: Token/Kontext-Anzeige + Handover- & Umbenennen-Button.
@Composable
internal fun PanelUsageBar(
    promptTokens: Int,
    completionTokens: Int,
    contextLimit: Int?,
    onHandover: () -> Unit,
    onRename: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
    ) {
        val used = promptTokens
        val fraction = contextLimit?.takeIf { it > 0 }?.let { used.toFloat() / it }
        val warn = fraction != null && fraction >= 0.8f
        val label =
            when {
                promptTokens <= 0 && completionTokens <= 0 -> ""
                contextLimit != null -> "$used/$contextLimit (${((fraction ?: 0f) * 100).toInt()}%)"
                else -> "${promptTokens + completionTokens} Tok"
            }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Edit, contentDescription = "Umbenennen")
        }
        IconButton(onClick = onHandover, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.SwapHoriz, contentDescription = "Handover erstellen")
        }
    }
}

@Composable
internal fun PanelInfoDialog(
    title: String,
    body: String,
) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text(title) },
        text = { Text(body) },
    )
}

@Composable
internal fun PanelHandoverPreview(
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Handover prüfen") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Neue Session starten") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
internal fun PanelTextInputDialog(
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
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
internal fun PanelMoveDialog(
    projects: List<Project>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("In Projekt verschieben") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
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
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
