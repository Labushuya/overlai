package de.overlai.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import de.overlai.feature.chat.ChatScreen
import de.overlai.feature.chat.ChatViewModel
import de.overlai.feature.onboarding.OnboardingScreen
import de.overlai.feature.onboarding.OnboardingViewModel
import de.overlai.feature.permissions.PermissionChecks
import de.overlai.feature.permissions.PermissionHubScreen
import de.overlai.feature.permissions.PermissionHubState
import de.overlai.feature.permissions.PermissionItem
import de.overlai.feature.settings.AboutScreen
import de.overlai.feature.settings.AppearanceScreen
import de.overlai.feature.settings.AppearanceViewModel
import de.overlai.feature.settings.SettingsListScreen
import de.overlai.feature.settings.SettingsRoutes
import de.overlai.feature.updater.UpdateViewModel
import de.overlai.feature.updater.UpdatesScreen
import de.overlai.llm.providers.ProviderRegistry
import kotlinx.coroutines.flow.first

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

        composable(SettingsRoutes.HOME) {
            val context = LocalContext.current
            var providerName by remember { mutableStateOf("") }
            var hasKey by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                val id = deps.settingsStore.activeProviderId.first()
                providerName = ProviderRegistry.byId(id)?.displayName ?: id
                hasKey = deps.keyVault.hasKey(id)
            }
            SettingsListScreen(
                activeProviderName = providerName,
                hasActiveKey = hasKey,
                onOpen = { navController.navigate(it) },
            )
        }

        composable(SettingsRoutes.PROVIDER) {
            val vm =
                viewModel<OnboardingViewModel>(
                    factory = simpleFactory { OnboardingViewModel(deps.keyVault, deps.settingsStore) },
                )
            OnboardingScreen(
                viewModel = vm,
                onDone = { navController.popBackStack() },
            )
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
    }
}

// Permission-Hub als eigene Route-Composable (hält den Live-Status).
@Composable
private fun PermissionsRoute(
    deps: AppDependencies,
    navController: NavHostController,
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(PermissionHubState()) }
    LaunchedEffect(Unit) {
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
                    ),
            )
    }
    PermissionHubScreen(
        state = state,
        onFix = { item ->
            when (item.id) {
                "api_key" -> navController.navigate(SettingsRoutes.PROVIDER)
                "install_packages" -> context.startActivity(PermissionChecks.installUnknownAppsIntent(context))
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
