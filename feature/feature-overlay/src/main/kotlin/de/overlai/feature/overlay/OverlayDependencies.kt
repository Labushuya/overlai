package de.overlai.feature.overlay

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.overlai.conversation.ConversationEngine

// CHANGE-MARKER v0.5.2: Overlay-Bubble Chat (M3.2, siehe CHANGELOG.md)
// Der OverlayService ist kein @AndroidEntryPoint und hat keine Konstruktor-Injektion.
// Über diesen Hilt-@EntryPoint holt er die app-weit bereitgestellte ConversationEngine
// (aus AppModule) — analog feature-share/ShareDependencies. Zugriff via
// EntryPointAccessors.fromApplication(context, OverlayDependencies::class.java).
@EntryPoint
@InstallIn(SingletonComponent::class)
interface OverlayDependencies {
    fun conversationEngine(): ConversationEngine
}
