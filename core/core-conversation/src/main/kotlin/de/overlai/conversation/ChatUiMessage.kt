package de.overlai.conversation

import de.overlai.llm.ChatMessage
import de.overlai.llm.Role

// CHANGE-MARKER: Chat-Kern vereinheitlicht (P2.1a, siehe CHANGELOG.md)
// Das EINE gemeinsame UI-Nachrichtenmodell für alle Oberflächen (Fullscreen-Chat,
// Overlay-Panel, künftig Notification/Share). Ersetzt die zuvor zwei getrennten,
// strukturgleichen UiMessage-Klassen in feature-chat und feature-overlay.
//
// `streaming` markiert die noch wachsende Assistant-Antwort (UI zeigt z.B. "…").
data class ChatUiMessage(
    val role: Role,
    val text: String,
    val streaming: Boolean = false,
) {
    fun toDomain(): ChatMessage = ChatMessage(role = role, content = text)
}
