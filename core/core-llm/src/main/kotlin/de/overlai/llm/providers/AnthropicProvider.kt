package de.overlai.llm.providers

import de.overlai.llm.AuthScheme
import de.overlai.llm.ChatDelta
import de.overlai.llm.ChatRequest
import de.overlai.llm.LlmError
import de.overlai.llm.LlmProvider
import de.overlai.llm.ProviderConfig
import de.overlai.llm.Usage
import de.overlai.llm.transport.AnthropicError
import de.overlai.llm.transport.AnthropicErrorEnvelope
import de.overlai.llm.transport.AnthropicMessageMapper
import de.overlai.llm.transport.AnthropicRequest
import de.overlai.llm.transport.AnthropicStreamEvent
import de.overlai.llm.transport.SseLineParser
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

// CHANGE-MARKER v0.1.0: Anthropic-Adapter (siehe CHANGELOG.md)
// Dedizierter Adapter für die Anthropic Messages-API. Beweist, dass die
// "OpenAI-compat Core + Adapter"-Abstraktion trägt: neutrale ChatRequest rein,
// ChatDelta-Flow raus — nur das Wire-Format dazwischen ist anders.
internal class AnthropicProvider(
    override val config: ProviderConfig,
    private val client: OkHttpClient,
    private val json: Json,
) : LlmProvider {
    override fun chat(
        request: ChatRequest,
        apiKey: String,
    ): Flow<ChatDelta> =
        callbackFlow {
            val body = AnthropicMessageMapper.toRequest(request)
            val jsonBody = json.encodeToString(AnthropicRequest.serializer(), body)

            val httpRequest =
                Request
                    .Builder()
                    .url(config.baseUrl.trimEnd('/') + config.chatPath)
                    .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    .apply { applyAuth(config.authScheme, apiKey) }
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
                                            when (val parsed = parseEvent(event.json)) {
                                                is EventResult.Delta -> {
                                                    if (parsed.chatDelta.text.isNotEmpty()) sawContent = true
                                                    trySend(parsed.chatDelta)
                                                }
                                                is EventResult.StreamError -> {
                                                    close(parsed.error)
                                                    return
                                                }
                                                EventResult.Ignore -> Unit
                                            }
                                        SseLineParser.Event.Done -> Unit // Anthropic nutzt message_stop, kein [DONE]
                                        SseLineParser.Event.Ignore -> Unit
                                    }
                                }
                                finishStream(sawContent)
                            } catch (e: IOException) {
                                close(LlmError.Network("Stream-Fehler", e))
                            }
                        }
                    }

                    // Stream sauber beenden; ohne jeden Content ist das eine ehrliche
                    // Leer-Meldung statt einer stummen leeren Bubble.
                    private fun finishStream(sawContent: Boolean) {
                        if (sawContent) {
                            trySend(ChatDelta(text = "", done = true))
                            close()
                        } else {
                            close(
                                LlmError.Api(
                                    EMPTY_RESPONSE_CODE,
                                    "Leere Antwort vom Modell — kurz warten oder ein anderes Modell wählen.",
                                ),
                            )
                        }
                    }
                },
            )

            awaitClose { call.cancel() }
        }

    // Ergebnis eines geparsten SSE-Events.
    private sealed interface EventResult {
        data class Delta(
            val chatDelta: ChatDelta,
        ) : EventResult

        data class StreamError(
            val error: LlmError,
        ) : EventResult

        data object Ignore : EventResult
    }

    private fun parseEvent(chunkJson: String): EventResult {
        val event =
            runCatching { json.decodeFromString(AnthropicStreamEvent.serializer(), chunkJson) }
                .getOrNull() ?: return EventResult.Ignore
        // type=="error" trägt das Fehlerobjekt inline (overloaded_error etc.) bei HTTP 200.
        if (event.type == "error" && event.error != null) {
            return EventResult.StreamError(mapStreamError(event.error))
        }
        return when (event.type) {
            "content_block_delta" -> {
                val text = event.delta?.text
                if (text.isNullOrEmpty()) EventResult.Ignore else EventResult.Delta(ChatDelta(text = text))
            }
            "message_start" -> {
                // Trägt die Eingabe-(Prompt-)Tokens des Requests. (E3)
                val input = event.message?.usage?.inputTokens
                if (input != null) {
                    EventResult.Delta(ChatDelta(text = "", usage = Usage(promptTokens = input, completionTokens = 0)))
                } else {
                    EventResult.Ignore
                }
            }
            "message_delta" -> {
                // Trägt stop_reason + die kumulierten Ausgabe-(Completion-)Tokens. (E3)
                val reason = event.delta?.stopReason
                val output = event.usage?.outputTokens
                if (reason != null || output != null) {
                    EventResult.Delta(
                        ChatDelta(
                            text = "",
                            finishReason = reason,
                            usage = output?.let { Usage(promptTokens = 0, completionTokens = it) },
                        ),
                    )
                } else {
                    EventResult.Ignore
                }
            }
            else -> EventResult.Ignore // content_block_start/stop, ping, message_stop
        }
    }

    // In-Stream-Fehler (type=="error" bei HTTP 200) -> typisierter LlmError.
    private fun mapStreamError(error: AnthropicError): LlmError {
        val msg = error.message?.ifBlank { null } ?: "Provider-Fehler im Stream"
        return when (error.type) {
            "overloaded_error" -> LlmError.RateLimited("Anthropic überlastet — kurz warten. ($msg)")
            "rate_limit_error" -> LlmError.RateLimited("Limit erreicht — kurz warten. ($msg)")
            "authentication_error" -> LlmError.Unauthorized(msg)
            else -> LlmError.Api(STREAM_ERROR_CODE, msg)
        }
    }

    private fun mapHttpError(response: Response): LlmError {
        val bodyText = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
        val error =
            runCatching {
                json.decodeFromString(AnthropicErrorEnvelope.serializer(), bodyText).error
            }.getOrNull()
        val apiMsg = error?.message ?: bodyText.take(ERROR_SNIPPET_LEN)
        return when (response.code) {
            HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> LlmError.Unauthorized(apiMsg.ifBlank { "API-Key ungültig" })
            HTTP_TOO_MANY_REQUESTS ->
                if (error?.type == "overloaded_error") {
                    LlmError.RateLimited("Anthropic überlastet — kurz warten.")
                } else {
                    LlmError.RateLimited("Rate-Limit erreicht.")
                }
            else -> LlmError.Api(response.code, apiMsg)
        }
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
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val ERROR_SNIPPET_LEN = 300
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val STREAM_ERROR_CODE = 502
        const val EMPTY_RESPONSE_CODE = 204
    }
}
