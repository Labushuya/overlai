package de.overlai.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.overlai.llm.providers.ProviderRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// CHANGE-MARKER v0.1.0: App-weite Einstellungen (siehe CHANGELOG.md)
// Persistiert die App-weite Auswahl (aktiver Provider) in DataStore. Chat und
// Quick-Actions lesen hier den aktiven Provider statt fix OpenAI zu nehmen.
private val Context.settingsStore by preferencesDataStore(name = "overlai_settings")

class SettingsStore(
    private val context: Context,
) {
    private val activeProviderKey = stringPreferencesKey("active_provider_id")

    // Aktiver Provider (Default: OpenAI). Fällt auf OpenAI zurück, falls die
    // gespeicherte ID nicht mehr in der Registry existiert.
    val activeProviderId: Flow<String> =
        context.settingsStore.data.map { prefs ->
            val stored = prefs[activeProviderKey]
            if (stored != null && ProviderRegistry.byId(stored) != null) {
                stored
            } else {
                ProviderRegistry.OPENAI.id
            }
        }

    suspend fun setActiveProvider(providerId: String) {
        context.settingsStore.edit { it[activeProviderKey] = providerId }
    }
}
