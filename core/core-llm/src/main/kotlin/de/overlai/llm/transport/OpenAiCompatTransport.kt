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
                                        is SseLineParser.Event.Data -> {
                                            emitDelta(event.json)?.let { trySend(it) }
                                        }
                                        SseLineParser.Event.Done -> {
                                            trySend(ChatDelta(text = "", done = true))
                                            close()
                                            return
                                        }
                                        SseLineParser.Event.Ignore -> Unit
                                    }
                                }
                                // Stream endete ohne [DONE] (manche Provider) -> sauber schließen.
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
            val chunk = json.decodeFromString(OpenAiStreamChunk.serializer(), chunkJson)
            val choice = chunk.choices.firstOrNull()
            val text = choice?.delta?.content
            if (text.isNullOrEmpty()) {
                null
            } else {
                ChatDelta(text = text, finishReason = choice.finishReason)
            }
        } catch (_: Exception) {
            // Unbekannter/kaputter Chunk -> überspringen statt Stream abbrechen.
            null
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
    }
}
