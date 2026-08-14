package de.overlai.app

import android.content.Intent
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
import de.overlai.app.notification.ChatNotificationManager
import de.overlai.common.ThemePreferences
import de.overlai.conversation.ConversationEngine
import de.overlai.core.data.SettingsStore
import de.overlai.core.data.chat.SessionRepository
import de.overlai.core.ui.theme.OverlAiTheme
import de.overlai.feature.overlay.OverlayService
import de.overlai.feature.permissions.PermissionChecks
import de.overlai.feature.settings.SettingsRoutes
import de.overlai.feature.updater.ApkDownloader
import de.overlai.feature.updater.PackageInstallerSession
import de.overlai.feature.updater.UpdateChecker
import de.overlai.llm.HttpModelCatalog
import de.overlai.llm.ProviderFactory
import de.overlai.security.KeyVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

    @Inject lateinit var modelCatalog: HttpModelCatalog

    @Inject lateinit var conversationEngine: ConversationEngine

    @Inject lateinit var sessionRepository: SessionRepository

    // Vor dem ersten Frame gesetzt; Splash hält, solange null.
    private val themeState = MutableStateFlow<ThemePreferences?>(null)

    // P2.4: von außen (Notification/Share) angefordertes Ziel — sessionId, die direkt geöffnet
    // werden soll. AppNavHost konsumiert diesen Flow und navigiert einmalig dorthin.
    private val pendingOpenSession = MutableStateFlow<String?>(null)

    // P2.4 Share „erst ergänzen": Text, der beim Öffnen des Chats ins Eingabefeld vorbereitet
    // (aber NICHT gesendet) wird. Paar aus (sessionId, text).
    private val pendingInput = MutableStateFlow<Pair<String, String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { themeState.value == null }
        handleIntent(intent)

        // Theme-Präferenz einmalig laden, dann laufend beobachten.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                settingsStore.themePreferences.collect { themeState.value = it }
            }
        }

        // Overlay-Bubble nach App-Start wiederherstellen: war sie zuletzt an und ist die
        // Berechtigung (noch) erteilt, den Service neu starten (Service ist START_NOT_STICKY,
        // überlebt einen Prozess-Kill also nicht von selbst).
        lifecycleScope.launch {
            if (settingsStore.overlayEnabled.first() && PermissionChecks.canDrawOverlays(this@MainActivity)) {
                OverlayService.start(this@MainActivity)
            }
            // P2.4: Benachrichtigungs-Zugang wiederherstellen (persistente Notification).
            if (settingsStore.notificationEnabled.first() &&
                PermissionChecks.notificationsEnabled(this@MainActivity)
            ) {
                val activeId = settingsStore.activeSessionId.first()
                ChatNotificationManager.show(this@MainActivity, activeId)
            }
        }

        enableEdgeToEdge()
        setContent {
            val prefs by themeState.collectAsStateWithLifecycle()
            val resolved = prefs ?: return@setContent // Splash hält noch
            OverlAiApp(
                prefs = resolved,
                pendingOpenSession = pendingOpenSession,
                pendingInput = pendingInput,
                deps =
                    AppDependencies(
                        keyVault = keyVault,
                        providerFactory = providerFactory,
                        settingsStore = settingsStore,
                        updater = UpdaterBundle(updateChecker, apkDownloader, packageInstaller),
                        modelCatalog = modelCatalog,
                        conversationEngine = conversationEngine,
                        sessionRepository = sessionRepository,
                        versionName = versionName(),
                    ),
            )
        }
    }

    private fun versionName(): String =
        runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        }.getOrDefault("?")

    // Bei erneutem Start mit neuem Intent (singleTask) das Ziel aktualisieren.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    // Deep-Link-Extra auswerten: EXTRA_OPEN_SESSION → diese Session direkt öffnen;
    // EXTRA_PENDING_INPUT → Text im Eingabefeld vorbereiten (Share „erst ergänzen").
    private fun handleIntent(intent: Intent?) {
        val session = intent?.getStringExtra(EXTRA_OPEN_SESSION)?.takeIf { it.isNotBlank() } ?: return
        pendingOpenSession.value = session
        intent.getStringExtra(EXTRA_PENDING_INPUT)?.takeIf { it.isNotBlank() }?.let {
            pendingInput.value = session to it
        }
    }

    companion object {
        // Von Notification/Share gesetzt: sessionId, die MainActivity direkt öffnen soll.
        const val EXTRA_OPEN_SESSION = "de.overlai.app.extra.OPEN_SESSION"

        // Share „erst ergänzen": Text, der im Eingabefeld vorbereitet (nicht gesendet) wird.
        const val EXTRA_PENDING_INPUT = "de.overlai.app.extra.PENDING_INPUT"
    }
}

// Gebündelte App-Abhängigkeiten, die der NavGraph an die Screens durchreicht.
class AppDependencies(
    val keyVault: KeyVault,
    val providerFactory: ProviderFactory,
    val settingsStore: SettingsStore,
    val updater: UpdaterBundle,
    val modelCatalog: HttpModelCatalog,
    val conversationEngine: ConversationEngine,
    val sessionRepository: SessionRepository,
    val versionName: String,
)

// Die drei Updater-Bausteine (nur von der Updates-Route gebraucht) — gebündelt, damit
// AppDependencies nicht überläuft.
class UpdaterBundle(
    val checker: UpdateChecker,
    val downloader: ApkDownloader,
    val installer: PackageInstallerSession,
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
    pendingOpenSession: MutableStateFlow<String?>,
    pendingInput: MutableStateFlow<Pair<String, String>?>,
    deps: AppDependencies,
) {
    OverlAiTheme(prefs = prefs) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        // Bottom-Bar nur auf den Tab-Roots zeigen; Sub-Screens haben eigenen Back-Arrow.
        val showBottomBar = currentRoute in setOf(Routes.CHAT, SettingsRoutes.HOME)

        // P2.4: von außen angefordertes Ziel (Notification/Share) einmalig öffnen.
        val pending by pendingOpenSession.collectAsStateWithLifecycle()
        androidx.compose.runtime.LaunchedEffect(pending) {
            pending?.let { id ->
                navController.navigate(Routes.chatDetail(id))
                pendingOpenSession.value = null
            }
        }

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
                pendingInput = pendingInput,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
