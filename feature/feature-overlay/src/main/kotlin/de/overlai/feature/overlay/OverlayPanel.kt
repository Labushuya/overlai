package de.overlai.feature.overlay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.overlai.conversation.ChatUiMessage
import de.overlai.core.data.chat.ChatSession
import de.overlai.core.ui.theme.OverlAiTheme
import de.overlai.llm.ModelInfo
import de.overlai.llm.Role

// CHANGE-MARKER: Bubble-UX-Block (P2.1c, siehe CHANGELOG.md)
// Das aufgeklappte Panel: VOLLE Steuerung wie die Haupt-App, aber bubble-kompakt (Icons
// statt Text, schmale Zeilen). Interne Navigation ohne NavHost — schlichter PanelScreen-
// State: Liste ⇄ Chat ⇄ Modelle. Alle Fähigkeiten kommen aus OverlayChatState (dieselbe
// persistente Session-Welt wie der Fullscreen-Chat).
internal enum class PanelScreen { LIST, CHAT, MODELS }

// Aktionen der Panel-Kopfzeile — gebündelt, damit PanelHeader nicht zu viele Parameter hat.
private class HeaderActions(
    val onList: () -> Unit,
    val onNew: () -> Unit,
    val onModels: () -> Unit,
    val onBackToChat: () -> Unit,
    val onClose: () -> Unit,
    val onDrag: (Int, Int) -> Unit,
    val onDragEnd: () -> Unit,
)

@Composable
internal fun OverlayPanel(
    chat: OverlayChatState,
    onClose: () -> Unit,
    onHeaderDrag: (Int, Int) -> Unit = { _, _ -> },
    onHeaderDragEnd: () -> Unit = {},
) {
    var screen by remember { mutableStateOf(PanelScreen.CHAT) }
    OverlAiTheme {
        Card(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(10.dp).fillMaxHeight()) {
                PanelHeader(
                    screen = screen,
                    actions =
                        HeaderActions(
                            onList = { screen = PanelScreen.LIST },
                            onNew = {
                                chat.newChat()
                                screen = PanelScreen.CHAT
                            },
                            onModels = { screen = PanelScreen.MODELS },
                            onBackToChat = { screen = PanelScreen.CHAT },
                            onClose = onClose,
                            onDrag = onHeaderDrag,
                            onDragEnd = onHeaderDragEnd,
                        ),
                )
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (screen) {
                        PanelScreen.LIST ->
                            ListScreen(
                                chat = chat,
                                onOpen = { screen = PanelScreen.CHAT },
                            )
                        PanelScreen.CHAT -> ChatScreenContent(chat)
                        PanelScreen.MODELS ->
                            ModelsScreen(
                                chat = chat,
                                onDone = { screen = PanelScreen.CHAT },
                            )
                    }
                }
            }
        }
    }
}

// Kompakte Kopfzeile: nur Icons. Links kontextabhängig (Liste bzw. Zurück-zu-Chat),
// rechts Neu/Modelle/Schließen. Der Titel-Bereich in der Mitte ist ein Drag-Griff (P2.1c/D):
// Ziehen verschiebt das Panel; beim Loslassen wandert die Bubble dorthin (zu → auf).
@Composable
private fun PanelHeader(
    screen: PanelScreen,
    actions: HeaderActions,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (screen == PanelScreen.CHAT) {
            IconButton(onClick = actions.onList) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Chats")
            }
        } else {
            IconButton(onClick = actions.onBackToChat) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück zum Chat")
            }
        }
        // Drag-Griff: nur dieser mittlere Bereich reagiert auf Ziehen (Icons bleiben tippbar,
        // Chat-Scroll darunter kollidiert nicht).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { actions.onDragEnd() },
                        ) { change, dragAmount ->
                            change.consume()
                            actions.onDrag(dragAmount.x.toInt(), dragAmount.y.toInt())
                        }
                    },
        ) {
            Icon(
                Icons.Filled.DragIndicator,
                contentDescription = "Verschieben",
                modifier = Modifier.padding(end = 4.dp),
            )
            Text(text = "OverlAI", style = MaterialTheme.typography.titleSmall)
        }
        IconButton(onClick = actions.onNew) {
            Icon(Icons.Filled.Add, contentDescription = "Neuer Chat")
        }
        IconButton(onClick = actions.onModels) {
            Icon(Icons.Filled.Tune, contentDescription = "Modell wählen")
        }
        IconButton(onClick = actions.onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Schließen")
        }
    }
}

