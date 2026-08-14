package de.overlai.conversation

import de.overlai.core.data.SettingsStore
import de.overlai.llm.ChatMessage
import de.overlai.llm.ChatRequest
import de.overlai.llm.LlmError
import de.overlai.llm.ProviderConfig
import de.overlai.llm.ProviderFactory
import de.overlai.llm.Usage
import de.overlai.llm.providers.ProviderRegistry
import de.overlai.security.KeyVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
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
) : ConversationSession.Streamer {
    // Ergebnis eines Stream-Schritts für die UI.
    sealed interface Event {
        data class Delta(
            val text: String,
        ) : Event

        // Token-Usage aus dem Stream (E3) — die Session akkumuliert die Werte.
        data class UsageUpdate(
            val usage: Usage,
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

    // ConversationSession.Streamer — dünne Delegation, damit die Session provider-agnostisch
    // an der Engine hängt (statt SettingsStore/KeyVault selbst zu kennen). Dieser Default-
    // Streamer nutzt die GLOBALE Auswahl (activeProviderId/activeModelId).
    override suspend fun providerDisplayName(): String = activeConfig().displayName

    override suspend fun hasKey(): Boolean = hasKeyForActive()

    // Streamt mit dem GLOBAL aktiven Provider (Default-Verhalten, unverändert).
    override fun stream(messages: List<ChatMessage>): Flow<Event> =
        flow {
            val config = activeConfig()
            val modelId = settingsStore.activeModelId(config.id).first()
            emitAll(streamWith(config, modelId, messages))
        }

    // P2.1b: Streamer für eine SPEZIFISCHE Session — fester Provider+Modell statt global.
    // Damit hat jede Multi-Chat-Session ihren eigenen Provider. Key kommt weiter aus dem
    // globalen KeyVault (BYOK ist pro Provider, nicht pro Session).
    fun streamerFor(
        providerId: String,
        modelId: String?,
    ): ConversationSession.Streamer =
        object : ConversationSession.Streamer {
            private val config: ProviderConfig = ProviderRegistry.byId(providerId) ?: ProviderRegistry.OPENAI

            override fun stream(messages: List<ChatMessage>): Flow<Event> = streamWith(config, modelId, messages)

            override suspend fun providerDisplayName(): String = config.displayName

            override suspend fun hasKey(): Boolean = keyVault.hasKey(config.id)
        }

    // Gemeinsamer Streaming-Kern: löst Key auf, baut Request, mappt Fehler.
    private fun streamWith(
        config: ProviderConfig,
        modelId: String?,
        messages: List<ChatMessage>,
    ): Flow<Event> =
        flow {
            val apiKey = keyVault.getKey(config.id)
            if (apiKey.isNullOrBlank()) {
                emit(Event.Failed("Kein API-Key für ${config.displayName} hinterlegt."))
                return@flow
            }
            val request = ChatRequest(model = modelId ?: config.defaultModel, messages = messages)
            providerFactory
                .create(config)
                .chat(request, apiKey)
                .catch { e -> emit(Event.Failed(mapError(e))) }
                .collect { delta ->
                    if (delta.text.isNotEmpty()) emit(Event.Delta(delta.text))
                    delta.usage?.let { emit(Event.UsageUpdate(it)) }
                    if (delta.done) emit(Event.Done)
                }
        }

    // E3: One-shot-Vervollständigung (kein UI-Streaming) — sammelt die ganze Antwort in einen
    // String. Für den Handover-Generator: schickt Verlauf + System-Instruktion an den Provider
    // der Session und gibt den fertigen Text zurück. Wirft LlmError bei Fehlern.
    suspend fun complete(
        providerId: String,
        modelId: String?,
        messages: List<ChatMessage>,
        system: String? = null,
    ): String {
        val config = ProviderRegistry.byId(providerId) ?: ProviderRegistry.OPENAI
        val apiKey = keyVault.getKey(config.id) ?: error("Kein API-Key für ${config.displayName} hinterlegt.")
        val request =
            ChatRequest(
                model = modelId ?: config.defaultModel,
                messages = messages,
                system = system,
            )
        val builder = StringBuilder()
        providerFactory.create(config).chat(request, apiKey).collect { delta -> builder.append(delta.text) }
        return builder.toString()
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
