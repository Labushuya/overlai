package de.overlai.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// CHANGE-MARKER: Design-System (P2.5, siehe CHANGELOG.md)
// Weiche, etwas großzügigere Radien passend zur warmen Palette (statt M3-Default).
val OverlaiShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )
