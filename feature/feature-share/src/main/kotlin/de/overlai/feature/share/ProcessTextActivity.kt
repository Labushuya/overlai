package de.overlai.feature.share

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import de.overlai.core.ui.theme.OverlAiTheme
import de.overlai.llm.providers.ProviderRegistry

// CHANGE-MARKER v0.1.0: Entry-Points (siehe CHANGELOG.md)
// ACTION_PROCESS_TEXT: "OverlAI" im Text-Selektions-Menü jeder App. Nimmt die
// Selektion, bietet Quick-Actions, zeigt das Ergebnis. Insert-in-Place ist
// host-abhängig (READONLY-Flag) -> sonst Copy-Fallback.
@AndroidEntryPoint
class ProcessTextActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val selected =
            intent
                ?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
                ?.toString()
                .orEmpty()
        val readonly = intent?.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false) ?: false
        val canReplace = !readonly

        if (selected.isBlank()) {
            Toast.makeText(this, "OverlAI: keine Textauswahl", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val deps =
            EntryPointAccessors.fromApplication(applicationContext, ShareDependencies::class.java)

        setContent {
            OverlAiTheme {
                val vm =
                    viewModel<QuickActionViewModel>(
                        factory = quickActionFactory(deps),
                    )
                vm.setSource(selected, canReplaceInHost = canReplace)

                QuickActionSurface(
                    viewModel = vm,
                    onCopy = { copyToClipboard(it); toastAndFinish("Kopiert") },
                    onInsert =
                        if (canReplace) {
                            { replaceInHost(it) }
                        } else {
                            null
                        },
                    onDismiss = { finish() },
                )
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("OverlAI", text))
    }

    // Ergebnis zurück in die Host-App schreiben (ersetzt die Selektion).
    private fun replaceInHost(text: String) {
        val result = Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, text)
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    private fun toastAndFinish(msg: String) {
        Toast.makeText(this, "OverlAI: $msg", Toast.LENGTH_SHORT).show()
        finish()
    }
}

// Gemeinsame ViewModel-Factory für die Entry-Activities.
internal fun quickActionFactory(deps: ShareDependencies): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            QuickActionViewModel(
                providerConfig = ProviderRegistry.OPENAI,
                providerFactory = deps.providerFactory(),
                keyVault = deps.keyVault(),
            ) as T
    }
