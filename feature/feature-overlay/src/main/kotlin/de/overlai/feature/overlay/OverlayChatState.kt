package de.overlai.feature.overlay

import de.overlai.conversation.ConversationEngine
import de.overlai.conversation.ConversationSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

// CHANGE-MARKER: Chat-Kern vereinheitlicht (P2.1a, siehe CHANGELOG.md)
// Dünner Wrapper um die gemeinsame ConversationSession (core-conversation). Die frühere
// eigene SnapshotStateList-Akkumulation + UiMessage-Klasse sind ENTFALLEN — der Overlay-
// Chat nutzt jetzt denselben Kern wie der Fullscreen-Chat. Vom Service gehalten (Service-
// Scope), damit der Zustand das Auf-/Zuklappen des Panels überdauert.
internal class OverlayChatState(
    engine: ConversationEngine,
    scope: CoroutineScope,
) {
    private val session = ConversationSession(engine, scope)

    val state: StateFlow<ConversationSession.State> get() = session.state

    fun send(input: String) = session.send(input)

    fun cancelStream() = session.cancel()

    fun reset() = session.reset()

    // Beim Öffnen des Panels: „kein Key"-Hinweis einblenden, falls nötig.
    fun checkKey() = session.showKeyHintIfMissing()
}
