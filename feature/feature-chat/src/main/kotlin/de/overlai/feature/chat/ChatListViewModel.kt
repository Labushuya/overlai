package de.overlai.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.overlai.core.data.SettingsStore
import de.overlai.core.data.chat.ChatSession
import de.overlai.core.data.chat.Project
import de.overlai.core.data.chat.SessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

// CHANGE-MARKER: Projekte/Gruppen (Phase 3 E2, siehe CHANGELOG.md)
// Übersicht aller Chat-Sessions, gruppiert nach Projekt. Beobachtet Sessions + Projekte
// reaktiv, bietet Chat-CRUD (open/rename/delete) sowie Projekt-CRUD (anlegen/umbenennen/
// löschen) und das Verschieben von Chats zwischen Projekten (inkl. „Ohne Projekt").
class ChatListViewModel(
    private val repo: SessionRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    // Eine Gruppe für die Anzeige: Projekt (null = „Ohne Projekt") + zugehörige Chats.
    data class Group(
        val project: Project?,
        val chats: List<ChatSession>,
    )

    val projects: StateFlow<List<Project>> =
        repo.observeProjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    // Gruppiert: erst die Projekte (nach updatedAt), zuletzt „Ohne Projekt". Innerhalb je
    // Gruppe die Chats nach updatedAt (Repository liefert bereits absteigend sortiert).
    val groups: StateFlow<List<Group>> =
        repo.observeSessions()
            .combine(repo.observeProjects()) { sessions, projects ->
                val byProject = sessions.groupBy { it.projectId }
                val projectGroups = projects.map { p -> Group(p, byProject[p.id].orEmpty()) }
                val orphan = byProject[null].orEmpty()
                if (orphan.isNotEmpty()) projectGroups + Group(null, orphan) else projectGroups
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    fun open(id: String) {
        viewModelScope.launch { settingsStore.setActiveSession(id) }
    }

    fun rename(
        id: String,
        newTitle: String,
    ) {
        val title = newTitle.trim()
        if (title.isEmpty()) return
        viewModelScope.launch { repo.updateTitle(id, title, System.currentTimeMillis()) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.deleteSession(id) }
    }

    // --- Projekte/Gruppen ---

    fun createProject(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repo.createProject(UUID.randomUUID().toString(), trimmed, System.currentTimeMillis())
        }
    }

    fun renameProject(
        id: String,
        newName: String,
    ) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repo.renameProject(id, trimmed, System.currentTimeMillis()) }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch { repo.deleteProject(id) }
    }

    fun moveChat(
        sessionId: String,
        projectId: String?,
    ) {
        viewModelScope.launch { repo.moveChatToProject(sessionId, projectId, System.currentTimeMillis()) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}
