package de.overlai.feature.updater

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// CHANGE-MARKER v0.2.1: In-App-Updater-UI (siehe CHANGELOG.md)
// State-Machine für den Updater. Wickelt UpdateChecker/ApkDownloader/
// PackageInstallerSession. Download ist indeterminate (ApkDownloader hat keinen
// Progress-Callback) — bewusst so, kein Fake-Fortschritt. Kein @HiltViewModel;
// im :app via simpleFactory gebaut.
class UpdateViewModel(
    private val currentVersion: String,
    private val checker: UpdateChecker,
    private val downloader: ApkDownloader,
    private val installer: PackageInstallerSession,
) : ViewModel() {
    sealed interface UiState {
        data class Idle(
            val current: String,
        ) : UiState

        data object Checking : UiState

        data class UpToDate(
            val current: String,
        ) : UiState

        data class Available(
            val manifest: LatestManifest,
        ) : UiState

        data object Downloading : UiState

        data class Ready(
            val apk: File,
            val versionName: String,
        ) : UiState

        data class Error(
            val message: String,
        ) : UiState

        // Installation nicht möglich, weil "Unbekannte Apps installieren" fehlt.
        data class NeedsInstallPermission(
            val apk: File,
        ) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle(currentVersion))
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun check() {
        _state.value = UiState.Checking
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { checker.check(currentVersion) }
            _state.value =
                when (result) {
                    is UpdateChecker.Result.UpdateAvailable -> UiState.Available(result.manifest)
                    UpdateChecker.Result.UpToDate -> UiState.UpToDate(currentVersion)
                    is UpdateChecker.Result.Error -> UiState.Error(result.message)
                }
        }
    }

    fun download(manifest: LatestManifest) {
        _state.value = UiState.Downloading
        viewModelScope.launch {
            val result = runCatching { downloader.download(manifest) }
            _state.value =
                result.fold(
                    onSuccess = { UiState.Ready(it, manifest.versionName) },
                    onFailure = { UiState.Error(it.message ?: "Download fehlgeschlagen") },
                )
        }
    }

    fun install(apk: File) {
        viewModelScope.launch {
            val started = withContext(Dispatchers.IO) { installer.install(apk) }
            if (!started) {
                _state.value = UiState.NeedsInstallPermission(apk)
            }
            // Bei Erfolg übernimmt der System-Install-Dialog; kein weiterer UI-State.
        }
    }
}
