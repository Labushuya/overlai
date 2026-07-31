package de.overlai.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.overlai.common.ThemeMode
import de.overlai.common.ThemePreferences
import de.overlai.core.data.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// CHANGE-MARKER v0.2.1: Darstellung/Theme (siehe CHANGELOG.md)
// Liest/schreibt die Theme-Präferenzen. Kein @HiltViewModel — im :app via
// simpleFactory gebaut (Projekt-Konvention).
class AppearanceViewModel(
    private val settingsStore: SettingsStore,
) : ViewModel() {
    val prefs: StateFlow<ThemePreferences> =
        settingsStore.themePreferences.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ThemePreferences(),
        )

    fun setMode(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setUseDynamicColor(enabled) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
