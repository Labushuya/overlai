package de.overlai.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.overlai.core.data.SettingsStore
import de.overlai.llm.HttpModelCatalog
import de.overlai.llm.LlmError
import de.overlai.llm.ModelInfo
import de.overlai.llm.ProviderConfig
import de.overlai.llm.providers.ProviderRegistry
import de.overlai.security.KeyVault
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// CHANGE-MARKER v0.4.7: Provider-Hub-UI (siehe CHANGELOG.md)
// Ein Screen für alles: pro Provider eine aufklappbare Karte mit Key-Verwaltung +
// live geladenem Modell-Katalog. Ersetzt OnboardingScreen + ModelCatalogScreen.
// Modelle werden LAZY beim Aufklappen geladen. Ein Modell-Tap setzt Modell UND
// Provider app-weit aktiv. Kein @HiltViewModel (core-* frei von DI); via simpleFactory.
@OptIn(ExperimentalCoroutinesApi::class)
class ProviderHubViewModel(
    private val keyVault: KeyVault,
    private val settingsStore: SettingsStore,
    private val catalog: HttpModelCatalog,
) : ViewModel() {
    // Ladezustand der Modellliste EINES Providers.
    sealed interface ModelListState {
        data object Idle : ModelListState

        data object Loading : ModelListState

        data class Loaded(
            val models: List<ModelInfo>,
        ) : ModelListState

        data object Empty : ModelListState

        sealed interface Error : ModelListState {
            data object Unauthorized : Error

            data object NoEndpoint : Error

            data object RateLimited : Error

            data class Network(
                val message: String,
            ) : Error
        }
    }

    data class UiState(
        val providers: List<ProviderConfig> = ProviderRegistry.all,
        val activeProviderId: String = ProviderRegistry.OPENAI.id,
        // Aktives Modell je Provider (null = Provider-Default).
        val activeModelByProvider: Map<String, String?> = emptyMap(),
        val keyPresentFor: Set<String> = emptySet(),
        // NUR die letzten 4 Zeichen des Keys — nie der ganze Key im State.
        val keyLast4: Map<String, String> = emptyMap(),
        val expandedProviderId: String? = null,
        val models: Map<String, ModelListState> = emptyMap(),
        // Welche Karte zeigt gerade die Klartext-Key-Eingabe (Neu/Ändern)?
        val editingKeyFor: String? = null,
        val keyInput: String = "",
        val freeOnly: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refreshKeyState()
        // Aktiver Provider REAKTIV beobachten; für jeden Wechsel dessen aktives
        // Modell nachziehen (flatMapLatest bricht die alte Modell-Beobachtung ab).
        viewModelScope.launch {
            settingsStore.activeProviderId
                .flatMapLatest { active ->
                    settingsStore.activeModelId(active).map { model -> active to model }
                }.collect { (id, model) ->
                    _state.value =
                        _state.value.copy(
                            activeProviderId = id,
                            activeModelByProvider = _state.value.activeModelByProvider + (id to model),
                        )
                }
        }
    }

    private fun refreshKeyState() {
        viewModelScope.launch {
            val present = mutableSetOf<String>()
            val last4 = mutableMapOf<String, String>()
            _state.value.providers.forEach { p ->
                val key = keyVault.getKey(p.id)
                if (!key.isNullOrBlank()) {
                    present += p.id
                    last4[p.id] = key.takeLast(KEY_TAIL)
                }
            }
            _state.value = _state.value.copy(keyPresentFor = present, keyLast4 = last4)
        }
    }

    fun onToggleExpand(providerId: String) {
        val nowOpen = _state.value.expandedProviderId != providerId
        _state.value =
            _state.value.copy(
                expandedProviderId = if (nowOpen) providerId else null,
                editingKeyFor = null,
                keyInput = "",
            )
        // Lazy: beim Öffnen laden, falls Key da und noch nicht geladen.
        if (nowOpen && providerId in _state.value.keyPresentFor) {
            val current = _state.value.models[providerId]
            if (current == null || current is ModelListState.Idle) {
                loadModels(providerId)
            }
        }
    }

    fun loadModels(providerId: String) {
        val config = ProviderRegistry.byId(providerId) ?: return
        setModelState(providerId, ModelListState.Loading)
        viewModelScope.launch {
            val key = keyVault.getKey(providerId)
            if (key.isNullOrBlank()) {
                setModelState(providerId, ModelListState.Error.Unauthorized)
                return@launch
            }
            val result = runCatching { catalog.listOrThrow(config, key) }
            result.fold(
                onSuccess = { models ->
                    setModelState(
                        providerId,
                        if (models.isEmpty()) ModelListState.Empty else ModelListState.Loaded(models),
                    )
                },
                onFailure = { e -> setModelState(providerId, mapError(e)) },
            )
        }
    }

    fun onKeyInput(text: String) {
        _state.value = _state.value.copy(keyInput = text)
    }

    // Klartext-Eingabe öffnen (Neu oder Ändern).
    fun onStartKeyEntry(providerId: String) {
        _state.value = _state.value.copy(editingKeyFor = providerId, keyInput = "")
    }

    fun onCancelKeyEntry() {
        _state.value = _state.value.copy(editingKeyFor = null, keyInput = "")
    }

    fun onSaveKey(providerId: String) {
        val key = _state.value.keyInput.trim()
        if (key.isEmpty()) return
        viewModelScope.launch {
            keyVault.putKey(providerId, key)
            // Key setzen macht den Provider app-weit aktiv (der Nutzer richtet ihn ein).
            settingsStore.setActiveProvider(providerId)
            _state.value = _state.value.copy(editingKeyFor = null, keyInput = "")
            refreshKeyState()
            loadModels(providerId)
        }
    }

    fun onDeleteKey(providerId: String) {
        viewModelScope.launch {
            keyVault.removeKey(providerId)
            _state.value =
                _state.value.copy(
                    models = _state.value.models - providerId,
                    editingKeyFor = null,
                    keyInput = "",
                )
            refreshKeyState()
        }
    }

    // Modell-Tap: Modell aktiv setzen UND diesen Provider app-weit aktiv.
    fun onSelectModel(
        providerId: String,
        modelId: String,
    ) {
        viewModelScope.launch {
            settingsStore.setActiveModel(providerId, modelId)
            settingsStore.setActiveProvider(providerId)
            _state.value =
                _state.value.copy(
                    activeProviderId = providerId,
                    activeModelByProvider = _state.value.activeModelByProvider + (providerId to modelId),
                )
        }
    }

    fun onToggleFreeOnly() {
        _state.value = _state.value.copy(freeOnly = !_state.value.freeOnly)
    }

    private fun setModelState(
        providerId: String,
        s: ModelListState,
    ) {
        _state.value = _state.value.copy(models = _state.value.models + (providerId to s))
    }

    private fun mapError(e: Throwable): ModelListState.Error =
        when (e) {
            is LlmError.Unauthorized -> ModelListState.Error.Unauthorized
            is LlmError.RateLimited, is LlmError.InsufficientQuota -> ModelListState.Error.RateLimited
            is LlmError.Api ->
                if (e.status == HTTP_NOT_FOUND || e.status == HTTP_METHOD_NA) {
                    ModelListState.Error.NoEndpoint
                } else {
                    ModelListState.Error.Network(e.message ?: "Fehler")
                }
            else -> ModelListState.Error.Network(e.message ?: "Netzwerkfehler")
        }

    private companion object {
        const val KEY_TAIL = 4
        const val HTTP_NOT_FOUND = 404
        const val HTTP_METHOD_NA = 405
    }
}
