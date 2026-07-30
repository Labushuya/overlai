package de.overlai.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

// CHANGE-MARKER v0.1.0: Initiales Projektgrundgerüst (siehe CHANGELOG.md)
// Zentraler Navigations-Graph. Routen sind hier als String-Konstanten definiert;
// die einzelnen Feature-Screens werden ab M1/M2 eingehängt (Chat, Onboarding,
// Permission Hub, Updater). In v0.1.0 nur ein Home-Platzhalter, damit die
// M0-Pipeline (Build/Signing/Update) an einem lauffähigen APK verifizierbar ist.
object Routes {
    const val HOME = "home"
    const val ONBOARDING = "onboarding"
    const val CHAT = "chat"
    const val PERMISSIONS = "permissions"
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            HomePlaceholder(
                onOpenChat = { navController.navigate(Routes.CHAT) },
                onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
            )
        }
        // TODO(M1): composable(Routes.ONBOARDING) { OnboardingScreen(...) }
        // TODO(M1): composable(Routes.CHAT) { ChatScreen(...) }
        // TODO(M2): composable(Routes.PERMISSIONS) { PermissionHubScreen(...) }
    }
}

// Minimaler Home-Screen für v0.1.0 (M0-Bootstrap). Wird in M1 durch den echten
// Einstieg (Onboarding-Check -> Chat) ersetzt.
@Composable
private fun HomePlaceholder(
    onOpenChat: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text("OverlAI")
        Button(onClick = onOpenChat) { Text("Chat") }
        Button(onClick = onOpenPermissions) { Text("Berechtigungen prüfen") }
    }
}
