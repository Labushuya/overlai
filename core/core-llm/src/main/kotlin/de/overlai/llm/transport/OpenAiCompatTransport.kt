package de.overlai.llm.transport

import de.overlai.llm.AuthScheme
import de.overlai.llm.ChatDelta
import de.overlai.llm.ChatRequest
import de.overlai.llm.LlmError
import de.overlai.llm.ProviderConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

// CHANGE-MARKER v0.1.0: Provider-Abstraktion (siehe CHANGELOG.md)
// OpenAI-kompatibler HTTP/SSE-Transport. Der Workhorse: treibt OpenAI + alle
// Config-Provider rein aus ProviderConfig. Streaming als callbackFlow über die
// OkHttp-Response-Zeilen; sauberes Abbrechen via awaitClose (Call.cancel()).
internal class OpenAiCompatTransport(
    private val client: OkHttpClient,
    private val json: Json,
) {
    fun stream(
        config: ProviderConfig,
        request: ChatRequest,
        apiKey: String,
    ): Flow<ChatDelta> =
        callbackFlow {
            val wireMessages =
                request.messages.map { msg ->
                    OpenAiMessage(
                        role = OpenAiContentBuilder.roleToWire(msg.role),
                        content = OpenAiContentBuilder.buildContent(msg),
                    )
                }
            val body =
                OpenAiChatRequest(
                    model = request.model,
                    messages = wireMessages,
                    stream = true,
                    temperature = request.temperature,
                    maxTokens = request.maxTokens ?: if (config.requiresMaxTokens) DEFAULT_MAX_TOKENS else null,
                )
            val jsonBody = json.encodeToString(OpenAiChatRequest.serializer(), body)

            val httpRequest =
                Request
                    .Builder()
                    .url(config.baseUrl.trimEnd('/') + config.chatPath)
                    .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    .apply { applyAuth(config.authScheme, apiKey) }
                    .apply { config.staticHeaders.forEach { (k, v) -> header(k, v) } }
                    .header("Accept", "text/event-stream")
                    .build()

            val call = client.newCall(httpRequest)
            call.enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        close(LlmError.Network(e.message ?: "Netzwerkfehler", e))
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        response.use { resp ->
                            if (!resp.isSuccessful) {
                                close(mapHttpError(resp))
                                return
                            }
                            val source = resp.body?.source()
                            if (source == null) {
                                close(LlmError.Network("Leere Antwort"))
                                return
                            }
                            try {
                                var sawContent = false
                                while (!source.exhausted()) {
                                    val line = source.readUtf8Line() ?: break
                                    when (val event = SseLineParser.parseLine(line)) {
                                        is SseLineParser.Event.Data ->
                                            when (val parsed = parseChunk(event.json)) {
                                                is ChunkResult.Delta -> {
                                                    sawContent = true
                                                    trySend(parsed.chatDelta)
                                                }
                                                is ChunkResult.StreamError -> {
                                                    close(parsed.error)
                                                    return
                                                }
                                                ChunkResult.Ignore -> Unit
                                            }
                                        SseLineParser.Event.Done -> {
                                            finishStream(sawContent)
                                            return
                                        }
                                        SseLineParser.Event.Ignore -> Unit
                                    }
                                }
                                // Stream endete ohne [DONE] (manche Provider).
                                finishStream(sawContent)
                            } catch (e: IOException) {
                                close(LlmError.Network("Stream-Fehler", e))
                            }
                        }
                    }

                    // Stream sauber beenden; endet er ohne jeden Content, ist das bei
                    // OpenRouter-:free-Modellen fast immer ein ausgelasteter/leerer Pool —
                    // ehrliche Meldung statt leerer Bubble.
                    private fun finishStream(sawContent: Boolean) {
                        if (sawContent) {
                            trySend(ChatDelta(text = "", done = true))
                            close()
                        } else {
                            close(
                                LlmError.Api(
                                    EMPTY_RESPONSE_CODE,
                                    "Leere Antwort vom Modell — bei kostenlosen Modellen oft ausgelastet. " +
                                        "Kurz warten oder ein anderes Modell wählen.",
                                ),
                            )
                        }
                    }
                },
            )

            awaitClose { call.cancel() }
        }

    // Ergebnis eines geparsten SSE-Data-Chunks.
    private sealed interface ChunkResult {
        data class Delta(
            val chatDelta: ChatDelta,
        ) : ChunkResult

        data class StreamError(
            val error: LlmError,
        ) : ChunkResult

        data object Ignore : ChunkResult
    }

    private fun parseChunk(chunkJson: String): ChunkResult {
        // Zuerst auf ein error-Objekt prüfen (OpenRouter liefert das bei 200 im Stream).
        val chunk =
            runCatching { json.decodeFromString(OpenAiStreamChunk.serializer(), chunkJson) }
                .getOrNull() ?: return ChunkResult.Ignore
        chunk.error?.let { err -> return ChunkResult.StreamError(mapStreamError(err)) }
        val choice = chunk.choices.firstOrNull()
        val text = choice?.delta?.content
        return if (text.isNullOrEmpty()) {
            ChunkResult.Ignore
        } else {
            ChunkResult.Delta(ChatDelta(text = text, finishReason = choice.finishReason))
        }
    }

    // In-Stream-Fehler ({"error":{...}} als data-Zeile bei HTTP 200) -> typisierter
    // LlmError. Kein HTTP-Status hier — Klassifikation über code/message.
    private fun mapStreamError(error: OpenAiError): LlmError {
        val msg = error.message?.ifBlank { null } ?: "Provider-Fehler im Stream"
        val code = error.code ?: error.type
        return when {
            code == "insufficient_quota" || msg.contains("quota", ignoreCase = true) ->
                LlmError.InsufficientQuota("Kein Guthaben/Kontingent. ($msg)")
            code == "rate_limit_exceeded" ||
                msg.contains("rate", ignoreCase = true) ||
                msg.contains("limit", ignoreCase = true) ->
                LlmError.RateLimited(
                    "Limit erreicht — kurz warten, eigenen Key hinterlegen oder anderes Modell wählen. ($msg)",
                )
            code == "invalid_api_key" || msg.contains("api key", ignoreCase = true) ->
                LlmError.Unauthorized(msg)
            else -> LlmError.Api(STREAM_ERROR_CODE, msg)
        }
    }

    private fun mapHttpError(response: Response): LlmError = HttpErrorMapper.map(response, json)

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
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val DEFAULT_MAX_TOKENS = 4096
        const val STREAM_ERROR_CODE = 502
        const val EMPTY_RESPONSE_CODE = 204
    }
}
