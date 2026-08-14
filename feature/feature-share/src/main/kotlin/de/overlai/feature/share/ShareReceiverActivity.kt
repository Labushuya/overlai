package de.overlai.feature.share

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import de.overlai.core.ui.theme.OverlAiTheme
import de.overlai.feature.ocr.MlKitOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// CHANGE-MARKER: Entry-Points (P2.4, siehe CHANGELOG.md)
// Share-Target (ACTION_SEND): Text bzw. Bild (→ ML Kit OCR, offline) startet einen
// PERSISTENTEN Chat (Provider/Modell-Wahl via NewChatSheet) mit dem Text als erster
// Nachricht. Ersetzt die frühere ephemere Quick-Action-Surface.
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {
    private val ocr = MlKitOcr()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val type = intent?.type.orEmpty()
        val deps = EntryPointAccessors.fromApplication(applicationContext, ShareDependencies::class.java)

        setContent {
            OverlAiTheme {
                // Quelltext: bei Text sofort da, bei Bild nach OCR nachgereicht.
                var sourceText by remember {
                    mutableStateOf(
                        if (type == "text/plain") intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty() else null,
                    )
                }
                if (type.startsWith("image/")) {
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        val uri = extractStreamUri()
                        if (uri != null) runOcr(uri) { sourceText = it } else sourceText = ""
                    }
                }

                val text = sourceText
                if (!text.isNullOrBlank()) {
                    ShareFlow(
                        deps = deps,
                        sourceText = text,
                        canReplaceInHost = false,
                        onCopy = { copyAndFinish(it) },
                        onInsert = null,
                        onDismiss = { finish() },
                        onOpenChat = { sessionId, pending -> openChatAndFinish(sessionId, pending) },
                    )
                }
                // Solange OCR läuft (sourceText == null) bzw. leer: transparent, kein Sheet.
            }
        }
    }

    private fun copyAndFinish(text: String) {
        val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("OverlAI", text))
        Toast.makeText(this, "OverlAI: Kopiert", Toast.LENGTH_SHORT).show()
        finish()
    }

    @Suppress("DEPRECATION")
    private fun extractStreamUri(): Uri? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent?.getParcelableExtra(Intent.EXTRA_STREAM)
        }

    private fun runOcr(
        uri: Uri,
        onText: (String) -> Unit,
    ) {
        lifecycleScope.launch {
            val text =
                runCatching {
                    val bitmap =
                        withContext(Dispatchers.IO) {
                            contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                        } ?: return@runCatching ""
                    ocr.recognize(bitmap)
                }.getOrDefault("")
            if (text.isBlank()) {
                Toast.makeText(this@ShareReceiverActivity, "OverlAI: kein Text im Bild erkannt", Toast.LENGTH_SHORT)
                    .show()
                finish()
            } else {
                onText(text)
            }
        }
    }
}
