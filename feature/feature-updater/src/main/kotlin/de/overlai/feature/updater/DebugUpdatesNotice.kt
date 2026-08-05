package de.overlai.feature.updater

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// CHANGE-MARKER: Updater im Debug-Build deaktiviert (siehe CHANGELOG.md)
// Der In-App-Updater ist im Debug-Build (Package …app.debug) bewusst abgeschaltet:
// die Debug-Variante ist anders signiert als die Release-App, ein Update auf eine
// Release-APK würde ohnehin an INSTALL_FAILED_UPDATE_INCOMPATIBLE scheitern. Statt des
// Checkers zeigt der Updates-Screen im Debug nur diesen Hinweis (Version zur Info).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugUpdatesNotice(
    versionName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Updates & Version") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Debug-Build",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "Version $versionName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Der In-App-Updater ist im Debug-Build deaktiviert. " +
                    "Debug-Builds werden per adb/CI installiert, nicht über den Updater.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
