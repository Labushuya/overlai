package de.overlai.security

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// CHANGE-MARKER v0.1.0: BYOK-Key-Storage (siehe CHANGELOG.md)
// Verschlüsselt BYOK-API-Keys mit Tink-AEAD; der Keyset-Master-Key ist im
// Android Keystore verankert (StrongBox/TEE, wo verfügbar). Ciphertext liegt in
// DataStore (base64). Bewusst NICHT EncryptedSharedPreferences (deprecated).
//
// Verifiziert gegen die dokumentierte Tink-Android-Integration:
//  - AndroidKeysetManager hält das Keyset; MasterKey-URI zeigt in den Keystore.
//  - AEAD verschlüsselt/entschlüsselt mit dem providerId als "associated data",
//    sodass ein Ciphertext nicht auf einen anderen Provider umgemünzt werden kann.
private val Context.keyVaultStore by preferencesDataStore(name = "overlai_key_vault")

class TinkKeyVault(
    private val context: Context,
) : KeyVault {
    private val aead: Aead by lazy { buildAead(context) }

    override suspend fun putKey(
        providerId: String,
        apiKey: String,
    ) {
        val ciphertext = aead.encrypt(apiKey.toByteArray(Charsets.UTF_8), providerId.toByteArray())
        val encoded = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        context.keyVaultStore.edit { it[keyFor(providerId)] = encoded }
    }

    override suspend fun getKey(providerId: String): String? {
        val encoded =
            context.keyVaultStore.data
                .map { it[keyFor(providerId)] }
                .first() ?: return null
        return runCatching {
            val ciphertext = Base64.decode(encoded, Base64.NO_WRAP)
            String(aead.decrypt(ciphertext, providerId.toByteArray()), Charsets.UTF_8)
        }.getOrNull()
    }

    override suspend fun hasKey(providerId: String): Boolean =
        context.keyVaultStore.data
            .map { it.contains(keyFor(providerId)) }
            .first()

    override suspend fun removeKey(providerId: String) {
        context.keyVaultStore.edit { it.remove(keyFor(providerId)) }
    }

    override suspend fun clear() {
        context.keyVaultStore.edit { it.clear() }
    }

    private fun keyFor(providerId: String) = stringPreferencesKey("key_$providerId")

    private companion object {
        const val MASTER_KEY_URI = "android-keystore://overlai_key_vault_master"
        const val KEYSET_PREF_FILE = "overlai_tink_keyset"
        const val KEYSET_PREF_NAME = "keyset"

        fun buildAead(context: Context): Aead {
            AeadConfig.register()
            val keysetHandle =
                AndroidKeysetManager
                    .Builder()
                    .withSharedPref(context, KEYSET_PREF_NAME, KEYSET_PREF_FILE)
                    .withKeyTemplate(com.google.crypto.tink.KeyTemplates.get("AES256_GCM"))
                    .withMasterKeyUri(MASTER_KEY_URI)
                    .build()
                    .keysetHandle
            return keysetHandle.getPrimitive(Aead::class.java)
        }
    }
}
