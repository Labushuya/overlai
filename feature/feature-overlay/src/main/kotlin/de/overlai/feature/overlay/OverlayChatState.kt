package de.overlai.feature.overlay

import de.overlai.conversation.ChatUiMessage
import de.overlai.conversation.ConversationEngine
import de.overlai.conversation.ConversationSession
import de.overlai.core.data.SettingsStore
import de.overlai.core.data.chat.SessionRepository
import de.overlai.llm.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

// CHANGE-MARKER: Multi-Chat-Persistenz (P2.1b, siehe CHANGELOG.md)
// Overlay-Chat, gebunden an die AKTIVE persistente Session (active_session_id) — zeigt
// denselben Verlauf wie der Fullscreen-Chat, mit deren eigenem Provider/Modell. Gibt es
// (noch) keine aktive Session, wird eine angelegt. Vom Service gehalten (Service-Scope),
// damit der Zustand das Auf-/Zuklappen des Panels überdauert.
internal class OverlayChatState(
    private val engine: ConversationEngine,
    private val scope: CoroutineScope,
    private val repo: SessionRepository,
    private val settingsStore: SettingsStore,
) {
    private val sessionFlow = MutableStateFlow<ConversationSession?>(null)

    // Der State der aktiven Session (leer, solange noch nicht geladen).
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<ConversationSession.State> =
        MutableStateFlow(ConversationSession.State()).also { out ->
            scope.launch {
                sessionFlow
                    .flatMapLatest { s -> s?.state ?: flowOf(ConversationSession.State()) }
                    .collect { out.value = it }
            }
        }

    init {
        scope.launch { ensureSession() }
    }

    // Aktive Session laden bzw. anlegen, dann die persistente ConversationSession bauen.
    private suspend fun ensureSession() {
        if (sessionFlow.value != null) return
        val activeId = settingsStore.activeSessionId.first()
        val session = activeId?.let { repo.getSession(it) }
        val id: String
        val providerId: String
        val modelId: String?
        if (session != null) {
            id = session.id
            providerId = session.providerId
            modelId = session.modelId
        } else {
            // Keine aktive Session → eine neue mit der globalen Provider-Wahl anlegen.
            providerId = settingsStore.activeProviderId.first()
            modelId = settingsStore.activeModelId(providerId).first()
            id = java.util.UUID.randomUUID().toString()
            repo.createSession(id, "Neuer Chat", providerId, modelId, System.currentTimeMillis())
            settingsStore.setActiveSession(id)
        }
        sessionFlow.value = buildSession(id, providerId, modelId)
    }

    private fun buildSession(
        sessionId: String,
        providerId: String,
        modelId: String?,
    ): ConversationSession {
        val persistence =
            object : ConversationSession.Persistence {
                private var titleSet = false

                override suspend fun loadHistory(): List<ChatUiMessage> =
                    repo.messages(sessionId).map { ChatUiMessage(it.role, it.text) }

                override suspend fun onUserMessage(text: String) {
                    val now = System.currentTimeMillis()
                    repo.appendMessage(sessionId, Role.USER, text, now)
                    if (!titleSet) {
                        repo.updateTitle(sessionId, text.take(40), now)
                        titleSet = true
                    }
                }

                override suspend fun onAssistantMessage(text: String) {
                    repo.appendMessage(sessionId, Role.ASSISTANT, text, System.currentTimeMillis())
                }
            }
        return ConversationSession(engine.streamerFor(providerId, modelId), scope, persistence)
    }

    fun send(input: String) {
        sessionFlow.value?.send(input)
    }

    fun cancelStream() {
        sessionFlow.value?.cancel()
    }

    fun reset() {
        sessionFlow.value?.reset()
    }

    fun checkKey() {
        sessionFlow.value?.showKeyHintIfMissing()
    }
}
