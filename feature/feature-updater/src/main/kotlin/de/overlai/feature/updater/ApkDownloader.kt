package de.overlai.feature.updater

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

// CHANGE-MARKER v0.1.0: In-App-Updater (siehe CHANGELOG.md)
// Lädt die Update-APK in den App-Cache (cache/updates/, via FileProvider teilbar)
// und verifiziert sha256 VOR der Rückgabe (T3). Wirft bei Hash-Mismatch.
class ApkDownloader(
    private val context: Context,
    private val client: OkHttpClient,
) {
    class IntegrityException(
        message: String,
    ) : IOException(message)

    suspend fun download(manifest: LatestManifest): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, "overlai-${manifest.versionName}.apk")

            val request = Request.Builder().url(manifest.apkUrl).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Download fehlgeschlagen: HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("Leerer Download")
                target.outputStream().use { out -> body.byteStream().copyTo(out) }
            }

            // Integrität VOR Installation prüfen.
            if (!Sha256Verifier.verify(target, manifest.sha256)) {
                target.delete()
                throw IntegrityException("sha256 stimmt nicht — Download verworfen")
            }
            target
        }
}
