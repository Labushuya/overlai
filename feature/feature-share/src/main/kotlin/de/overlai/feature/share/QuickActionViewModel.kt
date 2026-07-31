package de.overlai.feature.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.overlai.llm.ChatMessage
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
import kotlinx.coroutines.launch

// CHANGE-MARKER v0.1.0: Entry-Points (siehe CHANGELOG.md)
// Treibt die Quick-Action-Surface: nimmt Quelltext (aus Selektion/Share/OCR),
// ruft den Provider (Streaming), akkumuliert das Ergebnis. Bewusst schlank —
// die Surface ist kurzlebig und schließt nach der Aktion.
class QuickActionViewModel(
    private val providerConfig: ProviderConfig,
    private val providerFactory: ProviderFactory,
    private val keyVault: KeyVault,
) : ViewModel() {
    private val _state = MutableStateFlow(QuickActionUiState())
    val state: StateFlow<QuickActionUiState> = _state.asStateFlow()

    fun setSource(
        text: String,
        canReplaceInHost: Boolean,
    ) {
        _state.value = _state.value.copy(sourceText = text, canReplaceInHost = canReplaceInHost)
        viewModelScope.launch {
            _state.value = _state.value.copy(hasApiKey = keyVault.hasKey(providerConfig.id))
        }
    }

    fun run(action: QuickAction) {
        val source = _state.value.sourceText.trim()
        if (source.isEmpty() || _state.value.isLoading) return
        _state.value = _state.value.copy(isLoading = true, resultText = "", error = null)

        viewModelScope.launch {
            val apiKey = keyVault.getKey(providerConfig.id)
            if (apiKey.isNullOrBlank()) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        hasApiKey = false,
                        error = "Kein API-Key hinterlegt.",
                    )
                return@launch
            }

            val request =
                ChatRequest(
                    model = providerConfig.defaultModel,
                    messages = listOf(ChatMessage(Role.USER, action.buildPrompt(source))),
                )
            val provider = providerFactory.create(providerConfig)
            val builder = StringBuilder()
            provider
                .chat(request, apiKey)
                .catch { e -> _state.value = _state.value.copy(isLoading = false, error = mapError(e)) }
                .collect { delta ->
                    if (delta.text.isNotEmpty()) {
                        builder.append(delta.text)
                        _state.value = _state.value.copy(resultText = builder.toString())
                    }
                    if (delta.done) _state.value = _state.value.copy(isLoading = false)
                }
        }
    }

    private fun mapError(e: Throwable): String =
        when (e) {
            is LlmError.Unauthorized -> "API-Key ungültig."
            is LlmError.InsufficientQuota -> e.message ?: "Kein Guthaben/Kontingent beim Provider."
            is LlmError.RateLimited -> e.message ?: "Rate-Limit erreicht."
            is LlmError.Network -> "Netzwerkfehler."
            is LlmError.Api -> "Provider-Fehler: ${e.message}"
            else -> "Fehler: ${e.message}"
        }
}
