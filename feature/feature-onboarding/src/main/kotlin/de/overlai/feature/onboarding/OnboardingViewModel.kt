package de.overlai.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.overlai.llm.ProviderConfig
import de.overlai.llm.providers.ProviderRegistry
import de.overlai.security.KeyVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// CHANGE-MARKER v0.1.0: Onboarding/BYOK (siehe CHANGELOG.md)
// Verwaltet die Provider-Auswahl + Key-Eingabe und schreibt den Key verschlüsselt
// in den KeyVault. Zeigt pro Provider, ob bereits ein Key hinterlegt ist.
data class OnboardingUiState(
    val providers: List<ProviderConfig> = ProviderRegistry.all,
    val selectedProviderId: String = ProviderRegistry.OPENAI.id,
    val apiKeyInput: String = "",
    val keyPresentFor: Set<String> = emptySet(),
    val savedMessage: String? = null,
)

class OnboardingViewModel(
    private val keyVault: KeyVault,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        refreshKeyPresence()
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
            _state.value = _state.value.copy(apiKeyInput = "", savedMessage = "Key gespeichert.")
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
