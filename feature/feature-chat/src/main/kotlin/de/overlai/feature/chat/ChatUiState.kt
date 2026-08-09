package de.overlai.feature.chat

import de.overlai.conversation.ChatUiMessage

// CHANGE-MARKER: Chat-Kern vereinheitlicht (P2.1a, siehe CHANGELOG.md)
// UI-State des Chat-Screens. `messages` nutzt jetzt das gemeinsame ChatUiMessage
// (core-conversation), das alle Oberflächen teilen — die frühere eigene UiMessage-
// Klasse (strukturgleich) ist entfallen. Streaming/Verlauf kommen aus der
// ConversationSession; hier bleiben nur die screen-spezifischen Felder.
data class ChatUiState(
    val messages: List<ChatUiMessage> = emptyList(),
    val input: String = "",
    val isStreaming: Boolean = false,
    val error: String? = null,
    val providerName: String = "",
    // P3: Modell der Session für den Anbieter/Modell-Chip im Header (null = Provider-Default).
    val modelId: String? = null,
    val hasApiKey: Boolean = true,
)
