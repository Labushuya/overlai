package de.overlai.feature.overlay

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.overlai.core.ui.theme.OverlAiTheme

// CHANGE-MARKER: Bubble-Snapping + Papierkorb (P2.2, siehe CHANGELOG.md)
// Papierkorb-Schließzone, die während eines Bubble-Drags mittig-unten erscheint. Zieht
// man die Bubble darüber (highlighted=true), hebt sich die Zone hervor (Hover, größer/
// kräftiger) — Standard-Android-/HONOR-Verhalten. Loslassen über der Zone beendet das
// Overlay (Entscheidung im OverlayWindowController per Hit-Test).
internal val TrashZoneSize = 64.dp

@Composable
internal fun OverlayTrashZone(highlighted: Boolean) {
    OverlAiTheme {
        // Bei Hover: Zone wächst leicht + Farben kräftiger (visuelles Feedback).
        val boxSize by animateDpAsState(if (highlighted) TrashZoneSize + 8.dp else TrashZoneSize, label = "trashSize")
        val bg =
            if (highlighted) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
        val tint =
            if (highlighted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(TrashZoneSize + 16.dp)) {
            Surface(
                shape = CircleShape,
                color = bg.copy(alpha = 0.92f),
                shadowElevation = 8.dp,
                modifier = Modifier.size(boxSize),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Bubble schließen",
                        tint = tint,
                        modifier = Modifier.padding(14.dp).fillMaxSize(),
                    )
                }
            }
        }
    }
}
