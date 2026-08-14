package de.overlai.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.overlai.common.ThemeMode
import de.overlai.common.ThemePreferences
import de.overlai.llm.providers.ProviderRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

// CHANGE-MARKER v0.2.1: App-weite Einstellungen (siehe CHANGELOG.md)
// Persistiert App-weite Einstellungen in DataStore: aktiver Provider, Theme-
// Präferenzen und ein First-Run-Flag (ob das Setup schon einmal gezeigt wurde).
private val Context.settingsStore by preferencesDataStore(name = "overlai_settings")

class SettingsStore(
    private val context: Context,
) {
    private val activeProviderKey = stringPreferencesKey("active_provider_id")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val dynamicColorKey = booleanPreferencesKey("use_dynamic_color")
    private val onboardingShownKey = booleanPreferencesKey("onboarding_shown")
    private val overlayEnabledKey = booleanPreferencesKey("overlay_enabled")
    private val notificationEnabledKey = booleanPreferencesKey("notification_enabled")
    private val activeSessionKey = stringPreferencesKey("active_session_id")

    // Aktiver Provider (Default: OpenAI). Fällt auf OpenAI zurück, falls die
    // gespeicherte ID nicht mehr in der Registry existiert.
    val activeProviderId: Flow<String> =
        context.settingsStore.data
            .map { prefs ->
                val stored = prefs[activeProviderKey]
                if (stored != null && ProviderRegistry.byId(stored) != null) {
                    stored
                } else {
                    ProviderRegistry.OPENAI.id
                }
            }.distinctUntilChanged()

    suspend fun setActiveProvider(providerId: String) {
        context.settingsStore.edit { it[activeProviderKey] = providerId }
    }

    // Pro-Provider gewähltes Modell (ein Key je Provider — Preferences hat keinen
    // Map-Typ; distinctUntilChanged bleibt scoped). Null = noch keine Wahl -> die
    // Engine nimmt config.defaultModel.
    private fun activeModelKey(providerId: String) = stringPreferencesKey("active_model_id.$providerId")

    fun activeModelId(providerId: String): Flow<String?> =
        context.settingsStore.data
            .map { it[activeModelKey(providerId)] }
            .distinctUntilChanged()

    suspend fun setActiveModel(
        providerId: String,
        modelId: String,
    ) {
        context.settingsStore.edit { it[activeModelKey(providerId)] = modelId }
    }

    // Theme-Präferenzen. distinctUntilChanged ist load-bearing: data{} emittiert
    // bei JEDEM Write (auch active_provider_id), sonst rekomponiert das Theme grundlos.
    val themePreferences: Flow<ThemePreferences> =
        context.settingsStore.data
            .map { prefs ->
                ThemePreferences(
                    mode =
                        prefs[themeModeKey]
                            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                            ?: ThemeMode.SYSTEM,
                    // Default false (P2.1c): Marken-Palette greift sofort statt Systemfarbe.
                    useDynamicColor = prefs[dynamicColorKey] ?: false,
                )
            }.distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.settingsStore.edit { it[dynamicColorKey] = enabled }
    }

    // First-Run: wurde das Setup schon einmal geöffnet? (Guard gegen wiederholtes
    // Auto-Routing ins Onboarding.)
    val onboardingShown: Flow<Boolean> =
        context.settingsStore.data
            .map { it[onboardingShownKey] ?: false }
            .distinctUntilChanged()

    suspend fun markOnboardingShown() {
        context.settingsStore.edit { it[onboardingShownKey] = true }
    }

    // Overlay-Bubble: gewünschter Ein/Aus-Zustand (persistierter Nutzerwunsch). Der
    // Service wird NICHT automatisch am App-Start gestartet — der Toggle steuert ihn
    // explizit; dieses Flag hält nur den zuletzt gewählten Zustand für die UI.
    val overlayEnabled: Flow<Boolean> =
        context.settingsStore.data
            .map { it[overlayEnabledKey] ?: false }
            .distinctUntilChanged()

    suspend fun setOverlayEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[overlayEnabledKey] = enabled }
    }

    // P2.4: Benachrichtigungs-Zugang (persistente Notification mit Quick-Reply). Default aus.
    val notificationEnabled: Flow<Boolean> =
        context.settingsStore.data
            .map { it[notificationEnabledKey] ?: false }
            .distinctUntilChanged()

    suspend fun setNotificationEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[notificationEnabledKey] = enabled }
    }

    // Multi-Chat (P2.1b): zuletzt geöffnete Session. Überlebt Neustart; Overlay und
    // Fullscreen-Chat nutzen dieselbe aktive Session, damit beide denselben Verlauf zeigen.
    val activeSessionId: Flow<String?> =
        context.settingsStore.data
            .map { it[activeSessionKey] }
            .distinctUntilChanged()

    suspend fun setActiveSession(id: String) {
        context.settingsStore.edit { it[activeSessionKey] = id }
    }

    // P2.1c: aktive Auswahl leeren (z.B. wenn die aktive Session gelöscht wird). Das Overlay
    // zeigt danach einen leeren Panel-State bzw. legt bei Bedarf eine neue Session an.
    suspend fun clearActiveSession() {
        context.settingsStore.edit { it.remove(activeSessionKey) }
    }
}
