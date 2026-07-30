package de.overlai.feature.chat

import de.overlai.llm.ChatMessage
import de.overlai.llm.Role

// CHANGE-MARKER v0.1.0: Chat-UI (siehe CHANGELOG.md)
// UI-State des Chat-Screens. Getrennt vom LLM-Domänentyp, damit die UI eigene
// Belange (Streaming-Flag, Fehler) modellieren kann.
data class ChatUiState(
    val messages: List<UiMessage> = emptyList(),
    val input: String = "",
    val isStreaming: Boolean = false,
    val error: String? = null,
    val providerName: String = "",
    val hasApiKey: Boolean = true,
)

data class UiMessage(
    val role: Role,
    val text: String,
    // Während des Streamens wächst der Assistant-Text inkrementell.
    val streaming: Boolean = false,
) {
    fun toDomain(): ChatMessage = ChatMessage(role = role, content = text)
}
