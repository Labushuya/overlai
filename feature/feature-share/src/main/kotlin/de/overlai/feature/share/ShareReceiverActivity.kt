package de.overlai.feature.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import de.overlai.core.ui.theme.OverlAiTheme
import de.overlai.feature.ocr.MlKitOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// CHANGE-MARKER v0.1.0: Entry-Points (siehe CHANGELOG.md)
// Share-Target (ACTION_SEND): Text direkt -> Quick-Actions. Bild -> ML Kit OCR
// (offline) -> erkannter Text als Quelle. Erscheint im System-Share-Sheet.
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {
    private val ocr = MlKitOcr()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val type = intent?.type.orEmpty()
        val deps =
            EntryPointAccessors.fromApplication(applicationContext, ShareDependencies::class.java)

        setContent {
            OverlAiTheme {
                val vm = viewModel<QuickActionViewModel>(factory = quickActionFactory(deps))

                when {
                    type == "text/plain" -> {
                        val text = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                        vm.setSource(text, canReplaceInHost = false)
                    }
                    type.startsWith("image/") -> {
                        val uri = extractStreamUri()
                        if (uri != null) {
                            runOcr(uri) { recognized -> vm.setSource(recognized, canReplaceInHost = false) }
                        }
                    }
                }

                QuickActionSurface(
                    viewModel = vm,
                    onCopy = { copyAndFinish(it) },
                    onInsert = null,
                    onDismiss = { finish() },
                )
            }
        }
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
            }
            onText(text)
        }
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("OverlAI", text))
    }

    private fun copyAndFinish(text: String) {
        copyToClipboard(text)
        toastAndFinish("Kopiert")
    }

    private fun toastAndFinish(msg: String) {
        Toast.makeText(this, "OverlAI: $msg", Toast.LENGTH_SHORT).show()
        finish()
    }
}
