package de.overlai.feature.updater

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

// CHANGE-MARKER v0.4.2: In-App-Updater — Install-Status-Receiver (siehe CHANGELOG.md)
// Installiert eine (bereits heruntergeladene + sha256-verifizierte) APK über die
// PackageInstaller-Session-API. WICHTIG: Für eine normale App gibt es KEIN
// echtes Silent-Update. commit() zeigt NICHT direkt einen Dialog, sondern liefert
// zuerst den Broadcast STATUS_PENDING_USER_ACTION mit dem eigentlichen System-
// Install-Dialog in EXTRA_INTENT — den MÜSSEN wir per startActivity starten. Ohne
// den Receiver unten passiert beim "Installieren" sichtbar nichts. Die APK MUSS
// mit demselben Key signiert sein wie die Installation (sonst
// INSTALL_FAILED_UPDATE_INCOMPATIBLE) — daher der stabile CI-Signing-Key.
class PackageInstallerSession(
    private val context: Context,
) {
    companion object {
        const val ACTION_INSTALL_STATUS = "de.overlai.action.INSTALL_STATUS"
        private const val BUFFER = 65536
    }

    // Ergebnis des Install-Flows; vom Receiver gesetzt, vom ViewModel collected.
    sealed interface InstallStatus {
        data object Idle : InstallStatus

        // Systemdialog wurde gestartet und wartet auf Nutzerbestätigung.
        data object Pending : InstallStatus

        data object Success : InstallStatus

        data class Failure(
            val message: String,
        ) : InstallStatus
    }

    private val _status = MutableStateFlow<InstallStatus>(InstallStatus.Idle)
    val status: StateFlow<InstallStatus> = _status.asStateFlow()

    private val app = context.applicationContext

    @Volatile private var registered = false

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                ctx: Context,
                intent: Intent,
            ) {
                when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val confirm =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(PackageInstaller.EXTRA_INTENT, Intent::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(PackageInstaller.EXTRA_INTENT)
                            }
                        if (confirm != null) {
                            // Aus Non-Activity-Context zwingend NEW_TASK, sonst AndroidRuntimeException.
                            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            _status.value = InstallStatus.Pending
                            app.startActivity(confirm)
                        } else {
                            _status.value = InstallStatus.Failure("Kein Bestätigungs-Intent vom System")
                        }
                    }
                    PackageInstaller.STATUS_SUCCESS -> _status.value = InstallStatus.Success
                    else -> {
                        val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                        _status.value = InstallStatus.Failure(msg ?: "Installation fehlgeschlagen")
                    }
                }
            }
        }

    // Einmalig für die Prozesslebensdauer registrieren. NOT_EXPORTED + setPackage(self)
    // hält den Broadcast paket-intern (ab targetSdk 34 Pflicht für Context-Receiver).
    private fun ensureReceiver() {
        if (registered) return
        ContextCompat.registerReceiver(
            app,
            receiver,
            IntentFilter(ACTION_INSTALL_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        registered = true
    }

    // Startet die Installation der APK-Datei. Gibt false zurück, wenn die
    // Voraussetzung "Unbekannte Apps installieren" fehlt (Aufrufer -> Permission Hub).
    fun install(apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            return false
        }
        ensureReceiver()
        _status.value = InstallStatus.Idle
        val installer = context.packageManager.packageInstaller
        val params =
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
            }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            writeApk(session, apk)
            // commit() zeigt NICHT direkt den Dialog: das System sendet den Status
            // (u.a. STATUS_PENDING_USER_ACTION) an unseren Receiver -> der startet ihn.
            session.commit(buildStatusIntentSender(sessionId))
        }
        return true
    }

    private fun writeApk(
        session: PackageInstaller.Session,
        apk: File,
    ) {
        apk.inputStream().use { input ->
            session.openWrite("overlai_update", 0, apk.length()).use { output ->
                input.copyTo(output, BUFFER)
                session.fsync(output)
            }
        }
    }

    private fun buildStatusIntentSender(sessionId: Int): android.content.IntentSender {
        val statusIntent = Intent(ACTION_INSTALL_STATUS).setPackage(context.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(context, sessionId, statusIntent, flags).intentSender
    }
}
