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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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

                    // Stream sauber beenden; endet er ganz ohne Content, ehrliche
                    // NEUTRALE Meldung (Provider + Modell) statt leerer Bubble — kein
                    // "kostenlos"-Bias (der Fehler trifft auch bezahlte Provider).
                    private fun finishStream(sawContent: Boolean) {
                        if (sawContent) {
                            trySend(ChatDelta(text = "", done = true))
                            close()
                        } else {
                            close(
                                LlmError.Api(
                                    EMPTY_RESPONSE_CODE,
                                    "Keine Antwort von ${config.displayName} (Modell ${request.model}). " +
                                        "Der Stream endete ohne Inhalt — bitte erneut senden oder ein " +
                                        "anderes Modell wählen.",
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
        val delta = choice?.delta
        val content = delta?.content
        // Reasoning-Modelle streamen den Denktext in reasoning_content/reasoning
        // (statt/vor content). Als sichtbaren Text ausgeben, sonst bliebe der Stream
        // für uns leer -> falscher "leere Antwort"-Fehler.
        val reasoning = delta?.reasoningContent ?: delta?.reasoning
        val text = if (!content.isNullOrEmpty()) content else reasoning
        return if (text.isNullOrEmpty()) {
            ChunkResult.Ignore
        } else {
            ChunkResult.Delta(ChatDelta(text = text, finishReason = choice?.finishReason))
        }
    }

    // In-Stream-Fehler ({"error":{...}} als data-Zeile bei HTTP 200) -> typisierter
    // LlmError. Kein HTTP-Status hier — Klassifikation über code/message.
    private fun mapStreamError(error: OpenAiError): LlmError {
        val msg = error.message?.ifBlank { null } ?: "Provider-Fehler im Stream"
        // code kann String ("rate_limit_exceeded") ODER Zahl (429) sein.
        val code = (error.code as? JsonPrimitive)?.contentOrNull ?: error.type
        return when {
            isQuota(code, msg) -> LlmError.InsufficientQuota("Kein Guthaben/Kontingent. ($msg)")
            isRateLimited(code, msg) ->
                LlmError.RateLimited(
                    "Limit erreicht — kurz warten, eigenen Key hinterlegen oder anderes Modell wählen. ($msg)",
                )
            isUnauthorized(code, msg) -> LlmError.Unauthorized(msg)
            // OpenRouter: viele :free-Slugs sind abgeschaltet ("unavailable for free").
            code == "404" || msg.contains("unavailable for free", ignoreCase = true) ->
                LlmError.Api(STREAM_ERROR_CODE, "Modell nicht (mehr) verfügbar: $msg")
            else -> LlmError.Api(STREAM_ERROR_CODE, msg)
        }
    }

    private fun isQuota(
        code: String?,
        msg: String,
    ): Boolean = code == "insufficient_quota" || msg.contains("quota", ignoreCase = true)

    private fun isRateLimited(
        code: String?,
        msg: String,
    ): Boolean =
        code == "rate_limit_exceeded" || code == "429" ||
            msg.contains("rate", ignoreCase = true) || msg.contains("limit", ignoreCase = true)

    private fun isUnauthorized(
        code: String?,
        msg: String,
    ): Boolean = code == "invalid_api_key" || code == "401" || msg.contains("api key", ignoreCase = true)

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
