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
import de.overlai.core.data.SettingsStore
import de.overlai.feature.chat.ChatScreen
import de.overlai.feature.chat.ChatViewModel
import de.overlai.feature.onboarding.OnboardingScreen
import de.overlai.feature.onboarding.OnboardingViewModel
import de.overlai.feature.permissions.PermissionChecks
import de.overlai.feature.permissions.PermissionHubScreen
import de.overlai.feature.permissions.PermissionHubState
import de.overlai.feature.permissions.PermissionItem
import de.overlai.llm.ProviderFactory
import de.overlai.security.KeyVault
import kotlinx.coroutines.flow.first

// CHANGE-MARKER v0.1.0: Navigation (siehe CHANGELOG.md)
// Zentraler Navigations-Graph. Die ViewModels haben bewusst einfache
// Konstruktoren (core-* ohne DI-Annotationen); hier werden sie über eine
// kleine ViewModelProvider.Factory mit den DI-bereitgestellten Bausteinen erzeugt.
object Routes {
    const val ONBOARDING = "onboarding"
    const val CHAT = "chat"
    const val PERMISSIONS = "permissions"
}

@Composable
fun AppNavHost(
    keyVault: KeyVault,
    providerFactory: ProviderFactory,
    settingsStore: SettingsStore,
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
                                providerFactory = providerFactory,
                                keyVault = keyVault,
                                settingsStore = settingsStore,
                            )
                        },
                )
            // Nach Rückkehr aus dem Onboarding den aktiven Provider neu laden.
            LaunchedEffect(Unit) { vm.refreshActiveProvider() }
            ChatScreen(
                viewModel = vm,
                onOpenOnboarding = { navController.navigate(Routes.ONBOARDING) },
            )
        }

        composable(Routes.ONBOARDING) {
            val vm =
                viewModel<OnboardingViewModel>(
                    factory = simpleFactory { OnboardingViewModel(keyVault, settingsStore) },
                )
            OnboardingScreen(
                viewModel = vm,
                onDone = { navController.popBackStack() },
            )
        }

        composable(Routes.PERMISSIONS) {
            val context = LocalContext.current
            var state by remember { mutableStateOf(PermissionHubState()) }
            // Bei jedem Betreten neu prüfen (Nutzer kommt oft aus den Settings zurück).
            LaunchedEffect(Unit) {
                val activeId = settingsStore.activeProviderId.first()
                state =
                    PermissionHubState(
                        items =
                            listOf(
                                PermissionItem(
                                    id = "api_key",
                                    title = "API-Key hinterlegt",
                                    rationale = "OverlAI braucht deinen Provider-Key (BYOK), um Anfragen zu senden.",
                                    granted = keyVault.hasKey(activeId),
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
                        "api_key" -> navController.navigate(Routes.ONBOARDING)
                        "install_packages" ->
                            context.startActivity(PermissionChecks.installUnknownAppsIntent(context))
                        else -> context.startActivity(PermissionChecks.appDetailsIntent(context))
                    }
                },
            )
        }
    }
}

// Minimaler ViewModelProvider.Factory-Helfer für parametrisierte ViewModels.
private inline fun <reified VM : ViewModel> simpleFactory(crossinline builder: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = builder() as T
    }
