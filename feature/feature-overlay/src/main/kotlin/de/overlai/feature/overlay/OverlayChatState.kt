package de.overlai.feature.overlay

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import de.overlai.conversation.ConversationEngine
import de.overlai.llm.ChatMessage
import de.overlai.llm.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

// CHANGE-MARKER v0.5.2: Overlay-Bubble Chat (M3.2, siehe CHANGELOG.md)
// Schlanker State-Holder für den Overlay-Chat. Bewusst KEIN ViewModel: das Panel lebt
// im Service (nicht in einer Activity/NavGraph), der Zustand soll das Auf-/Zuklappen
// des Panels überdauern und wird darum vom Service gehalten, nicht vom ComposeView.
//
// Hält die sichtbare Nachrichtenliste (SnapshotStateList -> Compose rekomponiert bei
// Änderung) und streamt über die ConversationEngine. Akkumuliert Deltas in die letzte
// Assistant-Nachricht (gleiches Muster wie ChatViewModel, nur ohne dessen UI-State).
internal class OverlayChatState(
    private val engine: ConversationEngine,
    private val scope: CoroutineScope,
) {
    // role == USER | ASSISTANT; streaming markiert die noch wachsende Antwort-Bubble.
    data class UiMessage(
        val role: Role,
        val text: String,
        val streaming: Boolean = false,
    )

    val messages: SnapshotStateList<UiMessage> = mutableStateListOf()
    val isStreaming = mutableStateOf(false)

    // Der laufende Stream-Job — damit er per cancelStream() abgebrochen werden kann.
    private var streamJob: Job? = null

    // Nutzer-Nachricht senden + Antwort streamen. No-Op während bereits gestreamt wird
    // oder bei leerer Eingabe.
    fun send(input: String) {
        val text = input.trim()
        if (text.isEmpty() || isStreaming.value) return

        messages.add(UiMessage(Role.USER, text))
        // Leere, als "streaming" markierte Assistant-Bubble, die die Deltas füllen.
        messages.add(UiMessage(Role.ASSISTANT, "", streaming = true))
        isStreaming.value = true

        // Verlauf für die Engine: alle abgeschlossenen Nachrichten (die noch leere
        // streamende Platzhalter-Bubble wird ausgelassen).
        val history =
            messages
                .filterNot { it.role == Role.ASSISTANT && it.streaming }
                .map { ChatMessage(it.role, it.text) }

        val builder = StringBuilder()
        streamJob =
            engine
                .stream(history)
                .onEach { event ->
                    when (event) {
                        is ConversationEngine.Event.Delta -> {
                            builder.append(event.text)
                            updateLastAssistant(builder.toString(), streaming = true)
                        }
                        ConversationEngine.Event.Done -> Unit // Abschluss via onCompletion
                        is ConversationEngine.Event.Failed ->
                            updateLastAssistant(event.message, streaming = false)
                    }
                }.onCompletion {
                    // Streaming-Flag der letzten Assistant-Bubble löschen (Erfolg wie Fehler
                    // wie Abbruch). Bei Abbruch mit leerem Text einen Hinweis setzen.
                    val last = messages.lastOrNull()
                    if (last != null && last.role == Role.ASSISTANT && last.streaming) {
                        val shown = last.text.ifEmpty { "(abgebrochen)" }
                        updateLastAssistant(shown, streaming = false)
                    }
                    isStreaming.value = false
                }.launchIn(scope)
    }

    // Laufenden Stream abbrechen (Stopp-Button). onCompletion räumt das Streaming-Flag ab.
    fun cancelStream() {
        streamJob?.cancel()
    }

    // Verlauf leeren + laufenden Stream stoppen (Reset-Button). Setzt die Konversation
    // zurück — auch damit die an die Engine gereichte History nicht unbegrenzt wächst.
    fun reset() {
        streamJob?.cancel()
        messages.clear()
        isStreaming.value = false
    }

    private fun updateLastAssistant(
        text: String,
        streaming: Boolean,
    ) {
        val idx = messages.indexOfLast { it.role == Role.ASSISTANT }
        if (idx >= 0) {
            messages[idx] = messages[idx].copy(text = text, streaming = streaming)
        }
    }

    // Vom Service beim Anzeigen aufgerufen: einmalig eine "kein Key"-Warnung einblenden,
    // falls für den aktiven Provider kein API-Key hinterlegt ist.
    fun checkKey() {
        scope.launch {
            if (!engine.hasKeyForActive() && messages.isEmpty()) {
                val name = engine.activeConfig().displayName
                messages.add(
                    UiMessage(
                        Role.ASSISTANT,
                        "Kein API-Key für $name hinterlegt. In den Einstellungen einen Key eintragen.",
                    ),
                )
            }
        }
    }
}
