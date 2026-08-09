package de.overlai.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.overlai.core.data.SettingsStore
import de.overlai.core.data.chat.ChatSession
import de.overlai.core.data.chat.SessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// CHANGE-MARKER: Chat-Organisation & Modell-UX (Phase 3, siehe CHANGELOG.md)
// Übersicht aller Chat-Sessions. Beobachtet das SessionRepository (reaktiv), öffnet/benennt
// um/löscht Sessions. Das Anlegen eines neuen Chats liegt jetzt im geführten NewChatSheet
// (NewChatViewModel) — hier nur noch open/rename/delete.
class ChatListViewModel(
    private val repo: SessionRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    val sessions: StateFlow<List<ChatSession>> =
        repo.observeSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

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

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}
