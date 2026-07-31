package de.overlai.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.overlai.core.data.SettingsStore
import de.overlai.llm.HttpModelCatalog
import de.overlai.llm.LlmError
import de.overlai.llm.ModelInfo
import de.overlai.llm.providers.ProviderRegistry
import de.overlai.security.KeyVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// CHANGE-MARKER v0.4.0: Modell-Katalog-UI (siehe CHANGELOG.md)
// Lädt den Modell-Katalog eines Providers live mit dem hinterlegten Key, erlaubt
// Suche + (nur OpenRouter) "Nur kostenlose"-Filter und persistiert die Auswahl.
class ModelCatalogViewModel(
    private val providerId: String,
    private val catalog: HttpModelCatalog,
    private val keyVault: KeyVault,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    sealed interface Phase {
        data object Loading : Phase

        data object Loaded : Phase

        data object Empty : Phase

        sealed interface Error : Phase {
            data object NoKey : Error

            data object Unauthorized : Error

            data object NoEndpoint : Error

            data object RateLimited : Error

            data class Network(
                val message: String,
            ) : Error
        }
    }

    data class UiState(
        val providerName: String = "",
        val query: String = "",
        val freeOnly: Boolean = false,
        val showFreeFilter: Boolean = false,
        val selectedModelId: String? = null,
        val all: List<ModelInfo> = emptyList(),
        val phase: Phase = Phase.Loading,
    ) {
        val visible: List<ModelInfo>
            get() =
                all.filter { m ->
                    (!freeOnly || m.free) &&
                        (query.isBlank() || m.displayName.contains(query, true) || m.id.contains(query, true))
                }
    }

    private val _state =
        MutableStateFlow(
            UiState(
                providerName = ProviderRegistry.byId(providerId)?.displayName ?: providerId,
                showFreeFilter = providerId == "openrouter",
            ),
        )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = _state.value.copy(phase = Phase.Loading)
        viewModelScope.launch {
            val config = ProviderRegistry.byId(providerId)
            if (config == null) {
                _state.value = _state.value.copy(phase = Phase.Error.Network("Unbekannter Provider"))
                return@launch
            }
            val key = keyVault.getKey(providerId)
            if (key.isNullOrBlank()) {
                _state.value = _state.value.copy(phase = Phase.Error.NoKey)
                return@launch
            }
            val selected = settingsStore.activeModelId(providerId).first()
            val result = runCatching { catalog.listOrThrow(config, key) }
            result.fold(
                onSuccess = { models ->
                    _state.value =
                        _state.value.copy(
                            all = models,
                            selectedModelId = selected,
                            phase = if (models.isEmpty()) Phase.Empty else Phase.Loaded,
                        )
                },
                onFailure = { e -> _state.value = _state.value.copy(phase = mapError(e)) },
            )
        }
    }

    fun onSearch(q: String) {
        _state.value = _state.value.copy(query = q)
    }

    fun onToggleFreeOnly() {
        _state.value = _state.value.copy(freeOnly = !_state.value.freeOnly)
    }

    fun onSelect(modelId: String) {
        viewModelScope.launch {
            settingsStore.setActiveModel(providerId, modelId)
            _state.value = _state.value.copy(selectedModelId = modelId)
        }
    }

    private fun mapError(e: Throwable): Phase.Error =
        when (e) {
            is LlmError.Unauthorized -> Phase.Error.Unauthorized
            is LlmError.RateLimited, is LlmError.InsufficientQuota -> Phase.Error.RateLimited
            is LlmError.Api ->
                if (e.status == HTTP_NOT_FOUND || e.status == HTTP_METHOD_NA) {
                    Phase.Error.NoEndpoint
                } else {
                    Phase.Error.Network(e.message ?: "Fehler")
                }
            else -> Phase.Error.Network(e.message ?: "Netzwerkfehler")
        }

    private companion object {
        const val HTTP_NOT_FOUND = 404
        const val HTTP_METHOD_NA = 405
    }
}
