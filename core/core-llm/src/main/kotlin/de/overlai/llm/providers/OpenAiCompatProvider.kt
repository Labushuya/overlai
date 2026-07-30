package de.overlai.llm.providers

import de.overlai.llm.ChatDelta
import de.overlai.llm.ChatRequest
import de.overlai.llm.LlmProvider
import de.overlai.llm.ProviderConfig
import de.overlai.llm.transport.OpenAiCompatTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

// CHANGE-MARKER v0.1.0: Provider-Abstraktion (siehe CHANGELOG.md)
// OpenAI-kompatibler Provider. Nutzt für JEDEN Config-Provider denselben
// Transport; provider-spezifisches Verhalten steckt in der ProviderConfig.
// (Transcription-Endpoint kommt in M6; hier ist die Chat-Kette vollständig.)
internal class OpenAiCompatProvider(
    override val config: ProviderConfig,
    client: OkHttpClient,
    json: Json,
) : LlmProvider {
    private val transport = OpenAiCompatTransport(client, json)

    override fun chat(
        request: ChatRequest,
        apiKey: String,
    ): Flow<ChatDelta> = transport.stream(config, request, apiKey)
}
