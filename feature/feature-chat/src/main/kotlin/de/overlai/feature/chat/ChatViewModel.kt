package de.overlai.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.overlai.conversation.ChatUiMessage
import de.overlai.conversation.ConversationEngine
import de.overlai.conversation.ConversationSession
import de.overlai.conversation.HandoverGenerator
import de.overlai.core.data.chat.SessionRepository
import de.overlai.llm.ModelContextTable
import de.overlai.llm.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

// CHANGE-MARKER: Multi-Chat-Persistenz (P2.1b, siehe CHANGELOG.md)
// Dünner UI-Adapter über der gemeinsamen ConversationSession — jetzt an EINE persistente
// Session (sessionId) gebunden: eigener Provider/Modell (streamerFor) + Verlauf aus/in Room
// (Persistence). Streaming-/Akkumulationslogik lebt weiter in ConversationSession.
// Kein @HiltViewModel (core-* DI-frei; Verdrahtung im :app-Modul via Factory).
class ChatViewModel(
    engine: ConversationEngine,
    private val repo: SessionRepository,
    private val handover: HandoverGenerator,
    private val sessionId: String,
    private val providerId: String,
    private val modelId: String?,
) : ViewModel() {
    // Ob der Titel bereits gesetzt wurde (Auto-Titel aus erster User-Nachricht ODER manuelles
    // Umbenennen). Verhindert, dass ein manuell vergebener Titel später überschrieben wird.
    private var titleSet = false

    // Persistenz-Brücke Session → Room. Titel wird aus der ersten User-Nachricht gesetzt.
    private val persistence =
        object : ConversationSession.Persistence {
            override fun observeHistory() =
                repo.observeMessages(sessionId).map { list -> list.map { ChatUiMessage(it.role, it.text) } }

            override suspend fun onUserMessage(text: String) {
                repo.appendMessage(sessionId, Role.USER, text, now())
                if (!titleSet) {
                    repo.updateTitle(sessionId, text.take(TITLE_MAX_LEN), now())
                    titleSet = true
                }
            }

            override suspend fun onAssistantMessage(text: String) {
                repo.appendMessage(sessionId, Role.ASSISTANT, text, now())
            }
        }

    private val session =
        ConversationSession(
            streamer = engine.streamerFor(providerId, modelId),
            scope = viewModelScope,
            persistence = persistence,
        )

    private var currentInput = ""

    // E3b: Guard gegen Mehrfach-Autostart innerhalb dieser VM-Instanz.
    private var autostartTriggered = false

    private val _state =
        MutableStateFlow(ChatUiState(modelId = modelId, contextLimit = ModelContextTable.resolve(modelId, null)))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            session.state.collect { s ->
                val limit = _state.value.contextLimit
                // Auto-Vorschlag einmal auslösen, wenn der Kontext ~80% erreicht (bekanntes Limit).
                val suggest =
                    _state.value.suggestHandover ||
                        (limit != null && limit > 0 && s.promptTokens.toFloat() / limit >= HANDOVER_SUGGEST_FRACTION)
                _state.value =
                    _state.value.copy(
                        messages = s.messages,
                        isStreaming = s.isStreaming,
                        error = s.error,
                        promptTokens = s.promptTokens,
                        completionTokens = s.completionTokens,
                        suggestHandover = suggest,
                    )
            }
        }
        // Aktuellen Titel für den Umbenennen-Dialog laden.
        viewModelScope.launch {
            repo.getSession(sessionId)?.let { _state.value = _state.value.copy(title = it.title) }
        }
        refreshActiveProvider()
    }

    // E3b: In einer frisch per Handover erzeugten Fortsetzungs-Session soll das LLM SOFORT auf
    // den Handover antworten. Fingerabdruck einer solchen Session: genau EINE Nachricht, und
    // die ist ASSISTANT (der Handover-Text). Idempotent (danach ≥2 Nachrichten). Instanz-Guard
    // gegen Mehrfach-Aufruf; von ChatRoute via LaunchedEffect(sessionId) angestoßen.
    fun maybeAutostart() {
        if (autostartTriggered) return
        autostartTriggered = true
        viewModelScope.launch {
            val history = repo.messages(sessionId)
            if (history.size == 1) {
                when (history.single().role) {
                    // Handover-Fortsetzung: letzte Msg = ASSISTANT → synthetische Prime-Instruktion.
                    Role.ASSISTANT -> session.primeAndRespond(appendPrimeInstruction = true)
                    // Share-Einstieg: letzte Msg = echte USER-Nachricht → direkt beantworten.
                    Role.USER -> session.primeAndRespond(appendPrimeInstruction = false)
                    else -> Unit
                }
            }
        }
    }

    // Provider/Key-Status der SESSION (nicht global) in den UI-State spiegeln.
    fun refreshActiveProvider() {
        viewModelScope.launch {
            _state.value =
                _state.value.copy(providerName = session.providerDisplayName(), hasApiKey = session.hasKey())
        }
    }

    fun onInputChange(text: String) {
        currentInput = text
        _state.value = _state.value.copy(input = text)
    }

    fun onSend() {
        session.send(currentInput)
        currentInput = ""
        _state.value = _state.value.copy(input = "")
    }

    // Chat umbenennen (E3b): setzt den Titel + sperrt den Auto-Titel, damit er nicht von einer
    // späteren ersten User-Nachricht überschrieben wird.
    fun rename(newTitle: String) {
        val title = newTitle.trim().take(TITLE_MAX_LEN)
        if (title.isEmpty()) return
        titleSet = true
        _state.value = _state.value.copy(title = title)
        viewModelScope.launch { repo.updateTitle(sessionId, title, now()) }
    }

    // --- Handover (E3) ---

    // Handover generieren (one-shot) und zur Kontroll-Vorschau in den State legen.
    fun generateHandover() {
        if (_state.value.handoverLoading) return
        _state.value = _state.value.copy(handoverLoading = true, suggestHandover = false)
        viewModelScope.launch {
            val result = runCatching { handover.generate(sessionId) }
            _state.value =
                result.fold(
                    onSuccess = { text -> _state.value.copy(handoverLoading = false, handoverPreview = text) },
                    onFailure = { e ->
                        _state.value.copy(handoverLoading = false, error = e.message ?: "Handover fehlgeschlagen.")
                    },
                )
        }
    }

    // Kontroll-Vorschau verwerfen (kein neuer Chat).
    fun dismissHandover() {
        _state.value = _state.value.copy(handoverPreview = null)
    }

    // Auto-Vorschlag-Banner wegwischen, ohne Handover.
    fun dismissSuggestion() {
        _state.value = _state.value.copy(suggestHandover = false)
    }

    // Geprüften Handover-Text übernehmen → neue Session desselben Chats; ruft [onNewSession].
    fun applyHandover(
        text: String,
        onNewSession: (String) -> Unit,
    ) {
        viewModelScope.launch {
            val newId = UUID.randomUUID().toString()
            runCatching { handover.apply(sessionId, text, newId) }
                .onSuccess {
                    _state.value = _state.value.copy(handoverPreview = null)
                    onNewSession(newId)
                }
                .onFailure { e -> _state.value = _state.value.copy(error = e.message ?: "Übernahme fehlgeschlagen.") }
        }
    }

    private companion object {
        const val TITLE_MAX_LEN = 40
        const val HANDOVER_SUGGEST_FRACTION = 0.8f

        fun now(): Long = System.currentTimeMillis()
    }
}
