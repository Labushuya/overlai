package de.overlai.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.overlai.core.data.SettingsStore
import de.overlai.llm.ChatRequest
import de.overlai.llm.LlmError
import de.overlai.llm.ProviderConfig
import de.overlai.llm.ProviderFactory
import de.overlai.llm.Role
import de.overlai.llm.providers.ProviderRegistry
import de.overlai.security.KeyVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

// CHANGE-MARKER v0.1.0: Chat-UI (siehe CHANGELOG.md)
// Verbindet die BYOK-Kette mit der UI: liest den AKTIVEN Provider (SettingsStore),
// den Key aus dem KeyVault, ruft den Provider (Streaming), akkumuliert Deltas.
// Kein @HiltViewModel hier im Modul, um core-* frei von DI-Annotationen zu halten;
// die Verdrahtung passiert im :app-Modul (Factory).
class ChatViewModel(
    private val providerFactory: ProviderFactory,
    private val keyVault: KeyVault,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        refreshActiveProvider()
    }

    // Aktiven Provider (neu) laden — z.B. nach Rückkehr aus dem Onboarding.
    fun refreshActiveProvider() {
        viewModelScope.launch {
            val config = activeConfig()
            _state.value =
                _state.value.copy(
                    providerName = config.displayName,
                    hasApiKey = keyVault.hasKey(config.id),
                )
        }
    }

    private suspend fun activeConfig(): ProviderConfig {
        val id = settingsStore.activeProviderId.first()
        return ProviderRegistry.byId(id) ?: ProviderRegistry.OPENAI
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
            val config = activeConfig()
            val apiKey = keyVault.getKey(config.id)
            if (apiKey.isNullOrBlank()) {
                finishWithError("Kein API-Key für ${config.displayName} hinterlegt.")
                return@launch
            }

            val history = _state.value.messages.filter { !it.streaming }.map { it.toDomain() }
            // Gewähltes Modell (Katalog) bevorzugen; sonst Provider-Default.
            val selectedModel = settingsStore.activeModelId(config.id).first()
            val request = ChatRequest(model = selectedModel ?: config.defaultModel, messages = history)

            val provider = providerFactory.create(config)
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
        val msgs = _state.value.messages.filterNot { it.role == Role.ASSISTANT && it.text.isEmpty() }
        _state.value = _state.value.copy(messages = msgs, isStreaming = false, error = message)
    }

    private fun mapError(e: Throwable): String =
        when (e) {
            is LlmError.Unauthorized -> "API-Key ungültig — bitte im Onboarding prüfen."
            is LlmError.InsufficientQuota -> e.message ?: "Kein Guthaben/Kontingent beim Provider."
            is LlmError.RateLimited -> e.message ?: "Rate-Limit erreicht — später erneut versuchen."
            is LlmError.Network -> "Netzwerkfehler: ${e.message}"
            is LlmError.Api -> "Provider-Fehler: ${e.message}"
            else -> "Unbekannter Fehler: ${e.message}"
        }
}
