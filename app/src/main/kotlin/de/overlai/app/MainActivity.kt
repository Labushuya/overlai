package de.overlai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import de.overlai.common.ThemePreferences
import de.overlai.core.data.SettingsStore
import de.overlai.core.ui.theme.OverlAiTheme
import de.overlai.feature.settings.SettingsRoutes
import de.overlai.feature.updater.ApkDownloader
import de.overlai.feature.updater.PackageInstallerSession
import de.overlai.feature.updater.UpdateChecker
import de.overlai.llm.ProviderFactory
import de.overlai.security.KeyVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// CHANGE-MARKER v0.2.1: Bottom-Navigation + Theme (siehe CHANGELOG.md)
// Einzige Activity. Sammelt die Theme-Präferenz (Splash hält, bis geladen — kein
// Theme-Flash) und hostet den NavGraph mit Bottom-Navigation (Chat + Einstellungen).
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var keyVault: KeyVault

    @Inject lateinit var providerFactory: ProviderFactory

    @Inject lateinit var settingsStore: SettingsStore

    @Inject lateinit var updateChecker: UpdateChecker

    @Inject lateinit var apkDownloader: ApkDownloader

    @Inject lateinit var packageInstaller: PackageInstallerSession

    // Vor dem ersten Frame gesetzt; Splash hält, solange null.
    private val themeState = MutableStateFlow<ThemePreferences?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { themeState.value == null }

        // Theme-Präferenz einmalig laden, dann laufend beobachten.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                settingsStore.themePreferences.collect { themeState.value = it }
            }
        }

        enableEdgeToEdge()
        setContent {
            val prefs by themeState.collectAsStateWithLifecycle()
            val resolved = prefs ?: return@setContent // Splash hält noch
            OverlAiApp(
                prefs = resolved,
                deps =
                    AppDependencies(
                        keyVault = keyVault,
                        providerFactory = providerFactory,
                        settingsStore = settingsStore,
                        updateChecker = updateChecker,
                        apkDownloader = apkDownloader,
                        packageInstaller = packageInstaller,
                        versionName = versionName(),
                    ),
            )
        }
    }

    private fun versionName(): String =
        runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        }.getOrDefault("?")
}

// Gebündelte App-Abhängigkeiten, die der NavGraph an die Screens durchreicht.
class AppDependencies(
    val keyVault: KeyVault,
    val providerFactory: ProviderFactory,
    val settingsStore: SettingsStore,
    val updateChecker: UpdateChecker,
    val apkDownloader: ApkDownloader,
    val packageInstaller: PackageInstallerSession,
    val versionName: String,
)

// Tabs der Bottom-Navigation.
private enum class TopTab(
    val route: String,
    val label: String,
) {
    CHAT(Routes.CHAT, "Chat"),
    SETTINGS(SettingsRoutes.HOME, "Einstellungen"),
}

@Composable
private fun OverlAiApp(
    prefs: ThemePreferences,
    deps: AppDependencies,
) {
    OverlAiTheme(prefs = prefs) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        // Bottom-Bar nur auf den Tab-Roots zeigen; Sub-Screens haben eigenen Back-Arrow.
        val showBottomBar = currentRoute in setOf(Routes.CHAT, SettingsRoutes.HOME)

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        TopTab.entries.forEach { tab ->
                            val selected =
                                backStackEntry?.destination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector =
                                            if (tab == TopTab.CHAT) {
                                                Icons.AutoMirrored.Filled.Chat
                                            } else {
                                                Icons.Filled.Settings
                                            },
                                        contentDescription = tab.label,
                                    )
                                },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            AppNavHost(
                deps = deps,
                navController = navController,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
