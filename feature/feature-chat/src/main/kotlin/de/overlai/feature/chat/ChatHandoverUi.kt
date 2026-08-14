package de.overlai.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

// CHANGE-MARKER: Kontextfenster/Usage + Handover (Phase 3 E3, siehe CHANGELOG.md)
// UI-Bausteine für Usage-Anzeige + Handover (Overflow-Aktion, Auto-Vorschlag-Banner,
// Lade-/Vorschau-Dialog). Ausgelagert aus ChatScreen.

// Kompakte Token-/Kontext-Anzeige neben dem Modell-Chip. Zeigt „x/y (z%)" bei bekanntem
// Limit (Warnfarbe ab 80%), sonst nur die verbrauchten Tokens. Nichts, solange 0.
@Composable
internal fun UsageLabel(state: ChatUiState) {
    if (state.promptTokens <= 0 && state.completionTokens <= 0) return
    val fraction = state.contextFraction
    val warn = fraction != null && fraction >= 0.8f
    val text =
        if (state.contextLimit != null) {
            val pct = ((fraction ?: 0f) * 100).toInt()
            "${state.promptTokens}/${state.contextLimit} ($pct%)"
        } else {
            "${state.promptTokens + state.completionTokens} Tok"
        }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp),
    )
}

@Composable
internal fun ChatOverflowMenu(
    onHandover: () -> Unit,
    onRename: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    androidx.compose.material3.IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "Mehr")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text("Umbenennen") },
            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
            onClick = {
                open = false
                onRename()
            },
        )
        DropdownMenuItem(
            text = { Text("Handover erstellen") },
            leadingIcon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null) },
            onClick = {
                open = false
                onHandover()
            },
        )
    }
}

// Auto-Vorschlag bei ~vollem Kontext.
@Composable
internal fun HandoverSuggestionBanner(
    onHandover: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                "Kontext fast voll — Handover erstellen für nahtlose Fortsetzung?",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onHandover) { Text("Handover") }
            TextButton(onClick = onDismiss) { Text("Später") }
        }
    }
}

@Composable
internal fun HandoverLoadingDialog() {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text("Handover wird erstellt…") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                Text("Der Verlauf wird zusammengefasst.")
            }
        },
    )
}

// Kontroll-Vorschau: der generierte Handover-Text, bevor die neue Session startet.
@Composable
internal fun HandoverPreviewDialog(
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Handover prüfen") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Neue Session starten") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
