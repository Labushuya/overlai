package de.overlai.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.overlai.conversation.ChatUiMessage
import de.overlai.conversation.ConversationEngine
import de.overlai.conversation.ConversationSession
import de.overlai.core.data.chat.SessionRepository
import de.overlai.llm.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// CHANGE-MARKER: Multi-Chat-Persistenz (P2.1b, siehe CHANGELOG.md)
// Dünner UI-Adapter über der gemeinsamen ConversationSession — jetzt an EINE persistente
// Session (sessionId) gebunden: eigener Provider/Modell (streamerFor) + Verlauf aus/in Room
// (Persistence). Streaming-/Akkumulationslogik lebt weiter in ConversationSession.
// Kein @HiltViewModel (core-* DI-frei; Verdrahtung im :app-Modul via Factory).
class ChatViewModel(
    engine: ConversationEngine,
    private val repo: SessionRepository,
    private val sessionId: String,
    providerId: String,
    modelId: String?,
) : ViewModel() {
    // Persistenz-Brücke Session → Room. Titel wird aus der ersten User-Nachricht gesetzt.
    private val persistence =
        object : ConversationSession.Persistence {
            private var titleSet = false

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

    private val _state = MutableStateFlow(ChatUiState(modelId = modelId))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            session.state.collect { s ->
                _state.value = _state.value.copy(messages = s.messages, isStreaming = s.isStreaming, error = s.error)
            }
        }
        refreshActiveProvider()
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

    private companion object {
        const val TITLE_MAX_LEN = 40

        fun now(): Long = System.currentTimeMillis()
    }
}
