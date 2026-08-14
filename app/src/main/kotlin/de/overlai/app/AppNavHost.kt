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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import de.overlai.app.notification.ChatNotificationManager
import de.overlai.conversation.HandoverGenerator
import de.overlai.core.ui.util.OnResume
import de.overlai.feature.chat.ChatListScreen
import de.overlai.feature.chat.ChatListViewModel
import de.overlai.feature.chat.ChatScreen
import de.overlai.feature.chat.ChatViewModel
import de.overlai.feature.chat.NewChatViewModel
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
import de.overlai.feature.updater.DebugUpdatesNotice
import de.overlai.feature.updater.UpdateViewModel
import de.overlai.feature.updater.UpdatesScreen
import de.overlai.llm.providers.ProviderRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// CHANGE-MARKER v0.2.1: Navigation (siehe CHANGELOG.md)
// Zentraler Navigations-Graph. Alle Screens werden hier verdrahtet; die
// feature-Module kennen einander nicht (Komposition nur hier in :app).
object Routes {
    const val CHAT = "chat" // Chat-Liste (Tab-Root)
    const val CHAT_DETAIL = "chat/{sessionId}" // einzelner Chat

    fun chatDetail(sessionId: String) = "chat/$sessionId"
}

@Composable
fun AppNavHost(
    deps: AppDependencies,
    navController: NavHostController,
    pendingInput: kotlinx.coroutines.flow.MutableStateFlow<Pair<String, String>?>,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.CHAT,
        modifier = modifier,
    ) {
        composable(Routes.CHAT) {
            ChatListRoute(deps, navController)
        }

        composable(Routes.CHAT_DETAIL) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId")
            if (sessionId != null) {
                ChatRoute(deps, navController, sessionId, pendingInput)
            }
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
            // Debug-Build (…app.debug): Updater deaktiviert — andere Signatur als Release,
            // ein Update würde an INSTALL_FAILED_UPDATE_INCOMPATIBLE scheitern.
            if (context.packageName.endsWith(".debug")) {
                DebugUpdatesNotice(
                    versionName = deps.versionName,
                    onBack = { navController.popBackStack() },
                )
            } else {
                val vm =
                    viewModel<UpdateViewModel>(
                        factory =
                            simpleFactory {
                                UpdateViewModel(
                                    currentVersion = deps.versionName,
                                    checker = deps.updater.checker,
                                    downloader = deps.updater.downloader,
                                    installer = deps.updater.installer,
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

// Overlay-Bubble-Toggle: hält Enabled-Wunsch (SettingsStore) + Live-Permission-Status.
// P2.1c: Toggle ist von der Berechtigung ENTKOPPELT — der Wunsch wird immer gespeichert;
// der Service startet nur, wenn die Berechtigung da ist. „Zum Berechtigungs-Menü" navigiert
// ins gebündelte Permission-Menü (kein direkter Grant-Intent mehr hier).
@Composable
private fun OverlayRoute(
    deps: AppDependencies,
    navController: NavHostController,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }
    var notificationEnabled by remember { mutableStateOf(false) }
    // Nach Rückkehr aus den System-Einstellungen (Permission erteilt) neu einlesen. Erteilt der
    // Nutzer die Berechtigung und die Bubble war gewünscht, den Service jetzt nachstarten —
    // aber den Toggle NICHT umschalten (Berechtigung ≠ Aktion).
    OnResume {
        hasPermission = PermissionChecks.canDrawOverlays(context)
        scope.launch {
            enabled = deps.settingsStore.overlayEnabled.first()
            if (enabled && hasPermission) OverlayService.start(context)
            notificationEnabled = deps.settingsStore.notificationEnabled.first()
        }
    }
    OverlaySettingsScreen(
        enabled = enabled,
        hasOverlayPermission = hasPermission,
        onToggle = { wanted ->
            enabled = wanted
            scope.launch { deps.settingsStore.setOverlayEnabled(wanted) }
            // Service nur starten, wenn die Berechtigung da ist; sonst bleibt der Wunsch
            // gespeichert und die Bubble erscheint, sobald die Berechtigung erteilt wurde.
            if (wanted && hasPermission) {
                OverlayService.start(context)
            } else if (!wanted) {
                OverlayService.stop(context)
            }
        },
        onOpenPermissions = {
            navController.navigate(SettingsRoutes.PERMISSIONS)
        },
        onBack = { navController.popBackStack() },
        notification =
            de.overlai.feature.settings.AccessToggle(
                enabled = notificationEnabled,
                onToggle = { wanted ->
                    notificationEnabled = wanted
                    scope.launch {
                        deps.settingsStore.setNotificationEnabled(wanted)
                        if (wanted) {
                            val activeId = deps.settingsStore.activeSessionId.first()
                            ChatNotificationManager.show(context, activeId)
                        } else {
                            ChatNotificationManager.cancel(context)
                        }
                    }
                },
            ),
    )
}

// Chat-Liste (Tab-Root): Sessions verwalten. First-run-Routing ins Provider-Setup.
@Composable
private fun ChatListRoute(
    deps: AppDependencies,
    navController: NavHostController,
) {
    val vm =
        viewModel<ChatListViewModel>(
            factory = simpleFactory { ChatListViewModel(deps.sessionRepository, deps.settingsStore) },
        )
    val newChatVm =
        viewModel<NewChatViewModel>(
            factory =
                simpleFactory {
                    NewChatViewModel(
                        repo = deps.sessionRepository,
                        settingsStore = deps.settingsStore,
                        keyVault = deps.keyVault,
                        catalog = deps.modelCatalog,
                    )
                },
        )
    // First-run: einmalig ins Provider-Setup routen, wenn noch kein Key + Onboarding nie gezeigt.
    LaunchedEffect(Unit) {
        val shown = deps.settingsStore.onboardingShown.first()
        val activeId = deps.settingsStore.activeProviderId.first()
        if (!shown && !deps.keyVault.hasKey(activeId)) {
            deps.settingsStore.markOnboardingShown()
            navController.navigate(SettingsRoutes.PROVIDER)
        }
    }
    ChatListScreen(
        viewModel = vm,
        newChatViewModel = newChatVm,
        onOpenSession = { sessionId -> navController.navigate(Routes.chatDetail(sessionId)) },
    )
}

// Einzelner Chat (persistente Session mit eigenem Provider/Modell + Verlauf).
@Composable
private fun ChatRoute(
    deps: AppDependencies,
    navController: NavHostController,
    sessionId: String,
    pendingInput: kotlinx.coroutines.flow.MutableStateFlow<Pair<String, String>?>,
) {
    // Session-Metadaten (Provider/Modell) laden, dann das ViewModel damit bauen. Bis geladen:
    // nichts rendern (kurzer Moment). key(sessionId) → bei Wechsel neues VM.
    var meta by remember(sessionId) { mutableStateOf<de.overlai.core.data.chat.ChatSession?>(null) }
    LaunchedEffect(sessionId) {
        meta = deps.sessionRepository.getSession(sessionId)
    }
    val session = meta ?: return
    val vm =
        viewModel<ChatViewModel>(
            key = sessionId,
            factory =
                simpleFactory {
                    ChatViewModel(
                        engine = deps.conversationEngine,
                        repo = deps.sessionRepository,
                        handover = HandoverGenerator(deps.conversationEngine, deps.sessionRepository),
                        sessionId = session.id,
                        providerId = session.providerId,
                        modelId = session.modelId,
                    )
                },
        )
    // E3b: In einer frisch per Handover/Share erzeugten Session das LLM automatisch antworten
    // lassen (idempotent im VM per Verlaufs-Fingerabdruck).
    LaunchedEffect(sessionId) { vm.maybeAutostart() }
    // P2.4 Share „erst ergänzen": vorbereiteten Text ins Eingabefeld setzen (nicht senden).
    val pending by pendingInput.collectAsStateWithLifecycle()
    LaunchedEffect(pending, sessionId) {
        pending?.takeIf { it.first == sessionId }?.let {
            vm.onInputChange(it.second)
            pendingInput.value = null
        }
    }
    ChatScreen(
        viewModel = vm,
        onOpenOnboarding = { navController.navigate(SettingsRoutes.PROVIDER) },
        onBack = { navController.popBackStack() },
        // Handover → neue Session desselben Chats: alte Detailseite aus dem Backstack nehmen
        // und die neue öffnen (kein Zurück in die volle Alt-Session).
        onOpenSession = { newId ->
            navController.navigate(Routes.chatDetail(newId)) {
                popUpTo(Routes.CHAT_DETAIL) { inclusive = true }
            }
        },
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
                            // --- Bubble-Berechtigungen gebündelt (P2.1c) ---
                            PermissionItem(
                                id = "overlay",
                                title = "Bubble: Über anderen Apps anzeigen",
                                rationale = "Pflicht für die Overlay-Bubble — blendet OverlAI über anderen Apps ein.",
                                granted = PermissionChecks.canDrawOverlays(context),
                            ),
                            PermissionItem(
                                id = "notifications",
                                title = "Bubble: Benachrichtigungen",
                                rationale =
                                    "Die Bubble läuft als Vordergrund-Dienst und braucht dafür eine " +
                                        "Benachrichtigung. Außerdem für Update-Hinweise.",
                                granted = PermissionChecks.notificationsEnabled(context),
                            ),
                            PermissionItem(
                                id = "floating_window_hint",
                                title = "Bubble: „Schwebefenster\" (geräteabhängig)",
                                rationale =
                                    "Manche Hersteller (z.B. Honor/Huawei EMUI, Xiaomi) verstecken zusätzlich " +
                                        "einen „Schwebefenster\"-Schalter pro App. Bleibt die Bubble trotz erteilter " +
                                        "Berechtigung unsichtbar, aktiviere ihn in den System-App-Infos.",
                                granted = false,
                                isInfo = true,
                            ),
                        ),
                )
        }
    }
    PermissionHubScreen(
        state = state,
        onBack = { navController.popBackStack() },
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
