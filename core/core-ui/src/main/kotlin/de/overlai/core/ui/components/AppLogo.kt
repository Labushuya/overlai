package de.overlai.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// CHANGE-MARKER: Logo-Asset (P2.5, siehe CHANGELOG.md)
// Das D4-Marken-Logo als In-App-Composable (Header, Empty-States, Overlay-Bubble): Monogramm-O
// (Ring) + schwebender Punkt mit Funke = KI-Ebene über der App. Zeichnet mit Theme-Farben, passt
// sich also hell/dunkel an. [tint] überschreibt optional die Ringfarbe (z.B. onPrimary in der Bubble).
@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    ring: Color = MaterialTheme.colorScheme.primary,
    dot: Color = MaterialTheme.colorScheme.surface,
    halo: Color = MaterialTheme.colorScheme.onSurface,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val c = Offset(w * 0.5f, w * 0.5f)
        val ringRadius = w * 0.30f
        val ringStroke = w * 0.11f
        // Ring "O".
        drawCircle(color = ring, radius = ringRadius, center = c, style = Stroke(width = ringStroke))
        // Schwebender Punkt oben-rechts: Halo (Kontrast) + Punkt + Funke.
        val dotCenter = Offset(w * 0.72f, w * 0.30f)
        drawCircle(color = halo, radius = w * 0.135f, center = dotCenter)
        drawCircle(color = dot, radius = w * 0.10f, center = dotCenter)
        drawCircle(color = ring, radius = w * 0.04f, center = dotCenter)
    }
}
