package de.overlai.llm

import kotlinx.coroutines.flow.Flow

// CHANGE-MARKER v0.1.0: Provider-Abstraktion (siehe CHANGELOG.md)
// Das zentrale Provider-Interface. Alles hängt hieran. Reines Kotlin — keine
// Android-Deps -> mit MockWebServer ohne Emulator testbar.
interface LlmProvider {
    val config: ProviderConfig

    fun capabilities(): Set<Capability> = config.capabilities

    // Text- oder Vision-Chat (Bilder sind Teil der ChatMessage). Streaming per Flow.
    fun chat(
        request: ChatRequest,
        apiKey: String,
    ): Flow<ChatDelta>

    // Kurze Transcription (Audio -> Text). Optional; nur wenn CAPABILITY vorhanden.
    suspend fun transcribe(
        audio: ByteArray,
        mimeType: String,
        apiKey: String,
        model: String = config.defaultModel,
    ): String =
        throw UnsupportedOperationException("${config.displayName} unterstützt keine Transcription")
}

// Fehler-Hierarchie, die die UI unterscheiden kann (Key falsch vs. Netz vs. Rate-Limit).
sealed class LlmError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Unauthorized(message: String = "API-Key ungültig oder fehlt") : LlmError(message)

    class RateLimited(message: String = "Rate-Limit erreicht") : LlmError(message)

    class Network(
        message: String,
        cause: Throwable? = null,
    ) : LlmError(message, cause)

    class Api(
        val status: Int,
        message: String,
    ) : LlmError("API-Fehler $status: $message")

    class Unknown(
        message: String,
        cause: Throwable? = null,
    ) : LlmError(message, cause)
}
