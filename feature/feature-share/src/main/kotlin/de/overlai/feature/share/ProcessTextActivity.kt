package de.overlai.feature.share

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

// CHANGE-MARKER v0.1.0: Initiales Projektgrundgerüst (siehe CHANGELOG.md)
// ACTION_PROCESS_TEXT-Entry-Point: erscheint im Text-Selektions-Menü jeder App.
// v0.1.0 = Stub, der die Selektion entgegennimmt und quittiert; die echte
// LLM-Verarbeitung + Copy-Fallback kommt in M2.
class ProcessTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val selected =
            intent
                ?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
                ?.toString()
                .orEmpty()

        // TODO(M2): selektierten Text an core-llm übergeben, Antwort anzeigen,
        //           bei readonly-Host Copy-Fallback + Toast.
        Toast.makeText(
            this,
            if (selected.isBlank()) "OverlAI: kein Text" else "OverlAI: ${selected.take(40)}…",
            Toast.LENGTH_SHORT,
        ).show()
        finish()
    }
}
