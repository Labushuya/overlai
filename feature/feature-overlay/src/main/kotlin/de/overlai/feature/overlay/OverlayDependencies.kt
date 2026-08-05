package de.overlai.feature.overlay

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.overlai.conversation.ConversationEngine
import de.overlai.core.data.SettingsStore
import de.overlai.core.data.chat.SessionRepository
import de.overlai.llm.HttpModelCatalog
import de.overlai.security.KeyVault

// CHANGE-MARKER: Multi-Chat-Persistenz (P2.1b) + Bubble-Vollsteuerung (P2.1c, siehe CHANGELOG.md)
// Der OverlayService ist kein @AndroidEntryPoint und hat keine Konstruktor-Injektion.
// Über diesen Hilt-@EntryPoint holt er die app-weit bereitgestellten Singletons (aus
// AppModule): die Engine + (P2.1b) das SessionRepository und den SettingsStore, damit die
// Bubble dieselbe aktive, persistente Session wie der Fullscreen-Chat öffnet. (P2.1c) dazu
// KeyVault + ModelCatalog, damit das Panel Provider/Modell pro Session wechseln kann.
@EntryPoint
@InstallIn(SingletonComponent::class)
interface OverlayDependencies {
    fun conversationEngine(): ConversationEngine

    fun sessionRepository(): SessionRepository

    fun settingsStore(): SettingsStore

    fun keyVault(): KeyVault

    fun modelCatalog(): HttpModelCatalog
}
