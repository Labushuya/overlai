package de.overlai.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import de.overlai.core.ui.util.OnResume
import de.overlai.feature.chat.ChatScreen
import de.overlai.feature.chat.ChatViewModel
import de.overlai.feature.onboarding.ProviderHubScreen
import de.overlai.feature.onboarding.ProviderHubViewModel
import de.overlai.feature.overlay.OverlayService
import de.overlai.feature.permissions.PermissionChecks
import de.overlai.feature.permissions.PermissionHubScreen
import de.overlai.feature.permissions.PermissionHubState
import de.overlai.feature.permissions.PermissionItem
import de.overlai.feature.settings.AboutScreen
import de.overlai.feature.settings.AppearanceScreen
import de.overlai.feature.settings.AppearanceViewModel
import de.overlai.feature.settings.OverlaySettingsScreen
import de.overlai.feature.settings.SettingsListScreen
import de.overlai.feature.settings.SettingsRoutes
import de.overlai.feature.updater.UpdateViewModel
import de.overlai.feature.updater.UpdatesScreen
import de.overlai.llm.providers.ProviderRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// CHANGE-MARKER v0.2.1: Navigation (siehe CHANGELOG.md)
// Zentraler Navigations-Graph. Alle Screens werden hier verdrahtet; die
// feature-Module kennen einander nicht (Komposition nur hier in :app).
object Routes {
    const val CHAT = "chat"
}

@Composable
fun AppNavHost(
    deps: AppDependencies,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.CHAT,
        modifier = modifier,
    ) {
        composable(Routes.CHAT) {
            ChatRoute(deps, navController)
        }

        composable(SettingsRoutes.HOME) {
            SettingsHomeRoute(deps, navController)
        }

        composable(SettingsRoutes.PROVIDER) {
            val vm =
                viewModel<ProviderHubViewModel>(
                    factory =
                        simpleFactory {
                            ProviderHubViewModel(
                                keyVault = deps.keyVault,
                                settingsStore = deps.settingsStore,
                                catalog = deps.modelCatalog,
                            )
                        },
                )
            ProviderHubScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(SettingsRoutes.PERMISSIONS) {
            PermissionsRoute(deps, navController)
        }

        composable(SettingsRoutes.UPDATES) {
            val context = LocalContext.current
            val vm =
                viewModel<UpdateViewModel>(
                    factory =
                        simpleFactory {
                            UpdateViewModel(
                                currentVersion = deps.versionName,
                                checker = deps.updateChecker,
                                downloader = deps.apkDownloader,
                                installer = deps.packageInstaller,
                            )
                        },
                )
            UpdatesScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onFixInstallPermission = {
                    context.startActivity(PermissionChecks.installUnknownAppsIntent(context))
                },
            )
        }

        composable(SettingsRoutes.APPEARANCE) {
            val vm =
                viewModel<AppearanceViewModel>(
                    factory = simpleFactory { AppearanceViewModel(deps.settingsStore) },
                )
            AppearanceScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(SettingsRoutes.ABOUT) {
            AboutScreen(versionName = deps.versionName, onBack = { navController.popBackStack() })
        }

        composable(SettingsRoutes.OVERLAY) {
            OverlayRoute(deps, navController)
        }
    }
}

// Overlay-Bubble-Toggle: hält Enabled-Wunsch (SettingsStore) + Live-Permission-Status
// und steuert den Foreground-Service. Permission-Check + Service-Start liegen hier in
// :app (das Feature-Modul bleibt UI-only).
@Composable
private fun OverlayRoute(
    deps: AppDependencies,
    navController: NavHostController,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }
    // Nach Rückkehr aus den System-Einstellungen (Permission erteilt) neu einlesen.
    OnResume {
        hasPermission = PermissionChecks.canDrawOverlays(context)
        scope.launch { enabled = deps.settingsStore.overlayEnabled.first() }
    }
    OverlaySettingsScreen(
        enabled = enabled,
        hasOverlayPermission = hasPermission,
        onToggle = { wanted ->
            enabled = wanted
            scope.launch { deps.settingsStore.setOverlayEnabled(wanted) }
            if (wanted) OverlayService.start(context) else OverlayService.stop(context)
        },
        onRequestPermission = {
            context.startActivity(PermissionChecks.overlayPermissionIntent(context))
        },
        onBack = { navController.popBackStack() },
    )
}

