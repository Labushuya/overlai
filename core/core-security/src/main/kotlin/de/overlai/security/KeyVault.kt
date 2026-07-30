package de.overlai.security

// CHANGE-MARKER v0.1.0: BYOK-Key-Storage (siehe CHANGELOG.md)
// Abstraktion für die verschlüsselte Ablage der BYOK-API-Keys. Keys verlassen
// das Gerät nie; die konkrete Implementierung (TinkKeyVault) verankert den
// Master-Key im Android Keystore/TEE. Interface bewusst schlank -> testbar.
interface KeyVault {
    // Speichert/überschreibt den API-Key für einen Provider (z.B. "openai").
    suspend fun putKey(
        providerId: String,
        apiKey: String,
    )

    // Liest den API-Key oder null, wenn keiner hinterlegt ist.
    suspend fun getKey(providerId: String): String?

    // True, wenn für den Provider ein Key hinterlegt ist (für Permission-Hub-Check).
    suspend fun hasKey(providerId: String): Boolean

    // Entfernt den Key eines Providers.
    suspend fun removeKey(providerId: String)

    // Löscht ALLE Keys (z.B. "Alle Keys entfernen" im Datenschutz-Menü).
    suspend fun clear()
}
