package de.overlai.feature.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.overlai.core.ui.theme.OverlAiTheme

// CHANGE-MARKER v0.5.2: Overlay-Bubble (M3, siehe CHANGELOG.md)
// Die Bubble selbst — ein kleiner runder Anker über anderen Apps. Das Tap-Handling
// (Tap vs. Drag) sitzt im OverlayWindowController auf dem umschließenden View, NICHT
// hier: Compose-Klicks würden mit dem WindowManager-Drag konkurrieren.
//
// In OverlAiTheme gewickelt, damit das Overlay ohne Activity-Theme korrekt Material-3
// (inkl. dynamic color) rendert. Default-Prefs sind bewusst: das Overlay hat im
// Skelett keinen SettingsStore-Zugriff; System-Theme ist der richtige Default.
private val BubbleSize = 56.dp

@Composable
internal fun OverlayBubble() {
    OverlAiTheme {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 6.dp,
            modifier = Modifier.size(BubbleSize),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "OverlAI",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(14.dp).fillMaxSize(),
                )
            }
        }
    }
}