// Chat-Route: ViewModel + First-run-Routing ins Provider-Setup.
@Composable
private fun ChatRoute(
    deps: AppDependencies,
    navController: NavHostController,
) {
    val vm =
        viewModel<ChatViewModel>(
            factory =
                simpleFactory {
                    ChatViewModel(
                        providerFactory = deps.providerFactory,
                        keyVault = deps.keyVault,
                        settingsStore = deps.settingsStore,
                    )
                },
        )
    // First-run: einmalig ins Provider-Setup routen, wenn noch kein Key +
    // Onboarding noch nie gezeigt. Flag persistiert, damit es einmalig bleibt.
    LaunchedEffect(Unit) {
        vm.refreshActiveProvider()
        val shown = deps.settingsStore.onboardingShown.first()
        val activeId = deps.settingsStore.activeProviderId.first()
        if (!shown && !deps.keyVault.hasKey(activeId)) {
            deps.settingsStore.markOnboardingShown()
            navController.navigate(SettingsRoutes.PROVIDER)
        }
    }
    ChatScreen(
        viewModel = vm,
        onOpenOnboarding = { navController.navigate(SettingsRoutes.PROVIDER) },
    )
}

// Einstellungs-Übersicht: lädt aktiven Provider + Key-Status für die Hero-Card.
@Composable
private fun SettingsHomeRoute(
    deps: AppDependencies,
    navController: NavHostController,
) {
    val scope = rememberCoroutineScope()
    var providerName by remember { mutableStateOf("") }
    var hasKey by remember { mutableStateOf(false) }
    // Bei jeder Rückkehr neu laden (z.B. nach Key-Eingabe im Provider-Screen).
    OnResume {
        scope.launch {
            val id = deps.settingsStore.activeProviderId.first()
            providerName = ProviderRegistry.byId(id)?.displayName ?: id
            hasKey = deps.keyVault.hasKey(id)
        }
    }
    SettingsListScreen(
        activeProviderName = providerName,
        hasActiveKey = hasKey,
        onOpen = { navController.navigate(it) },
    )
}

// Permission-Hub als eigene Route-Composable (hält den Live-Status).
@Composable
private fun PermissionsRoute(
    deps: AppDependencies,
    navController: NavHostController,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(PermissionHubState()) }
    // ON_RESUME statt LaunchedEffect(Unit): nach Rückkehr aus den System-Einstellungen
    // (Berechtigung erteilt) wird der grün/rot-Status sofort neu gelesen.
    OnResume {
        scope.launch {
            val activeId = deps.settingsStore.activeProviderId.first()
            state =
                PermissionHubState(
                    items =
                        listOf(
                            PermissionItem(
                                id = "api_key",
                                title = "API-Key hinterlegt",
                                rationale = "OverlAI braucht deinen Provider-Key (BYOK), um Anfragen zu senden.",
                                granted = deps.keyVault.hasKey(activeId),
                                fixIsSystemSetting = false,
                            ),
                            PermissionItem(
                                id = "install_packages",
                                title = "Unbekannte Apps installieren",
                                rationale = "Nötig, damit der In-App-Updater neue Versionen installieren kann.",
                                granted = PermissionChecks.canInstallPackages(context),
                            ),
                            PermissionItem(
                                id = "notifications",
                                title = "Benachrichtigungen",
                                rationale = "Für Update- und Download-Hinweise.",
                                granted = PermissionChecks.notificationsEnabled(context),
                            ),
                            PermissionItem(
                                id = "overlay",
                                title = "Über anderen Apps anzeigen",
                                rationale = "Nötig für die Overlay-Bubble, die OverlAI über anderen Apps einblendet.",
                                granted = PermissionChecks.canDrawOverlays(context),
                            ),
                        ),
                )
        }
    }
    PermissionHubScreen(
        state = state,
        onFix = { item ->
            when (item.id) {
                "api_key" -> navController.navigate(SettingsRoutes.PROVIDER)
                "install_packages" -> context.startActivity(PermissionChecks.installUnknownAppsIntent(context))
                "overlay" -> context.startActivity(PermissionChecks.overlayPermissionIntent(context))
                else -> context.startActivity(PermissionChecks.appDetailsIntent(context))
            }
        },
    )
}

// Minimaler ViewModelProvider.Factory-Helfer für parametrisierte ViewModels.
private inline fun <reified VM : ViewModel> simpleFactory(crossinline builder: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = builder() as T
    }
