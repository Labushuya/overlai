package de.overlai.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.overlai.conversation.ConversationEngine
import de.overlai.conversation.ConversationSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// CHANGE-MARKER: Chat-Kern vereinheitlicht (P2.1a, siehe CHANGELOG.md)
// Dünner UI-Adapter über der gemeinsamen ConversationSession. Die frühere eigene
// Streaming-/Akkumulations-/mapError-Logik (byte-identisch zu Engine + OverlayChatState)
// ist ENTFALLEN — sie lebt jetzt einmal in ConversationSession (core-conversation).
// Das ViewModel spiegelt nur den Session-State + das UI-eigene Eingabefeld.
// Kein @HiltViewModel (core-* DI-frei; Verdrahtung im :app-Modul via Factory).
class ChatViewModel(
    engine: ConversationEngine,
) : ViewModel() {
    // Die Session ist an den viewModelScope gebunden (überlebt Recomposition, stirbt mit dem VM).
    private val session = ConversationSession(engine, viewModelScope)

    // Aktueller Eingabetext (nur interner Puffer, wird im UI-State gespiegelt).
    private var currentInput = ""

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        // Session-State laufend in den UI-State spiegeln.
        viewModelScope.launch {
            session.state.collect { s ->
                _state.value =
                    _state.value.copy(
                        messages = s.messages,
                        isStreaming = s.isStreaming,
                        error = s.error,
                    )
            }
        }
        refreshActiveProvider()
    }

    // Aktiven Provider (neu) laden — z.B. nach Rückkehr aus dem Onboarding.
    fun refreshActiveProvider() {
        viewModelScope.launch {
            _state.value =
                _state.value.copy(
                    providerName = session.providerDisplayName(),
                    hasApiKey = session.hasKey(),
                )
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
}
