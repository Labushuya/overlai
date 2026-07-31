package de.overlai.conversation

import de.overlai.core.data.SettingsStore
import de.overlai.llm.ChatMessage
import de.overlai.llm.ChatRequest
import de.overlai.llm.LlmError
import de.overlai.llm.ProviderConfig
import de.overlai.llm.ProviderFactory
import de.overlai.llm.providers.ProviderRegistry
import de.overlai.security.KeyVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

// CHANGE-MARKER v0.2.1: Konversations-Engine (siehe CHANGELOG.md)
// Gemeinsame Engine für Chat und Quick-Actions: löst den aktiven Provider auf,
// liest den BYOK-Key, streamt die Antwort. Zuvor in ChatViewModel und
// QuickActionViewModel dupliziert.
class ConversationEngine(
    private val providerFactory: ProviderFactory,
    private val keyVault: KeyVault,
    private val settingsStore: SettingsStore,
) {
    // Ergebnis eines Stream-Schritts für die UI.
    sealed interface Event {
        data class Delta(
            val text: String,
        ) : Event

        data object Done : Event

        data class Failed(
            val message: String,
        ) : Event
    }

    suspend fun activeConfig(): ProviderConfig {
        val id = settingsStore.activeProviderId.first()
        return ProviderRegistry.byId(id) ?: ProviderRegistry.OPENAI
    }

    suspend fun hasKeyForActive(): Boolean = keyVault.hasKey(activeConfig().id)

    // Streamt eine Konversation mit dem aktiven Provider. Löst Provider + Key auf,
    // mappt Fehler auf lesbare Meldungen. Emittiert Delta*/Done/Failed.
    fun stream(messages: List<ChatMessage>): Flow<Event> =
        flow {
            val config = activeConfig()
            val apiKey = keyVault.getKey(config.id)
            if (apiKey.isNullOrBlank()) {
                emit(Event.Failed("Kein API-Key für ${config.displayName} hinterlegt."))
                return@flow
            }
            // Gewähltes Modell (aus dem Katalog) bevorzugen; sonst Provider-Default.
            val selectedModel = settingsStore.activeModelId(config.id).first()
            val request = ChatRequest(model = selectedModel ?: config.defaultModel, messages = messages)
            providerFactory
                .create(config)
                .chat(request, apiKey)
                .catch { e -> emit(Event.Failed(mapError(e))) }
                .collect { delta ->
                    if (delta.text.isNotEmpty()) emit(Event.Delta(delta.text))
                    if (delta.done) emit(Event.Done)
                }
        }

    companion object {
        fun mapError(e: Throwable): String =
            when (e) {
                is LlmError.Unauthorized -> "API-Key ungültig — bitte im Onboarding prüfen."
                is LlmError.InsufficientQuota -> e.message ?: "Kein Guthaben/Kontingent beim Provider."
                is LlmError.RateLimited -> e.message ?: "Rate-Limit erreicht — später erneut versuchen."
                is LlmError.Network -> "Netzwerkfehler: ${e.message}"
                is LlmError.Api -> "Provider-Fehler: ${e.message}"
                else -> "Unbekannter Fehler: ${e.message}"
            }
    }
}
