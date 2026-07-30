package de.overlai.llm

import de.overlai.llm.providers.OpenAiCompatProvider
import de.overlai.llm.providers.ProviderRegistry
import okhttp3.OkHttpClient
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

// CHANGE-MARKER v0.1.0: Provider-Abstraktion (siehe CHANGELOG.md)
// Erzeugt LlmProvider-Instanzen aus einer ProviderConfig. Zentrale Stelle für
// den OkHttpClient (Timeouts; Cert-Pinning wird hier in M1/M5 ergänzt) und die
// Json-Konfiguration. Anthropic wird in M5 hier auf seinen eigenen Adapter geroutet.
class ProviderFactory(
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = defaultJson(),
) {
    fun create(config: ProviderConfig): LlmProvider =
        when (config.id) {
            // TODO(M5): "anthropic" -> AnthropicProvider(config, client, json)
            else -> OpenAiCompatProvider(config, client, json)
        }

    fun create(providerId: String): LlmProvider {
        val config =
            ProviderRegistry.byId(providerId)
                ?: throw IllegalArgumentException("Unbekannter Provider: $providerId")
        return create(config)
    }

    companion object {
        fun defaultJson(): Json =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = false
            }

        fun defaultClient(): OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
                // Read-Timeout großzügig: Streams laufen lange.
                .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
                .build()

        private const val CONNECT_TIMEOUT_S = 30L
        private const val READ_TIMEOUT_S = 300L
    }
}
