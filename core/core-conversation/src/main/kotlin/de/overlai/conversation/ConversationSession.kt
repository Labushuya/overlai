package de.overlai.conversation

import de.overlai.llm.ChatMessage
import de.overlai.llm.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

// CHANGE-MARKER: Chat-Kern vereinheitlicht (P2.1a, siehe CHANGELOG.md)
// Zustandsbehafteter Konversations-Holder ÜBER der zustandslosen ConversationEngine.
// Zentralisiert die zuvor DREIFACH duplizierte Logik (ChatViewModel, OverlayChatState,
// teils Engine): Verlauf halten, isStreaming, Delta→letzte-Assistant-Bubble-Akkumulation,
// Fehler/Done. Jede Oberfläche hält eine eigene Session, anders gescoped (viewModelScope
// bzw. Service-Scope) — daher kein ViewModel, kein Hilt hier (core-* bleibt DI-frei).
//
// Testbar ohne Android: hängt nur an dem schmalen Streamer-Interface (die Engine
// implementiert es), nicht am ganzen ProviderFactory/SettingsStore/Context-Stack.
class ConversationSession(
    private var streamer: Streamer,
    private val scope: CoroutineScope,
    // Optionale Persistenz. null = flüchtig (nur im Speicher, wie P2.1a). Gesetzt =
    // Verlauf beim Start laden + jede abgeschlossene Nachricht speichern (P2.1b Multi-Chat).
    private val persistence: Persistence? = null,
) {
    // Was die Session von der Engine braucht — ConversationEngine implementiert es.
    interface Streamer {
        fun stream(messages: List<ChatMessage>): Flow<ConversationEngine.Event>

        suspend fun providerDisplayName(): String

        suspend fun hasKey(): Boolean
    }

    // Schmale Persistenz-Schnittstelle (DI-frei; die Room-Anbindung liefert :app/Overlay).
    // Entkoppelt core-conversation vom konkreten SessionRepository/Room (keine Zirkularität).
    interface Persistence {
        // Reaktiv: emittiert bei jeder Änderung am persistierten Verlauf (Room-Flow). Default
        // leer für flüchtige Sessions.
        fun observeHistory(): Flow<List<ChatUiMessage>> = kotlinx.coroutines.flow.flowOf(emptyList())

        suspend fun onUserMessage(text: String)

        suspend fun onAssistantMessage(text: String)
    }

    data class State(
        val messages: List<ChatUiMessage> = emptyList(),
        val isStreaming: Boolean = false,
        val error: String? = null,
        // Token-Verbrauch des letzten Turns (E3): prompt = aktueller Kontext, completion =
        // letzte Antwort. Für die Usage-/Kontextfenster-Anzeige. 0 solange nichts lief.
        val promptTokens: Int = 0,
        val completionTokens: Int = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var streamJob: Job? = null

    init {
        // Persistierten Verlauf reaktiv beobachten (falls Persistenz gesetzt). Während des
        // Streamens NICHT übernehmen — sonst überschreibt der DB-Snapshot die laufende,
        // noch nicht persistierte Assistant-Bubble. Im Ruhezustand ist die DB die Wahrheit.
        persistence?.let { p ->
            scope.launch {
                p.observeHistory().collect { persisted ->
                    if (!_state.value.isStreaming) {
                        _state.value = _state.value.copy(messages = persisted)
                    }
                }
            }
        }
    }

    // Nutzer-Nachricht senden + Antwort streamen. No-Op bei leerer Eingabe oder während
    // bereits gestreamt wird.
    fun send(input: String) {
        val text = input.trim()
        if (text.isEmpty() || _state.value.isStreaming) return

        // User-Bubble + leere, als streamend markierte Assistant-Bubble anhängen.
        _state.value =
            _state.value.copy(
                messages =
                    _state.value.messages +
                        ChatUiMessage(Role.USER, text) +
                        ChatUiMessage(Role.ASSISTANT, "", streaming = true),
                isStreaming = true,
                error = null,
                // Completion-Tokens gelten pro Turn → zurücksetzen; promptTokens folgt dem
                // wachsenden Kontext und wird vom nächsten UsageUpdate überschrieben. (E3)
                completionTokens = 0,
            )
        // User-Nachricht sofort persistieren.
        persistence?.let { p -> scope.launch { p.onUserMessage(text) } }

        // Verlauf für die Engine: abgeschlossene Nachrichten (leere streamende Bubble raus).
        val history =
            _state.value.messages
                .filterNot { it.role == Role.ASSISTANT && it.streaming }
                .map { it.toDomain() }
        runTurn(history)
    }

    // E3/P2.4: Auto-Antwort auf einen bereits vorhandenen Verlauf, OHNE sichtbare/persistierte
    // User-Bubble. Handover (letzte Msg = ASSISTANT): appendPrimeInstruction=true hängt eine
    // synthetische USER-Instruktion NUR für die Engine an (Anthropic-Alternierung + Anstoß).
    // Share (letzte Msg = bereits eine echte USER-Nachricht): appendPrimeInstruction=false —
    // der vorhandene Verlauf wird direkt beantwortet.
    fun primeAndRespond(appendPrimeInstruction: Boolean = true) {
        if (_state.value.isStreaming || _state.value.messages.isEmpty()) return
        _state.value =
            _state.value.copy(
                messages = _state.value.messages + ChatUiMessage(Role.ASSISTANT, "", streaming = true),
                isStreaming = true,
                error = null,
                completionTokens = 0,
            )
        val base =
            _state.value.messages
                .filterNot { it.role == Role.ASSISTANT && it.streaming }
                .map { it.toDomain() }
        val history = if (appendPrimeInstruction) base + ChatMessage(Role.USER, PRIME_INSTRUCTION) else base
        runTurn(history)
    }

    // Gemeinsamer Streaming-Kern für send()/primeAndRespond(): streamt [history], akkumuliert
    // Deltas in die letzte (streamende) Assistant-Bubble, schließt via finishTurn() ab.
    private fun runTurn(history: List<ChatMessage>) {
        val builder = StringBuilder()
        streamJob =
            streamer
                .stream(history)
                .onEach { event ->
                    when (event) {
                        is ConversationEngine.Event.Delta -> {
                            builder.append(event.text)
                            updateLastAssistant(builder.toString(), streaming = true)
                        }
                        is ConversationEngine.Event.UsageUpdate -> applyUsage(event.usage)
                        ConversationEngine.Event.Done -> Unit // via onCompletion abgeschlossen
                        is ConversationEngine.Event.Failed ->
                            updateLastAssistant(event.message, streaming = false)
                    }
                }.onCompletion { finishTurn() }
                .launchIn(scope)
    }

    // Turn-Abschluss (Erfolg/Fehler/Abbruch): Streaming-Flag der letzten Assistant-Bubble
    // löschen, isStreaming zurücksetzen, fertige Antwort persistieren (nicht pro Delta).
    private suspend fun finishTurn() {
        val last = _state.value.messages.lastOrNull()
        if (last != null && last.role == Role.ASSISTANT && last.streaming) {
            updateLastAssistant(last.text.ifEmpty { "(abgebrochen)" }, streaming = false)
        }
        _state.value = _state.value.copy(isStreaming = false)
        val done = _state.value.messages.lastOrNull()
        if (persistence != null && done.isPersistableAssistant()) {
            persistence.onAssistantMessage(done!!.text)
        }
    }

    private fun ChatUiMessage?.isPersistableAssistant(): Boolean =
        this != null && role == Role.ASSISTANT && text.isNotEmpty()

    // Usage-Events kommen teils getrennt (Anthropic: prompt via message_start, completion via
    // message_delta) oder gebündelt (OpenAI). Je Feld den Maximalwert übernehmen — robust gegen
    // 0-Teilwerte und Doppel-Emission; die Werte gelten für den aktuellen Turn. (E3)
    private fun applyUsage(usage: de.overlai.llm.Usage) {
        _state.value =
            _state.value.copy(
                promptTokens = maxOf(_state.value.promptTokens, usage.promptTokens),
                completionTokens = maxOf(_state.value.completionTokens, usage.completionTokens),
            )
    }

    // Laufenden Stream abbrechen (Stopp-Button). onCompletion räumt das Flag ab.
    fun cancel() {
        streamJob?.cancel()
    }

    // P2.5-E2: Provider/Modell der laufenden Session wechseln, OHNE Verlauf/State anzufassen und
    // ohne einen laufenden Turn abzubrechen (der würde sonst als „(abgebrochen)" persistiert).
    // Nur der Streamer für den NÄCHSTEN Turn wird getauscht. Kein Neuaufbau der Session nötig —
    // Verlauf, observeHistory-Collector und Usage bleiben unberührt.
    fun swapStreamer(newStreamer: Streamer) {
        streamer = newStreamer
    }

    // Verlauf leeren + laufenden Stream stoppen (neuer Chat / Reset).
    fun reset() {
        streamJob?.cancel()
        _state.value = State()
    }

    // Einmalig einen "kein Key"-Hinweis einblenden, wenn kein Key hinterlegt ist und der
    // Verlauf noch leer ist. Nützlich beim Öffnen einer frischen Konversation.
    fun showKeyHintIfMissing() {
        scope.launch {
            if (!streamer.hasKey() && _state.value.messages.isEmpty()) {
                val name = streamer.providerDisplayName()
                _state.value =
                    _state.value.copy(
                        messages =
                            listOf(
                                ChatUiMessage(
                                    Role.ASSISTANT,
                                    "Kein API-Key für $name hinterlegt. In den Einstellungen einen Key eintragen.",
                                ),
                            ),
                    )
            }
        }
    }

    // Für „kein Key"-Banner / Titel der jeweiligen Oberfläche — an den Streamer delegiert.
    suspend fun providerDisplayName(): String = streamer.providerDisplayName()

    suspend fun hasKey(): Boolean = streamer.hasKey()

    private fun updateLastAssistant(
        text: String,
        streaming: Boolean,
    ) {
        val msgs = _state.value.messages.toMutableList()
        val idx = msgs.indexOfLast { it.role == Role.ASSISTANT }
        if (idx >= 0) {
            msgs[idx] = msgs[idx].copy(text = text, streaming = streaming)
            _state.value = _state.value.copy(messages = msgs)
        }
    }

    private companion object {
        // Synthetische USER-Instruktion für den Handover-Autostart (nur an die Engine, nicht
        // im State/persistiert). Fordert das Modell auf, direkt auf Basis des Handovers weiter-
        // zuarbeiten.
        const val PRIME_INSTRUCTION =
            "Das ist ein Handover aus einer vorherigen Session. Bestätige kurz den Stand und " +
                "arbeite direkt weiter: nenne den nächsten konkreten Schritt und beginne damit."
    }
}
