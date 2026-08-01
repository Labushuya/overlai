package de.overlai.llm.transport

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// CHANGE-MARKER v0.1.0: Anthropic-Adapter (siehe CHANGELOG.md)
// Anthropic Messages-API Wire-Format (/v1/messages). Unterscheidet sich vom
// OpenAI-Format: system als Top-Level-Feld, max_tokens required, content als
// Block-Array, eigene SSE-Event-Typen. Verifiziert gegen die claude-api-Referenz
// (2023-06-01): kein temperature/top_p (400 auf neuen Modellen), kein thinking-Feld.

@Serializable
internal data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<AnthropicMessage>,
    val system: String? = null,
    // MUSS im Body landen — identischer Fallstrick wie bei OpenAiChatRequest.stream:
    // encodeDefaults=false (defaultJson) würde stream=true (== Default) sonst weglassen
    // -> Anthropic antwortet non-streaming (ein JSON-Objekt statt SSE) -> unser
    // SSE-Reader sieht kein content_block_delta -> falscher "leere Antwort"-Fehler.
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val stream: Boolean = true,
)

@Serializable
internal data class AnthropicMessage(
    // "user" | "assistant" (nie "system" — das ist Top-Level)
    val role: String,
    // String ODER Array von Content-Blocks (Vision)
    val content: JsonElement,
)

// --- Streaming-Events (SSE) ---
// Anthropic sendet: message_start, content_block_start, content_block_delta,
// content_block_stop, message_delta, message_stop. Wir brauchen v.a. die
// text_delta aus content_block_delta.

@Serializable
internal data class AnthropicStreamEvent(
    val type: String,
    val delta: AnthropicDelta? = null,
    @SerialName("content_block") val contentBlock: AnthropicContentBlock? = null,
    // SSE-Event type=="error" trägt das Fehlerobjekt inline (overloaded_error etc.).
    val error: AnthropicError? = null,
)

@Serializable
internal data class AnthropicDelta(
    // "text_delta" | "input_json_delta" | …
    val type: String? = null,
    val text: String? = null,
    @SerialName("stop_reason") val stopReason: String? = null,
)

@Serializable
internal data class AnthropicContentBlock(
    val type: String? = null,
    val text: String? = null,
)

// --- Fehler-Body ---
@Serializable
internal data class AnthropicErrorEnvelope(
    val error: AnthropicError? = null,
)

@Serializable
internal data class AnthropicError(
    val type: String? = null,
    val message: String? = null,
)
