package de.overlai.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.overlai.llm.ChatRequest
import de.overlai.llm.LlmError
import de.overlai.llm.ProviderConfig
import de.overlai.llm.ProviderFactory
import de.overlai.llm.Role
import de.overlai.security.KeyVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

// CHANGE-MARKER v0.1.0: Chat-UI (siehe CHANGELOG.md)
// Verbindet die BYOK-Kette mit der UI: liest den Key aus dem KeyVault, ruft den
// Provider (Streaming), akkumuliert Deltas in die letzte Assistant-Nachricht.
// Kein @HiltViewModel hier im Modul, um core-* frei von DI-Annotationen zu halten;
// die Verdrahtung passiert im :app-Modul (Factory).
class ChatViewModel(
    private val providerConfig: ProviderConfig,
    private val providerFactory: ProviderFactory,
    private val keyVault: KeyVault,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState(providerName = providerConfig.displayName))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(hasApiKey = keyVault.hasKey(providerConfig.id))
        }
    }

    fun onInputChange(text: String) {
        _state.value = _state.value.copy(input = text)
    }

    fun onSend() {
        val prompt = _state.value.input.trim()
        if (prompt.isEmpty() || _state.value.isStreaming) return

        val userMsg = UiMessage(Role.USER, prompt)
        val assistantMsg = UiMessage(Role.ASSISTANT, "", streaming = true)
        _state.value =
            _state.value.copy(
                messages = _state.value.messages + userMsg + assistantMsg,
                input = "",
                isStreaming = true,
                error = null,
            )

        viewModelScope.launch {
            val apiKey = keyVault.getKey(providerConfig.id)
            if (apiKey.isNullOrBlank()) {
                finishWithError("Kein API-Key für ${providerConfig.displayName} hinterlegt.")
                return@launch
            }

            val history = _state.value.messages.filter { !it.streaming }.map { it.toDomain() }
            val request =
                ChatRequest(
                    model = providerConfig.defaultModel,
                    messages = history,
                )

            val provider = providerFactory.create(providerConfig)
            val builder = StringBuilder()
            provider
                .chat(request, apiKey)
                .catch { e -> finishWithError(mapError(e)) }
                .onCompletion { cause -> if (cause == null) markStreamingDone() }
                .collect { delta ->
                    if (delta.text.isNotEmpty()) {
                        builder.append(delta.text)
                        updateLastAssistant(builder.toString())
                    }
                }
        }
    }

    private fun updateLastAssistant(text: String) {
        val msgs = _state.value.messages.toMutableList()
        val idx = msgs.indexOfLast { it.role == Role.ASSISTANT }
        if (idx >= 0) {
            msgs[idx] = msgs[idx].copy(text = text, streaming = true)
            _state.value = _state.value.copy(messages = msgs)
        }
    }

    private fun markStreamingDone() {
        val msgs = _state.value.messages.toMutableList()
        val idx = msgs.indexOfLast { it.role == Role.ASSISTANT }
        if (idx >= 0) msgs[idx] = msgs[idx].copy(streaming = false)
        _state.value = _state.value.copy(messages = msgs, isStreaming = false)
    }

    private fun finishWithError(message: String) {
        // Streaming-Platzhalter (leere Assistant-Nachricht) entfernen.
        val msgs = _state.value.messages.filterNot { it.role == Role.ASSISTANT && it.text.isEmpty() }
        _state.value = _state.value.copy(messages = msgs, isStreaming = false, error = message)
    }

    private fun mapError(e: Throwable): String =
        when (e) {
            is LlmError.Unauthorized -> "API-Key ungültig — bitte im Onboarding prüfen."
            is LlmError.RateLimited -> "Rate-Limit erreicht — später erneut versuchen."
            is LlmError.Network -> "Netzwerkfehler: ${e.message}"
            is LlmError.Api -> "Provider-Fehler: ${e.message}"
            else -> "Unbekannter Fehler: ${e.message}"
        }
}
