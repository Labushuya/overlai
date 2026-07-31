package de.overlai.core.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

// CHANGE-MARKER v0.3.1: Lifecycle-Helfer (siehe CHANGELOG.md)
// Führt [onResume] bei jedem ON_RESUME des aktuellen LifecycleOwners aus. Genutzt,
// damit Screens nach Rückkehr aus den System-Einstellungen (z.B. Berechtigungen
// erteilt) ihren Status frisch laden — nicht nur beim ersten Betreten.
@Composable
fun OnResume(onResume: () -> Unit) {
    val currentOnResume by rememberUpdatedState(onResume)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) currentOnResume()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
