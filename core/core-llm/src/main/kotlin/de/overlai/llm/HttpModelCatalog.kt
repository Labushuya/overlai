package de.overlai.llm

import de.overlai.llm.catalog.AnthropicModelsResponse
import de.overlai.llm.catalog.ModelParsers
import de.overlai.llm.catalog.StaticModels
import de.overlai.llm.transport.HttpErrorMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

// CHANGE-MARKER v0.4.0: Modell-Katalog (siehe CHANGELOG.md)
// Lädt den Modell-Katalog live mit dem BYOK-Key. Nur bestätigte Endpoints werden
// live abgefragt (openai/anthropic/openrouter/deepseek); die übrigen fallen auf
// eine kuratierte Liste zurück. list() fängt Fehler und liefert nie leer;
// listOrThrow() reicht typisierte LlmError für die UI durch.
class HttpModelCatalog(
    private val client: OkHttpClient = ProviderFactory.defaultClient(),
    private val json: Json = ProviderFactory.defaultJson(),
) : ModelCatalog {
    override suspend fun list(
        config: ProviderConfig,
        apiKey: String,
    ): List<ModelInfo> =
        runCatching { listOrThrow(config, apiKey) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: StaticModels.forProvider(config)

    // Wirft typisiertes LlmError durch (401/403 -> Unauthorized, 429 -> RateLimited/Quota,
    // 404/405 -> Api). Provider ohne bestätigten Endpoint -> StaticModels.
    suspend fun listOrThrow(
        config: ProviderConfig,
        apiKey: String,
    ): List<ModelInfo> {
        val path = modelsPath(config.id) ?: return StaticModels.forProvider(config)
        return withContext(Dispatchers.IO) {
            if (config.id == "anthropic") {
                fetchAnthropicPaged(config, path, apiKey)
            } else {
                val body = fetch(config, path, apiKey, cursor = null)
                ModelParsers.parse(config.id, body, json)
            }
        }
    }

    // Nur bestätigte Live-Endpoints. Grok/Kimi/Gemini -> null -> StaticModels
    // (vor Ship verifizieren, dann hier ergänzen).
    private fun modelsPath(id: String): String? =
        when (id) {
            "openai", "anthropic", "openrouter", "deepseek" -> "/v1/models"
            else -> null
        }

    private fun fetch(
        config: ProviderConfig,
        path: String,
        apiKey: String,
        cursor: String?,
    ): String {
        val url =
            buildString {
                append(config.baseUrl.trimEnd('/'))
                append(path)
                if (cursor != null) append("?after_id=").append(cursor)
            }
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .apply { applyAuth(config.authScheme, apiKey) }
                .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw HttpErrorMapper.map(resp, json)
            return resp.body?.string() ?: ""
        }
    }

    // Anthropic paginiert via Cursor (has_more/last_id). Alle Seiten sammeln.
    private fun fetchAnthropicPaged(
        config: ProviderConfig,
        path: String,
        apiKey: String,
    ): List<ModelInfo> {
        val all = mutableListOf<ModelInfo>()
        var cursor: String? = null
        var guard = 0
        do {
            val body = fetch(config, path, apiKey, cursor)
            all += ModelParsers.parse(config.id, body, json)
            val page = runCatching { json.decodeFromString(AnthropicModelsResponse.serializer(), body) }.getOrNull()
            cursor = if (page?.hasMore == true) page.lastId else null
            guard++
        } while (cursor != null && guard < MAX_PAGES)
        return all.distinctBy { it.id }.sortedBy { it.displayName.lowercase() }
    }

    private fun Request.Builder.applyAuth(
        scheme: AuthScheme,
        apiKey: String,
    ) {
        when (scheme) {
            AuthScheme.Bearer -> header("Authorization", "Bearer $apiKey")
            is AuthScheme.ApiKeyHeader -> {
                header(scheme.headerName, apiKey)
                scheme.extraHeaders.forEach { (k, v) -> header(k, v) }
            }
        }
    }

    private companion object {
        const val MAX_PAGES = 10
    }
}
