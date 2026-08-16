package de.overlai.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.overlai.common.ThemeMode

// CHANGE-MARKER: Darstellung/Theme (P2.5, siehe CHANGELOG.md)
// Theme-Einstellungen: nur System/Hell/Dunkel. Material You entfernt (feste Marken-Palette).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    viewModel: AppearanceViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Darstellung") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Modus", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ModeOption("System folgen", prefs.mode == ThemeMode.SYSTEM) { viewModel.setMode(ThemeMode.SYSTEM) }
            ModeOption("Hell", prefs.mode == ThemeMode.LIGHT) { viewModel.setMode(ThemeMode.LIGHT) }
            ModeOption("Dunkel", prefs.mode == ThemeMode.DARK) { viewModel.setMode(ThemeMode.DARK) }

            // P2.5: OverlAI nutzt eine feste Marken-Palette (warm) — bewusst KEIN Material You,
            // damit Fullscreen und Overlay konsistent aussehen. Der frühere (folgenlose) Schalter
            // wurde entfernt.
            Text(
                "OverlAI nutzt eine feste Marken-Farbpalette. Nur der Hell-/Dunkel-Modus ist wählbar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun ModeOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
