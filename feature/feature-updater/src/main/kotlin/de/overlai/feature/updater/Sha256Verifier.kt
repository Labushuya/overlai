package de.overlai.feature.updater

import java.io.File
import java.security.MessageDigest

// CHANGE-MARKER v0.1.0: In-App-Updater (siehe CHANGELOG.md)
// Verifiziert die heruntergeladene APK gegen den sha256 aus latest.json — VOR
// der Installation (T3: manipuliertes Manifest/APK). Groß-/Kleinschreibung egal.
object Sha256Verifier {
    private const val BUFFER = 8192

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(BUFFER)
            var read = stream.read(buffer)
            while (read >= 0) {
                digest.update(buffer, 0, read)
                read = stream.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verify(
        file: File,
        expectedSha256: String,
    ): Boolean = sha256(file).equals(expectedSha256.trim(), ignoreCase = true)
}
