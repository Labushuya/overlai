package de.overlai.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import de.overlai.conversation.ConversationEngine
import de.overlai.core.data.SettingsStore
import de.overlai.core.data.chat.SessionRepository
import de.overlai.llm.ChatMessage
import de.overlai.llm.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// CHANGE-MARKER: Entry-Points (P2.4, siehe CHANGELOG.md)
// Empfängt die Quick-Reply (RemoteInput) der persistenten Chat-Notification: hängt die
// Nachricht an die aktive Session, erzeugt HEADLESS die Antwort (ConversationEngine.complete),
// persistiert sie und aktualisiert die Notification. goAsync() hält den Receiver für den
// suspend-Aufruf am Leben.
class QuickReplyReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun conversationEngine(): ConversationEngine

        fun sessionRepository(): SessionRepository

        fun settingsStore(): SettingsStore
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ChatNotificationManager.ACTION_REPLY) return
        val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(ChatNotificationManager.KEY_REPLY)
        val text = reply?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return

        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        val repo = deps.sessionRepository()
        val engine = deps.conversationEngine()
        val settings = deps.settingsStore()
        val app = context.applicationContext

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sessionId =
                    intent.getStringExtra(ChatNotificationManager.EXTRA_SESSION_ID)
                        ?: settings.activeSessionId.first()
                if (sessionId == null) return@launch
                val session = repo.getSession(sessionId) ?: return@launch
                val now = System.currentTimeMillis()
                repo.appendMessage(sessionId, Role.USER, text, now)

                // Headless-Antwort auf den vollen Verlauf.
                val history = repo.messages(sessionId).map { ChatMessage(it.role, it.text) }
                val answer =
                    runCatching { engine.complete(session.providerId, session.modelId, history) }
                        .getOrElse { "(Fehler: ${it.message})" }
                repo.appendMessage(sessionId, Role.ASSISTANT, answer, System.currentTimeMillis())

                // Notification mit letzter Runde aktualisieren.
                ChatNotificationManager.show(app, sessionId, lastUserText = text, lastReply = answer)
            } finally {
                pending.finish()
            }
        }
    }
}