// --- LIST: kompakte Session-Liste (aktive markiert), Tap → Chat, Papierkorb-Icon löscht ---
@Composable
private fun ListScreen(
    chat: OverlayChatState,
    onOpen: () -> Unit,
) {
    val sessions by chat.sessions.collectAsStateWithLifecycle()
    val activeId by chat.activeSessionId.collectAsStateWithLifecycle()
    if (sessions.isEmpty()) {
        Text(
            "Noch keine Chats. Tippe auf ＋, um einen zu starten.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(8.dp),
        )
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(sessions, key = { it.id }) { session ->
            SessionRow(
                session = session,
                active = session.id == activeId,
                onOpen = {
                    chat.switchTo(session.id)
                    onOpen()
                },
                onDelete = { chat.delete(session.id) },
            )
        }
    }
}

@Composable
private fun SessionRow(
    session: ChatSession,
    active: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val container =
        if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Surface(
        color = container,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp),
        ) {
            Text(
                text = session.title.ifBlank { "Neuer Chat" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp)
                        .clickableRow(onOpen),
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "Löschen")
            }
        }
    }
}

// --- CHAT: der Verlauf der aktiven Session + Composer (wie bisher, kompakt) ---
@Composable
private fun ChatScreenContent(chat: OverlayChatState) {
    val s by chat.state.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxHeight()) {
        MessageList(s.messages, modifier = Modifier.weight(1f))
        Composer(streaming = s.isStreaming, onSend = { chat.send(it) })
    }
}

@Composable
private fun MessageList(
    messages: List<ChatUiMessage>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        items(messages) { msg -> MessageBubble(msg) }
    }
}

@Composable
private fun MessageBubble(msg: ChatUiMessage) {
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
private fun Composer(
    streaming: Boolean,
    onSend: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }

    fun submit() {
        onSend(input)
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
        IconButton(
            onClick = ::submit,
            enabled = !streaming && input.isNotBlank(),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Senden")
        }
    }
}

// --- MODELS: Provider-Auswahl → Modelle des Providers; Tap setzt Provider+Modell der Session ---
@Composable
private fun ModelsScreen(
    chat: OverlayChatState,
    onDone: () -> Unit,
) {
    var providerId by remember { mutableStateOf<String?>(null) }
    if (providerId == null) {
        ProviderPicker(chat = chat, onPick = { providerId = it })
    } else {
        ModelPicker(
            chat = chat,
            providerId = providerId!!,
            onBack = { providerId = null },
            onPick = { modelId ->
                chat.setModel(providerId!!, modelId)
                onDone()
            },
        )
    }
}

@Composable
private fun ProviderPicker(
    chat: OverlayChatState,
    onPick: (String) -> Unit,
) {
    var withKey by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) { withKey = chat.providersWithKey() }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(chat.providers, key = { it.id }) { p ->
            val hasKey = p.id in withKey
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().clickableRow { if (hasKey) onPick(p.id) },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                ) {
                    Text(p.displayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        text = if (hasKey) "" else "kein Key",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelPicker(
    chat: OverlayChatState,
    providerId: String,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
) {
    var models by remember(providerId) { mutableStateOf<List<ModelInfo>?>(null) }
    LaunchedEffect(providerId) {
        models = chat.listModels(providerId)
    }
    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück zu Providern")
            }
            Text("Modell wählen", style = MaterialTheme.typography.titleSmall)
        }
        val list = models
        when {
            list == null ->
                Text("Lade Modelle…", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp))
            list.isEmpty() ->
                Text(
                    "Keine Modelle (Key fehlt oder Abruf fehlgeschlagen).",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(8.dp),
                )
            else ->
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(list, key = { it.id }) { m ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().clickableRow { onPick(m.id) },
                        ) {
                            Text(
                                text = m.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
        }
    }
}

// Kleiner Klick-Helper (Row als Ganzes klickbar).
private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.then(Modifier.clickable { onClick() })
