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

    // Phase 1: Owner am View-Baum registrieren, Lifecycle nur bis CREATED. VOR
    // windowManager.addView aufrufen. Bewusst NICHT direkt RESUMED: der Compose-
    // WindowRecomposer registriert seinen Observer erst beim Window-Attach und braucht
    // DANACH einen Lifecycle-Übergang (STARTED/RESUMED), um den Frame-Clock zu starten.
    // Setzt man RESUMED schon vor dem Attach, verpasst der Recomposer den Puls → keine
    // Erstkomposition → ComposeView misst 0×0 → Fenster bleibt frame 0x0 / alpha 0.
    fun attachTo(view: View) {
        savedStateController.performRestore(null)
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    // Phase 2: NACH windowManager.addView aufrufen. Fährt STARTED→RESUMED hoch, sodass
    // der beim Attach registrierte Recomposer die Übergänge sieht und zu komponieren beginnt.
    fun markResumed() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    // Lifecycle sauber herunterfahren, wenn das Overlay entfernt wird. Danach ist dieser
    // Owner verbraucht (LifecycleRegistry erlaubt kein Re-RESUME nach DESTROYED) — für ein
    // erneutes Anzeigen eine neue Instanz erzeugen.
    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
