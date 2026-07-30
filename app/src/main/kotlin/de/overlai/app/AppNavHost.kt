package de.overlai.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import de.overlai.llm.ProviderFactory
import de.overlai.llm.providers.ProviderRegistry
import de.overlai.security.KeyVault

// CHANGE-MARKER v0.1.0: Navigation (siehe CHANGELOG.md)
// Zentraler Navigations-Graph. Die ViewModels haben bewusst einfache
// Konstruktoren (core-* ohne DI-Annotationen); hier werden sie über eine
// kleine ViewModelProvider.Factory mit den DI-bereitgestellten Bausteinen erzeugt.
object Routes {
    const val ONBOARDING = "onboarding"
    const val CHAT = "chat"
}

@Composable
fun AppNavHost(
    keyVault: KeyVault,
    providerFactory: ProviderFactory,
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
                                providerConfig = ProviderRegistry.OPENAI,
                                providerFactory = providerFactory,
                                keyVault = keyVault,
                            )
                        },
                )
            ChatScreen(
                viewModel = vm,
                onOpenOnboarding = { navController.navigate(Routes.ONBOARDING) },
            )
        }

        composable(Routes.ONBOARDING) {
            val vm =
                viewModel<OnboardingViewModel>(
                    factory = simpleFactory { OnboardingViewModel(keyVault) },
                )
            OnboardingScreen(
                viewModel = vm,
                onDone = { navController.popBackStack() },
            )
        }
    }
}

// Minimaler ViewModelProvider.Factory-Helfer für parametrisierte ViewModels.
private inline fun <reified VM : ViewModel> simpleFactory(
    crossinline builder: () -> VM,
): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = builder() as T
    }
