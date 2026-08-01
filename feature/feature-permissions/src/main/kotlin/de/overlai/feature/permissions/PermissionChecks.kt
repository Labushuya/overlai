package de.overlai.feature.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

// CHANGE-MARKER v0.1.0: Permission Hub (siehe CHANGELOG.md)
// Kapselt die tatsächlichen System-Checks + die Deep-Link-Intents zu genau der
// richtigen Einstellungsseite. Bewusst als reine Funktionen -> die ViewModel-
// Schicht bleibt dünn und die Intents sind an einer Stelle dokumentiert.
object PermissionChecks {
    // "Install unknown apps" für DIESE App (Voraussetzung des In-App-Updaters).
    fun canInstallPackages(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    // Deep-Link auf die "Unbekannte Apps installieren"-Seite dieser App.
    fun installUnknownAppsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )

    // App-Detailseite (Fallback für diverse Berechtigungen).
    fun appDetailsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )

    // Benachrichtigungen aktiviert? (Android 13+ Laufzeit-Permission; darunter true.)
    fun notificationsEnabled(context: Context): Boolean =
        androidx.core.app.NotificationManagerCompat
            .from(context)
            .areNotificationsEnabled()

    // "Über anderen Apps anzeigen" (SYSTEM_ALERT_WINDOW) — Voraussetzung der Overlay-Bubble.
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    // Deep-Link auf die Overlay-Berechtigungsseite dieser App.
    fun overlayPermissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
}
