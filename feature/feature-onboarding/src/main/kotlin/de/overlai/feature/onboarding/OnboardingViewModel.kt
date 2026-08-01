package de.overlai.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.overlai.core.data.SettingsStore
import de.overlai.llm.ProviderConfig
import de.overlai.llm.providers.ProviderRegistry
import de.overlai.security.KeyVault
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// CHANGE-MARKER v0.1.0: Onboarding/BYOK (siehe CHANGELOG.md)
// Verwaltet die Provider-Auswahl + Key-Eingabe und schreibt den Key verschlüsselt
// in den KeyVault. Zeigt pro Provider, ob bereits ein Key hinterlegt ist.
data class OnboardingUiState(
    val providers: List<ProviderConfig> = ProviderRegistry.all,
    val selectedProviderId: String = ProviderRegistry.OPENAI.id,
    // App-weit aktiver Provider (aus dem Store) — kann sich von der lokalen
    // Auswahl unterscheiden, solange kein Key gespeichert wurde.
    val activeProviderId: String = ProviderRegistry.OPENAI.id,
    val apiKeyInput: String = "",
    val keyPresentFor: Set<String> = emptySet(),
    // Aktuell gewähltes Modell des selektierten Providers (null = Provider-Default).
    val activeModelId: String? = null,
    val savedMessage: String? = null,
) {
    val selectedProvider: ProviderConfig
        get() = providers.firstOrNull { it.id == selectedProviderId } ?: ProviderRegistry.OPENAI

    // Woher bekommt man den Key? Hinweis-URL je Provider (in der UI angezeigt).
    val selectedKeyHint: String
        get() =
            when (selectedProviderId) {
                "openai" -> "platform.openai.com/api-keys"
                "anthropic" -> "console.anthropic.com/settings/keys"
                "grok" -> "console.x.ai"
                "deepseek" -> "platform.deepseek.com/api_keys"
                "kimi" -> "platform.moonshot.ai"
                "openrouter" -> "openrouter.ai/keys"
                else -> ""
            }
}

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModel(
    private val keyVault: KeyVault,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        // Startauswahl = app-weit aktiver Provider (nicht hart OpenAI), damit der
        // Screen den echten Zustand spiegelt.
        viewModelScope.launch {
            val active = settingsStore.activeProviderId.first()
            _state.value = _state.value.copy(selectedProviderId = active, activeProviderId = active)
            refreshKeyPresence()
        }
        // Aktives Modell REAKTIV am selektierten Provider beobachten — sonst zeigt
        // der Screen nach Rückkehr aus dem Modell-Katalog einen veralteten Wert
        // (einmaliges first() nach Navigation ist die Falle).
        viewModelScope.launch {
            _state
                .map { it.selectedProviderId }
                .distinctUntilChanged()
                .flatMapLatest { settingsStore.activeModelId(it) }
                .collect { model -> _state.value = _state.value.copy(activeModelId = model) }
        }
    }

    fun onSelectProvider(providerId: String) {
        _state.value = _state.value.copy(selectedProviderId = providerId, apiKeyInput = "", savedMessage = null)
    }

    fun onKeyInputChange(text: String) {
        _state.value = _state.value.copy(apiKeyInput = text)
    }

    fun onSaveKey() {
        val id = _state.value.selectedProviderId
        val key = _state.value.apiKeyInput.trim()
        if (key.isEmpty()) return
        viewModelScope.launch {
            keyVault.putKey(id, key)
            // Den soeben eingerichteten Provider auch app-weit aktiv setzen,
            // damit der Chat ihn direkt nutzt.
            settingsStore.setActiveProvider(id)
            _state.value =
                _state.value.copy(
                    apiKeyInput = "",
                    activeProviderId = id,
                    savedMessage = "Key gespeichert & als aktiv gesetzt.",
                )
            refreshKeyPresence()
        }
    }

    fun onRemoveKey(providerId: String) {
        viewModelScope.launch {
            keyVault.removeKey(providerId)
            refreshKeyPresence()
        }
    }

    private fun refreshKeyPresence() {
        viewModelScope.launch {
            val present = _state.value.providers.filter { keyVault.hasKey(it.id) }.map { it.id }.toSet()
            _state.value = _state.value.copy(keyPresentFor = present)
        }
    }
}
