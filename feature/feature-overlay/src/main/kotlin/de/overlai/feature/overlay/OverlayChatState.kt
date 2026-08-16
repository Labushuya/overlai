package de.overlai.feature.overlay

import de.overlai.conversation.ChatUiMessage
import de.overlai.conversation.ConversationEngine
import de.overlai.conversation.ConversationSession
import de.overlai.conversation.HandoverGenerator
import de.overlai.core.data.SettingsStore
import de.overlai.core.data.chat.ChatSession
import de.overlai.core.data.chat.Project
import de.overlai.core.data.chat.SessionRepository
import de.overlai.llm.ModelContextTable
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

// CHANGE-MARKER: Overlay-Parität (Phase 3 E3c, siehe CHANGELOG.md)
// Overlay-Chat, gebunden an die AKTIVE persistente Session — REAKTIV (folgt Session-Wechsel +
// Verlauf). Spiegelt jetzt die VOLLEN Fullscreen-Fähigkeiten fürs Overlay-Panel: Session-Liste
// + Projekte/Gruppen (CRUD + verschieben), umbenennen, Provider/Modell-Wahl, Usage/Kontext-
// fenster und Handover. Vom Service gehalten (Service-Scope).
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // Panel-State-Holder spiegelt bewusst die volle Fullscreen-Fläche.
internal class OverlayChatState(
    private val engine: ConversationEngine,
    private val scope: CoroutineScope,
    private val repo: SessionRepository,
    private val settingsStore: SettingsStore,
    private val keyVault: KeyVault,
    private val modelCatalog: de.overlai.llm.HttpModelCatalog,
    private val handover: HandoverGenerator,
) {
    // Eine Projekt-Gruppe für die Panel-Liste (null = „Ohne Projekt").
    data class Group(
        val project: Project?,
        val chats: List<ChatSession>,
    )

    // Handover-/Usage-UI-State des Panels (getrennt von ConversationSession.State).
    data class PanelUiState(
        val contextLimit: Int? = null,
        val handoverPreview: String? = null,
        val handoverLoading: Boolean = false,
    )

    private val sessionFlow = MutableStateFlow<ConversationSession?>(null)

    private val _ui = MutableStateFlow(PanelUiState())
    val ui: StateFlow<PanelUiState> = _ui.asStateFlow()

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

    // Projekte + gruppierte Chats (E3c) — wie ChatListViewModel.
    val projects: StateFlow<List<Project>> =
        repo.observeProjects().stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val groups: StateFlow<List<Group>> =
        repo.observeSessions()
            .combine(repo.observeProjects()) { sessions, projects ->
                val byProject = sessions.groupBy { it.projectId }
                val projectGroups = projects.map { p -> Group(p, byProject[p.id].orEmpty()) }
                val orphan = byProject[null].orEmpty()
                if (orphan.isNotEmpty()) projectGroups + Group(null, orphan) else projectGroups
            }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    // Alle bekannten Provider (fester Katalog) — für den Modell-Screen.
    val providers: List<ProviderConfig> get() = ProviderRegistry.all

    init {
        // Aktive Session reaktiv beobachten: bei Wechsel/Löschung die ConversationSession
        // neu bauen. Kein Cachen mehr → Bubble zeigt immer die aktuelle aktive Session.
        scope.launch {
            settingsStore.activeSessionId.collect { activeId ->
                val session = activeId?.let { repo.getSession(it) }
                _ui.value = _ui.value.copy(contextLimit = ModelContextTable.resolve(session?.modelId, null))
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

    // Provider/Modell der AKTIVEN Session setzen: persistieren + nur den Streamer der
    // bestehenden Session tauschen (kein Neuaufbau — der würde einen ggf. laufenden Turn als
    // „(abgebrochen)" persistieren und einen zweiten observeHistory-Collector leaken).
    fun setModel(
        providerId: String,
        modelId: String?,
    ) {
        scope.launch {
            val id = settingsStore.activeSessionId.first() ?: return@launch
            repo.updateProviderModel(id, providerId, modelId, System.currentTimeMillis())
            _ui.value = _ui.value.copy(contextLimit = ModelContextTable.resolve(modelId, null))
            val current = sessionFlow.value
            if (current != null) {
                current.swapStreamer(engine.streamerFor(providerId, modelId))
            } else {
                sessionFlow.value = buildSession(id, providerId, modelId)
            }
        }
    }

    // --- Chat-CRUD + Projekte/Gruppen fürs Panel (E3c, spiegelt ChatListViewModel) ---

    fun rename(
        id: String,
        newTitle: String,
    ) {
        val title = newTitle.trim().take(TITLE_MAX_LEN)
        if (title.isEmpty()) return
        scope.launch { repo.updateTitle(id, title, System.currentTimeMillis()) }
    }

    fun createProject(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        scope.launch { repo.createProject(UUID.randomUUID().toString(), trimmed, System.currentTimeMillis()) }
    }

    fun renameProject(
        id: String,
        newName: String,
    ) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        scope.launch { repo.renameProject(id, trimmed, System.currentTimeMillis()) }
    }

    fun deleteProject(id: String) {
        scope.launch { repo.deleteProject(id) }
    }

    fun moveChat(
        sessionId: String,
        projectId: String?,
    ) {
        scope.launch { repo.moveChatToProject(sessionId, projectId, System.currentTimeMillis()) }
    }

    // --- Handover fürs Panel (E3c, spiegelt ChatViewModel) ---

    fun generateHandover() {
        if (_ui.value.handoverLoading) return
        _ui.value = _ui.value.copy(handoverLoading = true)
        scope.launch {
            val id = settingsStore.activeSessionId.first()
            if (id == null) {
                _ui.value = _ui.value.copy(handoverLoading = false)
                return@launch
            }
            val result = runCatching { handover.generate(id) }
            _ui.value =
                result.fold(
                    onSuccess = { text -> _ui.value.copy(handoverLoading = false, handoverPreview = text) },
                    onFailure = { _ui.value.copy(handoverLoading = false) },
                )
        }
    }

    fun dismissHandover() {
        _ui.value = _ui.value.copy(handoverPreview = null)
    }

    // Handover übernehmen → neue Session desselben Chats aktiv setzen (Panel folgt reaktiv);
    // sobald sie gebaut ist und genau die Handover-Nachricht enthält, Auto-Antwort anstoßen.
    fun applyHandover(text: String) {
        scope.launch {
            val id = settingsStore.activeSessionId.first() ?: return@launch
            val newId = UUID.randomUUID().toString()
            runCatching { handover.apply(id, text, newId) }
                .onSuccess {
                    _ui.value = _ui.value.copy(handoverPreview = null)
                    settingsStore.setActiveSession(newId)
                    // Warten, bis der activeSessionId-Collector die Session für newId gebaut hat.
                    activeSessionId.filterNotNull().first { it == newId }
                    val session = sessionFlow.filterNotNull().first()
                    // Fingerabdruck: genau die Handover-Nachricht (1 × ASSISTANT) → Auto-Antwort.
                    val history = repo.messages(newId)
                    if (history.size == 1 && history.single().role == Role.ASSISTANT) {
                        session.primeAndRespond()
                    }
                }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
        const val TITLE_MAX_LEN = 40
    }
}
