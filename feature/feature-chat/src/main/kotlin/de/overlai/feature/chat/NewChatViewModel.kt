package de.overlai.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.overlai.core.data.SettingsStore
import de.overlai.core.data.chat.SessionRepository
import de.overlai.llm.HttpModelCatalog
import de.overlai.llm.ModelInfo
import de.overlai.llm.ProviderConfig
import de.overlai.llm.providers.ProviderRegistry
import de.overlai.security.KeyVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

// CHANGE-MARKER: Chat-Organisation & Modell-UX (Phase 3, siehe CHANGELOG.md)
// Geführter "Neuer Chat"-Flow: erst Anbieter, dann (lazy geladenes) Modell. Wiederverwendet
// die Katalog-Lade-Logik aus dem ProviderHub, ändert aber NICHT den globalen Default —
// legt direkt eine Session mit dem gewählten Provider/Modell an. Der Schnellstart-Weg
// (globaler aktiver Provider/Modell, ein Tap) bleibt über [quickStart] erhalten.
class NewChatViewModel(
    private val repo: SessionRepository,
    private val settingsStore: SettingsStore,
    private val keyVault: KeyVault,
    private val catalog: HttpModelCatalog,
) : ViewModel() {
    // Ladezustand der Modellliste eines Providers (analog ProviderHubViewModel, reduziert).
    sealed interface ModelsState {
        data object Idle : ModelsState

        data object Loading : ModelsState

        data class Loaded(
            val models: List<ModelInfo>,
        ) : ModelsState

        data object Empty : ModelsState

        data class Error(
            val message: String,
        ) : ModelsState
    }

    data class UiState(
        val providers: List<ProviderConfig> = ProviderRegistry.all,
        val keyPresentFor: Set<String> = emptySet(),
        val selectedProviderId: String? = null,
        val models: ModelsState = ModelsState.Idle,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val present = ProviderRegistry.all.mapNotNull { p -> p.id.takeIf { keyVault.hasKey(p.id) } }.toSet()
            _state.value = _state.value.copy(keyPresentFor = present)
        }
    }

    // Anbieter wählen → dessen Modelle lazy laden.
    fun selectProvider(providerId: String) {
        _state.value = _state.value.copy(selectedProviderId = providerId, models = ModelsState.Loading)
        val config = ProviderRegistry.byId(providerId) ?: return
        viewModelScope.launch {
            val key = keyVault.getKey(providerId)
            if (key.isNullOrBlank()) {
                _state.value = _state.value.copy(models = ModelsState.Error("Kein API-Key für diesen Anbieter."))
                return@launch
            }
            val result = runCatching { catalog.listOrThrow(config, key) }
            _state.value =
                _state.value.copy(
                    models =
                        result.fold(
                            onSuccess = { m -> if (m.isEmpty()) ModelsState.Empty else ModelsState.Loaded(m) },
                            onFailure = {
                                    e ->
                                ModelsState.Error(e.message ?: "Modelle konnten nicht geladen werden.")
                            },
                        ),
                )
        }
    }

    // Zurück zur Anbieter-Auswahl.
    fun clearProvider() {
        _state.value = _state.value.copy(selectedProviderId = null, models = ModelsState.Idle)
    }

    // Session mit explizit gewähltem Provider+Modell anlegen.
    fun create(
        providerId: String,
        modelId: String?,
        onCreated: (String) -> Unit,
    ) {
        viewModelScope.launch {
            val id = createSession(providerId, modelId)
            onCreated(id)
        }
    }

    // Schnellstart: globaler aktiver Provider/Modell (bisheriger Ein-Tap-Weg).
    fun quickStart(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val providerId = settingsStore.activeProviderId.first()
            val modelId = settingsStore.activeModelId(providerId).first()
            val id = createSession(providerId, modelId)
            onCreated(id)
        }
    }

    private suspend fun createSession(
        providerId: String,
        modelId: String?,
    ): String {
        val id = UUID.randomUUID().toString()
        repo.createSession(id, "Neuer Chat", providerId, modelId, System.currentTimeMillis())
        settingsStore.setActiveSession(id)
        return id
    }
}
