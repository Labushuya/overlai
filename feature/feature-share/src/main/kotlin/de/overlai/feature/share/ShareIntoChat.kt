package de.overlai.feature.share

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import de.overlai.feature.chat.NewChatSheet
import de.overlai.feature.chat.NewChatViewModel

// CHANGE-MARKER: Entry-Points (P2.4, siehe CHANGELOG.md)
// Gemischter Share-Flow: kompakte QuickActionSurface (Schnellaktionen + Kopieren/Einfügen,
// ephemer) UND "In Chat öffnen" → Provider/Modell-Wahl (NewChatSheet) → Wahl "sofort absenden"
// vs. "erst ergänzen". Öffnet MainActivity (im :app-Modul) per expliziten setClassName-Intent.

internal fun quickActionFactory(deps: ShareDependencies): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            QuickActionViewModel(
                providerFactory = deps.providerFactory(),
                keyVault = deps.keyVault(),
                settingsStore = deps.settingsStore(),
            ) as T
    }

internal fun newChatFactory(deps: ShareDependencies): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NewChatViewModel(
                repo = deps.sessionRepository(),
                settingsStore = deps.settingsStore(),
                keyVault = deps.keyVault(),
                catalog = deps.modelCatalog(),
            ) as T
    }

// Öffnet MainActivity im neuen Chat und beendet die (transparente) Share-Activity.
// pendingInput != null → Text wird im Chat-Eingabefeld vorbereitet (nicht gesendet).
internal fun Activity.openChatAndFinish(
    sessionId: String,
    pendingInput: String? = null,
) {
    val intent =
        Intent(Intent.ACTION_VIEW).apply {
            setClassName(packageName, "de.overlai.app.MainActivity")
            putExtra("de.overlai.app.extra.OPEN_SESSION", sessionId)
            pendingInput?.let { putExtra("de.overlai.app.extra.PENDING_INPUT", it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    startActivity(intent)
    finish()
}

// Der komplette Share-Flow als Composable — von beiden Entry-Activities genutzt.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShareFlow(
    deps: ShareDependencies,
    sourceText: String,
    canReplaceInHost: Boolean,
    onCopy: (String) -> Unit,
    onInsert: ((String) -> Unit)?,
    onDismiss: () -> Unit,
    onOpenChat: (sessionId: String, pendingInput: String?) -> Unit,
) {
    // Text, mit dem der Chat gestartet werden soll (Ergebnis einer Quick-Action ODER Quelltext).
    var chatText by remember { mutableStateOf<String?>(null) }

    val quickVm = viewModel<QuickActionViewModel>(factory = quickActionFactory(deps))
    quickVm.setSource(sourceText, canReplaceInHost = canReplaceInHost)

    // Über der Host-App: abdunkelnder Scrim (Tap schließt) + die Surface angedockt, aber mit
    // Abstand vom Rand (Systemleisten via safeDrawing) — schwebt statt am Bildschirmrand zu kleben.
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    onDismiss()
                },
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Klick auf die Surface selbst darf NICHT durch zum Scrim (sonst schließt es).
        Box(
            modifier =
                Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 24.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            QuickActionSurface(
                viewModel = quickVm,
                onCopy = onCopy,
                onInsert = onInsert,
                onOpenInChat = { chatText = it },
                onDismiss = onDismiss,
            )
        }
    }

    val text = chatText
    if (text != null) {
        val newChatVm = viewModel<NewChatViewModel>(factory = newChatFactory(deps))
        var createdId by remember { mutableStateOf<String?>(null) }
        val id = createdId
        if (id == null) {
            NewChatSheet(
                viewModel = newChatVm,
                onDismiss = { chatText = null },
                onCreated = { createdId = it },
                // Text NICHT automatisch senden — die Absenden-Wahl entscheidet darüber.
                initialUserText = null,
            )
        } else {
            SendChoiceSheet(
                // Sofort absenden: Text als erste User-Nachricht → Autostart im Chat.
                onSendNow = { newChatVm.appendUserMessage(id, text) { onOpenChat(id, null) } },
                // Erst ergänzen: Text nur ins Eingabefeld vorbereiten.
                onEditFirst = { onOpenChat(id, text) },
                onDismiss = { chatText = null },
            )
        }
    }
}

// Wahl nach der Modell-Auswahl: sofort absenden oder erst im Eingabefeld ergänzen.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendChoiceSheet(
    onSendNow: () -> Unit,
    onEditFirst: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Wie fortfahren?")
            Button(onClick = onSendNow, modifier = Modifier.fillMaxWidth()) { Text("Sofort absenden") }
            OutlinedButton(onClick = onEditFirst, modifier = Modifier.fillMaxWidth()) {
                Text("In Chat übernehmen & ergänzen")
            }
        }
    }
}
