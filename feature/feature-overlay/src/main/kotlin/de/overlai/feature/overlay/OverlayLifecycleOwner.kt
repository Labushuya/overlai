package de.overlai.feature.overlay

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

// CHANGE-MARKER v0.5.2: Overlay-Bubble (M3, siehe CHANGELOG.md)
// Ein ComposeView lebt normalerweise in einer Activity und erbt deren Lifecycle-,
// ViewModelStore- und SavedStateRegistry-Owner über den ViewTree. In einem
// WindowManager-Overlay gibt es keine Activity — ohne diese Owner wirft Compose beim
// Anhängen eine IllegalStateException ("ViewTreeLifecycleOwner not found").
//
// Diese Klasse stellt einen minimalen, eigenständigen Owner-Satz bereit und heftet
// ihn an das Root-View. Der Lifecycle wird manuell gesteuert: RESUMED sobald das View
// im Fenster hängt, DESTROYED beim Entfernen (räumt auch den ViewModelStore ab).
internal class OverlayLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    // Owner am View-Baum registrieren + Lifecycle bis RESUMED hochfahren. Einmal pro
    // angezeigtem Overlay-Root aufrufen, bevor das View dem WindowManager übergeben wird.
    fun attachTo(view: View) {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }

    // Lifecycle sauber herunterfahren, wenn das Overlay entfernt wird. Danach ist dieser
    // Owner verbraucht (LifecycleRegistry erlaubt kein Re-RESUME nach DESTROYED) — für ein
    // erneutes Anzeigen eine neue Instanz erzeugen.
    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
