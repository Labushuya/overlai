package de.overlai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import de.overlai.core.data.SettingsStore
import de.overlai.core.ui.theme.OverlAiTheme
import de.overlai.llm.ProviderFactory
import de.overlai.security.KeyVault
import javax.inject.Inject

// CHANGE-MARKER v0.1.0: Initiales Projektgrundgerüst (siehe CHANGELOG.md)
// Einzige Activity (Single-Activity-Compose-Architektur). Hostet den NavGraph.
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var keyVault: KeyVault

    @Inject lateinit var providerFactory: ProviderFactory

    @Inject lateinit var settingsStore: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OverlAiApp(keyVault, providerFactory, settingsStore)
        }
    }
}

@Composable
private fun OverlAiApp(
    keyVault: KeyVault,
    providerFactory: ProviderFactory,
    settingsStore: SettingsStore,
) {
    OverlAiTheme {
        val navController = rememberNavController()
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            AppNavHost(
                keyVault = keyVault,
                providerFactory = providerFactory,
                settingsStore = settingsStore,
                navController = navController,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
