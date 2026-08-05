package de.overlai.feature.overlay

import de.overlai.conversation.ChatUiMessage
import de.overlai.conversation.ConversationEngine
import de.overlai.conversation.ConversationSession
import de.overlai.core.data.SettingsStore
import de.overlai.core.data.chat.ChatSession
import de.overlai.core.data.chat.SessionRepository
import de.overlai.llm.ModelInfo
import de.overlai.llm.ProviderConfig
import de.overlai.llm.Role
import de.overlai.llm.providers.ProviderRegistry
import de.overlai.security.KeyVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

// CHANGE-MARKER: Bubble-UX-Block (P2.1c, siehe CHANGELOG.md)
// Overlay-Chat, gebunden an die AKTIVE persistente Session — REAKTIV: folgt live dem
// Session-Wechsel (activeSessionId als Flow) und dem Verlauf (observeHistory). Zeigt daher
// keine gelöschten/veralteten Verläufe mehr. Vom Service gehalten (Service-Scope).
//
// P2.1c: Spiegelt zusätzlich die ViewModel-Fähigkeiten fürs Overlay-Panel — Session-Liste
// beobachten, neuer Chat, wechseln, löschen, und Provider/Modell pro Session setzen
// (Modell-Katalog via KeyVault + HttpModelCatalog, wie im ProviderHub, reduziert).
@OptIn(ExperimentalCoroutinesApi::class)
internal class OverlayChatState(
    private val engine: ConversationEngine,
    private val scope: CoroutineScope,
    private val repo: SessionRepository,
    private val settingsStore: SettingsStore,
    private val keyVault: KeyVault,
    private val modelCatalog: de.overlai.llm.HttpModelCatalog,
) {
    private val sessionFlow = MutableStateFlow<ConversationSession?>(null)

    // Der State der aktiven Session (leer, solange keine gesetzt/geladen).
    val state: StateFlow<ConversationSession.State> =
        MutableStateFlow(ConversationSession.State()).also { out ->
            scope.launch {
                sessionFlow
                    .flatMapLatest { s -> s?.state ?: flowOf(ConversationSession.State()) }
                    .collect { out.value = it }
            }
        }

    // Alle Sessions (für die Panel-Liste) — reaktiv aus dem Repository.
    val sessions: StateFlow<List<ChatSession>> =
        repo.observeSessions().stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    // Aktive Session-Id (für Markierung „aktiv" in der Liste + Modell-Screen).
    val activeSessionId: StateFlow<String?> =
        settingsStore.activeSessionId.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    // Alle bekannten Provider (fester Katalog) — für den Modell-Screen.
    val providers: List<ProviderConfig> get() = ProviderRegistry.all

    init {
        // Aktive Session reaktiv beobachten: bei Wechsel/Löschung die ConversationSession
        // neu bauen. Kein Cachen mehr → Bubble zeigt immer die aktuelle aktive Session.
        scope.launch {
            settingsStore.activeSessionId.collect { activeId ->
                val session = activeId?.let { repo.getSession(it) }
                sessionFlow.value =
                    if (session != null) {
                        buildSession(session.id, session.providerId, session.modelId)
                    } else {
                        null // keine aktive Session (z.B. gelöscht) → leerer Panel-State
                    }
            }
        }
    }

    // Vom Panel/Service: falls noch keine aktive Session existiert, eine anlegen (mit
    // globaler Provider-Wahl). Der activeSessionId-Flow baut danach die Session.
    suspend fun ensureSessionExists() {
        if (settingsStore.activeSessionId.first() != null) return
        val providerId = settingsStore.activeProviderId.first()
        val modelId = settingsStore.activeModelId(providerId).first()
        val id = UUID.randomUUID().toString()
        repo.createSession(id, "Neuer Chat", providerId, modelId, System.currentTimeMillis())
        settingsStore.setActiveSession(id)
    }

    private fun buildSession(
        sessionId: String,
        providerId: String,
        modelId: String?,
    ): ConversationSession {
        val persistence =
            object : ConversationSession.Persistence {
                private var titleSet = false

                override fun observeHistory() =
                    repo.observeMessages(sessionId).map { list -> list.map { ChatUiMessage(it.role, it.text) } }

                override suspend fun onUserMessage(text: String) {
                    val now = System.currentTimeMillis()
                    repo.appendMessage(sessionId, Role.USER, text, now)
                    if (!titleSet) {
                        repo.updateTitle(sessionId, text.take(TITLE_MAX_LEN), now)
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
        scope.launch {
            ensureSessionExists()
            // Auf die (ggf. gerade neu angelegte) Session warten — der activeSessionId-Flow
            // baut sie asynchron; erst danach senden, damit die Nachricht nicht verpufft.
            val session = sessionFlow.value ?: sessionFlow.filterNotNull().first()
            session.send(input)
        }
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

    // --- Session-Verwaltung fürs Panel (spiegelt ChatListViewModel) ---

    // Neuen Chat anlegen + aktiv setzen (übernimmt globalen Provider/Modell als Default).
    fun newChat() {
        scope.launch {
            val providerId = settingsStore.activeProviderId.first()
            val modelId = settingsStore.activeModelId(providerId).first()
            val id = UUID.randomUUID().toString()
            repo.createSession(id, "Neuer Chat", providerId, modelId, System.currentTimeMillis())
            settingsStore.setActiveSession(id)
        }
    }

    // Bestehende Session aktiv setzen (Panel wechselt zu ihrem Verlauf).
    fun switchTo(id: String) {
        scope.launch { settingsStore.setActiveSession(id) }
    }

    // Session löschen; war sie aktiv, aktive Auswahl leeren (Panel zeigt dann leer/Liste).
    fun delete(id: String) {
        scope.launch {
            repo.deleteSession(id)
            if (settingsStore.activeSessionId.first() == id) {
                settingsStore.clearActiveSession()
            }
        }
    }

    // --- Modellwahl fürs Panel (spiegelt ProviderHubViewModel, reduziert) ---

    // Modelle eines Providers laden (BYOK: braucht Key). list() fängt Fehler intern und
    // liefert immer eine nicht-leere Liste (mind. Default-Modell). Ohne Key → leer (Hinweis).
    suspend fun listModels(providerId: String): List<ModelInfo> {
        val config = ProviderRegistry.byId(providerId) ?: return emptyList()
        val key = keyVault.getKey(providerId)
        if (key.isNullOrBlank()) return emptyList()
        return runCatching { modelCatalog.list(config, key) }.getOrDefault(emptyList())
    }

    // Provider mit hinterlegtem Key (für die Provider-Auswahl im Panel — nur mit Key wählbar).
    suspend fun providersWithKey(): Set<String> =
        providers.mapNotNull { p -> p.id.takeIf { keyVault.hasKey(p.id) } }.toSet()

    // Provider/Modell der AKTIVEN Session setzen: persistieren + Session neu bauen (der
    // activeSessionId-Flow re-emittiert nicht bei reiner Metadaten-Änderung, daher hier
    // explizit neu aufbauen mit dem neuen Streamer).
    fun setModel(
        providerId: String,
        modelId: String?,
    ) {
        scope.launch {
            val id = settingsStore.activeSessionId.first() ?: return@launch
            repo.updateProviderModel(id, providerId, modelId, System.currentTimeMillis())
            sessionFlow.value = buildSession(id, providerId, modelId)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
        const val TITLE_MAX_LEN = 40
    }
}
