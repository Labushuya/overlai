package de.overlai.llm

// CHANGE-MARKER v0.1.0: Provider-Abstraktion (siehe CHANGELOG.md)
// Neutrale Chat-Domänentypen. Jeder Provider-Adapter serialisiert diese in sein
// eigenes Wire-Format — die App-Seite kennt nur diese Typen.

enum class Role { SYSTEM, USER, ASSISTANT }

// Ein Bild-Anhang für Vision-Anfragen. Bytes bleiben roh; der Adapter kodiert
// sie provider-spezifisch (OpenAI: data-URL base64, Anthropic: source.base64, …).
data class ImageRef(
    val bytes: ByteArray,
    val mimeType: String,
) {
    // data/equals/hashCode manuell wegen ByteArray-Identität.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImageRef) return false
        return mimeType == other.mimeType && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + mimeType.hashCode()
}

data class ChatMessage(
    val role: Role,
    val content: String,
    val images: List<ImageRef> = emptyList(),
)

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val system: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val stream: Boolean = true,
    // Web-Suche für diesen Request gewünscht? Der CapabilityRouter entscheidet,
    // ob native Grounding oder externer RAG-Loop genutzt wird.
    val webSearch: Boolean = false,
)

// Ein Streaming-Delta (SSE-Chunk). `text` ist das inkrementelle Token-Stück.
data class ChatDelta(
    val text: String,
    val done: Boolean = false,
    // Optionale Metadaten am Ende des Streams (finish_reason, usage …).
    val finishReason: String? = null,
)
