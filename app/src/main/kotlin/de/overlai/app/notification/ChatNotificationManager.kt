package de.overlai.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import de.overlai.app.MainActivity

// CHANGE-MARKER: Entry-Points (P2.4, siehe CHANGELOG.md)
// Persistenter Benachrichtigungs-Zugang zum Chat: Tippen öffnet den aktiven Chat (Deep-Link
// via MainActivity.EXTRA_OPEN_SESSION); die "Antworten"-Action (RemoteInput) schickt eine
// Nachricht direkt an den aktiven Chat (headless über QuickReplyReceiver). MessagingStyle
// zeigt die letzte Antwort inline. Schaltbar via SettingsStore.notificationEnabled.
object ChatNotificationManager {
    const val CHANNEL_ID = "overlai_quickchat"
    const val NOTIFICATION_ID = 4202
    const val KEY_REPLY = "de.overlai.app.notification.KEY_REPLY"
    const val ACTION_REPLY = "de.overlai.app.notification.action.REPLY"
    const val EXTRA_SESSION_ID = "de.overlai.app.notification.EXTRA_SESSION_ID"

    private val botPerson = Person.Builder().setName("OverlAI").build()

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel =
            NotificationChannel(CHANNEL_ID, "OverlAI-Schnellzugang", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Persistenter Chat-Zugang mit Direktantwort."
                setShowBadge(false)
            }
        manager.createNotificationChannel(channel)
    }

    // Postet/aktualisiert die persistente Notification. [lastReply] optional = letzte Antwort
    // des Assistenten (nach Quick-Reply), die inline angezeigt wird.
    fun show(
        context: Context,
        activeSessionId: String?,
        lastUserText: String? = null,
        lastReply: String? = null,
    ) {
        ensureChannel(context)

        val openIntent =
            Intent(context, MainActivity::class.java).apply {
                activeSessionId?.let { putExtra(MainActivity.EXTRA_OPEN_SESSION, it) }
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        val contentPi =
            PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val remoteInput = RemoteInput.Builder(KEY_REPLY).setLabel("Nachricht an OverlAI…").build()
        val replyIntent =
            Intent(context, QuickReplyReceiver::class.java).apply {
                action = ACTION_REPLY
                activeSessionId?.let { putExtra(EXTRA_SESSION_ID, it) }
            }
        val replyPi =
            PendingIntent.getBroadcast(
                context,
                1,
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
        val replyAction =
            NotificationCompat.Action.Builder(android.R.drawable.ic_menu_send, "Antworten", replyPi)
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(false)
                .build()

        val style =
            NotificationCompat.MessagingStyle(botPerson).also { s ->
                lastUserText?.let {
                    s.addMessage(
                        it,
                        System.currentTimeMillis(),
                        Person.Builder().setName("Du").build(),
                    )
                }
                lastReply?.let { s.addMessage(it, System.currentTimeMillis(), botPerson) }
                if (lastUserText == null && lastReply == null) {
                    s.addMessage("Tippe zum Öffnen oder antworte direkt.", System.currentTimeMillis(), botPerson)
                }
            }

        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setStyle(style)
                .setContentIntent(contentPi)
                .addAction(replyAction)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

        // POST_NOTIFICATIONS-Check obliegt dem Aufrufer; NotificationManagerCompat ignoriert
        // den Post ohne Permission ohnehin still.
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
