package de.overlai.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

// CHANGE-MARKER v0.2.1: Einstellungs-Hub (siehe CHANGELOG.md)
// Übersicht des Einstellungs-Hubs. Zeigt den aktiven Provider und navigiert zu
// den fünf Bereichen — emittiert dabei NUR Route-Strings (Komposition in :app),
// daher keine Abhängigkeit auf andere feature-Module.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsListScreen(
    activeProviderName: String,
    activeModelId: String?,
    hasActiveKey: Boolean,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Einstellungen") }) },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                // Klickbare Hero-Pill: aktiver Standard-Anbieter (+ Modell) → tippen öffnet den
                // ProviderHub zur Standard-Anbieter-/Modell-Wahl (P2.5).
                ElevatedCard(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .clickable { onOpen(SettingsRoutes.PROVIDER) },
                ) {
                    ListItem(
                        overlineContent = { Text("Standard-Anbieter & Modell") },
                        headlineContent = { Text(activeProviderName) },
                        supportingContent =
                            activeModelId?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                        trailingContent = {
                            AssistChip(
                                onClick = { onOpen(SettingsRoutes.PROVIDER) },
                                label = { Text(if (hasActiveKey) "Bereit" else "Kein Key") },
                            )
                        },
                    )
                }
            }

            settingsRow("Provider & API-Keys", Icons.Filled.Key, SettingsRoutes.PROVIDER, onOpen)
            settingsRow("Overlay-Bubble", Icons.Filled.BubbleChart, SettingsRoutes.OVERLAY, onOpen)
            settingsRow("Berechtigungen & Status", Icons.Filled.VerifiedUser, SettingsRoutes.PERMISSIONS, onOpen)
            settingsRow("Updates & Version", Icons.Filled.SystemUpdate, SettingsRoutes.UPDATES, onOpen)
            settingsRow("Darstellung", Icons.Filled.Palette, SettingsRoutes.APPEARANCE, onOpen)
            settingsRow("Über & Datenschutz", Icons.Filled.Info, SettingsRoutes.ABOUT, onOpen)
        }
    }
}

private fun LazyListScope.settingsRow(
    title: String,
    icon: ImageVector,
    route: String,
    onOpen: (String) -> Unit,
) {
    item {
        ListItem(
            leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            headlineContent = { Text(title) },
            modifier = Modifier.clickable { onOpen(route) },
        )
        HorizontalDivider()
    }
}
