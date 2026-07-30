package de.overlai.feature.updater

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import java.io.File

// CHANGE-MARKER v0.1.0: In-App-Updater (siehe CHANGELOG.md)
// Installiert eine (bereits heruntergeladene + sha256-verifizierte) APK über die
// PackageInstaller-Session-API. WICHTIG: Für eine normale App gibt es KEIN
// echtes Silent-Update — das System zeigt den Install-Bestätigungsdialog. Diese
// Klasse startet die Session; der OS-Dialog erscheint beim commit(). Die APK MUSS
// mit demselben Key signiert sein wie die Installation (sonst
// INSTALL_FAILED_UPDATE_INCOMPATIBLE) — daher der stabile CI-Signing-Key.
class PackageInstallerSession(
    private val context: Context,
) {
    companion object {
        const val ACTION_INSTALL_STATUS = "de.overlai.action.INSTALL_STATUS"
        private const val BUFFER = 65536
    }

    // Startet die Installation der APK-Datei. Gibt false zurück, wenn die
    // Voraussetzung "Unbekannte Apps installieren" fehlt (Aufrufer -> Permission Hub).
    fun install(apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            return false
        }
        val installer = context.packageManager.packageInstaller
        val params =
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
            }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("overlai_update", 0, apk.length()).use { output ->
                    val buffer = ByteArray(BUFFER)
                    var read = input.read(buffer)
                    while (read >= 0) {
                        output.write(buffer, 0, read)
                        read = input.read(buffer)
                    }
                    session.fsync(output)
                }
            }
            val statusIntent = Intent(ACTION_INSTALL_STATUS).setPackage(context.packageName)
            val flags =
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            val pending = PendingIntent.getBroadcast(context, sessionId, statusIntent, flags)
            // commit() -> das System zeigt jetzt den Install-Bestätigungsdialog.
            session.commit(pending.intentSender)
        }
        return true
    }
}
