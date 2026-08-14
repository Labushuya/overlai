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
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import de.overlai.core.ui.theme.OverlAiTheme

// CHANGE-MARKER: Entry-Points (P2.4, siehe CHANGELOG.md)
// ACTION_PROCESS_TEXT: "OverlAI" im Text-Selektions-Menü jeder App. Gemischter Flow:
// Schnellaktionen + Kopieren/Einfügen (ephemer) ODER "In Chat öffnen" (persistenter Chat).
@AndroidEntryPoint
class ProcessTextActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val selected =
            intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        val readonly = intent?.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false) ?: false
        val canReplace = !readonly

        if (selected.isBlank()) {
            Toast.makeText(this, "OverlAI: keine Textauswahl", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val deps = EntryPointAccessors.fromApplication(applicationContext, ShareDependencies::class.java)

        setContent {
            OverlAiTheme {
                ShareFlow(
                    deps = deps,
                    sourceText = selected,
                    canReplaceInHost = canReplace,
                    onCopy = { copyAndFinish(it) },
                    onInsert =
                        if (canReplace) {
                            { replaceInHost(it) }
                        } else {
                            null
                        },
                    onDismiss = { finish() },
                    onOpenChat = { sessionId, pending -> openChatAndFinish(sessionId, pending) },
                )
            }
        }
    }

    private fun copyAndFinish(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("OverlAI", text))
        Toast.makeText(this, "OverlAI: Kopiert", Toast.LENGTH_SHORT).show()
        finish()
    }

    // Ergebnis zurück in die Host-App schreiben (ersetzt die Selektion).
    private fun replaceInHost(text: String) {
        setResult(Activity.RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, text))
        finish()
    }
}
