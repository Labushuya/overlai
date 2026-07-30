package de.overlai.feature.share

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

// CHANGE-MARKER v0.1.0: Initiales Projektgrundgerüst (siehe CHANGELOG.md)
// Share-Target (ACTION_SEND) für Text und Bild. v0.1.0 = Stub. In M2:
// Text -> LLM; Bild -> ML Kit OCR (feature-ocr) -> LLM.
class ShareReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = intent?.type.orEmpty()
        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT)

        // TODO(M2): Bild via EXTRA_STREAM -> OCR; Text direkt -> Chat-Flow.
        val msg = when {
            type.startsWith("image/") -> "OverlAI: Bild empfangen"
            !sharedText.isNullOrBlank() -> "OverlAI: ${sharedText.take(40)}…"
            else -> "OverlAI: Inhalt empfangen"
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        finish()
    }
}
