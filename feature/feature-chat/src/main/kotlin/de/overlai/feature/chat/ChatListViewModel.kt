package de.overlai.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.overlai.core.data.SettingsStore
import de.overlai.core.data.chat.ChatSession
import de.overlai.core.data.chat.SessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

// CHANGE-MARKER: Multi-Chat-Persistenz (P2.1b, siehe CHANGELOG.md)
// Übersicht aller Chat-Sessions. Beobachtet das SessionRepository (reaktiv), legt neue
// Sessions an und löscht sie. Der neue Chat übernimmt zunächst den global gewählten
// Provider/Modell (SettingsStore) als sinnvollen Default; pro Session änderbar (später).
class ChatListViewModel(
    private val repo: SessionRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    val sessions: StateFlow<List<ChatSession>> =
        repo.observeSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Neue Session anlegen; ruft [onCreated] mit der neuen id (Navigation zum Chat).
    fun newChat(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val providerId = settingsStore.activeProviderId.first()
            val modelId = settingsStore.activeModelId(providerId).first()
            val id = UUID.randomUUID().toString()
            repo.createSession(
                id = id,
                title = "Neuer Chat",
                providerId = providerId,
                modelId = modelId,
                now = System.currentTimeMillis(),
            )
            settingsStore.setActiveSession(id)
            onCreated(id)
        }
    }

    fun open(id: String) {
        viewModelScope.launch { settingsStore.setActiveSession(id) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.deleteSession(id) }
    }
}
