package de.overlai.feature.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

// CHANGE-MARKER v0.5.2: Overlay-Bubble (M3, siehe CHANGELOG.md)
// Foreground-Service, der die Overlay-Bubble trägt. specialUse (nicht dataSync — dessen
// 6h-Cap würde ein dauerhaft verfügbares Overlay beenden). Der Service besitzt den
// OverlayWindowController; ohne SYSTEM_ALERT_WINDOW beendet er sich sofort selbst.
//
// Der Chat im Panel läuft über die app-weite ConversationEngine, die der Service via
// Hilt-@EntryPoint (OverlayDependencies) holt — der Service selbst ist kein
// @AndroidEntryPoint, analog feature-share/ShareDependencies.
class OverlayService : Service() {
    private var controller: OverlayWindowController? = null

    // Service-eigener Scope für die Chat-Streams (Main-Dispatcher: die Engine-Collector
    // aktualisieren Compose-State). In onDestroy gecancelt.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val chatState: OverlayChatState by lazy {
        val engine =
            EntryPointAccessors
                .fromApplication(applicationContext, OverlayDependencies::class.java)
                .conversationEngine()
        OverlayChatState(engine, serviceScope)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // Ohne Overlay-Permission kein Overlay — sauber beenden statt zu crashen.
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startBubble()
        }
        // NICHT sticky: nach einem Kill soll die Bubble nicht ungefragt zurückkehren —
        // sie erscheint nur auf ausdrücklichen Nutzerwunsch (Toggle).
        return START_NOT_STICKY
    }

    private fun startBubble() {
        startForegroundNotification()
        val ctrl = controller ?: OverlayWindowController(this, chatState).also { controller = it }
        ctrl.showBubble()
    }

    private fun startForegroundNotification() {
        createChannel()
        val notification: Notification =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("OverlAI-Bubble aktiv")
                .setContentText("Tippe die Bubble an, um OverlAI zu öffnen.")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // NotificationChannel ist ab API 26 Pflicht; createNotificationChannel ist idempotent.
    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "OverlAI-Overlay",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Hinweis, dass die Overlay-Bubble läuft."
                setShowBadge(false)
            }
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        controller?.removeAll()
        controller = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "overlai_overlay"
        private const val NOTIFICATION_ID = 4201
        private const val ACTION_STOP = "de.overlai.feature.overlay.action.STOP"

        // Bubble starten (Foreground-Service). Voraussetzung: Settings.canDrawOverlays.
        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.startForegroundService(intent)
        }

        // Bubble beenden (Service stoppt sich selbst und räumt die Views ab).
        fun stop(context: Context) {
            val intent =
                Intent(context, OverlayService::class.java).apply {
                    action = ACTION_STOP
                }
            context.startService(intent)
        }
    }
}
