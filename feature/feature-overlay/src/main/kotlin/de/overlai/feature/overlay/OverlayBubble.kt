package de.overlai.feature.overlay

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import de.overlai.core.ui.components.AppLogo
import de.overlai.core.ui.theme.OverlAiTheme

// CHANGE-MARKER v0.5.2: Overlay-Bubble (M3, siehe CHANGELOG.md)
// Die Bubble selbst — ein kleiner runder Anker über anderen Apps.
//
// Touch-Handling läuft über Compose (Modifier.pointerInput), NICHT über einen
// View.OnTouchListener am Fenster-Root: ein ComposeView konsumiert ACTION_DOWN in seiner
// eigenen dispatchTouchEvent (AndroidComposeView gibt true zurück, sobald der Hit-Test
// komponierten Content trifft) — ein OnTouchListener am umschließenden View feuert dann
// per Framework-Kontrakt NIE. Also erkennt Compose selbst Drag + Tap und meldet sie per
// Callback an den Controller, der WindowManager.updateViewLayout bzw. das Panel steuert.
//
// In OverlAiTheme gewickelt, damit das Overlay ohne Activity-Theme korrekt Material-3
// (inkl. dynamic color) rendert.
private val BubbleSize = 56.dp

@Composable
internal fun OverlayBubble(
    onDragStart: () -> Unit,
    onDrag: (dx: Int, dy: Int) -> Unit,
    onDragEnd: () -> Unit,
    onTap: () -> Unit,
) {
    OverlAiTheme {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 6.dp,
            modifier =
                Modifier
                    .size(BubbleSize)
                    // Tap: öffnet/schließt das Panel.
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onTap() })
                    }
                    // Drag: verschiebt das Overlay-Fenster (delta-basiert; dragAmount ist
                    // inkrementell). onDragStart zeigt die Papierkorb-Zone, onDragEnd
                    // entscheidet (über Papierkorb → beenden, sonst → an den Rand snappen).
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.x.toInt(), dragAmount.y.toInt())
                            },
                        )
                    },
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                // D4-Marken-Logo statt generischem Chat-Icon (P2.5). Auf der primary-Bubble:
                // Ring/Funke in onPrimary, Punkt hell für Kontrast.
                AppLogo(
                    size = 30.dp,
                    ring = MaterialTheme.colorScheme.onPrimary,
                    dot = MaterialTheme.colorScheme.primaryContainer,
                    halo = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
