package de.overlai.feature.overlay

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.overlai.conversation.ConversationEngine
import de.overlai.core.data.SettingsStore
import de.overlai.core.data.chat.SessionRepository

// CHANGE-MARKER: Multi-Chat-Persistenz (P2.1b, siehe CHANGELOG.md)
// Der OverlayService ist kein @AndroidEntryPoint und hat keine Konstruktor-Injektion.
// Über diesen Hilt-@EntryPoint holt er die app-weit bereitgestellten Singletons (aus
// AppModule): die Engine + (P2.1b) das SessionRepository und den SettingsStore, damit die
// Bubble dieselbe aktive, persistente Session wie der Fullscreen-Chat öffnet.
@EntryPoint
@InstallIn(SingletonComponent::class)
interface OverlayDependencies {
    fun conversationEngine(): ConversationEngine

    fun sessionRepository(): SessionRepository

    fun settingsStore(): SettingsStore
}
