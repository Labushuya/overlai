package de.overlai.llm.providers

import de.overlai.llm.AuthScheme
import de.overlai.llm.ChatDelta
import de.overlai.llm.ChatRequest
import de.overlai.llm.LlmError
import de.overlai.llm.LlmProvider
import de.overlai.llm.ProviderConfig
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
                                while (!source.exhausted()) {
                                    val line = source.readUtf8Line() ?: break
                                    when (val event = SseLineParser.parseLine(line)) {
                                        is SseLineParser.Event.Data -> emitDelta(event.json)?.let { trySend(it) }
                                        SseLineParser.Event.Done -> Unit // Anthropic nutzt message_stop, kein [DONE]
                                        SseLineParser.Event.Ignore -> Unit
                                    }
                                }
                                trySend(ChatDelta(text = "", done = true))
                                close()
                            } catch (e: IOException) {
                                close(LlmError.Network("Stream-Fehler", e))
                            }
                        }
                    }
                },
            )

            awaitClose { call.cancel() }
        }

    private fun emitDelta(chunkJson: String): ChatDelta? =
        try {
            val event = json.decodeFromString(AnthropicStreamEvent.serializer(), chunkJson)
            when (event.type) {
                "content_block_delta" -> {
                    val text = event.delta?.text
                    if (text.isNullOrEmpty()) null else ChatDelta(text = text)
                }
                "message_delta" -> {
                    val reason = event.delta?.stopReason
                    if (reason != null) ChatDelta(text = "", finishReason = reason) else null
                }
                else -> null // message_start, content_block_start/stop, ping, message_stop
            }
        } catch (_: Exception) {
            null
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
    }
}
