package de.overlai.llm.transport

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// CHANGE-MARKER v0.1.0: Provider-Abstraktion (siehe CHANGELOG.md)
// OpenAI-kompatibles Wire-Format (/v1/chat/completions). Deckt OpenAI, DeepSeek,
// Grok, Kimi, OpenRouter und Gemini (via OpenAI-Shim) ab. Anthropic hat ein
// eigenes Format -> eigener Adapter (M5).

@Serializable
internal data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    // MUSS im Body landen. encodeDefaults=false würde stream=true (== Default)
    // sonst weglassen -> Provider antwortet non-streaming (ein JSON-Objekt statt
    // SSE) -> unser SSE-Reader sieht keine data:-Zeile -> falscher "leerer Stream".
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val stream: Boolean = true,
    // include_usage: true → OpenAI/kompatible senden einen finalen Chunk mit usage
    // (prompt/completion tokens). Ohne das gibt es im Stream keine Token-Zahlen. (E3)
    @SerialName("stream_options") @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val streamOptions: OpenAiStreamOptions = OpenAiStreamOptions(),
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
)

@Serializable
internal data class OpenAiStreamOptions(
    @SerialName("include_usage") @EncodeDefault(EncodeDefault.Mode.ALWAYS) val includeUsage: Boolean = true,
)

@Serializable
internal data class OpenAiMessage(
    val role: String,
    // content ist bei reinem Text ein String, bei Vision ein Array von Parts.
    // Wir serialisieren als JsonElement, um beide Formen abzudecken.
    val content: JsonElement,
)

// --- Streaming-Response-Chunks ---

@Serializable
internal data class OpenAiStreamChunk(
    val choices: List<OpenAiStreamChoice> = emptyList(),
    // Finaler Chunk mit include_usage: prompt/completion Token-Zahlen. (E3)
    val usage: OpenAiUsage? = null,
    // Manche OpenAI-kompatible Provider (v.a. OpenRouter) liefern HTTP 200 und
    // schicken den Fehler IM Stream als data-Zeile mit einem error-Objekt.
    val error: OpenAiError? = null,
)

@Serializable
internal data class OpenAiUsage(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
)

@Serializable
internal data class OpenAiStreamChoice(
    val delta: OpenAiDelta = OpenAiDelta(),
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
internal data class OpenAiDelta(
    val content: String? = null,
    // Reasoning-Modelle (Kimi k2-thinking, DeepSeek-R1) streamen den Denktext in
    // einem separaten Feld — Provider benennen es unterschiedlich, beide abdecken.
    // Ohne das bleibt der Stream für uns "leer" -> falscher 204.
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    val reasoning: String? = null,
)

// --- Fehler-Body ---

@Serializable
internal data class OpenAiErrorEnvelope(
    val error: OpenAiError? = null,
)

@Serializable
internal data class OpenAiError(
    val message: String? = null,
    val type: String? = null,
    // code kann String ("rate_limit_exceeded") ODER Zahl (429) sein — je nach Provider.
    // Als JsonElement, damit die Deserialisierung des ganzen Chunks nicht kippt (sonst
    // wird der In-Stream-Fehler verschluckt -> irreführende leere Antwort/204).
    val code: JsonElement? = null,
)
